package com.example.studyappandroid.data.ai

import com.example.studyappandroid.data.ai.local.ChatTurn
import com.example.studyappandroid.data.ai.local.LocalLlmEngine
import com.example.studyappandroid.data.ai.local.LocalModelState
import com.example.studyappandroid.data.document.HandoutRetriever
import com.example.studyappandroid.data.local.db.ConversationMessageEntity
import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.data.local.db.QuestionEntity
import com.example.studyappandroid.data.local.db.TopicEntity
import com.example.studyappandroid.data.local.preferences.UserPreferences
import com.example.studyappandroid.domain.model.LectureAction
import com.example.studyappandroid.domain.model.QuizDifficulty
import com.example.studyappandroid.domain.model.StudyContextSnapshot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

sealed class AiResult<out T> {
    data class Success<T>(val data: T) : AiResult<T>()
    data class Error(
        val message: String,
        val isLowMemory: Boolean = false,
    ) : AiResult<Nothing>()
}

@Serializable
data class TeachEvaluationResult(
    val scoreSummary: String = "",
    val whatYouGotRight: List<String> = emptyList(),
    val missingDetails: List<String> = emptyList(),
    val misconceptions: List<String> = emptyList(),
    val followUpQuestion: String = "",
    val modelExplanation: String = "",
    val reaction: String = "",
)

