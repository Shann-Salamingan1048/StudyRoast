package com.example.studyappandroid.ui.screens.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studyappandroid.data.ai.AiResult
import com.example.studyappandroid.data.ai.AiService
import com.example.studyappandroid.data.ai.LocalFallbackResponder
import com.example.studyappandroid.data.ai.LocalRoastTemplates
import com.example.studyappandroid.data.local.db.ConversationMessageEntity
import com.example.studyappandroid.data.local.preferences.UserPreferences
import com.example.studyappandroid.data.local.preferences.UserPreferencesRepository
import com.example.studyappandroid.data.repository.StudyRepository
import com.example.studyappandroid.domain.model.AiPersonality
import com.example.studyappandroid.domain.model.AvatarExpression
import com.example.studyappandroid.domain.model.AvatarStyle
import com.example.studyappandroid.domain.model.StudyContextSnapshot
import com.example.studyappandroid.voice.VoiceCommandParser
import com.example.studyappandroid.voice.VoiceContextualCommand
import com.example.studyappandroid.voice.VoiceController
import com.example.studyappandroid.voice.VoiceLectureCommand
import com.example.studyappandroid.voice.VoiceNavigationCommand
import com.example.studyappandroid.voice.VoiceQuizCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompanionUiState(
    val isPanelExpanded: Boolean = false,
    val sessionId: String = "companion_main",
    val availableSessions: List<String> = emptyList(),
    val messages: List<ConversationMessageEntity> = emptyList(),
    val contextSnapshot: StudyContextSnapshot = StudyContextSnapshot(),
    val avatarExpression: AvatarExpression = AvatarExpression.IDLE,
    val avatarStyle: AvatarStyle = AvatarStyle.SPARK,
    val personality: AiPersonality = AiPersonality.SARCASTIC,
    val isThinking: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val livePartialSpeech: String = "",
    val errorMessage: String? = null,
)

