package com.example.studyappandroid.data.ai

import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.data.local.db.TopicEntity
import com.example.studyappandroid.domain.model.AiPersonality
import com.example.studyappandroid.domain.model.LectureAction
import com.example.studyappandroid.domain.model.StudyContextSnapshot
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides fast deterministic handout-grounded fallback responses when the on-device LLM
 * cannot complete a request (e.g. critically low memory or malformed structured output).
 */
@Singleton
class LocalFallbackResponder @Inject constructor(
    private val roastTemplates: LocalRoastTemplates,
    private val json: Json,
) {
    fun buildOfflineLecture(
        handout: HandoutEntity,
        topics: List<TopicEntity>,
        topicTitle: String,
        action: LectureAction,
    ): String {
        val matchingTopic = topics.firstOrNull { it.title.equals(topicTitle, ignoreCase = true) }
        val excerpt = matchingTopic?.contentSnippet?.takeIf { it.isNotBlank() }
            ?: extractRelevantExcerpt(handout.extractedText, topicTitle)
            ?: handout.summary

        val definitions = decodeStringList(handout.definitionsJson)
        val formulas = decodeStringList(handout.formulasJson)
        val objectives = decodeStringList(handout.keyObjectivesJson)

        return buildString {
            appendLine("[Offline Study Mode — ${action.label}]")
            appendLine("Topic: $topicTitle")
            appendLine()
            when (action) {
                LectureAction.SUMMARIZE -> {
                    appendLine("Key Excerpt:")
                    appendLine(excerpt.take(650))
                    if (objectives.isNotEmpty()) {
                        appendLine()
                        appendLine("Key Points:")
                        objectives.take(5).forEach { appendLine("• $it") }
                    }
                }
                LectureAction.COMPARE -> {
                    appendLine(excerpt.take(600))
                    if (definitions.size >= 2) {
                        appendLine()
                        appendLine("Terms to Contrast:")
                        definitions.take(4).forEach { appendLine("• $it") }
                    }
                }
                else -> {
                    appendLine(excerpt.take(800))
                }
            }
            if (definitions.isNotEmpty() && action != LectureAction.COMPARE) {
                appendLine()
                appendLine("Extracted Terms:")
                definitions.take(3).forEach { appendLine("• $it") }
            }
            if (formulas.isNotEmpty()) {
                appendLine()
                appendLine("Related Formulas:")
                formulas.take(3).forEach { appendLine("• $it") }
            }
        }.trim()
    }

    fun replyAsTutor(
        personality: AiPersonality,
        userMessage: String,
        contextSnapshot: StudyContextSnapshot,
        relevantHandoutText: String?,
        reasonLabel: String = "Offline Local Mode",
    ): String {
        if (!contextSnapshot.currentQuestionText.isNullOrBlank() && !contextSnapshot.isQuizAnswered) {
            val prefix = roastTemplates.hintRefusalPrefix(personality)
            val topicHint = contextSnapshot.topicTitle?.let { "Focus on the core idea behind \"$it\". " }.orEmpty()
            val handoutClue = relevantHandoutText
                ?.let { findBestMatchingSentence(it, contextSnapshot.currentQuestionText) }
                ?.let { "From your notes: \"$it\"" }
                ?: "Check the definitions and key terms in your handout before locking in your choice."
            return "[$reasonLabel] $prefix$topicHint$handoutClue"
        }

        val sourceText = relevantHandoutText
            ?: contextSnapshot.handoutSummary
            ?: ""

        if (sourceText.isNotBlank()) {
            val matched = findBestMatchingSentence(sourceText, "$userMessage ${contextSnapshot.topicTitle.orEmpty()}")
            if (!matched.isNullOrBlank()) {
                return "[$reasonLabel] From your handout (${contextSnapshot.handoutTitle ?: "Study Notes"}):\n\n$matched"
            }
            return "[$reasonLabel] Here's an excerpt from ${contextSnapshot.handoutTitle ?: "your notes"}:\n\n${sourceText.take(420)}"
        }

        return "[$reasonLabel] No handout text is open for \"$userMessage\". " +
            "Upload or open a handout in Library so the built-in AI teacher can answer from your notes."
    }

    fun evaluateTeachOffline(
        handout: HandoutEntity?,
        topicTitle: String,
        studentExplanation: String,
    ): TeachEvaluationResult {
        val reference = handout?.extractedText
            ?.let { extractRelevantExcerpt(it, topicTitle) ?: it.take(1200) }
            .orEmpty()

        val stopWords = setOf(
            "about", "above", "after", "again", "because", "before", "being", "between",
            "could", "should", "their", "there", "these", "those", "through", "under",
            "where", "which", "while", "would", "using", "other", "within", "without",
        )
        val referenceKeywords = reference
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 5 && it !in stopWords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(8)

        val lowerExplanation = studentExplanation.lowercase()
        val matched = referenceKeywords.filter { it in lowerExplanation }
        val missed = referenceKeywords.filterNot { it in lowerExplanation }

        val summary = when {
            referenceKeywords.isEmpty() -> "Offline Review"
            matched.size >= (referenceKeywords.size / 2).coerceAtLeast(2) -> "Good Coverage (Offline Check)"
            else -> "Keep Expanding (Offline Check)"
        }

        return TeachEvaluationResult(
            scoreSummary = summary,
            reaction = "Checked your explanation against your handout's key concepts for \"$topicTitle\".",
            whatYouGotRight = if (matched.isNotEmpty()) {
                listOf("Covered key handout terms: ${matched.joinToString(", ")}")
            } else {
                listOf("Attempted an explanation in your own words.")
            },
            missingDetails = if (missed.isNotEmpty()) {
                listOf("Consider connecting these handout concepts: ${missed.take(4).joinToString(", ")}")
            } else {
                emptyList()
            },
            followUpQuestion = if (missed.isNotEmpty()) {
                "How does ${missed.first()} fit into $topicTitle?"
            } else {
                "Can you give a concrete example of $topicTitle?"
            },
            modelExplanation = reference.take(500).ifBlank {
                "Open a handout on this topic for a deeper reference explanation."
            },
        )
    }

    private fun decodeStringList(rawJson: String): List<String> {
        return runCatching { json.decodeFromString<List<String>>(rawJson) }.getOrDefault(emptyList())
    }

    private fun extractRelevantExcerpt(fullText: String, topicTitle: String): String? {
        if (fullText.isBlank()) return null
        val idx = fullText.indexOf(topicTitle, ignoreCase = true)
        if (idx < 0) return null
        val start = (idx - 150).coerceAtLeast(0)
        val end = (start + 900).coerceAtMost(fullText.length)
        return fullText.substring(start, end).trim()
    }

    private fun findBestMatchingSentence(text: String, query: String): String? {
        val keywords = query.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .distinct()
        val sentences = text
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length in 20..360 }
        if (sentences.isEmpty()) return null
        if (keywords.isEmpty()) return sentences.first()

        return sentences.maxByOrNull { sentence ->
            val lower = sentence.lowercase()
            keywords.count { lower.contains(it) }
        }
    }
}