@Singleton
class AiService @Inject constructor(
    private val localLlmEngine: LocalLlmEngine,
    private val handoutRetriever: HandoutRetriever,
    private val quizValidator: QuizValidator,
    private val localFallbackResponder: LocalFallbackResponder,
    private val json: Json,
) {
    val modelState: StateFlow<LocalModelState> = localLlmEngine.state

    suspend fun ensureModelReady(): Result<Unit> = localLlmEngine.ensureReady()

    suspend fun unloadModel() = localLlmEngine.unloadForLowMemory()

    fun cancelGeneration() = localLlmEngine.cancelGeneration()

    fun getDiagnostics(): String = localLlmEngine.getSystemDiagnostics()

    /**
     * Generates a strictly source-grounded 4-choice quiz from ranked informative handout passages.
     * Every candidate question is verified against its supporting passage via [QuizValidator.parseAndValidateGrounded]
     * before it can be saved or displayed.
     */
    suspend fun generateQuiz(
        handouts: List<HandoutEntity>,
        topicsByHandout: Map<Long, List<TopicEntity>>,
        topicFilter: String?,
        questionCount: Int,
        difficulty: QuizDifficulty,
    ): AiResult<List<QuestionEntity>> {
        if (handouts.isEmpty()) {
            return AiResult.Error("Select at least one handout before generating a quiz.")
        }

        val ready = localLlmEngine.ensureReady()
        if (ready.isFailure) {
            val stateErr = localLlmEngine.state.value as? LocalModelState.Error
            return AiResult.Error(
                message = ready.exceptionOrNull()?.localizedMessage ?: "Offline AI teacher could not be initialized.",
                isLowMemory = stateErr?.isLowMemory == true,
            )
        }

        val passages = handoutRetriever.selectDistinctPassagesForQuiz(
            handouts = handouts,
            topicFilter = topicFilter,
            desiredCount = questionCount.coerceAtLeast(1),
        )
        if (passages.isEmpty()) {
            return AiResult.Error("Selected handouts do not contain enough readable text to build a quiz.")
        }

        val collected = mutableListOf<QuestionEntity>()
        val maxAttempts = (questionCount + 4).coerceAtMost(questionCount * 2 + 2)

        val systemPrompt = buildString {
            append("You are a strict document-grounded quiz writer. ")
            append("Output ONLY valid JSON. ")
            append("Use ONLY facts explicitly written in PASSAGE. ")
            append("Never invent names, numbers, dates, formulas, or outside knowledge.")
        }

        var attempt = 0
        var consecutiveRejections = 0
        while (collected.size < questionCount && attempt < maxAttempts) {
            val passage = passages[attempt % passages.size]
            attempt++

            // If previous candidates failed validation, simplify prompt for dense/difficult text
            val forceSimpleMode = consecutiveRejections >= 1 || difficulty == QuizDifficulty.EASY
            val effectiveDifficulty = if (forceSimpleMode && difficulty != QuizDifficulty.EASY) {
                QuizDifficulty.EASY
            } else {
                difficulty
            }

            val avoidNote = if (collected.isNotEmpty()) {
                val recentStems = collected.takeLast(3).joinToString(" | ") { it.questionText.take(60) }
                "\nDo not repeat or paraphrase: $recentStems"
            } else {
                ""
            }

            val userPrompt = buildString {
                if (forceSimpleMode) {
                    appendLine("Create 1 simple, direct factual 4-choice question using ONLY one clear fact in PASSAGE.")
                } else {
                    appendLine("Create 1 ${effectiveDifficulty.label.lowercase()} 4-choice question strictly from PASSAGE.")
                }
                if (!topicFilter.isNullOrBlank()) {
                    appendLine("Focus topic: $topicFilter")
                }
                appendLine("Strict rules:")
                appendLine("- Use exact terminology from PASSAGE.")
                appendLine("- Do NOT reveal the correct answer inside the question.")
                appendLine("- Provide 4 distinct, non-empty options and correctIndex (0..3).")
                appendLine("- Include supportingQuote copied from PASSAGE.")
                appendLine("JSON schema: {\"topic\":\"string\",\"question\":\"string\",\"options\":[\"optA\",\"optB\",\"optC\",\"optD\"],\"correctIndex\":0,\"supportingQuote\":\"short quote from PASSAGE\",\"explanation\":\"string\",\"wrongOptionsExplanation\":\"string\",\"sourceSection\":\"${passage.sectionTitle.take(45)}\",\"difficulty\":\"${effectiveDifficulty.name}\"}")
                if (avoidNote.isNotBlank()) appendLine(avoidNote)
                val sourceTag = listOfNotNull(
                    passage.handoutTitle,
                    passage.pageLabel.takeIf { it.isNotBlank() },
                    passage.sectionTitle.takeIf { it.isNotBlank() },
                ).joinToString(" - ")
                appendLine("\nPASSAGE ($sourceTag):")
                appendLine(passage.text.take(680))
            }

            val genRes = localLlmEngine.generate(
                messages = listOf(
                    ChatTurn("system", systemPrompt),
                    ChatTurn("user", userPrompt),
                ),
                maxTokens = 230,
                temperature = 0.2f,
            )

            val raw = genRes.getOrNull()
            if (raw.isNullOrBlank()) {
                consecutiveRejections++
                continue
            }

            val validated = quizValidator.parseAndValidateGrounded(
                rawJson = raw,
                passage = passage,
                fallbackDifficulty = effectiveDifficulty,
                existingQuestions = collected,
            )
            if (validated.validQuestions.isNotEmpty()) {
                consecutiveRejections = 0
                for (q in validated.validQuestions) {
                    if (collected.size < questionCount) {
                        collected += q.copy(orderIndex = collected.size)
                    }
                }
            } else {
                consecutiveRejections++
            }
        }

        // Supplement any remaining slots strictly with evidence-verified handout extraction (no fake options)
        if (collected.size < questionCount) {
            val filteredTopicsMap = if (!topicFilter.isNullOrBlank()) {
                topicsByHandout.mapValues { (_, list) ->
                    list.filter { it.title.equals(topicFilter, ignoreCase = true) }
                }
            } else {
                topicsByHandout
            }
            val extras = quizValidator.buildOfflinePracticeQuestions(
                handouts = handouts,
                topicsByHandout = filteredTopicsMap,
                requestedCount = questionCount - collected.size,
                difficulty = difficulty,
            )
            for (q in extras) {
                if (collected.size < questionCount &&
                    collected.none { it.questionText.equals(q.questionText, ignoreCase = true) }
                ) {
                    collected += q.copy(orderIndex = collected.size)
                }
            }
        }

        return if (collected.isEmpty()) {
            AiResult.Error(
                "Could not verify enough distinct handout facts to build a 4-choice quiz. Check the Extracted Text Preview in Library or try a longer handout.",
            )
        } else {
            AiResult.Success(collected.take(questionCount))
        }
    }

    suspend fun generateLectureExplanation(
        prefs: UserPreferences,
        handout: HandoutEntity,
        topicTitle: String,
        action: LectureAction,
    ): AiResult<String> {
        val passages = handoutRetriever.retrievePassages(
            handouts = listOf(handout),
            query = "$topicTitle ${action.label}",
            topicFilter = topicTitle,
            maxPassages = 2,
            maxTotalChars = 1100,
        )
        val excerpt = if (passages.isNotEmpty()) {
            passages.joinToString("\n\n") { passage ->
                val header = listOfNotNull(
                    passage.pageLabel.takeIf { it.isNotBlank() },
                    passage.sectionTitle.takeIf { it.isNotBlank() },
                ).joinToString(" • ")
                "[$header]\n${passage.text}"
            }
        } else {
            handout.extractedText.take(1000)
        }

        val systemPrompt = buildString {
            appendLine(prefs.personality.systemPromptDirective)
            appendLine("You are an offline AI teacher giving a clear, focused study explanation.")
            appendLine("Style: ${prefs.preferredExplanationStyle}.")
            appendLine("GROUNDING RULE: Base every fact strictly on the handout excerpt. If a detail is not in the excerpt, state that the handout does not cover it.")
        }
        val userPrompt = buildString {
            appendLine("Handout: ${handout.title} (${handout.subjectName})")
            appendLine("Topic: $topicTitle")
            appendLine("Task (${action.label}): ${action.promptInstruction}")
            appendLine("\nHandout Excerpt:\n$excerpt")
        }

        val genRes = localLlmEngine.generate(
            messages = listOf(
                ChatTurn("system", systemPrompt),
                ChatTurn("user", userPrompt),
            ),
            maxTokens = 260,
            temperature = 0.5f,
        )
        return genRes.fold(
            onSuccess = { text ->
                if (text.isBlank()) AiResult.Error("Offline AI returned an empty lecture.")
                else AiResult.Success(text)
            },
            onFailure = { err ->
                val stateErr = localLlmEngine.state.value as? LocalModelState.Error
                AiResult.Error(
                    message = err.localizedMessage ?: "Failed to generate lecture.",
                    isLowMemory = stateErr?.isLowMemory == true,
                )
            },
        )
    }

    suspend fun chatWithTutor(
        prefs: UserPreferences,
        userMessage: String,
        history: List<ConversationMessageEntity>,
        contextSnapshot: StudyContextSnapshot,
        matchingHandout: HandoutEntity?,
        fallbackHandoutText: String? = null,
    ): AiResult<String> {
        val retrievedExcerpt = when {
            matchingHandout != null -> {
                val query = "$userMessage ${contextSnapshot.topicTitle.orEmpty()} ${contextSnapshot.currentQuestionText.orEmpty()}"
                val passages = handoutRetriever.retrievePassages(
                    handouts = listOf(matchingHandout),
                    query = query,
                    topicFilter = contextSnapshot.topicTitle,
                    maxPassages = 2,
                    maxTotalChars = 1000,
                )
                passages.joinToString("\n\n") { "[${it.sectionTitle}]\n${it.text}" }
            }
            !fallbackHandoutText.isNullOrBlank() -> fallbackHandoutText.take(900)
            else -> ""
        }

        val systemPrompt = buildString {
            appendLine(prefs.personality.systemPromptDirective)
            appendLine("Student: ${prefs.userName}. Screen: ${contextSnapshot.currentScreen}.")
            contextSnapshot.handoutTitle?.let { appendLine("Handout: $it") }
            contextSnapshot.topicTitle?.let { appendLine("Topic: $it") }
            if (!contextSnapshot.currentQuestionText.isNullOrBlank()) {
                appendLine("Active quiz question: ${contextSnapshot.currentQuestionText}")
                if (contextSnapshot.currentOptions.isNotEmpty()) {
                    appendLine("Choices: ${contextSnapshot.currentOptions.joinToString(" | ")}")
                }
                if (!contextSnapshot.isQuizAnswered) {
                    appendLine("CRITICAL: Student has NOT submitted yet. NEVER reveal the correct option or letter; give a helpful hint only.")
                } else {
                    appendLine("Student already answered; you may explain why the right answer works and why mistakes happened.")
                }
            }
            if (retrievedExcerpt.isNotBlank()) {
                appendLine("\nHandout Passages (use ONLY these facts; if not covered here, say the handout does not state it):\n$retrievedExcerpt")
            } else {
                appendLine("No handout is currently selected. Answer briefly and remind the student they can open a handout for grounded explanations.")
            }
            appendLine("Keep replies concise (3-5 sentences), natural, and easy to read or hear via Text-to-Speech.")
        }

        val recentHistory = history
            .takeLast(4)
            .map { msg ->
                ChatTurn(
                    role = if (msg.role == "USER") "user" else "assistant",
                    content = msg.content.take(200),
                )
            }

        val turns = buildList {
            add(ChatTurn("system", systemPrompt))
            addAll(recentHistory)
            add(ChatTurn("user", userMessage.take(450)))
        }

        val genRes = localLlmEngine.generate(
            messages = turns,
            maxTokens = 220,
            temperature = 0.55f,
        )
        return genRes.fold(
            onSuccess = { text ->
                if (text.isBlank()) AiResult.Error("Offline AI returned an empty reply.")
                else AiResult.Success(text)
            },
            onFailure = { err ->
                val stateErr = localLlmEngine.state.value as? LocalModelState.Error
                AiResult.Error(
                    message = err.localizedMessage ?: "Offline AI could not respond.",
                    isLowMemory = stateErr?.isLowMemory == true,
                )
            },
        )
    }

    suspend fun evaluateTeachTheAi(
        prefs: UserPreferences,
        handout: HandoutEntity?,
        topicTitle: String,
        studentExplanation: String,
    ): AiResult<TeachEvaluationResult> {
        val referenceText = if (handout != null) {
            handoutRetriever.retrievePassages(
                handouts = listOf(handout),
                query = "$topicTitle $studentExplanation",
                topicFilter = topicTitle,
                maxPassages = 2,
                maxTotalChars = 950,
            ).joinToString("\n\n") { it.text }
        } else {
            ""
        }

        val systemPrompt = buildString {
            appendLine(prefs.personality.systemPromptDirective)
            appendLine("You are a student listening in Feynman 'Teach the AI' mode. Compare the user's explanation ONLY against the handout reference.")
            appendLine("Return ONLY valid JSON: {\"scoreSummary\":\"string\",\"reaction\":\"string\",\"whatYouGotRight\":[\"...\"],\"missingDetails\":[\"...\"],\"misconceptions\":[\"...\"],\"followUpQuestion\":\"string\",\"modelExplanation\":\"string\"}")
        }
        val userPrompt = buildString {
            appendLine("Topic: $topicTitle")
            appendLine("Student's explanation: \"${studentExplanation.take(500)}\"")
            if (referenceText.isNotBlank()) {
                appendLine("\nHandout Reference:\n$referenceText")
            }
        }

        val genRes = localLlmEngine.generate(
            messages = listOf(
                ChatTurn("system", systemPrompt),
                ChatTurn("user", userPrompt),
            ),
            maxTokens = 240,
            temperature = 0.3f,
        )
        return genRes.fold(
            onSuccess = { raw ->
                val cleaned = extractJsonObject(raw)
                val parsed = runCatching {
                    json.decodeFromString<TeachEvaluationResult>(cleaned)
                }.getOrNull()

                val finalEval = if (parsed != null && (parsed.reaction.isNotBlank() || parsed.whatYouGotRight.isNotEmpty())) {
                    parsed
                } else {
                    val offline = localFallbackResponder.evaluateTeachOffline(handout, topicTitle, studentExplanation)
                    if (raw.length in 15..280) offline.copy(reaction = raw) else offline
                }
                AiResult.Success(finalEval)
            },
            onFailure = { err ->
                val stateErr = localLlmEngine.state.value as? LocalModelState.Error
                AiResult.Error(
                    message = err.localizedMessage ?: "Failed to evaluate explanation.",
                    isLowMemory = stateErr?.isLowMemory == true,
                )
            },
        )
    }

    private fun extractJsonObject(raw: String): String {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.getOrNull(1)
        val candidate = (fenced ?: raw).trim()
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        return if (start >= 0 && end > start) candidate.substring(start, end + 1) else candidate
    }
}
