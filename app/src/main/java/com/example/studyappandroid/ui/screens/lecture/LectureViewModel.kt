package com.example.studyappandroid.ui.screens.lecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studyappandroid.data.ai.AiResult
import com.example.studyappandroid.data.ai.AiService
import com.example.studyappandroid.data.ai.LocalFallbackResponder
import com.example.studyappandroid.data.ai.TeachEvaluationResult
import com.example.studyappandroid.data.local.db.ConversationMessageEntity
import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.data.local.db.LectureEntity
import com.example.studyappandroid.data.local.db.TopicEntity
import com.example.studyappandroid.data.local.preferences.UserPreferences
import com.example.studyappandroid.data.local.preferences.UserPreferencesRepository
import com.example.studyappandroid.data.repository.StudyRepository
import com.example.studyappandroid.domain.model.LectureAction
import com.example.studyappandroid.domain.model.StudyContextSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LectureUiState(
    val handouts: List<HandoutEntity> = emptyList(),
    val selectedHandout: HandoutEntity? = null,
    val topics: List<TopicEntity> = emptyList(),
    val selectedTopic: String = "",
    val activeAction: LectureAction = LectureAction.EXPLAIN_SIMPLY,
    val lectureContent: String = "",
    val isFromCache: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val qaMessages: List<ConversationMessageEntity> = emptyList(),
    val teachEvaluation: TeachEvaluationResult? = null,
    val teachHistory: List<Pair<String, String>> = emptyList(),
    val isEvaluatingTeach: Boolean = false
)

