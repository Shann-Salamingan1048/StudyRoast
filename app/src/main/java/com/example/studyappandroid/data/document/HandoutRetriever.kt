package com.example.studyappandroid.data.document

import com.example.studyappandroid.data.local.db.HandoutEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

data class RetrievedPassage(
    val handoutId: Long,
    val handoutTitle: String,
    val sectionTitle: String,
    val pageLabel: String = "",
    val text: String,
    val score: Double,
)

@Singleton
class HandoutRetriever @Inject constructor() {

    companion object {
        private const val TARGET_CHUNK_CHARS = 680
        private const val MAX_CHUNK_CHARS = 860
        private const val MIN_CHUNK_CHARS = 70
        private val PAGE_MARKER_REGEX = Regex("^\\[Page\\s+(\\d+)]$")
        private val TOC_DOTTED_REGEX = Regex("\\.{4,}\\s*\\d+$")
        private val STOP_WORDS = setOf(
            "a", "an", "and", "are", "as", "at", "be", "been", "being", "by", "can", "could",
            "did", "do", "does", "for", "from", "had", "has", "have", "how", "in", "into",
            "is", "it", "its", "may", "might", "more", "most", "no", "not", "of", "on", "or",
            "should", "so", "such", "than", "that", "the", "their", "them", "then", "there",
            "these", "they", "this", "those", "to", "was", "were", "what", "when", "where",
            "which", "while", "who", "why", "will", "with", "would", "you", "your",
            "explain", "tell", "about", "please", "give", "example", "handout", "notes",
        )
    }

    fun chunkHandout(handout: HandoutEntity): List<RetrievedPassage> {
        val text = handout.extractedText.trim()
        if (text.isEmpty()) return emptyList()

        val blocks = text.split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val passages = mutableListOf<RetrievedPassage>()
        var currentSection = handout.title
        var currentPageLabel = ""
        val buffer = StringBuilder()

        fun flushBuffer() {
            val content = buffer.toString().trim()
            if (content.length >= MIN_CHUNK_CHARS || passages.isEmpty()) {
                if (content.isNotBlank()) {
                    passages += RetrievedPassage(
                        handoutId = handout.id,
                        handoutTitle = handout.title,
                        sectionTitle = currentSection,
                        pageLabel = currentPageLabel,
                        text = content.take(MAX_CHUNK_CHARS),
                        score = 0.0,
                    )
                }
            }
            buffer.clear()
        }

        for (rawBlock in blocks) {
            // Check if block begins with [Page N] marker
            val blockLines = rawBlock.lines()
            val cleanedLines = mutableListOf<String>()
            for (line in blockLines) {
                val trimmedLine = line.trim()
                val pageMatch = PAGE_MARKER_REGEX.matchEntire(trimmedLine)
                if (pageMatch != null) {
                    if (buffer.isNotEmpty()) flushBuffer()
                    currentPageLabel = "Page ${pageMatch.groupValues[1]}"
                } else {
                    cleanedLines += line
                }
            }

            val block = cleanedLines.joinToString("\n").trim()
            if (block.isEmpty()) continue

            val firstLine = block.lineSequence().firstOrNull()?.trim().orEmpty()
            if (firstLine.length in 4..72 &&
                !firstLine.endsWith(".") &&
                !firstLine.contains(" | ") &&
                block.lines().size == 1
            ) {
                if (buffer.isNotEmpty()) flushBuffer()
                currentSection = firstLine.trimEnd(':')
                continue
            }

            if (block.length > MAX_CHUNK_CHARS) {
                if (buffer.isNotEmpty()) flushBuffer()
                val units = if (block.contains(" | ")) {
                    block.lines()
                } else {
                    block.split(Regex("(?<=[.!?])\\s+"))
                }
                for (unit in units) {
                    if (buffer.length + unit.length + 1 > TARGET_CHUNK_CHARS && buffer.isNotEmpty()) {
                        val overlapTail = if (block.contains(" | ")) {
                            ""
                        } else {
                            buffer.toString().takeLast(110).substringAfter(' ', "")
                        }
                        flushBuffer()
                        if (overlapTail.isNotBlank()) buffer.append(overlapTail).append(' ')
                    }
                    if (buffer.isNotEmpty()) {
                        buffer.append(if (block.contains(" | ")) '\n' else ' ')
                    }
                    buffer.append(unit)
                }
            } else {
                if (buffer.length + block.length + 2 > TARGET_CHUNK_CHARS && buffer.isNotEmpty()) {
                    flushBuffer()
                }
                if (buffer.isNotEmpty()) buffer.append("\n\n")
                buffer.append(block)
            }
        }
        if (buffer.isNotEmpty()) flushBuffer()

        return passages
    }

