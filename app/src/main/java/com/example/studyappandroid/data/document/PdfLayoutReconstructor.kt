package com.example.studyappandroid.data.document

import kotlin.math.abs
import kotlin.math.max

data class PositionedGlyph(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val pageWidth: Float,
    val spaceWidth: Float,
) {
    val maxX: Float get() = x + width.coerceAtLeast(0f)
}

object PdfLayoutReconstructor {

    private data class TextSegment(
        val text: String,
        val minX: Float,
        val maxX: Float,
        val y: Float,
        val height: Float,
        val lineIndex: Int,
    )

    fun reconstructPage(rawGlyphs: List<PositionedGlyph>): String {
        val glyphs = rawGlyphs
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<PositionedGlyph> { it.y }.thenBy { it.x })

        if (glyphs.isEmpty()) return ""

        // 1. Group glyphs into visual horizontal lines
        val rawLines = mutableListOf<MutableList<PositionedGlyph>>()
        var currentLineY = glyphs.first().y
        var currentLineHeight = glyphs.first().height.coerceAtLeast(6f)
        var currentLine = mutableListOf<PositionedGlyph>()

        for (glyph in glyphs) {
            val yTol = max(currentLineHeight * 0.55f, 3.5f)
            if (currentLine.isNotEmpty() && abs(glyph.y - currentLineY) > yTol) {
                rawLines += currentLine
                currentLine = mutableListOf()
                currentLineY = glyph.y
                currentLineHeight = glyph.height.coerceAtLeast(6f)
            }
            currentLine += glyph
            currentLineHeight = max(currentLineHeight, glyph.height.coerceAtLeast(6f))
        }
        if (currentLine.isNotEmpty()) {
            rawLines += currentLine
        }

        // 2. Build horizontal segments per visual line
        val linesWithSegments = rawLines.mapIndexedNotNull { lineIdx, lineGlyphs ->
            val sorted = lineGlyphs.sortedBy { it.x }
            val segments = buildSegmentsForLine(sorted, lineIdx)
            if (segments.isEmpty()) null else segments
        }
        if (linesWithSegments.isEmpty()) return ""

        val allSegments = linesWithSegments.flatten()
        val effectivePageWidth = max(
            glyphs.maxOfOrNull { it.pageWidth } ?: 0f,
            allSegments.maxOfOrNull { it.maxX } ?: 1f,
        ).coerceAtLeast(1f)

        // 3. Detect genuine 2-column prose vs table layout
        val splitResult = detectTwoColumnSplit(linesWithSegments, allSegments, effectivePageWidth)
        if (splitResult != null) {
            val (gutterX, firstSplitLineIdx) = splitResult
            val topHeaders = mutableListOf<String>()
            val leftSegments = mutableListOf<TextSegment>()
            val rightSegments = mutableListOf<TextSegment>()
            val trailingFooters = mutableListOf<String>()

            linesWithSegments.forEachIndexed { idx, rowSegments ->
                val hasLeft = rowSegments.any { it.maxX <= gutterX + 4f }
                val hasRight = rowSegments.any { it.minX >= gutterX - 4f }
                val spanSegs = rowSegments.filter { it.maxX > gutterX + 4f && it.minX < gutterX - 4f }

                if (idx < firstSplitLineIdx && !hasRight && spanSegs.isNotEmpty()) {
                    topHeaders += formatLineSegments(rowSegments, isTabular = false)
                } else if (spanSegs.isNotEmpty() && !hasLeft && !hasRight && idx > linesWithSegments.lastIndex - 2) {
                    trailingFooters += formatLineSegments(rowSegments, isTabular = false)
                } else {
                    for (seg in rowSegments) {
                        val center = (seg.minX + seg.maxX) / 2f
                        if (center <= gutterX) {
                            leftSegments += seg
                        } else {
                            rightSegments += seg
                        }
                    }
                }
            }

            return buildString {
                if (topHeaders.isNotEmpty()) {
                    appendLine(topHeaders.joinToString("\n"))
                    appendLine()
                }
                if (leftSegments.isNotEmpty()) {
                    appendLine(formatColumnSegments(leftSegments))
                }
                if (rightSegments.isNotEmpty()) {
                    if (isNotEmpty()) appendLine()
                    appendLine(formatColumnSegments(rightSegments))
                }
                if (trailingFooters.isNotEmpty()) {
                    appendLine()
                    appendLine(trailingFooters.joinToString("\n"))
                }
            }.trim()
        }

        // 4. Single-column or multi-column table output
        val multiSegmentRows = linesWithSegments.count { it.size >= 2 }
        val tableLikePage = multiSegmentRows >= 2