sealed interface CompanionEffect {
    data class Navigate(val command: VoiceNavigationCommand) : CompanionEffect
    data class QuizCommand(val command: VoiceQuizCommand) : CompanionEffect
    data class LectureCommand(val command: VoiceLectureCommand) : CompanionEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CompanionViewModel @Inject constructor(
    private val repository: StudyRepository,
    private val aiService: AiService,
    private val roastTemplates: LocalRoastTemplates,
    private val localFallbackResponder: LocalFallbackResponder,
    private val preferencesRepository: UserPreferencesRepository,
    private val voiceController: VoiceController,
) : ViewModel() {

    private val activeSessionId = MutableStateFlow("companion_main")
    private val localOverlayState = MutableStateFlow(CompanionUiState())
    private var currentPrefs = UserPreferences()
    private val ephemeralMessages = MutableStateFlow<List<ConversationMessageEntity>>(emptyList())

    private val _effects = MutableSharedFlow<CompanionEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<CompanionEffect> = _effects.asSharedFlow()

    private val persistedMessagesFlow = activeSessionId.flatMapLatest { sid ->
        repository.observeConversation(sid)
    }

    init {
        viewModelScope.launch {
            voiceController.transcripts.collect { transcript ->
                handleVoiceTranscript(transcript)
            }
        }
    }

    val uiState: StateFlow<CompanionUiState> = combine(
        persistedMessagesFlow,
        ephemeralMessages,
        repository.observeSessionIds(),
        preferencesRepository.preferencesFlow,
        combine(localOverlayState, voiceController.voiceState) { local, voice -> local to voice },
    ) { persisted, ephemeral, sessions, prefs, (local, voice) ->
        currentPrefs = prefs
        voiceController.updatePreferences(
            voiceEnabled = prefs.voiceEnabled,
            speechRate = prefs.ttsSpeechRate,
            pitch = prefs.ttsPitch,
            interactionMode = prefs.voiceInteractionMode,
        )
        val msgs = if (prefs.persistentMemoryEnabled) persisted else ephemeral
        val resolvedExpression = when {
            voice.isListening -> AvatarExpression.LISTENING
            voice.isSpeaking -> AvatarExpression.SPEAKING
            local.isThinking -> AvatarExpression.THINKING
            else -> local.avatarExpression
        }
        local.copy(
            sessionId = activeSessionId.value,
            availableSessions = sessions,
            messages = msgs,
            avatarExpression = resolvedExpression,
            avatarStyle = prefs.avatarStyle,
            personality = prefs.personality,
            isListening = voice.isListening,
            isSpeaking = voice.isSpeaking,
            livePartialSpeech = voice.partialTranscript,
            errorMessage = local.errorMessage ?: voice.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CompanionUiState(),
    )

    fun setPanelExpanded(expanded: Boolean) {
        localOverlayState.update { it.copy(isPanelExpanded = expanded) }
    }

    fun updateStudyContext(snapshot: StudyContextSnapshot) {
        localOverlayState.update { it.copy(contextSnapshot = snapshot) }
    }

    fun setAvatarExpression(expression: AvatarExpression) {
        localOverlayState.update { it.copy(avatarExpression = expression) }
    }

    fun hasRecordAudioPermission(): Boolean = voiceController.hasRecordAudioPermission()

    fun startVoiceListening() {
        localOverlayState.update { it.copy(errorMessage = null) }
        voiceController.clearError()
        voiceController.startListening()
    }

    fun stopVoice() {
        voiceController.stopAll()
    }

    fun speakText(text: String, force: Boolean = false) {
        voiceController.speak(text, force = force)
    }

    fun stopSpeaking() {
        voiceController.stopSpeaking()
    }

    fun reportVoiceError(message: String) {
        voiceController.reportError(message)
    }

    fun handleVoiceTranscript(transcript: String) {
        val currentState = localOverlayState.value
        val parsed = VoiceCommandParser.parseContextualCommand(
            transcript = transcript,
            context = currentState.contextSnapshot,
            isCompanionExpanded = currentState.isPanelExpanded,
        )

        when (parsed) {
            is VoiceContextualCommand.Navigation -> {
                when (val nav = parsed.command) {
                    VoiceNavigationCommand.MinimizeCompanion -> setPanelExpanded(false)
                    VoiceNavigationCommand.ExpandCompanion -> setPanelExpanded(true)
                    VoiceNavigationCommand.StartNewChat -> startNewSession()
                    VoiceNavigationCommand.ClearChat -> clearCurrentSession()
                    VoiceNavigationCommand.StopSpeaking -> voiceController.stopSpeaking()
                    else -> {
                        setAvatarExpression(AvatarExpression.HAPPY)
                        _effects.tryEmit(CompanionEffect.Navigate(nav))
                    }
                }
            }
            is VoiceContextualCommand.Quiz -> {
                _effects.tryEmit(CompanionEffect.QuizCommand(parsed.command))
            }
            is VoiceContextualCommand.Lecture -> {
                _effects.tryEmit(CompanionEffect.LectureCommand(parsed.command))
            }
            is VoiceContextualCommand.CompanionChat -> {
                if (!currentState.isPanelExpanded) {
                    setPanelExpanded(true)
                }
                sendMessage(parsed.message, speakReply = true)
            }
        }
    }

    fun setPersonality(personality: AiPersonality) {
        viewModelScope.launch { preferencesRepository.setPersonality(personality) }
    }

    fun startNewSession() {
        val sid = "session_${System.currentTimeMillis()}"
        activeSessionId.value = sid
        ephemeralMessages.value = emptyList()
    }

    fun clearCurrentSession() {
        val sid = activeSessionId.value
        ephemeralMessages.value = emptyList()
        viewModelScope.launch { repository.clearConversation(sid) }
    }

    fun sendMessage(
        text: String,
        speakReply: Boolean = false,
        onAssistantReply: ((String) -> Unit)? = null,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val snapshot = localOverlayState.value.contextSnapshot
        val sid = activeSessionId.value

        viewModelScope.launch {
            val userMsg = ConversationMessageEntity(
                sessionId = sid,
                role = "USER",
                content = trimmed,
                contextScreen = snapshot.currentScreen,
            )
            if (currentPrefs.persistentMemoryEnabled) {
                repository.saveConversationMessage(userMsg)
            } else {
                ephemeralMessages.update { it + userMsg }
            }

            localOverlayState.update {
                it.copy(
                    isThinking = true,
                    errorMessage = null,
                    avatarExpression = AvatarExpression.THINKING,
                )
            }

            val matchingHandout = snapshot.handoutTitle?.let { title ->
                repository.getHandoutByTitle(title)
            }
            val matchingHandoutText = matchingHandout?.extractedText

            val promptText = if (trimmed.contains("hint", ignoreCase = true) && !snapshot.currentQuestionText.isNullOrBlank()) {
                "${roastTemplates.hintRefusalPrefix(currentPrefs.personality)} The student asked: $trimmed"
            } else {
                trimmed
            }

            when (
                val res = aiService.chatWithTutor(
                    prefs = currentPrefs,
                    userMessage = promptText,
                    history = uiState.value.messages,
                    contextSnapshot = snapshot,
                    matchingHandout = matchingHandout,
                    fallbackHandoutText = matchingHandoutText,
                )
            ) {
                is AiResult.Success -> {
                    val replyMsg = ConversationMessageEntity(
                        sessionId = sid,
                        role = "ASSISTANT",
                        content = res.data,
                        contextScreen = snapshot.currentScreen,
                    )
                    if (currentPrefs.persistentMemoryEnabled) {
                        repository.saveConversationMessage(replyMsg)
                    } else {
                        ephemeralMessages.update { it + replyMsg }
                    }
                    localOverlayState.update {
                        it.copy(
                            isThinking = false,
                            avatarExpression = AvatarExpression.HAPPY,
                        )
                    }
                    if (speakReply) {
                        voiceController.speak(res.data)
                    }
                    onAssistantReply?.invoke(res.data)
                }
                is AiResult.Error -> {
                    val fallbackReply = localFallbackResponder.replyAsTutor(
                        personality = currentPrefs.personality,
                        userMessage = trimmed,
                        contextSnapshot = snapshot,
                        relevantHandoutText = matchingHandoutText,
                    )
                    val replyMsg = ConversationMessageEntity(
                        sessionId = sid,
                        role = "ASSISTANT",
                        content = fallbackReply,
                        contextScreen = snapshot.currentScreen,
                    )
                    if (currentPrefs.persistentMemoryEnabled) {
                        repository.saveConversationMessage(replyMsg)
                    } else {
                        ephemeralMessages.update { it + replyMsg }
                    }
                    localOverlayState.update {
                        it.copy(
                            isThinking = false,
                            avatarExpression = AvatarExpression.CONFUSED,
                            errorMessage = res.message,
                        )
                    }
                    if (speakReply) {
                        voiceController.speak(fallbackReply)
                    }
                    onAssistantReply?.invoke(fallbackReply)
                }
            }
        }
    }

    fun clearError() {
        localOverlayState.update { it.copy(errorMessage = null) }
        voiceController.clearError()
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }
}
