#pragma once

#include <android/log.h>
#include "ggml.h"

#ifndef SR_LOG_TAG
#define SR_LOG_TAG "StudyRoastLlama"
#endif

#if defined(NDEBUG)
#define SR_LOG_MIN_LEVEL ANDROID_LOG_INFO
#else
#define SR_LOG_MIN_LEVEL ANDROID_LOG_DEBUG
#endif

static inline int sr_should_log(int prio) {
    return prio >= SR_LOG_MIN_LEVEL;
}

#define SR_LOGD(...) do { if (sr_should_log(ANDROID_LOG_DEBUG)) __android_log_print(ANDROID_LOG_DEBUG, SR_LOG_TAG, __VA_ARGS__); } while (0)
#define SR_LOGI(...) do { if (sr_should_log(ANDROID_LOG_INFO))  __android_log_print(ANDROID_LOG_INFO,  SR_LOG_TAG, __VA_ARGS__); } while (0)
#define SR_LOGW(...) do { if (sr_should_log(ANDROID_LOG_WARN))  __android_log_print(ANDROID_LOG_WARN,  SR_LOG_TAG, __VA_ARGS__); } while (0)
#define SR_LOGE(...) do { if (sr_should_log(ANDROID_LOG_ERROR)) __android_log_print(ANDROID_LOG_ERROR, SR_LOG_TAG, __VA_ARGS__); } while (0)

static inline int sr_android_log_prio(enum ggml_log_level level) {
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: return ANDROID_LOG_ERROR;
        case GGML_LOG_LEVEL_WARN:  return ANDROID_LOG_WARN;
        case GGML_LOG_LEVEL_INFO:  return ANDROID_LOG_INFO;
        case GGML_LOG_LEVEL_DEBUG: return ANDROID_LOG_DEBUG;
        default:                   return ANDROID_LOG_DEFAULT;
    }
}

static inline void sr_llama_log_callback(enum ggml_log_level level, const char * text, void *) {
    if (!text) return;
    const int prio = sr_android_log_prio(level);
    if (!sr_should_log(prio)) return;
    __android_log_write(prio, SR_LOG_TAG, text);
}