        return linesWithSegments.mapIndexed { idx, rowSegs ->
            val neighborHasMulti = (idx > 0 && linesWithSegments[idx - 1].size >= 2) ||
                (idx < linesWithSegments.lastIndex && linesWithSegments[idx + 1].size >= 2)
            val isTabular = rowSegs.size >= 2 && (tableLikePage && neighborHasMulti || rowSegs.size >= 3)
            formatLineSegments(rowSegs, isTabular = isTabular)
        }.joinToString("\n").trim()
    }

    private fun buildSegmentsForLine(sortedGlyphs: List<PositionedGlyph>, lineIndex: Int): List<TextSegment> {
        if (sortedGlyphs.isEmpty()) return emptyList()
        val segments = mutableListOf<TextSegment>()

        val avgCharW = sortedGlyphs
            .map { glyph -> (glyph.width / glyph.text.length.coerceAtLeast(1)).coerceAtLeast(1f) }
            .average()
            .toFloat()
            .coerceAtLeast(2.5f)

        val sb = StringBuilder()
        var segMinX = sortedGlyphs.first().x
        var segMaxX = sortedGlyphs.first().maxX
        var segY = sortedGlyphs.first().y
        var segHeight = sortedGlyphs.first().height
        var prevEndX = sortedGlyphs.first().maxX
        var prevSpaceW = sortedGlyphs.first().spaceWidth

        sb.append(sortedGlyphs.first().text)

        for (i in 1 until sortedGlyphs.size) {
            val g = sortedGlyphs[i]
            val gap = g.x - prevEndX
            val spaceW = max(g.spaceWidth, prevSpaceW).takeIf { it > 0.5f } ?: (avgCharW * 0.9f)
            val wordSpaceThreshold = max(spaceW * 0.35f, avgCharW * 0.32f).coerceAtLeast(1.4f)
            val columnGapThreshold = max(spaceW * 3.4f, avgCharW * 3.2f).coerceAtLeast(18f)

            if (gap > columnGapThreshold) {
                val segText = sb.toString().trim()
                if (segText.isNotEmpty()) {
                    segments += TextSegment(segText, segMinX, segMaxX, segY, segHeight, lineIndex)
                }
                sb.clear()
                segMinX = g.x
                segMaxX = g.maxX
                segY = g.y
                segHeight = g.height
            } else if (gap > wordSpaceThreshold && !sb.endsWith(" ")) {
                sb.append(' ')
            }

            sb.append(g.text)
            segMaxX = max(segMaxX, g.maxX)
            segHeight = max(segHeight, g.height)
            prevEndX = g.maxX
            prevSpaceW = g.spaceWidth
        }

        val tailText = sb.toString().trim()
        if (tailText.isNotEmpty()) {
            segments += TextSegment(tailText, segMinX, segMaxX, segY, segHeight, lineIndex)
        }
        return segments
    }

    private fun detectTwoColumnSplit(
        linesWithSegments: List<List<TextSegment>>,
        allSegments: List<TextSegment>,
        pageWidth: Float,
    ): Pair<Float, Int>? {
        if (allSegments.size < 10 || linesWithSegments.size < 5) return null

        val candidateRatios = listOf(0.40f, 0.45f, 0.50f, 0.55f, 0.60f)
        var bestScore = Int.MIN_VALUE
        var bestGutter = 0f
        var bestFirstSplitLine = 0

        for (ratio in candidateRatios) {
            val gutterX = pageWidth * ratio
            var leftCount = 0
            var rightCount = 0
            var spanCount = 0
            var coexistingRows = 0
            var firstSplitLine = Int.MAX_VALUE
            var alignedShortLeftRows = 0

            linesWithSegments.forEachIndexed { idx, row ->
                var rowHasLeft = false
                var rowHasRight = false
                var leftRowChars = 0
                var leftEndsSentence = false

                for (seg in row) {
                    when {
                        seg.maxX <= gutterX + 4f -> {
                            leftCount++
                            rowHasLeft = true
                            leftRowChars += seg.text.length
                            leftEndsSentence = seg.text.endsWith(".")
                        }
                        seg.minX >= gutterX - 4f -> {
                            rightCount++
                            rowHasRight = true
                        }
                        else -> spanCount++
                    }
                }
                if (rowHasLeft && rowHasRight) {
                    coexistingRows++
                    if (firstSplitLine == Int.MAX_VALUE) firstSplitLine = idx
                    if (leftRowChars <= 34 && !leftEndsSentence) {
                        alignedShortLeftRows++
                    }
                }
            }

            val looksLikeKeyValueTable = coexistingRows >= 3 &&
                alignedShortLeftRows.toFloat() / coexistingRows.toFloat() >= 0.78f &&
                leftCount <= coexistingRows + 1

            if (leftCount >= 5 &&
                rightCount >= 5 &&
                coexistingRows >= 3 &&
                spanCount <= (allSegments.size * 0.25f) &&
                !looksLikeKeyValueTable
            ) {
                val score = leftCount.coerceAtMost(rightCount) * 2 + coexistingRows * 3 - spanCount * 2
                if (score > bestScore) {
                    bestScore = score
                    bestGutter = gutterX
                    bestFirstSplitLine = firstSplitLine
                }
            }
        }

        return if (bestScore > Int.MIN_VALUE) bestGutter to bestFirstSplitLine else null
    }

    private fun formatColumnSegments(segments: List<TextSegment>): String {
        val groupedByLine = segments.groupBy { it.lineIndex }.toSortedMap()
        return groupedByLine.values.joinToString("\n") { row ->
            row.sortedBy { it.minX }.joinToString(" ") { it.text }
        }
    }

    private fun formatLineSegments(segments: List<TextSegment>, isTabular: Boolean): String {
        val sorted = segments.sortedBy { it.minX }
        return if (isTabular) {
            sorted.joinToString(" | ") { it.text }
        } else {
            sorted.joinToString(" ") { it.text }
        }
    }
}
