package com.example.studyappandroid.data.ai.local

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

sealed class LocalModelState {
    data object Uninitialized : LocalModelState()
    data class Preparing(val progress: Float = 0f, val stage: String = "Preparing offline AI teacher...") : LocalModelState()
    data class Ready(
        val modelName: String = LocalModelManager.MODEL_DISPLAY_NAME,
        val contextTokens: Int = 2048,
        val loadTimeMs: Long = 0L,
    ) : LocalModelState()
    data class Error(val message: String, val isLowMemory: Boolean = false) : LocalModelState()
}

data class ChatTurn(
    val role: String, // "system", "user", "assistant"
    val content: String,
)

@Singleton
class LocalLlmEngine @Inject constructor(
    private val modelManager: LocalModelManager,
) {
    companion object {
        private const val TAG = "LocalLlmEngine"
    }

    private val inferenceDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "StudyRoast-Llama").apply { priority = Thread.NORM_PRIORITY }
    }.asCoroutineDispatcher()

    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engineMutex = Mutex()

    private val _state = MutableStateFlow<LocalModelState>(LocalModelState.Uninitialized)
    val state: StateFlow<LocalModelState> = _state.asStateFlow()

    @Volatile
    private var isModelLoaded = false
    private var initDeferred: CompletableDeferred<Result<Unit>>? = null

    fun warmUpAsync() {
        warmupScope.launch {
            ensureReady()
        }
    }

    suspend fun ensureReady(): Result<Unit> {
        if (isModelLoaded) return Result.success(Unit)

        var isOwner = false
        val deferred = synchronized(this) {
            if (isModelLoaded) return Result.success(Unit)
            initDeferred ?: CompletableDeferred<Result<Unit>>().also {
                initDeferred = it
                isOwner = true
            }
        }

        if (!isOwner) {
            return deferred.await()
        }

        val result = try {
            engineMutex.withLock {
                if (isModelLoaded) {
                    Result.success(Unit)
                } else {
                    loadInternal()
                }
            }
        } finally {
            synchronized(this) {
                if (initDeferred === deferred) {
                    initDeferred = null
                }
            }
        }
        deferred.complete(result)
        return result
    }

    private suspend fun loadInternal(): Result<Unit> {
        val startMs = System.currentTimeMillis()
        _state.value = LocalModelState.Preparing(0.05f, "Checking device memory...")

        val budget = modelManager.getMemoryBudget()
        if (budget.isLowMemory && budget.availableBytes < 220L * 1024L * 1024L) {
            val msg = "Device memory is critically low (${budget.availableBytes / (1024 * 1024)} MB free). Close background apps and retry."
            _state.value = LocalModelState.Error(msg, isLowMemory = true)
            return Result.failure(IllegalStateException(msg))
        }

        _state.value = LocalModelState.Preparing(0.15f, "Preparing offline model...")
        val modelFileResult = modelManager.ensureModelFileExtracted { progress ->
            _state.value = LocalModelState.Preparing(
                progress = 0.15f + progress * 0.65f,
                stage = "Unpacking offline AI teacher (${(progress * 100).toInt()}%)...",
            )
        }

        val modelFile = modelFileResult.getOrElse { err ->
            val msg = err.localizedMessage ?: "Failed to prepare offline AI model."
            _state.value = LocalModelState.Error(msg, isLowMemory = false)
            return Result.failure(err)
        }

        _state.value = LocalModelState.Preparing(0.85f, "Loading AI teacher into memory...")

        return withContext(inferenceDispatcher) {
            runCatching {
                NativeLlamaBridge.nativeInit(modelManager.getNativeLibDir())
                val rc = NativeLlamaBridge.nativeLoadModel(
                    modelPath = modelFile.absolutePath,
                    nCtx = budget.recommendedContextTokens,
                    requestedThreads = budget.recommendedThreads,
                )
                if (rc != 0) {
                    error("Native model load failed with code $rc")
                }
                isModelLoaded = true
                val elapsed = System.currentTimeMillis() - startMs
                Log.i(TAG, "Offline LLM ready in ${elapsed}ms (ctx=${budget.recommendedContextTokens}, threads=${budget.recommendedThreads})")
                _state.value = LocalModelState.Ready(
                    modelName = LocalModelManager.MODEL_DISPLAY_NAME,
                    contextTokens = budget.recommendedContextTokens,
                    loadTimeMs = elapsed,
                )
            }.onFailure { err ->
                isModelLoaded = false
                val msg = err.localizedMessage ?: "Unable to initialize offline AI model."
                Log.e(TAG, "Offline LLM init error: $msg", err)
                _state.value = LocalModelState.Error(msg, isLowMemory = budget.isLowMemory)
            }
        }
    }

    suspend fun generate(
        messages: List<ChatTurn>,
        maxTokens: Int = 256,
        temperature: Float = 0.55f,
        onToken: ((String) -> Unit)? = null,
    ): Result<String> {
        val ready = ensureReady()
        if (ready.isFailure) {
            return Result.failure(ready.exceptionOrNull() ?: IllegalStateException("Offline AI is not ready."))
        }

        return engineMutex.withLock {
            withContext(inferenceDispatcher) {
                runCatching {
                    val roles = messages.map { it.role }.toTypedArray()
                    val contents = messages.map { it.content }.toTypedArray()

                    val prepCode = NativeLlamaBridge.nativePreparePrompt(
                        roles = roles,
                        contents = contents,
                        maxTokens = maxTokens,
                        temperature = temperature,
                    )
                    if (prepCode != 0) {
                        error("Prompt preparation failed ($prepCode)")
                    }

                    val sb = StringBuilder()
                    while (true) {
                        val piece = NativeLlamaBridge.nativeStepToken() ?: break
                        if (piece.isNotEmpty()) {
                            sb.append(piece)
                            onToken?.invoke(piece)
                        }
                    }
                    sb.toString().trim()
                }
            }
        }
    }

    fun cancelGeneration() {
        NativeLlamaBridge.nativeCancel()
    }

    suspend fun unloadForLowMemory() {
        NativeLlamaBridge.nativeCancel()
        engineMutex.withLock {
            withContext(inferenceDispatcher) {
                NativeLlamaBridge.nativeUnload()
                isModelLoaded = false
                _state.value = LocalModelState.Uninitialized
            }
        }
    }

    fun getSystemDiagnostics(): String {
        return runCatching {
            NativeLlamaBridge.nativeSystemInfo()
        }.getOrElse { "Native backend not initialized yet." }
    }
}
