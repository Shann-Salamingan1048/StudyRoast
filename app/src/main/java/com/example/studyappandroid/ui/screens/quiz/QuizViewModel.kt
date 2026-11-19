package com.example.studyappandroid.ui.screens.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studyappandroid.data.ai.AiResult
import com.example.studyappandroid.data.ai.AiService
import com.example.studyappandroid.data.ai.LocalRoastTemplates
import com.example.studyappandroid.data.ai.local.LocalModelManager
import com.example.studyappandroid.data.ai.local.LocalModelState
import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.data.local.db.QuestionEntity
import com.example.studyappandroid.data.local.db.QuestionResponseEntity
import com.example.studyappandroid.data.local.db.QuizAttemptEntity
import com.example.studyappandroid.data.local.db.QuizEntity
import com.example.studyappandroid.data.local.db.TopicEntity
import com.example.studyappandroid.data.local.preferences.UserPreferences
import com.example.studyappandroid.data.local.preferences.UserPreferencesRepository
import com.example.studyappandroid.data.repository.SpacedRepetitionEngine
import com.example.studyappandroid.data.repository.StudyRepository
import com.example.studyappandroid.domain.model.AiPersonality
import com.example.studyappandroid.domain.model.AvatarExpression
import com.example.studyappandroid.domain.model.QuizDifficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class QuizHubUiState(
    val handouts: List<HandoutEntity> = emptyList(),
    val selectedHandoutIds: Set<Long> = emptySet(),
    val availableTopics: List<TopicEntity> = emptyList(),
    val selectedTopicFilter: String? = null,
    val questionCount: Int = 5,
    val difficulty: QuizDifficulty = QuizDifficulty.MIXED,
    val isTimed: Boolean = false,
    val secondsPerQuestion: Int = 45,
    val randomizeOrder: Boolean = true,
    val showExplanationsImmediately: Boolean = true,
    val voiceQuizMode: Boolean = false,
    val savedQuizzes: List<QuizEntity> = emptyList(),
    val recentAttempts: List<QuizAttemptEntity> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val modelState: LocalModelState = LocalModelState.Uninitialized,
    val aiStatusText: String = "",
)

