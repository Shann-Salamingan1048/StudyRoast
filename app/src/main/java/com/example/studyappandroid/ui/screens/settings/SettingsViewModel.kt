package com.example.studyappandroid.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studyappandroid.data.ai.AiService
import com.example.studyappandroid.data.ai.local.LocalModelManager
import com.example.studyappandroid.data.ai.local.LocalModelState
import com.example.studyappandroid.data.local.db.QuizAttemptEntity
import com.example.studyappandroid.data.local.preferences.UserPreferences
import com.example.studyappandroid.data.local.preferences.UserPreferencesRepository
import com.example.studyappandroid.data.repository.StudyRepository
import com.example.studyappandroid.domain.model.AiPersonality
import com.example.studyappandroid.domain.model.AvatarStyle
import com.example.studyappandroid.domain.model.ThemeMode
import com.example.studyappandroid.domain.model.VoiceInteractionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val attempts: List<QuizAttemptEntity> = emptyList(),
    val handoutCount: Int = 0,
    val averageAccuracy: Float = 0f,
    val modelState: LocalModelState = LocalModelState.Uninitialized,
    val modelName: String = LocalModelManager.MODEL_DISPLAY_NAME,
    val statusMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val repository: StudyRepository,
    private val aiService: AiService,
) : ViewModel() {

    private val statusMessageFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferencesFlow,
        repository.observeAllAttempts(),
        repository.observeAllHandouts(),
        aiService.modelState,
        statusMessageFlow,
    ) { prefs, attempts, handouts, modelState, statusMsg ->
        val avg = if (attempts.isEmpty()) 0f
        else attempts.asSequence().map { it.accuracyPercent }.average().toFloat()
        SettingsUiState(
            prefs = prefs,
            attempts = attempts,
            handoutCount = handouts.size,
            averageAccuracy = avg,
            modelState = modelState,
            modelName = LocalModelManager.MODEL_DISPLAY_NAME,
            statusMessage = statusMsg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(),
    )

    fun setUserName(name: String) {
        viewModelScope.launch { preferencesRepository.setUserName(name) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setPersonality(personality: AiPersonality) {
        viewModelScope.launch { preferencesRepository.setPersonality(personality) }
    }

    fun setAvatarStyle(style: AvatarStyle) {
        viewModelScope.launch { preferencesRepository.setAvatarStyle(style) }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVoiceEnabled(enabled) }
    }

    fun setVoiceMode(mode: VoiceInteractionMode) {
        viewModelScope.launch { preferencesRepository.setVoiceInteractionMode(mode) }
    }

    fun setTtsSettings(rate: Float, pitch: Float) {
        viewModelScope.launch { preferencesRepository.setTtsSettings(rate, pitch) }
    }

    fun setPersistentMemory(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPersistentMemoryEnabled(enabled) }
    }

    fun prepareOfflineModel() {
        viewModelScope.launch {
            statusMessageFlow.value = null
            val res = aiService.ensureModelReady()
            statusMessageFlow.value = res.fold(
                onSuccess = { "Built-in offline AI teacher is loaded and ready." },
                onFailure = { err -> err.localizedMessage ?: "Failed to load built-in AI teacher." },
            )
        }
    }

    fun unloadOfflineModel() {
        viewModelScope.launch {
            aiService.unloadModel()
            statusMessageFlow.value = "Unloaded offline AI model from memory. It will reload automatically when needed."
        }
    }

    fun clearConversationHistory() {
        viewModelScope.launch {
            repository.clearAllConversations()
            statusMessageFlow.value = "Conversation history cleared."
        }
    }
}
