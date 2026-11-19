package com.example.studyappandroid.data.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Xml
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedTopic(
    val title: String,
    val contentSnippet: String,
)

data class ProcessedDocumentResult(
    val title: String,
    val fileName: String,
    val fileType: String,
    val localFilePath: String,
    val extractedText: String,
    val summary: String,
    val topics: List<ExtractedTopic>,
    val definitionsJson: String,
    val formulasJson: String,
    val keyObjectivesJson: String,
    val isOcrUsed: Boolean,
    val wordCount: Int,
)

sealed class DocumentProcessingOutcome {
    data class Success(val result: ProcessedDocumentResult) : DocumentProcessingOutcome()
    data class Failure(val userMessage: String) : DocumentProcessingOutcome()
}

@Singleton
class DocumentProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val MIN_PAGE_TEXT_CHARS_FOR_SKIP_OCR = 25
        private const val MAX_OCR_PAGES = 20
        private val PAGE_MARKER_LINE_REGEX = Regex("^\\[Page\\s+\\d+]$")
    }

    suspend fun processUri(uri: Uri): DocumentProcessingOutcome = withContext(Dispatchers.IO) {
        val displayName = resolveFileName(uri)
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val fileType = detectFileType(displayName, mimeType)
            ?: return@withContext DocumentProcessingOutcome.Failure(
                "Unsupported document format ($displayName). Please select a PDF, DOCX, or TXT file.",
            )

        val handoutsDir = File(context.filesDir, "handouts").apply { mkdirs() }
        val safeName = "${UUID.randomUUID()}_$displayName".replace(Regex("[^A-Za-z0-9._-]"), "_")
        val localFile = File(handoutsDir, safeName)

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext DocumentProcessingOutcome.Failure(
                "Could not read the selected file. Please check file access permissions.",
            )

            if (localFile.length() == 0L) {
                localFile.delete()
                return@withContext DocumentProcessingOutcome.Failure(
                    "The selected file ($displayName) is empty.",
                )
            }

            var isOcrUsed = false
            val rawText = when (fileType) {
                "TXT" -> readPlainTextFile(localFile)
                "DOCX" -> extractDocxText(localFile)
                "PDF" -> {
                    val pdfOutcome = extractPdfPagesWithOcrFallback(localFile)
                    isOcrUsed = pdfOutcome.ocrUsed
                    pdfOutcome.text
                }
                else -> ""
            }

            val cleanedText = cleanExtractedText(rawText)
            val qualityFailure = assessExtractionQuality(cleanedText, displayName)
            if (qualityFailure != null) {
                localFile.delete()
                return@withContext DocumentProcessingOutcome.Failure(qualityFailure)
            }

            val title = displayName.substringBeforeLast('.').ifBlank { "Study Handout" }
            val topics = extractTopics(cleanedText, title)
            val definitions = extractDefinitions(cleanedText)
            val formulas = extractFormulas(cleanedText)
            val objectives = extractKeyObjectives(cleanedText)
            val summary = buildLocalSummary(cleanedText, topics, definitions)
            val wordCount = cleanedText
                .lines()
                .filterNot { PAGE_MARKER_LINE_REGEX.matches(it.trim()) }
                .joinToString(" ")
                .split(Regex("\\s+"))
                .count { it.isNotBlank() }

            DocumentProcessingOutcome.Success(
                ProcessedDocumentResult(
                    title = title,
                    fileName = displayName,
                    fileType = fileType,
                    localFilePath = localFile.absolutePath,
                    extractedText = cleanedText,
                    summary = summary,
                    topics = topics,
                    definitionsJson = json.encodeToString(definitions),
                    formulasJson = json.encodeToString(formulas),
                    keyObjectivesJson = json.encodeToString(objectives),
                    isOcrUsed = isOcrUsed,
                    wordCount = wordCount,
                ),
            )
        } catch (e: Exception) {
            localFile.delete()
            DocumentProcessingOutcome.Failure(
                "Failed to process $displayName: ${e.localizedMessage ?: "Corrupted or unreadable document."}",
            )
        }
    }

    private fun resolveFileName(uri: Uri): String {
        var name = "handout"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx) ?: name
            }
        }
        return name
    }

    private fun detectFileType(fileName: String, mimeType: String): String? {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".pdf") || mimeType == "application/pdf" -> "PDF"
            lower.endsWith(".docx") || mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "DOCX"
            lower.endsWith(".txt") || mimeType.startsWith("text/") -> "TXT"
            else -> null
        }
    }

    private fun readPlainTextFile(file: File): String {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return ""

        val charset: Charset
        val offset: Int
        when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> {
                charset = Charsets.UTF_8
                offset = 3
            }
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> {
                charset = Charsets.UTF_16LE
                offset = 2
            }
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> {
                charset = Charsets.UTF_16BE
                offset = 2
            }
            else -> {
                charset = Charsets.UTF_8
                offset = 0
            }
        }

        // Reject obvious binary files disguised as .txt (except UTF-16 which legitimately contains 0x00 bytes)
        if (charset == Charsets.UTF_8 && bytes.take(512).any { it == 0.toByte() }) {
            throw IllegalArgumentException("This TXT file appears to contain binary data rather than plain text.")
        }

        return String(bytes, offset, bytes.size - offset, charset)
    }

    private data class PdfExtractionBundle(
        val text: String,
        val ocrUsed: Boolean,
    )

    private suspend fun extractPdfPagesWithOcrFallback(file: File): PdfExtractionBundle {
        val pageTexts = mutableListOf<String>()
        val pagesNeedingOcr = mutableListOf<Int>() // 0-based indices
        val totalPages: Int

        try {
            PDDocument.load(file).use { document ->
                if (document.isEncrypted) {
                    runCatching { document.isAllSecurityToBeRemoved = true }
                }
                totalPages = document.numberOfPages
                if (totalPages <= 0) {
                    throw IllegalStateException("PDF has no readable pages.")
                }

                val stripper = ColumnAwarePdfStripper()
                for (pageNum in 1..totalPages) {
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum
                    val extracted = runCatching { stripper.getText(document) }.getOrDefault("").trim()
                    val alphaNumCount = extracted.count { it.isLetterOrDigit() }
                    if (alphaNumCount >= MIN_PAGE_TEXT_CHARS_FOR_SKIP_OCR) {
                        pageTexts += extracted
                    } else {
                        pageTexts += extracted
                        pagesNeedingOcr += (pageNum - 1)
                    }
                }
            }
        } catch (e: InvalidPasswordException) {
            throw IllegalStateException("PDF is password-protected and cannot be read.", e)
        }

        var ocrUsed = false
        if (pagesNeedingOcr.isNotEmpty()) {
            val ocrByPage = extractSpecificPdfPagesViaOcr(file, pagesNeedingOcr.take(MAX_OCR_PAGES))
            ocrByPage.forEach { (pageIdx, ocrText) ->
                if (ocrText.count { it.isLetterOrDigit() } > pageTexts[pageIdx].count { it.isLetterOrDigit() }) {
                    pageTexts[pageIdx] = ocrText
                    ocrUsed = true
                }
            }
        }

        val combined = buildString {
            pageTexts.forEachIndexed { idx, content ->
                if (content.isNotBlank()) {
                    appendLine("[Page ${idx + 1}]")
                    appendLine(content)
                    appendLine()
                }
            }
        }.trim()

        if (combined.isBlank()) {
            throw IllegalStateException(
                "This PDF appears to be scanned or image-only and on-device OCR could not extract readable text. Please use a clearer scan or a text-based PDF/DOCX/TXT.",
            )
        }
        return PdfExtractionBundle(text = combined, ocrUsed = ocrUsed)
    }

    private suspend fun extractSpecificPdfPagesViaOcr(
        file: File,
        pageIndices: List<Int>,
    ): Map<Int, String> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val resultMap = linkedMapOf<Int, String>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val maxIndex = renderer.pageCount - 1
            for (pageIndex in pageIndices) {
                if (pageIndex !in 0..maxIndex) continue
                renderer.openPage(pageIndex).use { page ->
                    val width = (page.width * 2).coerceIn(600, 2048)
                    val height = (page.height * 2).coerceIn(600, 2800)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    try {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val visionText = recognizer.process(image).await()
                        val text = visionText.text.trim()
                        if (text.isNotBlank()) {
                            resultMap[pageIndex] = text
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } catch (_: Exception) {
            // Keep any partial OCR results or text-layer results already captured
        } finally {
            renderer?.close()
            pfd?.close()
            recognizer.close()
        }
        return resultMap
    }

    private fun extractDocxText(file: File): String {
        val sb = StringBuilder()
        var foundDocumentXml = false
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    foundDocumentXml = true
                    val parser = Xml.newPullParser()
                    parser.setInput(zip, "UTF-8")
                    var eventType = parser.eventType
                    var inParagraph = false
                    var isListParagraph = false
                    var inTableRow = false
                    var inTableCell = false
                    val paragraphBuffer = StringBuilder()
                    val cellBuffer = mutableListOf<String>()
                    val rowCells = mutableListOf<String>()

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                when (parser.name) {
                                    "tr" -> {
                                        inTableRow = true
                                        rowCells.clear()
                                    }
                                    "tc" -> {
                                        inTableCell = true
                                        cellBuffer.clear()
                                    }
                                    "p" -> {
                                        inParagraph = true
                                        isListParagraph = false
                                        paragraphBuffer.clear()
                                    }
                                    "numPr" -> if (inParagraph) isListParagraph = true
                                    "t" -> if (inParagraph) paragraphBuffer.append(parser.nextText())
                                    "tab" -> if (inParagraph) paragraphBuffer.append(" ")
                                    "br" -> if (inParagraph) paragraphBuffer.append("\n")
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                when (parser.name) {
                                    "p" -> {
                                        if (inParagraph) {
                                            val line = paragraphBuffer.toString().trim()
                                            if (line.isNotEmpty()) {
                                                val formatted = if (isListParagraph && !line.startsWith("•") && !line.startsWith("-")) {
                                                    "• $line"
                                                } else {
                                                    line
                                                }
                                                if (inTableCell) {
                                                    cellBuffer += formatted
                                                } else {
                                                    sb.appendLine(formatted)
                                                }
                                            }
                                            inParagraph = false
                                        }
                                    }
                                    "tc" -> {
                                        if (inTableCell) {
                                            val cellText = cellBuffer.joinToString(" ").replace(Regex("\\s+"), " ").trim()
                                            if (cellText.isNotEmpty()) {
                                                rowCells += cellText
                                            }
                                            inTableCell = false
                                        }
                                    }
                                    "tr" -> {
                                        if (inTableRow) {
                                            if (rowCells.isNotEmpty()) {
                                                sb.appendLine(rowCells.joinToString(" | "))
                                            }
                                            inTableRow = false
                                        }
                                    }
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }
        if (!foundDocumentXml) {
            throw IllegalStateException("Invalid DOCX archive: missing word/document.xml.")
        }
        return sb.toString()
    }

    fun cleanExtractedText(raw: String): String {
        val normalized = raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\u00A0", " ")
            // Rejoin hyphenated PDF word breaks across lines
            .replace(Regex("([A-Za-z])-\\n([a-z])"), "$1$2")

        val rawLines = normalized.lines().map { it.replace(Regex("[ \\t]+"), " ").trim() }

        // Detect repetitive short header/footer lines appearing across 3+ places (excluding [Page N] markers)
        val lineFrequency = rawLines
            .filter { it.length in 4..75 && !PAGE_MARKER_LINE_REGEX.matches(it) }
            .groupingBy { it.lowercase() }
            .eachCount()

        val pageNumberOnlyRegex = Regex("^(page\\s+\\d+(\\s*(of|/)\\s*\\d+)?|-\\s*\\d+\\s*-|\\d+\\s*/\\s*\\d+|\\d{1,3})$", RegexOption.IGNORE_CASE)

        val filteredLines = rawLines.filter { line ->
            when {
                line.isEmpty() -> true
                PAGE_MARKER_LINE_REGEX.matches(line) -> true
                pageNumberOnlyRegex.matches(line) -> false
                (lineFrequency[line.lowercase()] ?: 0) >= 3 && !line.endsWith(".") -> false
                else -> true
            }
        }

        // Rejoin wrapped prose lines while keeping [Page N], headings, bullets, and table rows separate
        val result = StringBuilder()
        var previousLine = ""
        for (line in filteredLines) {
            if (line.isEmpty()) {
                if (previousLine.isNotEmpty()) {
                    result.append("\n\n")
                    previousLine = ""
                }
                continue
            }

            if (PAGE_MARKER_LINE_REGEX.matches(line)) {
                if (result.isNotEmpty() && !result.endsWith("\n\n")) {
                    result.append("\n\n")
                }
                result.append(line).append("\n")
                previousLine = ""
                continue
            }

            val isTableRow = line.contains(" | ")
            val isBoundary = isTableRow ||
                isLikelyHeading(line) ||
                line.startsWith("•") ||
                line.startsWith("- ") ||
                line.startsWith("* ") ||
                Regex("^\\d+[.)]\\s+.*").matches(line)

            if (previousLine.isEmpty()) {
                result.append(line)
            } else if (isBoundary ||
                previousLine.contains(" | ") ||
                isLikelyHeading(previousLine) ||
                previousLine.endsWith(":") ||
                previousLine.endsWith(".") ||
                previousLine.endsWith("!") ||
                previousLine.endsWith("?")
            ) {
                result.append('\n').append(line)
            } else {
                result.append(' ').append(line)
            }
            previousLine = line
        }

        return result.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun assessExtractionQuality(cleanedText: String, fileName: String): String? {
        val bodyText = cleanedText
            .lines()
            .filterNot { PAGE_MARKER_LINE_REGEX.matches(it.trim()) }
            .joinToString(" ")
            .trim()

        if (bodyText.length < 25) {
            return "No readable educational text was found in $fileName."
        }

        val words = bodyText.split(Regex("\\s+")).filter { word -> word.any { it.isLetterOrDigit() } }
        if (words.size < 8) {
            return "$fileName contains too little readable text (${words.size} words) to study or generate quizzes."
        }

        val replacementChars = bodyText.count { it == '\uFFFD' }
        val controlOrGarbled = bodyText.count { !it.isWhitespace() && !it.isLetterOrDigit() && it !in ".,;:!?()'\"/-+%*=<>[]|•°Δπ∑√" }
        val nonSpaceCount = bodyText.count { !it.isWhitespace() }.coerceAtLeast(1)

        if (replacementChars > 10 || (controlOrGarbled.toFloat() / nonSpaceCount.toFloat()) > 0.35f) {
            return "Extracted text from $fileName appears corrupted or uses an unsupported font encoding."
        }
        return null
    }

    private fun isLikelyHeading(line: String): Boolean {
        if (PAGE_MARKER_LINE_REGEX.matches(line)) return false
        if (line.contains(" | ")) return false
        if (line.length !in 4..80) return false
        if (line.endsWith(".") || line.endsWith(",")) return false
        val numberedOrLabeled = Regex("^((Chapter|Unit|Module|Section|Lesson|Topic|Part)\\s+[0-9IVX]+.*|[0-9]+(\\.[0-9]+)*\\s+[A-Z].*|#{1,4}\\s+.*)")
        if (numberedOrLabeled.matches(line)) return true
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size in 2..8 && line == line.uppercase() && line.any { it.isLetter() }) return true
        if (words.size in 2..7 && line.endsWith(":")) return true
        return false
    }

    fun extractTopics(text: String, fallbackTitle: String): List<ExtractedTopic> {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !PAGE_MARKER_LINE_REGEX.matches(it) }
        val topics = mutableListOf<ExtractedTopic>()
        var currentHeading: String? = null
        val currentBody = StringBuilder()

        for (line in lines) {
            if (isLikelyHeading(line)) {
                if (currentHeading != null && currentBody.isNotBlank()) {
                    topics += ExtractedTopic(
                        title = currentHeading.removePrefix("#").trim().trimEnd(':'),
                        contentSnippet = currentBody.toString().trim().take(900),
                    )
                }
                currentHeading = line.trimStart('#', ' ').trimEnd(':')
                currentBody.clear()
            } else {
                if (currentBody.length < 1100) {
                    currentBody.appendLine(line)
                }
            }
        }
        if (currentHeading != null && currentBody.isNotBlank()) {
            topics += ExtractedTopic(
                title = currentHeading,
                contentSnippet = currentBody.toString().trim().take(900),
            )
        }

        if (topics.isEmpty()) {
            val paragraphs = text.lines()
                .filterNot { PAGE_MARKER_LINE_REGEX.matches(it.trim()) }
                .joinToString("\n")
                .split("\n\n")
                .filter { it.length > 40 }
            if (paragraphs.isEmpty()) {
                topics += ExtractedTopic(fallbackTitle, text.take(900))
            } else {
                paragraphs.chunked(2).take(12).forEachIndexed { idx, chunk ->
                    val combined = chunk.joinToString("\n\n")
                    val firstSentence = combined.lineSequence().firstOrNull()?.take(55)?.trimEnd('.', ':')
                        ?: "Section ${idx + 1}"
                    topics += ExtractedTopic(
                        title = if (firstSentence.length >= 6) firstSentence else "Section ${idx + 1}",
                        contentSnippet = combined.take(900),
                    )
                }
            }
        }
        return topics.distinctBy { it.title.lowercase() }.take(20)
    }

    fun extractDefinitions(text: String): List<String> {
        val results = mutableListOf<String>()
        val defRegex = Regex(
            "^[•\\-*\\s]*([A-Za-z][A-Za-z0-9 ()/-]{1,50})\\s+(is defined as|refers to|means|represents|describes|is a|is an|is the|are|—|–|-|:)\\s+(.{12,240})",
        )
        val bodyLines = text.lines().filterNot { PAGE_MARKER_LINE_REGEX.matches(it.trim()) }
        val candidates = bodyLines + bodyLines.joinToString(" ").split(Regex("(?<=[.!?])\\s+"))
        for (raw in candidates) {
            val trimmed = raw.trim().trimStart('•', '-', '*', ' ')
            if (trimmed.length in 18..260 && defRegex.containsMatchIn(trimmed)) {
                results += trimmed
            }
            if (results.size >= 25) break
        }
        return results.distinct()
    }

    private fun extractFormulas(text: String): List<String> {
        val results = mutableListOf<String>()
        val formulaRegex = Regex("[A-Za-z0-9)\\]]\\s*=\\s*[A-Za-z0-9(\\[\\s+\\-*/^.%]+")
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (!PAGE_MARKER_LINE_REGEX.matches(trimmed) &&
                trimmed.length in 5..120 &&
                formulaRegex.containsMatchIn(trimmed) &&
                (trimmed.any { it in "+-*/^∑√Δπ" } || trimmed.count { it == '=' } == 1)
            ) {
                results += trimmed
            }
            if (results.size >= 12) break
        }
        return results.distinct()
    }

    private fun extractKeyObjectives(text: String): List<String> {
        val results = mutableListOf<String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if ((trimmed.startsWith("•") || trimmed.startsWith("- ") || trimmed.startsWith("* ")) &&
                trimmed.length in 14..180
            ) {
                results += trimmed.trimStart('•', '-', '*', ' ')
            }
            if (results.size >= 12) break
        }
        return results.distinct()
    }

    private fun buildLocalSummary(
        text: String,
        topics: List<ExtractedTopic>,
        definitions: List<String>,
    ): String {
        val bodyPreview = text.lines()
            .filterNot { PAGE_MARKER_LINE_REGEX.matches(it.trim()) }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val firstChunk = bodyPreview.take(260).trim()
        val topicPreview = topics.take(4).joinToString(", ") { it.title }
        return buildString {
            append(firstChunk)
            if (bodyPreview.length > 260) append("...")
            if (topicPreview.isNotBlank()) {
                append("\nKey topics: $topicPreview")
            }
            if (definitions.isNotEmpty()) {
                append(" (${definitions.size} key terms detected)")
            }
        }
    }

    private class ColumnAwarePdfStripper : PDFTextStripper() {
        override fun writePage() {
            val glyphs = mutableListOf<PositionedGlyph>()
            for (article in charactersByArticle) {
                for (tp: TextPosition in article) {
                    val unicode = tp.unicode ?: continue
                    if (unicode.isEmpty()) continue
                    glyphs += PositionedGlyph(
                        text = unicode,
                        x = tp.xDirAdj,
                        y = tp.yDirAdj,
                        width = tp.widthDirAdj,
                        height = tp.heightDir,
                        pageWidth = tp.pageWidth,
                        spaceWidth = tp.widthOfSpace,
                    )
                }
            }
            val reconstructed = PdfLayoutReconstructor.reconstructPage(glyphs)
            if (reconstructed.isNotEmpty()) {
                writeString(reconstructed)
                writeLineSeparator()
            }
        }
    }
}