data class QuizPlayUiState(
    val quiz: QuizEntity? = null,
    val questions: List<QuestionEntity> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOptionIndex: Int? = null,
    val isSubmitted: Boolean = false,
    val isCorrect: Boolean = false,
    val wasAssisted: Boolean = false,
    val reactionMessage: String = "",
    val avatarExpression: AvatarExpression = AvatarExpression.IDLE,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val assistedCount: Int = 0,
    val correctStreak: Int = 0,
    val wrongStreak: Int = 0,
    val remainingSeconds: Int? = null,
    val totalElapsedSeconds: Int = 0,
    val isCompleted: Boolean = false,
    val completedAttemptId: Long? = null,
    val errorMessage: String? = null,
) {
    val currentQuestion: QuestionEntity? get() = questions.getOrNull(currentIndex)
}

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val repository: StudyRepository,
    private val aiService: AiService,
    private val roastTemplates: LocalRoastTemplates,
    private val spacedRepetition: SpacedRepetitionEngine,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val configState = MutableStateFlow(QuizHubUiState())
    private val _playState = MutableStateFlow(QuizPlayUiState())
    val playState: StateFlow<QuizPlayUiState> = _playState

    private val recordedResponses = mutableListOf<QuestionResponseEntity>()
    private var timerJob: Job? = null
    private var currentPrefs = UserPreferences()

    val hubState: StateFlow<QuizHubUiState> = combine(
        repository.observeAllHandouts(),
        repository.observeAllQuizzes(),
        repository.observeAllAttempts(),
        preferencesRepository.preferencesFlow,
        combine(configState, aiService.modelState) { cfg, modelState -> cfg to modelState },
    ) { handouts, quizzes, attempts, prefs, (cfg, modelState) ->
        currentPrefs = prefs
        val validSelection = cfg.selectedHandoutIds.filter { id -> handouts.any { it.id == id } }.toSet()
        val statusText = when (modelState) {
            is LocalModelState.Uninitialized -> "Built-in offline AI (${LocalModelManager.MODEL_DISPLAY_NAME}) loads automatically when you generate a quiz."
            is LocalModelState.Preparing -> modelState.stage
            is LocalModelState.Ready -> "Built-in offline AI ready (${modelState.modelName})"
            is LocalModelState.Error -> modelState.message
        }
        cfg.copy(
            handouts = handouts,
            selectedHandoutIds = if (validSelection.isEmpty() && handouts.size == 1 && cfg.selectedHandoutIds.isEmpty()) {
                setOf(handouts.first().id)
            } else {
                validSelection
            },
            savedQuizzes = quizzes,
            recentAttempts = attempts,
            modelState = modelState,
            aiStatusText = statusText,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QuizHubUiState(),
    )

    fun preselectHandouts(ids: List<Long>) {
        if (ids.isEmpty()) return
        configState.update { it.copy(selectedHandoutIds = ids.toSet(), selectedTopicFilter = null) }
        refreshAvailableTopics(ids.toSet())
    }

    fun toggleHandout(id: Long) {
        val next = configState.value.selectedHandoutIds.let { cur ->
            if (id in cur) cur - id else cur + id
        }
        configState.update { it.copy(selectedHandoutIds = next, selectedTopicFilter = null) }
        refreshAvailableTopics(next)
    }

    private fun refreshAvailableTopics(handoutIds: Set<Long>) {
        viewModelScope.launch {
            val topics = handoutIds.flatMap { repository.getTopicsForHandout(it) }
            configState.update { it.copy(availableTopics = topics) }
        }
    }

    fun setTopicFilter(topic: String?) {
        configState.update { it.copy(selectedTopicFilter = topic) }
    }

    fun setQuestionCount(count: Int) {
        configState.update { it.copy(questionCount = count.coerceIn(1, 50)) }
    }

    fun setDifficulty(difficulty: QuizDifficulty) {
        configState.update { it.copy(difficulty = difficulty) }
    }

    fun setTimed(timed: Boolean) {
        configState.update { it.copy(isTimed = timed) }
    }

    fun setRandomizeOrder(randomize: Boolean) {
        configState.update { it.copy(randomizeOrder = randomize) }
    }

    fun setShowExplanations(show: Boolean) {
        configState.update { it.copy(showExplanationsImmediately = show) }
    }

    fun setVoiceQuizMode(enabled: Boolean) {
        configState.update { it.copy(voiceQuizMode = enabled) }
    }

    fun clearHubError() {
        configState.update { it.copy(errorMessage = null) }
    }

    fun generateQuiz(onQuizReady: (Long) -> Unit) {
        val state = hubState.value
        val selectedIds = state.selectedHandoutIds.toList()
        if (selectedIds.isEmpty()) {
            configState.update { it.copy(errorMessage = "Please select at least one handout first.") }
            return
        }
        viewModelScope.launch {
            configState.update {
                it.copy(
                    isGenerating = true,
                    errorMessage = null,
                )
            }
            try {
                val handouts = repository.getHandoutsByIds(selectedIds)
                if (handouts.isEmpty()) {
                    configState.update { it.copy(isGenerating = false, errorMessage = "Selected handouts could not be loaded.") }
                    return@launch
                }

                val topicsMap = handouts.associate { it.id to repository.getTopicsForHandout(it.id) }
                val questions = when (
                    val aiRes = aiService.generateQuiz(
                        handouts = handouts,
                        topicsByHandout = topicsMap,
                        topicFilter = state.selectedTopicFilter,
                        questionCount = state.questionCount,
                        difficulty = state.difficulty,
                    )
                ) {
                    is AiResult.Success -> aiRes.data
                    is AiResult.Error -> {
                        configState.update {
                            it.copy(
                                isGenerating = false,
                                errorMessage = aiRes.message,
                            )
                        }
                        return@launch
                    }
                }

                val ordered = if (state.randomizeOrder) questions.shuffled() else questions
                val baseTitle = if (handouts.size == 1) {
                    "${handouts.first().title} Quiz"
                } else {
                    "${handouts.first().subjectName} Multi-Handout Quiz (${handouts.size})"
                }

                val quizEntity = QuizEntity(
                    title = baseTitle,
                    subjectName = handouts.first().subjectName,
                    handoutIdsCsv = selectedIds.joinToString(","),
                    topicFilter = state.selectedTopicFilter,
                    difficulty = state.difficulty.name,
                    questionCount = ordered.size,
                    isTimed = state.isTimed,
                    timeLimitSeconds = if (state.isTimed) state.secondsPerQuestion else 0,
                )
                val quizId = repository.saveQuiz(quizEntity, ordered)
                configState.update { it.copy(isGenerating = false) }
                onQuizReady(quizId)
            } catch (e: Exception) {
                configState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = e.localizedMessage ?: "Failed to generate quiz.",
                    )
                }
            }
        }
    }

    fun deleteQuiz(quizId: Long) {
        viewModelScope.launch { repository.deleteQuiz(quizId) }
    }

    fun loadQuizForPlay(quizId: Long, retryWrongOnly: Boolean) {
        timerJob?.cancel()
        recordedResponses.clear()
        viewModelScope.launch {
            val quiz = repository.getQuiz(quizId)
            if (quiz == null) {
                _playState.value = QuizPlayUiState(errorMessage = "Quiz not found.")
                return@launch
            }
            val loadedQuestions = if (retryWrongOnly) {
                repository.getLastIncorrectQuestions(quizId).ifEmpty {
                    repository.getQuestionsForQuiz(quizId)
                }
            } else {
                repository.getQuestionsForQuiz(quizId)
            }

            if (loadedQuestions.isEmpty()) {
                _playState.value = QuizPlayUiState(errorMessage = "This quiz has no questions.")
                return@launch
            }

            _playState.value = QuizPlayUiState(
                quiz = quiz,
                questions = loadedQuestions,
                currentIndex = 0,
                remainingSeconds = if (quiz.isTimed && quiz.timeLimitSeconds > 0) quiz.timeLimitSeconds else null,
                avatarExpression = AvatarExpression.IDLE,
            )
            startTimerLoop()
        }
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1.seconds)
                val cur = _playState.value
                if (cur.isCompleted) break
                val newElapsed = cur.totalElapsedSeconds + 1
                val rem = cur.remainingSeconds
                if (!cur.isSubmitted && rem != null) {
                    if (rem <= 1) {
                        _playState.update { it.copy(remainingSeconds = 0, totalElapsedSeconds = newElapsed) }
                        submitAnswer(timedOut = true)
                    } else {
                        _playState.update { it.copy(remainingSeconds = rem - 1, totalElapsedSeconds = newElapsed) }
                    }
                } else {
                    _playState.update { it.copy(totalElapsedSeconds = newElapsed) }
                }
            }
        }
    }

    fun selectOption(index: Int) {
        val cur = _playState.value
        if (cur.isSubmitted || cur.isCompleted) return
        _playState.update {
            it.copy(
                selectedOptionIndex = index,
                avatarExpression = AvatarExpression.THINKING,
            )
        }
    }

    fun revealAnswerAssisted() {
        val cur = _playState.value
        val q = cur.currentQuestion ?: return
        if (cur.isSubmitted) return

        val reaction = when (currentPrefs.personality) {
            AiPersonality.FRIENDLY ->
                "No worries! Here's the answer and why it works (marked as assisted)."
            AiPersonality.SARCASTIC ->
                "Waving the white flag already? 🏳️ Fine, marked as assisted. Remember this one!"
            AiPersonality.SAVAGE ->
                "Surrendering?! 💀 Marked as assisted—don't expect me to count this as a solo win!"
            AiPersonality.SERIOUS ->
                "Answer revealed. Question marked as assisted."
        }

        recordResponse(
            question = q,
            selectedIdx = q.correctOptionIndex,
            isCorrect = false,
            wasAssisted = true,
        )

        _playState.update {
            it.copy(
                selectedOptionIndex = q.correctOptionIndex,
                isSubmitted = true,
                isCorrect = false,
                wasAssisted = true,
                assistedCount = it.assistedCount + 1,
                wrongCount = it.wrongCount + 1,
                correctStreak = 0,
                reactionMessage = reaction,
                avatarExpression = AvatarExpression.DISAPPOINTED,
            )
        }
    }

    fun submitAnswer(timedOut: Boolean = false) {
        val cur = _playState.value
        val q = cur.currentQuestion ?: return
        if (cur.isSubmitted || cur.isCompleted) return

        val chosen = if (timedOut && cur.selectedOptionIndex == null) -1 else cur.selectedOptionIndex ?: return
        val correct = chosen == q.correctOptionIndex

        val newCorrectStreak = if (correct) cur.correctStreak + 1 else 0
        val newWrongStreak = if (!correct) cur.wrongStreak + 1 else 0

        val reaction = if (timedOut && chosen == -1) {
            "⏰ Time's up! You froze on the buzzer—let's check the right answer."
        } else if (correct) {
            roastTemplates.correctReaction(currentPrefs.personality, newCorrectStreak)
        } else {
            roastTemplates.wrongReaction(currentPrefs.personality, newWrongStreak)
        }

        val expression = when {
            correct && newCorrectStreak >= 3 -> AvatarExpression.EXCITED
            correct -> AvatarExpression.HAPPY
            newWrongStreak >= 3 -> AvatarExpression.FACEPALMING
            newWrongStreak == 2 -> AvatarExpression.SHOCKED
            else -> AvatarExpression.LAUGHING
        }

        recordResponse(
            question = q,
            selectedIdx = chosen,
            isCorrect = correct,
            wasAssisted = false,
        )

        _playState.update {
            it.copy(
                isSubmitted = true,
                isCorrect = correct,
                wasAssisted = false,
                correctCount = it.correctCount + if (correct) 1 else 0,
                wrongCount = it.wrongCount + if (!correct) 1 else 0,
                correctStreak = newCorrectStreak,
                wrongStreak = newWrongStreak,
                reactionMessage = reaction,
                avatarExpression = expression,
            )
        }
    }

    private fun recordResponse(
        question: QuestionEntity,
        selectedIdx: Int,
        isCorrect: Boolean,
        wasAssisted: Boolean,
    ) {
        recordedResponses += QuestionResponseEntity(
            attemptId = 0L,
            questionId = question.id,
            topicTitle = question.topicTitle,
            selectedOptionIndex = selectedIdx,
            isCorrect = isCorrect,
            wasAssisted = wasAssisted,
        )
        viewModelScope.launch {
            val existing = repository.getReviewSchedule(question.topicTitle)
            val updated = spacedRepetition.nextSchedule(
                existing = existing,
                handoutId = question.handoutId,
                subjectName = _playState.value.quiz?.subjectName ?: "General",
                topicTitle = question.topicTitle,
                isCorrect = isCorrect,
                wasAssisted = wasAssisted,
            )
            repository.upsertReviewSchedule(updated)
        }
    }

    fun nextQuestion(onCompleted: (Long) -> Unit) {
        val cur = _playState.value
        if (!cur.isSubmitted) return
        val nextIdx = cur.currentIndex + 1
        if (nextIdx < cur.questions.size) {
            val quiz = cur.quiz
            _playState.update {
                it.copy(
                    currentIndex = nextIdx,
                    selectedOptionIndex = null,
                    isSubmitted = false,
                    isCorrect = false,
                    wasAssisted = false,
                    reactionMessage = "",
                    avatarExpression = AvatarExpression.IDLE,
                    remainingSeconds = if (quiz?.isTimed == true && quiz.timeLimitSeconds > 0) quiz.timeLimitSeconds else null,
                )
            }
        } else {
            finishQuiz(onCompleted)
        }
    }

    private fun finishQuiz(onCompleted: (Long) -> Unit) {
        timerJob?.cancel()
        val cur = _playState.value
        val quiz = cur.quiz ?: return
        viewModelScope.launch {
            val total = cur.questions.size.coerceAtLeast(1)
            val accuracy = (cur.correctCount.toFloat() / total.toFloat()) * 100f

            val byTopic = recordedResponses.groupBy { it.topicTitle }
            val topicScores = byTopic.mapValues { (_, list) ->
                list.count { it.isCorrect }.toFloat() / list.size.coerceAtLeast(1)
            }
            val strongest = topicScores.maxByOrNull { it.value }?.key ?: "General"
            val weakest = topicScores.filter { it.value < 1f }.minByOrNull { it.value }?.key
                ?: topicScores.minByOrNull { it.value }?.key
                ?: "None"

            val comment = roastTemplates.summaryComment(currentPrefs.personality, accuracy)
            val attempt = QuizAttemptEntity(
                quizId = quiz.id,
                quizTitle = quiz.title,
                subjectName = quiz.subjectName,
                totalQuestions = cur.questions.size,
                correctCount = cur.correctCount,
                wrongCount = cur.wrongCount,
                assistedCount = cur.assistedCount,
                accuracyPercent = accuracy,
                durationSeconds = cur.totalElapsedSeconds,
                strongestTopic = strongest,
                weakestTopic = weakest,
                roastComment = comment,
            )
            val attemptId = repository.saveQuizAttempt(attempt, recordedResponses.toList())
            _playState.update { it.copy(isCompleted = true, completedAttemptId = attemptId) }
            onCompleted(attemptId)
        }
    }

    suspend fun getAttemptSummary(attemptId: Long): Pair<QuizAttemptEntity?, List<QuestionResponseEntity>> {
        val attempt = repository.getAttempt(attemptId)
        val responses = repository.getResponsesForAttempt(attemptId)
        return attempt to responses
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
