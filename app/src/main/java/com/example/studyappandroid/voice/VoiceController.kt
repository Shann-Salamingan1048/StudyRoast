package com.example.studyappandroid.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.studyappandroid.domain.model.VoiceInteractionMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

data class VoiceControllerState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val partialTranscript: String = "",
    val errorMessage: String? = null,
    val isHandsFreeSession: Boolean = false,
)

@Singleton
class VoiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {
    companion object {
        private const val TAG = "VoiceController"
        private const val VOSK_ASSET_DIR = "vosk-model-small-en-us"
        private const val VOSK_ASSET_ZIP = "models/vosk-model-small-en-us-0.15.zip"
        private const val VOSK_SAMPLE_RATE = 16000.0f
        private const val VOSK_LISTEN_TIMEOUT_MS = 12000
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val voskMutex = Mutex()

    private val _voiceState = MutableStateFlow(VoiceControllerState())
    val voiceState: StateFlow<VoiceControllerState> = _voiceState.asStateFlow()

    private val _transcripts = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val transcripts: SharedFlow<String> = _transcripts.asSharedFlow()

    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var voskSpeechService: SpeechService? = null
    @Volatile
    private var voskTranscriptEmittedInTurn = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingSpeakText: String? = null
    private var autoResumeListeningJob: Job? = null

    private var voiceEnabled: Boolean = true
    private var speechRate: Float = 1.0f
    private var pitch: Float = 1.0f
    private var interactionMode: VoiceInteractionMode = VoiceInteractionMode.PUSH_TO_TALK

    fun updatePreferences(
        voiceEnabled: Boolean,
        speechRate: Float,
        pitch: Float,
        interactionMode: VoiceInteractionMode,
    ) {
        this.voiceEnabled = voiceEnabled
        this.speechRate = speechRate.coerceIn(0.6f, 1.8f)
        this.pitch = pitch.coerceIn(0.6f, 1.6f)
        this.interactionMode = interactionMode
        if (!voiceEnabled) {
            stopSpeaking()
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening() {
        runOnMain {
            autoResumeListeningJob?.cancel()

            if (!hasRecordAudioPermission()) {
                _voiceState.update {
                    it.copy(
                        isListening = false,
                        errorMessage = "Microphone permission is required for voice commands.",
                    )
                }
                return@runOnMain
            }

            // Barge-in: stop TTS before opening microphone
            if (_voiceState.value.isSpeaking) {
                stopSpeakingInternal()
            }

            val handsFree = interactionMode == VoiceInteractionMode.HANDS_FREE
            _voiceState.update {
                it.copy(
                    isListening = true,
                    partialTranscript = "",
                    errorMessage = null,
                    isHandsFreeSession = handsFree,
                )
            }

            ioScope.launch {
                val startedVosk = tryStartVoskListening()
                if (!startedVosk) {
                    runOnMain {
                        startPlatformSpeechRecognizerFallback()
                    }
                }
            }
        }
    }

    private suspend fun tryStartVoskListening(): Boolean = voskMutex.withLock {
        val modelDir = ensureVoskModelExtracted() ?: return@withLock false
        return@withLock runCatching {
            val model = voskModel ?: Model(modelDir.absolutePath).also { voskModel = it }
            voskSpeechService?.cancel()
            voskSpeechService?.shutdown()
            voskRecognizer?.close()

            val recognizer = Recognizer(model, VOSK_SAMPLE_RATE).also { voskRecognizer = it }
            val service = SpeechService(recognizer, VOSK_SAMPLE_RATE).also { voskSpeechService = it }
            voskTranscriptEmittedInTurn = false

            val started = service.startListening(
                object : VoskRecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        val partial = extractVoskField(hypothesis, "partial")
                        if (partial.isNotBlank()) {
                            _voiceState.update { it.copy(partialTranscript = partial) }
                        }
                    }

                    override fun onResult(hypothesis: String?) {
                        val text = extractVoskField(hypothesis, "text")
                        if (text.isNotBlank() && !voskTranscriptEmittedInTurn) {
                            voskTranscriptEmittedInTurn = true
                            _voiceState.update { it.copy(isListening = false, partialTranscript = "") }
                            _transcripts.tryEmit(text)
                            ioScope.launch {
                                voskMutex.withLock { voskSpeechService?.stop() }
                            }
                        }
                    }

                    override fun onFinalResult(hypothesis: String?) {
                        val text = extractVoskField(hypothesis, "text")
                        _voiceState.update { it.copy(isListening = false, partialTranscript = "") }
                        if (text.isNotBlank() && !voskTranscriptEmittedInTurn) {
                            voskTranscriptEmittedInTurn = true
                            _transcripts.tryEmit(text)
                        }
                    }

                    override fun onError(exception: Exception?) {
                        _voiceState.update {
                            it.copy(
                                isListening = false,
                                partialTranscript = "",
                                isHandsFreeSession = false,
                                errorMessage = exception?.localizedMessage ?: "Offline speech recognition error.",
                            )
                        }
                    }

                    override fun onTimeout() {
                        _voiceState.update {
                            it.copy(
                                isListening = false,
                                partialTranscript = "",
                                isHandsFreeSession = false,
                                errorMessage = "Didn't catch any speech. Tap the mic and try again.",
                            )
                        }
                    }
                },
                VOSK_LISTEN_TIMEOUT_MS,
            )
            started
        }.onFailure { err ->
            Log.w(TAG, "Vosk start failed, falling back to Android SpeechRecognizer: ${err.localizedMessage}")
        }.getOrDefault(false)
    }

    private suspend fun ensureVoskModelExtracted(): File? = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, VOSK_ASSET_DIR)
        val marker = File(targetDir, ".ready")
        if (marker.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            return@withContext targetDir
        }

