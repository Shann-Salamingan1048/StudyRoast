package com.example.studyappandroid.data.ai

import com.example.studyappandroid.data.document.RetrievedPassage
import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.data.local.db.QuestionEntity
import com.example.studyappandroid.data.local.db.TopicEntity
import com.example.studyappandroid.domain.model.QuizDifficulty
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GeneratedQuestionDto(
    val topic: String = "General",
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = -1,
    val explanation: String = "",
    val wrongOptionsExplanation: String = "",
    val sourceSection: String = "",
    val supportingQuote: String = "",
    val difficulty: String = "MEDIUM",
)

@Serializable
data class GeneratedQuizPayload(
    val questions: List<GeneratedQuestionDto> = emptyList(),
)

@Singleton
class QuizValidator @Inject constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private val STOP_WORDS = setOf(
            "about", "after", "again", "against", "among", "because", "before", "being", "below",
            "between", "could", "does", "doing", "during", "each", "from", "have", "having",
            "here", "hers", "herself", "himself", "into", "itself", "more", "most", "other",
            "ours", "over", "same", "should", "some", "such", "than", "that", "their", "theirs",
            "them", "then", "there", "these", "they", "this", "those", "through", "under",
            "until", "very", "what", "when", "where", "which", "while", "with", "would", "your",
            "according", "handout", "passage", "statement", "following", "correct", "true", "false",
        )
        private val BANNED_OPTION_PHRASES = setOf(
            "all of the above",
            "none of the above",
            "both a and b",
            "neither a nor b",
            "not mentioned",
            "cannot be determined",
        )
    }

    data class ValidationResult(
        val validQuestions: List<QuestionEntity>,
        val invalidCount: Int,
        val rejectionReasons: List<String> = emptyList(),
    )

    fun parseAndValidate(
        rawJson: String,
        handoutIds: List<Long>,
        fallbackDifficulty: QuizDifficulty,
    ): ValidationResult {
        val dtos = decodeQuestionDtos(rawJson) ?: return ValidationResult(emptyList(), 1, listOf("Invalid JSON format"))
        val valid = mutableListOf<QuestionEntity>()
        val reasons = mutableListOf<String>()
        var invalidCount = 0

        dtos.forEachIndexed { idx, dto ->
            val candidate = validateSingleDto(
                dto = dto,
                handoutId = handoutIds.firstOrNull(),
                passage = null,
                fallbackDifficulty = fallbackDifficulty,
                existingQuestions = valid,
                orderIndex = idx,
            )
            if (candidate.first != null) {
                valid += candidate.first!!
            } else {
                invalidCount++
                candidate.second?.let { reasons += it }
            }
        }
        return ValidationResult(valid, invalidCount, reasons)
    }

    /**
     * Strictly validates LLM output against its exact source [RetrievedPassage], enforcing
     * structural integrity, non-leakage, duplicate avoidance, and evidence-based grounding.
     */
    fun parseAndValidateGrounded(
        rawJson: String,
        passage: RetrievedPassage,
        fallbackDifficulty: QuizDifficulty,
        existingQuestions: List<QuestionEntity> = emptyList(),
    ): ValidationResult {
        val dtos = decodeQuestionDtos(rawJson) ?: return ValidationResult(emptyList(), 1, listOf("Malformed JSON"))
        val valid = mutableListOf<QuestionEntity>()
        val reasons = mutableListOf<String>()
        var invalidCount = 0

        dtos.forEachIndexed { idx, dto ->
            val candidate = validateSingleDto(
                dto = dto,
                handoutId = passage.handoutId,
                passage = passage,
                fallbackDifficulty = fallbackDifficulty,
                existingQuestions = existingQuestions + valid,
                orderIndex = existingQuestions.size + idx,
            )
            if (candidate.first != null) {
                valid += candidate.first!!
            } else {
                invalidCount++
                candidate.second?.let { reasons += it }
            }
        }
        return ValidationResult(valid, invalidCount, reasons)
    }

    private fun decodeQuestionDtos(rawJson: String): List<GeneratedQuestionDto>? {
        val cleaned = extractJsonObject(rawJson)
        return runCatching {
            val multi = json.decodeFromString<GeneratedQuizPayload>(cleaned)
            multi.questions.ifEmpty {
                val single = json.decodeFromString<GeneratedQuestionDto>(cleaned)
                if (single.question.isNotBlank()) listOf(single) else emptyList()
            }
        }.getOrElse {
            val single = runCatching { json.decodeFromString<GeneratedQuestionDto>(cleaned) }.getOrNull()
            if (single != null && single.question.isNotBlank()) listOf(single) else null
        }?.takeIf { it.isNotEmpty() }
    }

    private fun validateSingleDto(
        dto: GeneratedQuestionDto,
        handoutId: Long?,
        passage: RetrievedPassage?,
        fallbackDifficulty: QuizDifficulty,
        existingQuestions: List<QuestionEntity>,
        orderIndex: Int,
    ): Pair<QuestionEntity?, String?> {
        val qText = dto.question.trim().replace(Regex("\\s+"), " ")
        val normalizedKey = normalizeForCompare(qText)
        if (qText.length < 14 || normalizedKey.length < 10) {
            return null to "Question text is too short or empty."
        }

        // Duplicate & near-duplicate stem check
        if (existingQuestions.any { existing ->
                val existingKey = normalizeForCompare(existing.questionText)
                existingKey == normalizedKey || tokenJaccard(qText, existing.questionText) >= 0.78
            }
        ) {
            return null to "Duplicate or near-duplicate question."
        }

        val opts = dto.options.map { cleanOptionPrefix(it) }
        if (opts.size != 4) {
            return null to "Expected exactly 4 options."
        }

        val normalizedOpts = opts.map { normalizeForCompare(it) }
        if (normalizedOpts.distinct().size != 4) {
            return null to "Duplicate options detected."
        }

        val hasBadOption = opts.any { opt ->
            val lower = opt.lowercase()
            lower.isBlank() ||
                lower.length < 2 ||
                lower == "..." ||
                lower.startsWith("option ") ||
                normalizeForCompare(opt) == normalizedKey ||
                lower in BANNED_OPTION_PHRASES
        }
        if (hasBadOption) {
            return null to "Invalid or placeholder option text."
        }

        if (dto.correctIndex !in 0..3) {
            return null to "Invalid correctIndex (${dto.correctIndex})."
        }

        val correctOption = opts[dto.correctIndex]
        val normalizedCorrect = normalizeForCompare(correctOption)

        // Answer-leak check: do not reveal the correct answer verbatim inside the question stem
        if (normalizedCorrect.length >= 6 &&
            normalizedCorrect !in STOP_WORDS &&
            normalizedKey.contains(normalizedCorrect)
        ) {
            return null to "Correct answer is leaked directly inside the question text."
        }

        val explanation = dto.explanation.trim()
        if (explanation.length < 10) {
            return null to "Missing or insufficient explanation."
        }

        // Evidence-based grounding checks against the exact source passage
        if (passage != null) {
            val groundingError = checkPassageGrounding(
                questionText = qText,
                correctOption = correctOption,
                explanation = explanation,
                supportingQuote = dto.supportingQuote.trim(),
                passageText = passage.text,
            )
            if (groundingError != null) {
                return null to groundingError
            }
        }

        val entity = QuestionEntity(
            quizId = 0L,
            handoutId = handoutId,
            topicTitle = dto.topic.trim().ifBlank { passage?.sectionTitle ?: "Core Concept" },
            questionText = qText,
            optionA = opts[0],
            optionB = opts[1],
            optionC = opts[2],
            optionD = opts[3],
            correctOptionIndex = dto.correctIndex,
            explanation = explanation,
            wrongOptionsExplanation = dto.wrongOptionsExplanation.trim()
                .ifBlank { "The other choices do not match the information in the handout." },
            sourceSection = dto.sourceSection.trim().ifBlank { passage?.sectionTitle ?: dto.topic.trim() },
            sourcePage = passage?.pageLabel.orEmpty(),
            supportingPassage = passage?.text?.take(650).orEmpty(),
            validationStatus = if (passage != null) "VERIFIED_LLM_GROUNDED" else "VERIFIED_FORMAT",
            difficulty = dto.difficulty.uppercase().ifBlank { fallbackDifficulty.name },
            orderIndex = orderIndex,
        )
        return entity to null
    }

    private fun checkPassageGrounding(
        questionText: String,
        correctOption: String,
        explanation: String,
        supportingQuote: String,
        passageText: String,
    ): String? {
        val passageLower = passageText.lowercase()
        val passageTokens = contentTokens(passageText).toSet()
        if (passageTokens.isEmpty()) return "Supporting passage has no verifiable tokens."

        // 1. Numeric/Year/Percentage grounding: numbers in question or correct answer must exist in passage
        val passageNumbers = extractNumbers(passageText)
        val qaNumbers = extractNumbers("$questionText $correctOption")
        for (num in qaNumbers) {
            if (num !in passageNumbers) {
                return "Numeric value '$num' is not supported by the passage."
            }
        }

        // 2. Question must be anchored in the passage (>= 1 key content token in passage)
        val qTokens = contentTokens(questionText)
        val qOverlap = qTokens.count { it in passageTokens }
        if (qOverlap < 1) {
            return "Question appears unrelated to the supporting passage."
        }

        // 3. Correct answer + explanation must be evidenced in the passage
        val answerTokens = contentTokens(correctOption)
        val answerOverlap = answerTokens.count { it in passageTokens }
        val quoteOverlap = if (supportingQuote.length >= 12) {
            val qWords = contentTokens(supportingQuote)
            qWords.isNotEmpty() && qWords.count { it in passageTokens } >= (qWords.size / 2).coerceAtLeast(1)
        } else {
            false
        }
        val expOverlap = contentTokens(explanation).count { it in passageTokens }

        val answerExactHit = normalizeForCompare(correctOption).let { norm ->
            norm.length >= 4 && normalizeForCompare(passageLower).contains(norm)
        }

        if (!answerExactHit && answerOverlap == 0 && !(quoteOverlap && expOverlap >= 2)) {
            return "Correct answer is not supported by the recorded passage."
        }
        if ((qOverlap + answerOverlap) < 2 && !(answerExactHit && expOverlap >= 1)) {
            return "Insufficient passage evidence across question and answer."
        }

        return null
    }

    /**
     * Builds deterministic handout-grounded 4-choice questions ONLY when 4 distinct real facts
     * or definitions exist in the uploaded handouts (never invents fake placeholder options).
     */
    fun buildOfflinePracticeQuestions(
        handouts: List<HandoutEntity>,
        topicsByHandout: Map<Long, List<TopicEntity>>,
        requestedCount: Int,
        difficulty: QuizDifficulty,
    ): List<QuestionEntity> {
        val defPairs = mutableListOf<Triple<Long, String, Pair<String, String>>>()
        handouts.forEach { handout ->
            val defs = runCatching {
                json.decodeFromString<List<String>>(handout.definitionsJson)
            }.getOrDefault(emptyList())

            defs.forEach { rawDef ->
                val split = splitDefinition(rawDef)
                if (split != null) {
                    defPairs += Triple(handout.id, handout.title, split)
                }
            }
        }

        val questions = mutableListOf<QuestionEntity>()
        val allDefinitionsPool = defPairs.map { it.third.second }.distinctBy { normalizeForCompare(it) }

        // 1. Definition-based MCQs when >= 4 genuine definitions exist across the handouts
        if (allDefinitionsPool.size >= 4) {
            defPairs.shuffled().forEach { (handoutId, handoutTitle, pair) ->
                if (questions.size >= requestedCount) return@forEach
                val (term, definition) = pair
                val defNorm = normalizeForCompare(definition)
                val qText = "According to '$handoutTitle', which statement best describes $term?"

                if (defNorm.length >= 6 && normalizeForCompare(qText).contains(defNorm)) return@forEach
                if (questions.any { tokenJaccard(it.questionText, qText) >= 0.78 }) return@forEach

                val distractors = allDefinitionsPool
                    .filter { normalizeForCompare(it) != defNorm }
                    .shuffled()
                    .take(3)
                if (distractors.size < 3) return@forEach

                val options = (distractors + definition).shuffled()
                val correctIdx = options.indexOf(definition)
                val topicName = topicsByHandout[handoutId]?.firstOrNull()?.title ?: handoutTitle
                val sourcePage = handouts.firstOrNull { it.id == handoutId }
                    ?.let { findPageLabelForText(it.extractedText, definition) }
                    .orEmpty()

                questions += QuestionEntity(
                    quizId = 0L,
                    handoutId = handoutId,
                    topicTitle = topicName,
                    questionText = qText,
                    optionA = options[0],
                    optionB = options[1],
                    optionC = options[2],
                    optionD = options[3],
                    correctOptionIndex = correctIdx,
                    explanation = "$term: $definition",
                    wrongOptionsExplanation = "The other options describe different terms from your handout.",
                    sourceSection = topicName,
                    sourcePage = sourcePage,
                    supportingPassage = "$term: $definition",
                    validationStatus = "VERIFIED_EXTRACTED",
                    difficulty = difficulty.name,
                    orderIndex = questions.size,
                )
            }
        }

        // 2. Supplement from genuine distinct topic sentences when >= 4 real sentences exist
        val allTopics = handouts.flatMap { h ->
            (topicsByHandout[h.id].orEmpty()).map { t -> h to t }
        }
        val sentenceEntries = allTopics.flatMap { (handout, topic) ->
            topic.contentSnippet
                .split(Regex("(?<=[.!?])\\s+"))
                .map { it.trim() }
                .filter { it.length in 30..160 && !it.contains(" | ") }
                .map { sentence -> Triple(handout, topic, sentence) }
        }.distinctBy { normalizeForCompare(it.third) }

        if (sentenceEntries.size >= 4) {
            sentenceEntries.shuffled().forEach { (handout, topic, keyFact) ->
                if (questions.size >= requestedCount) return@forEach
                val factNorm = normalizeForCompare(keyFact)
                if (questions.any { normalizeForCompare(it.explanation).contains(factNorm) }) return@forEach

                val distractors = sentenceEntries
                    .filter { it.second.title != topic.title && normalizeForCompare(it.third) != factNorm }
                    .map { it.third }
                    .ifEmpty {
                        sentenceEntries
                            .filter { normalizeForCompare(it.third) != factNorm }
                            .map { it.third }
                    }
                    .shuffled()
                    .take(3)
                if (distractors.size < 3) return@forEach

                val options = (distractors + keyFact).shuffled()
                val sourcePage = findPageLabelForText(handout.extractedText, keyFact)
                questions += QuestionEntity(
                    quizId = 0L,
                    handoutId = handout.id,
                    topicTitle = topic.title,
                    questionText = "Which statement belongs to '${topic.title}' in '${handout.title}'?",
                    optionA = options[0],
                    optionB = options[1],
                    optionC = options[2],
                    optionD = options[3],
                    correctOptionIndex = options.indexOf(keyFact),
                    explanation = keyFact,
                    wrongOptionsExplanation = "The remaining statements come from other sections of the handout.",
                    sourceSection = topic.title,
                    sourcePage = sourcePage,
                    supportingPassage = topic.contentSnippet.take(500),
                    validationStatus = "VERIFIED_EXTRACTED",
                    difficulty = difficulty.name,
                    orderIndex = questions.size,
                )
            }
        }

        return questions.take(requestedCount)
    }

    private fun findPageLabelForText(fullText: String, snippet: String): String {
        val anchor = snippet.take(40).trim()
        if (anchor.isEmpty()) return ""
        val idx = fullText.indexOf(anchor, ignoreCase = true)
        if (idx < 0) return ""
        val before = fullText.substring(0, idx)
        val lastMatch = Regex("\\[Page\\s+(\\d+)]").findAll(before).lastOrNull()
        return lastMatch?.groupValues?.getOrNull(1)?.let { "Page $it" }.orEmpty()
    }

    private fun extractNumbers(text: String): Set<String> {
        return Regex("\\b\\d+(?:[.,]\\d+)?%?\\b")
            .findAll(text)
            .map { it.value }
            .toSet()
    }

    private fun contentTokens(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in STOP_WORDS }
    }

    private fun tokenJaccard(a: String, b: String): Double {
        val setA = contentTokens(a).toSet()
        val setB = contentTokens(b).toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        val intersection = setA.intersect(setB).size.toDouble()
        val union = setA.union(setB).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private fun cleanOptionPrefix(raw: String): String {
        return raw.trim()
            .replace(Regex("^[A-Da-d][.):\\-]\\s+"), "")
            .trim()
    }

    private fun normalizeForCompare(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun splitDefinition(raw: String): Pair<String, String>? {
        val separators = listOf(" is defined as ", " refers to ", " means ", ": ", " is a ", " is the ")
        for (sep in separators) {
            val idx = raw.indexOf(sep)
            if (idx in 2..50) {
                val term = raw.substring(0, idx).trim()
                val def = raw.substring(idx + sep.length).trim()
                if (term.isNotEmpty() && def.length >= 10) {
                    return term to def
                }
            }
        }
        return null
    }

    private fun extractJsonObject(raw: String): String {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.getOrNull(1)
        val candidate = (fenced ?: raw).trim()
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        return if (start >= 0 && end > start) candidate.substring(start, end + 1) else candidate
    }
}