    fun retrievePassages(
        handouts: List<HandoutEntity>,
        query: String,
        topicFilter: String? = null,
        maxPassages: Int = 3,
        maxTotalChars: Int = 1400,
    ): List<RetrievedPassage> {
        val allChunks = handouts.flatMap { chunkHandout(it) }
        if (allChunks.isEmpty()) return emptyList()

        val combinedQuery = listOfNotNull(topicFilter, query).joinToString(" ").trim()
        val queryTerms = tokenize(combinedQuery)
        if (queryTerms.isEmpty()) {
            return trimToBudget(allChunks.take(maxPassages), maxTotalChars)
        }

        val docCount = allChunks.size.toDouble()
        val chunkTermLists = allChunks.map { chunk ->
            tokenize("${chunk.sectionTitle} ${chunk.text}")
        }
        val avgLen = chunkTermLists.map { it.size }.average().coerceAtLeast(1.0)

        val df = mutableMapOf<String, Int>()
        chunkTermLists.forEach { terms ->
            terms.distinct().forEach { term ->
                df[term] = (df[term] ?: 0) + 1
            }
        }

        val k1 = 1.4
        val b = 0.75
        val lowerTopic = topicFilter?.trim()?.lowercase()
        val lowerQuery = query.trim().lowercase()

        val scored = allChunks.mapIndexed { idx, chunk ->
            val terms = chunkTermLists[idx]
            val termFreq = terms.groupingBy { it }.eachCount()
            val docLen = terms.size.toDouble()

            var score = 0.0
            for (qTerm in queryTerms) {
                val tf = (termFreq[qTerm] ?: 0).toDouble()
                if (tf > 0) {
                    val docFreq = (df[qTerm] ?: 0).toDouble()
                    val idf = ln((docCount - docFreq + 0.5) / (docFreq + 0.5) + 1.0)
                    score += idf * ((tf * (k1 + 1.0)) / (tf + k1 * (1.0 - b + b * (docLen / avgLen))))
                }
            }

            val lowerChunk = chunk.text.lowercase()
            val lowerSection = chunk.sectionTitle.lowercase()
            if (!lowerTopic.isNullOrBlank() &&
                (lowerSection.contains(lowerTopic) || lowerChunk.contains(lowerTopic))
            ) {
                score += 4.0
            }
            if (lowerQuery.length >= 6 && lowerChunk.contains(lowerQuery)) {
                score += 2.5
            }

            chunk.copy(score = score)
        }

        val sorted = scored.sortedByDescending { it.score }
        val nonZero = sorted.filter { it.score > 0.0 }
        val picked = (nonZero.ifEmpty { sorted }).take(maxPassages)
        return trimToBudget(picked, maxTotalChars)
    }

    fun selectDistinctPassagesForQuiz(
        handouts: List<HandoutEntity>,
        topicFilter: String?,
        desiredCount: Int,
    ): List<RetrievedPassage> {
        val allChunks = handouts.flatMap { chunkHandout(it) }
            .filter { it.text.length >= MIN_CHUNK_CHARS && !isLowValueBoilerplate(it.text) }
        if (allChunks.isEmpty()) {
            return handouts.flatMap { chunkHandout(it) }.filter { it.text.length >= 40 }
        }

        return if (!topicFilter.isNullOrBlank()) {
            retrievePassages(
                handouts = handouts,
                query = topicFilter,
                topicFilter = topicFilter,
                maxPassages = desiredCount.coerceAtLeast(4),
                maxTotalChars = desiredCount * 850,
            ).sortedByDescending { scorePassageForQuiz(it) }
        } else {
            val ranked = allChunks
                .map { it.copy(score = scorePassageForQuiz(it)) }
                .sortedByDescending { it.score }

            // Spread across handouts and top-ranked informative chunks
            val topPool = ranked.take((desiredCount * 2).coerceAtLeast(ranked.size))
            val step = (topPool.size.toDouble() / desiredCount.coerceAtLeast(1)).coerceAtLeast(1.0)
            val chosen = mutableListOf<RetrievedPassage>()
            var pos = 0.0
            while (chosen.size < desiredCount && pos < topPool.size) {
                val candidate = topPool[pos.toInt()]
                if (candidate !in chosen) chosen += candidate
                pos += step
            }
            if (chosen.size < desiredCount) {
                topPool.forEach { chunk ->
                    if (chosen.size < desiredCount && chunk !in chosen) {
                        chosen += chunk
                    }
                }
            }
            chosen
        }
    }

    fun scorePassageForQuiz(passage: RetrievedPassage): Double {
        val text = passage.text
        val lower = text.lowercase()
        var score = 0.0

        val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
        if (wordCount in 25..150) score += 2.0 else if (wordCount >= 15) score += 1.0

        val definitionMarkers = listOf(" is defined as ", " refers to ", " means ", " consists of ", " causes ", " produces ", ": ")
        score += definitionMarkers.count { lower.contains(it) } * 1.5

        if (text.contains(" | ")) score += 1.5
        if (Regex("[A-Za-z0-9)\\]]\\s*=\\s*[A-Za-z0-9]").containsMatchIn(text)) score += 1.5

        val sentenceCount = text.split(Regex("[.!?]+")).count { it.trim().length >= 20 }
        score += sentenceCount.coerceAtMost(4) * 0.8

        if (isLowValueBoilerplate(text)) score -= 6.0
        return score
    }

    private fun isLowValueBoilerplate(text: String): Boolean {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return true
        val tocLines = lines.count { TOC_DOTTED_REGEX.containsMatchIn(it) }
        if (tocLines >= (lines.size + 1) / 2 && tocLines >= 2) return true

        val lower = text.lowercase()
        if (lower.startsWith("references") || lower.startsWith("bibliography") ||
            lower.startsWith("table of contents") || lower.contains("all rights reserved")
        ) {
            return true
        }
        return false
    }

    private fun trimToBudget(passages: List<RetrievedPassage>, maxTotalChars: Int): List<RetrievedPassage> {
        val out = mutableListOf<RetrievedPassage>()
        var remaining = maxTotalChars
        for (p in passages) {
            if (remaining < 100) break
            val clipped = if (p.text.length <= remaining) p.text else p.text.take(remaining)
            out += p.copy(text = clipped)
            remaining -= clipped.length
        }
        return out
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
    }
}