        // Option A: Unpacked folder in assets/vosk-model-small-en-us/
        val assetEntries = runCatching { context.assets.list(VOSK_ASSET_DIR) }.getOrNull()
        if (!assetEntries.isNullOrEmpty()) {
            val ok = runCatching {
                targetDir.deleteRecursively()
                copyAssetTree(VOSK_ASSET_DIR, targetDir)
                marker.writeText("1")
                true
            }.getOrDefault(false)
            if (ok) return@withContext targetDir
        }

        // Option B: Zip archive in assets/models/vosk-model-small-en-us-0.15.zip
        val hasZip = runCatching {
            context.assets.open(VOSK_ASSET_ZIP).use { true }
        }.getOrDefault(false)
        if (hasZip) {
            val ok = runCatching {
                targetDir.deleteRecursively()
                targetDir.mkdirs()
                context.assets.open(VOSK_ASSET_ZIP).use { raw ->
                    ZipInputStream(raw.buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            // Strip top-level folder prefix if archive wraps files inside vosk-model-small-en-us-0.15/
                            val rel = entry.name.substringAfter('/', entry.name)
                            if (rel.isNotBlank()) {
                                val outFile = File(targetDir, rel)
                                if (outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                                    if (entry.isDirectory) {
                                        outFile.mkdirs()
                                    } else {
                                        outFile.parentFile?.mkdirs()
                                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                                    }
                                }
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
                marker.writeText("1")
                true
            }.getOrDefault(false)
            if (ok) return@withContext targetDir
        }

        null
    }

    private fun copyAssetTree(assetPath: String, destDir: File) {
        val children = context.assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            destDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destDir).use { output -> input.copyTo(output) }
            }
        } else {
            destDir.mkdirs()
            for (child in children) {
                copyAssetTree("$assetPath/$child", File(destDir, child))
            }
        }
    }

    private fun extractVoskField(rawJson: String?, field: String): String {
        if (rawJson.isNullOrBlank()) return ""
        return runCatching {
            json.parseToJsonElement(rawJson).jsonObject[field]?.jsonPrimitive?.content.orEmpty().trim()
        }.getOrDefault("")
    }

    private fun startPlatformSpeechRecognizerFallback() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.update {
                it.copy(
                    isListening = false,
                    isHandsFreeSession = false,
                    errorMessage = "Offline voice model not found and device speech recognition is unavailable.",
                )
            }
            return
        }

        val recognizer = ensureSpeechRecognizer() ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        runCatching {
            recognizer.cancel()
            recognizer.startListening(intent)
        }.onFailure { err ->
            _voiceState.update {
                it.copy(
                    isListening = false,
                    isHandsFreeSession = false,
                    errorMessage = err.localizedMessage ?: "Failed to start voice recognition.",
                )
            }
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (!voiceEnabled && !force) return
        val cleanText = sanitizeForSpeech(text)
        if (cleanText.isBlank()) return

        runOnMain {
            autoResumeListeningJob?.cancel()
            if (_voiceState.value.isListening) {
                stopListeningInternal()
            }

            val engine = ensureTts()
            if (!isTtsReady) {
                pendingSpeakText = cleanText
                return@runOnMain
            }
            speakInternal(engine, cleanText)
        }
    }

    fun stopSpeaking() {
        runOnMain {
            autoResumeListeningJob?.cancel()
            pendingSpeakText = null
            stopSpeakingInternal()
        }
    }

    fun stopAll() {
        runOnMain {
            autoResumeListeningJob?.cancel()
            pendingSpeakText = null
            stopSpeakingInternal()
            stopListeningInternal()
            _voiceState.update {
                it.copy(
                    isListening = false,
                    isSpeaking = false,
                    partialTranscript = "",
                    isHandsFreeSession = false,
                )
            }
        }
    }

    private fun stopListeningInternal() {
        runCatching { speechRecognizer?.cancel() }
        ioScope.launch {
            voskMutex.withLock {
                runCatching { voskSpeechService?.cancel() }
            }
        }
        _voiceState.update { it.copy(isListening = false, partialTranscript = "") }
    }

    fun reportError(message: String) {
        _voiceState.update { it.copy(errorMessage = message) }
    }

    fun clearError() {
        _voiceState.update { it.copy(errorMessage = null) }
    }

    fun release() {
        runOnMain {
            autoResumeListeningJob?.cancel()
            pendingSpeakText = null
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }
            tts = null
            isTtsReady = false
            _voiceState.value = VoiceControllerState()
        }
        ioScope.launch {
            voskMutex.withLock {
                runCatching { voskSpeechService?.cancel() }
                runCatching { voskSpeechService?.shutdown() }
                runCatching { voskRecognizer?.close() }
                runCatching { voskModel?.close() }
                voskSpeechService = null
                voskRecognizer = null
                voskModel = null
            }
        }
    }

    private fun stopSpeakingInternal() {
        runCatching { tts?.stop() }
        _voiceState.update { it.copy(isSpeaking = false) }
    }

    private fun ensureTts(): TextToSpeech {
        val existing = tts
        if (existing != null) return existing

        val created = TextToSpeech(context) { status ->
            runOnMain {
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    tts?.language = Locale.getDefault()
                    val pending = pendingSpeakText
                    pendingSpeakText = null
                    if (!pending.isNullOrBlank()) {
                        tts?.let { speakInternal(it, pending) }
                    }
                } else {
                    isTtsReady = false
                    pendingSpeakText = null
                    _voiceState.update {
                        it.copy(
                            isSpeaking = false,
                            errorMessage = "Text-to-Speech initialization failed.",
                        )
                    }
                }
            }
        }
        created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnMain {
                    _voiceState.update { it.copy(isSpeaking = true) }
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnMain {
                    _voiceState.update { it.copy(isSpeaking = false) }
                    maybeScheduleHandsFreeResume()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnMain {
                    _voiceState.update { it.copy(isSpeaking = false) }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                runOnMain {
                    _voiceState.update { it.copy(isSpeaking = false) }
                }
            }
        })
        tts = created
        return created
    }

    private fun speakInternal(engine: TextToSpeech, cleanText: String) {
        engine.setSpeechRate(speechRate)
        engine.setPitch(pitch)
        val utteranceId = UUID.randomUUID().toString()
        val result = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            _voiceState.update { it.copy(isSpeaking = false) }
        } else {
            _voiceState.update { it.copy(isSpeaking = true) }
        }
    }

    private fun maybeScheduleHandsFreeResume() {
        val cur = _voiceState.value
        if (cur.isHandsFreeSession &&
            interactionMode == VoiceInteractionMode.HANDS_FREE &&
            !cur.isListening &&
            hasRecordAudioPermission()
        ) {
            autoResumeListeningJob?.cancel()
            autoResumeListeningJob = mainScope.launch {
                delay(350.milliseconds)
                val latest = _voiceState.value
                if (latest.isHandsFreeSession && !latest.isSpeaking && !latest.isListening) {
                    startListening()
                }
            }
        }
    }

    private fun ensureSpeechRecognizer(): SpeechRecognizer? {
        val existing = speechRecognizer
        if (existing != null) return existing

        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (created == null) {
            _voiceState.update {
                it.copy(
                    isListening = false,
                    isHandsFreeSession = false,
                    errorMessage = "Could not initialize SpeechRecognizer.",
                )
            }
            return null
        }

        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _voiceState.update { it.copy(isListening = true, errorMessage = null) }
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                _voiceState.update { it.copy(isListening = false) }
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech recognition network error."
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Didn't catch any speech. Tap the mic and try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
                    else -> "Voice input error ($error)."
                }
                _voiceState.update {
                    it.copy(
                        isListening = false,
                        partialTranscript = "",
                        isHandsFreeSession = false,
                        errorMessage = msg,
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim().orEmpty()
                _voiceState.update { it.copy(isListening = false, partialTranscript = "") }
                if (transcript.isNotBlank()) {
                    _transcripts.tryEmit(transcript)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    _voiceState.update { it.copy(partialTranscript = partial) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        speechRecognizer = created
        return created
    }

    private fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("[*_`#]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(3500)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
