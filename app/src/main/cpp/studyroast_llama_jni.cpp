#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cstring>
#include <mutex>
#include <sstream>
#include <string>
#include <unistd.h>
#include <vector>

#include "llama.h"
#include "logging.h"

namespace {

constexpr int kBatchSize = 256;
constexpr int kMinThreads = 2;
constexpr int kMaxThreads = 4;

std::mutex g_engine_mutex;
bool g_backend_initialized = false;

llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
llama_batch_ext * g_batch = nullptr;
llama_sampler * g_sampler = nullptr;

int g_n_ctx = 2048;
llama_pos g_current_pos = 0;
llama_pos g_stop_pos = 0;
std::string g_cached_utf8;
std::atomic<bool> g_cancel_requested{false};

bool is_valid_utf8(const char * s) {
    if (!s) return true;
    const auto * bytes = reinterpret_cast<const unsigned char *>(s);
    while (*bytes != 0x00) {
        int num = 0;
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

void unload_locked() {
    g_cached_utf8.clear();
    g_current_pos = 0;
    g_stop_pos = 0;
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_batch) {
        llama_batch_ext_free(g_batch);
        g_batch = nullptr;
    }
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

struct StoredChatMsg {
    std::string role;
    std::string content;
};

std::string format_chat_prompt(const llama_model * model, const std::vector<StoredChatMsg> & messages) {
    const char * tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl && std::strlen(tmpl) > 0) {
        std::vector<llama_chat_message> chat;
        chat.reserve(messages.size());
        size_t total_chars = 0;
        for (const auto & m : messages) {
            chat.push_back({m.role.c_str(), m.content.c_str()});
            total_chars += m.role.size() + m.content.size() + 32;
        }
        std::vector<char> buf(std::max<size_t>(1024, total_chars * 2));
        int32_t needed = llama_chat_apply_template(
                tmpl, chat.data(), chat.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
        if (needed > static_cast<int32_t>(buf.size())) {
            buf.resize(static_cast<size_t>(needed) + 16);
            needed = llama_chat_apply_template(
                    tmpl, chat.data(), chat.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
        }
        if (needed > 0 && needed <= static_cast<int32_t>(buf.size())) {
            return {buf.data(), static_cast<size_t>(needed)};
        }
    }

    // ChatML fallback (native format for Qwen2.5-Instruct)
    std::ostringstream oss;
    for (const auto & msg : messages) {
        oss << "<|im_start|>" << msg.role << "\n" << msg.content << "<|im_end|>\n";
    }
    oss << "<|im_start|>assistant\n";
    return oss.str();
}

std::vector<llama_token> tokenize_prompt(const llama_vocab * vocab, const std::string & text) {
    int32_t n = -llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), nullptr, 0, true, true);
    if (n <= 0) {
        return {};
    }
    std::vector<llama_token> tokens(static_cast<size_t>(n));
    int32_t actual = llama_tokenize(
            vocab, text.c_str(), static_cast<int32_t>(text.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (actual < 0) {
        return {};
    }
    tokens.resize(static_cast<size_t>(actual));
    return tokens;
}

std::string token_to_piece_str(const llama_vocab * vocab, llama_token token) {
    char stack_buf[128];
    int32_t n = llama_token_to_piece(vocab, token, stack_buf, sizeof(stack_buf), 0, true);
    if (n >= 0) {
        return std::string(stack_buf, static_cast<size_t>(n));
    }
    std::vector<char> heap_buf(static_cast<size_t>(-n));
    n = llama_token_to_piece(vocab, token, heap_buf.data(), static_cast<int32_t>(heap_buf.size()), 0, true);
    if (n > 0) {
        return std::string(heap_buf.data(), static_cast<size_t>(n));
    }
    return "";
}

int decode_tokens_batched(
        llama_context * ctx,
        llama_batch_ext * batch,
        const std::vector<llama_token> & tokens,
        llama_pos start_pos,
        bool compute_last_logit) {
    const int total = static_cast<int>(tokens.size());
    for (int i = 0; i < total; i += kBatchSize) {
        const int cur = std::min(total - i, kBatchSize);
        llama_batch_ext_clear(batch);
        for (int j = 0; j < cur; ++j) {
            const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i + j]);
            const llama_pos pos = start_pos + i + j;
            llama_batch_ext_set_pos(batch, idx, &pos);
        }
        if (compute_last_logit && i + cur == total && cur > 0) {
            llama_batch_ext_set_output_logits(batch, cur - 1, true);
        }
        if (llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
            SR_LOGE("llama_process failed at offset %d", i);
            return -1;
        }
    }
    return 0;
}

} // namespace

extern "C"
JNIEXPORT void JNICALL
Java_com_example_studyappandroid_data_ai_local_NativeLlamaBridge_nativeInit(
        JNIEnv * /*env*/,
        jobject /*thiz*/,
        jstring /*nativeLibDir*/) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_backend_initialized) {
        return;
    }
    llama_log_set(sr_llama_log_callback, nullptr);
    llama_backend_init();
    g_backend_initialized = true;
    SR_LOGI("llama_backend_init completed");
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_studyappandroid_data_ai_local_NativeLlamaBridge_nativeLoadModel(
        JNIEnv * env,
        jobject /*thiz*/,
        jstring jModelPath,
        jint nCtx,
        jint requestedThreads) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    unload_locked();

    const char * model_path = env->GetStringUTFChars(jModelPath, nullptr);
    if (!model_path) {
        return -1;
    }
    std::string path_str(model_path);
    env->ReleaseStringUTFChars(jModelPath, model_path);

    SR_LOGI("Loading GGUF model from %s", path_str.c_str());

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;

    g_model = llama_model_load_from_file(path_str.c_str(), model_params);
    if (!g_model) {
        SR_LOGE("Failed to load llama model from %s", path_str.c_str());
        return -2;
    }

    const int online_cpus = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
    const int auto_threads = std::max(kMinThreads, std::min(kMaxThreads, std::max(2, online_cpus - 2)));
    const int n_threads = requestedThreads > 0 ? std::min(requestedThreads, 6) : auto_threads;

    const int trained_ctx = llama_model_n_ctx_train(g_model);
    g_n_ctx = std::max(512, std::min(static_cast<int>(nCtx), trained_ctx > 0 ? trained_ctx : 4096));

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = g_n_ctx;
    ctx_params.n_batch = kBatchSize;
    ctx_params.n_ubatch = kBatchSize;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    g_context = llama_init_from_model(g_model, ctx_params);
    if (!g_context) {
        SR_LOGE("Failed to initialize llama context (n_ctx=%d)", g_n_ctx);
        unload_locked();
        return -3;
    }

    g_batch = llama_batch_ext_init(g_context);
    if (!g_batch) {
        SR_LOGE("Failed to allocate llama_batch_ext");
        unload_locked();
        return -4;
    }

    SR_LOGI("Model loaded successfully (n_ctx=%d, threads=%d)", g_n_ctx, n_threads);
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_studyappandroid_data_ai_local_NativeLlamaBridge_nativePreparePrompt(
        JNIEnv * env,
        jobject /*thiz*/,
        jobjectArray jRoles,
        jobjectArray jContents,
        jint maxTokens,
        jfloat temperature) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_model || !g_context || !g_batch) {
        return -1;
    }

    g_cancel_requested.store(false);
    g_cached_utf8.clear();
    g_current_pos = 0;
    g_stop_pos = 0;

    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    llama_memory_clear(llama_get_memory(g_context), true);

    const jsize count = env->GetArrayLength(jRoles);
    const jsize content_count = env->GetArrayLength(jContents);
    if (count <= 0 || count != content_count) {
        return -2;
    }

    std::vector<StoredChatMsg> messages;
    messages.reserve(count);
    for (jsize i = 0; i < count; ++i) {
        auto jRole = static_cast<jstring>(env->GetObjectArrayElement(jRoles, i));
        auto jContent = static_cast<jstring>(env->GetObjectArrayElement(jContents, i));
        const char * role_c = jRole ? env->GetStringUTFChars(jRole, nullptr) : nullptr;
        const char * content_c = jContent ? env->GetStringUTFChars(jContent, nullptr) : nullptr;

        StoredChatMsg msg;
        msg.role = role_c ? role_c : "user";
        msg.content = content_c ? content_c : "";
        messages.push_back(std::move(msg));

        if (jRole && role_c) env->ReleaseStringUTFChars(jRole, role_c);
        if (jContent && content_c) env->ReleaseStringUTFChars(jContent, content_c);
        if (jRole) env->DeleteLocalRef(jRole);
        if (jContent) env->DeleteLocalRef(jContent);
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const std::string rendered_prompt = format_chat_prompt(g_model, messages);
    std::vector<llama_token> tokens = tokenize_prompt(vocab, rendered_prompt);
    if (tokens.empty()) {
        return -3;
    }

    const int safe_gen_budget = std::max(32, std::min(static_cast<int>(maxTokens), g_n_ctx / 2));
    const int max_prompt_tokens = g_n_ctx - safe_gen_budget - 8;
    if (max_prompt_tokens <= 32) {
        return -3;
    }

    if (static_cast<int>(tokens.size()) > max_prompt_tokens) {
        SR_LOGW("Prompt tokens (%d) exceed budget (%d); truncating middle.",
                static_cast<int>(tokens.size()), max_prompt_tokens);
        const int head_keep = max_prompt_tokens / 2;
        const int tail_keep = max_prompt_tokens - head_keep;
        std::vector<llama_token> trimmed;
        trimmed.reserve(max_prompt_tokens);
        trimmed.insert(trimmed.end(), tokens.begin(), tokens.begin() + head_keep);
        trimmed.insert(trimmed.end(), tokens.end() - tail_keep, tokens.end());
        tokens = std::move(trimmed);
    }

    if (decode_tokens_batched(g_context, g_batch, tokens, 0, true) != 0) {
        return -4;
    }

    auto sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    const int32_t n_vocab = llama_vocab_n_tokens(vocab);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_penalties(n_vocab, 64, 1.12f, 0.0f, 0.0f));
    if (temperature <= 0.01f) {
        llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.90f, 1));
        llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(0.05f, 1));
        llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    g_current_pos = static_cast<llama_pos>(tokens.size());
    g_stop_pos = std::min(static_cast<llama_pos>(g_n_ctx - 2), g_current_pos + safe_gen_budget);
    SR_LOGD("Prompt prepared: %d tokens, max_stop_pos=%d", static_cast<int>(tokens.size()), g_stop_pos);
    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_studyappandroid_data_ai_local_NativeLlamaBridge_nativeStepToken(
        JNIEnv * env,
        jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_model || !g_context || !g_batch || !g_sampler) {
        return nullptr;
    }
    if (g_cancel_requested.load() || g_current_pos >= g_stop_pos || g_current_pos >= g_n_ctx - 2) {
        return nullptr;
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const llama_token token_id = llama_sampler_sample(g_sampler, g_context, -1);

    if (llama_vocab_is_eog(vocab, token_id)) {
        return nullptr;
    }

    const std::string piece = token_to_piece_str(vocab, token_id);
    if (piece == "<|im_end|>" || piece == "<|endoftext|>" || piece == "<end_of_turn>") {
        return nullptr;
    }

    llama_batch_ext_clear(g_batch);
    const int32_t idx = llama_batch_ext_add_token(g_batch, 0, token_id);
    llama_batch_ext_set_pos(g_batch, idx, &g_current_pos);
    llama_batch_ext_set_output_logits(g_batch, idx, true);
    if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch) != 0) {
        SR_LOGE("llama_process failed during generation step at pos=%d", g_current_pos);
        return nullptr;
    }
    g_current_pos++;

    g_cached_utf8 += piece;
    if (is_valid_utf8(g_cached_utf8.c_str())) {
        jstring out = env->NewStringUTF(g_cached_utf8.c_str());
        g_cached_utf8.clear();
        return out;
    }
    return env->NewStringUTF("");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_studyappandroid_data_ai_local_NativeLlamaBridge_nativeCancel(
        JNIEnv * /*env*/,
        jobject /*thiz*/) {
    g_cancel_requested.store(true);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_studyappandroid_data_ai_local_NativeLlamaBridge_nativeUnload(
        JNIEnv * /*env*/,
        jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    unload_locked();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_studyappandroid_data_ai_local_NativeLlamaBridge_nativeSystemInfo(
        JNIEnv * env,
        jobject /*thiz*/) {
    return env->NewStringUTF(llama_print_system_info());
}