@HiltViewModel
class LectureViewModel @Inject constructor(
    private val repository: StudyRepository,
    private val aiService: AiService,
    private val localFallbackResponder: LocalFallbackResponder,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val localState = MutableStateFlow(LectureUiState())
    private var currentPrefs = UserPreferences()
    private val lectureSessionId = "lecture_qa"

    val uiState: StateFlow<LectureUiState> = combine(
        repository.observeAllHandouts(),
        repository.observeConversation(lectureSessionId),
        preferencesRepository.preferencesFlow,
        localState
    ) { handouts, qa, prefs, local ->
        currentPrefs = prefs
        val selected = local.selectedHandout ?: handouts.firstOrNull()
        local.copy(
            handouts = handouts,
            selectedHandout = selected,
            qaMessages = qa
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LectureUiState()
    )

    fun initialize(handoutId: Long?, topicTitle: String?) {
        viewModelScope.launch {
            val handout = if (handoutId != null) repository.getHandout(handoutId) else null
            val targetHandout = handout ?: uiState.value.handouts.firstOrNull()
            if (targetHandout != null) {
                selectHandout(targetHandout, topicTitle)
            }
        }
    }

    fun selectHandout(handout: HandoutEntity, preferredTopic: String? = null) {
        viewModelScope.launch {
            val topics = repository.getTopicsForHandout(handout.id)
            val chosenTopic = preferredTopic?.takeIf { it.isNotBlank() }
                ?: topics.firstOrNull()?.title
                ?: handout.title

            localState.update {
                it.copy(
                    selectedHandout = handout,
                    topics = topics,
                    selectedTopic = chosenTopic,
                    lectureContent = "",
                    teachEvaluation = null,
                    errorMessage = null
                )
            }
            loadCachedIfAvailable(handout.id, chosenTopic, localState.value.activeAction)
        }
    }

    fun selectTopic(topicTitle: String) {
        val handout = uiState.value.selectedHandout ?: return
        localState.update {
            it.copy(
                selectedTopic = topicTitle,
                lectureContent = "",
                teachEvaluation = null,
                errorMessage = null
            )
        }
        loadCachedIfAvailable(handout.id, topicTitle, localState.value.activeAction)
    }

    private fun loadCachedIfAvailable(handoutId: Long, topicTitle: String, action: LectureAction) {
        viewModelScope.launch {
            val cached = repository.getCachedLecture(handoutId, topicTitle, action.name)
            if (cached != null) {
                localState.update {
                    it.copy(lectureContent = cached.content, isFromCache = true)
                }
            }
        }
    }

    fun requestLectureAction(action: LectureAction, forceRefresh: Boolean = false) {
        val state = uiState.value
        val handout = state.selectedHandout ?: return
        val topic = state.selectedTopic.ifBlank { handout.title }

        viewModelScope.launch {
            localState.update { it.copy(activeAction = action, isLoading = true, errorMessage = null) }
            if (!forceRefresh) {
                val cached = repository.getCachedLecture(handout.id, topic, action.name)
                if (cached != null) {
                    localState.update {
                        it.copy(
                            lectureContent = cached.content,
                            isFromCache = true,
                            isLoading = false
                        )
                    }
                    return@launch
                }
            }

            when (
                val res = aiService.generateLectureExplanation(
                    prefs = currentPrefs,
                    handout = handout,
                    topicTitle = topic,
                    action = action,
                )
            ) {
                is AiResult.Success -> {
                    repository.saveLecture(
                        LectureEntity(
                            handoutId = handout.id,
                            topicTitle = topic,
                            actionType = action.name,
                            content = res.data
                        )
                    )
                    localState.update {
                        it.copy(
                            lectureContent = res.data,
                            isFromCache = false,
                            isLoading = false
                        )
                    }
                }
                is AiResult.Error -> {
                    val offlineContent = localFallbackResponder.buildOfflineLecture(
                        handout = handout,
                        topics = state.topics,
                        topicTitle = topic,
                        action = action,
                    )
                    localState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = res.message,
                            lectureContent = it.lectureContent.ifBlank { offlineContent }
                        )
                    }
                }
            }
        }
    }

    fun askFollowUp(question: String, onResponseReady: ((String) -> Unit)? = null) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return
        val state = uiState.value
        val handout = state.selectedHandout

        viewModelScope.launch {
            localState.update { it.copy(isLoading = true, errorMessage = null) }
            val userMsg = ConversationMessageEntity(
                sessionId = lectureSessionId,
                role = "USER",
                content = trimmed,
                contextScreen = "Lecture",
                handoutId = handout?.id
            )
            repository.saveConversationMessage(userMsg)

            val snapshot = StudyContextSnapshot(
                currentScreen = "Lecture",
                handoutTitle = handout?.title,
                topicTitle = state.selectedTopic
            )

            when (
                val res = aiService.chatWithTutor(
                    prefs = currentPrefs,
                    userMessage = trimmed,
                    history = state.qaMessages,
                    contextSnapshot = snapshot,
                    matchingHandout = handout,
                    fallbackHandoutText = handout?.extractedText,
                )
            ) {
                is AiResult.Success -> {
                    repository.saveConversationMessage(
                        ConversationMessageEntity(
                            sessionId = lectureSessionId,
                            role = "ASSISTANT",
                            content = res.data,
                            contextScreen = "Lecture",
                            handoutId = handout?.id
                        )
                    )
                    localState.update { it.copy(isLoading = false) }
                    onResponseReady?.invoke(res.data)
                }
                is AiResult.Error -> {
                    val fallbackReply = localFallbackResponder.replyAsTutor(
                        personality = currentPrefs.personality,
                        userMessage = trimmed,
                        contextSnapshot = snapshot,
                        relevantHandoutText = handout?.extractedText,
                    )
                    repository.saveConversationMessage(
                        ConversationMessageEntity(
                            sessionId = lectureSessionId,
                            role = "ASSISTANT",
                            content = fallbackReply,
                            contextScreen = "Lecture",
                            handoutId = handout?.id
                        )
                    )
                    localState.update { it.copy(isLoading = false, errorMessage = res.message) }
                    onResponseReady?.invoke(fallbackReply)
                }
            }
        }
    }

    fun submitTeachExplanation(explanation: String, onFeedbackReady: ((String) -> Unit)? = null) {
        val trimmed = explanation.trim()
        if (trimmed.isEmpty()) return
        val state = uiState.value
        val handout = state.selectedHandout
        val topic = state.selectedTopic.ifBlank { handout?.title ?: "Selected Concept" }

        viewModelScope.launch {
            localState.update {
                it.copy(
                    isEvaluatingTeach = true,
                    errorMessage = null,
                    teachHistory = it.teachHistory + ("You" to trimmed)
                )
            }
            when (
                val res = aiService.evaluateTeachTheAi(
                    prefs = currentPrefs,
                    handout = handout,
                    topicTitle = topic,
                    studentExplanation = trimmed,
                )
            ) {
                is AiResult.Success -> {
                    val eval = res.data
                    val replyText = buildString {
                        append(eval.reaction)
                        if (eval.followUpQuestion.isNotBlank()) {
                            append("\n\nFollow-up: ${eval.followUpQuestion}")
                        }
                    }
                    localState.update {
                        it.copy(
                            isEvaluatingTeach = false,
                            teachEvaluation = eval,
                            teachHistory = it.teachHistory + ("StudyRoast" to replyText)
                        )
                    }
                    onFeedbackReady?.invoke(replyText)
                }
                is AiResult.Error -> {
                    val offlineEval = localFallbackResponder.evaluateTeachOffline(
                        handout = handout,
                        topicTitle = topic,
                        studentExplanation = trimmed,
                    )
                    val replyText = buildString {
                        append(offlineEval.reaction)
                        if (offlineEval.followUpQuestion.isNotBlank()) {
                            append("\n\nFollow-up: ${offlineEval.followUpQuestion}")
                        }
                    }
                    localState.update {
                        it.copy(
                            isEvaluatingTeach = false,
                            errorMessage = res.message,
                            teachEvaluation = offlineEval,
                            teachHistory = it.teachHistory + ("StudyRoast" to replyText)
                        )
                    }
                    onFeedbackReady?.invoke(replyText)
                }
            }
        }
    }

    fun clearError() {
        localState.update { it.copy(errorMessage = null) }
    }
}
