package com.example.studyappandroid.data.ai

import com.example.studyappandroid.data.document.RetrievedPassage
import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.data.local.db.TopicEntity
import com.example.studyappandroid.domain.model.QuizDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizValidatorTest {

    private val validator = QuizValidator()

    private val samplePassage = RetrievedPassage(
        handoutId = 7L,
        handoutTitle = "Cellular Respiration",
        sectionTitle = "Glycolysis",
        pageLabel = "Page 2",
        text = "Glycolysis takes place in the cytoplasm and breaks down one glucose molecule into two pyruvate molecules, producing a net gain of 2 ATP and 2 NADH.",
        score = 5.0,
    )

    @Test
    fun parseAndValidateGrounded_acceptsValidGroundedQuestionAndRecordsMetadata() {
        val raw = """
            ```json
            {
              "topic": "Glycolysis",
              "question": "Where does glycolysis occur inside a cell?",
              "options": ["A. Cytoplasm", "B. Mitochondrial matrix", "C. Nucleus", "D. Golgi apparatus"],
              "correctIndex": 0,
              "supportingQuote": "Glycolysis takes place in the cytoplasm",
              "explanation": "The handout states glycolysis takes place in the cytoplasm.",
              "wrongOptionsExplanation": "The other organelles are not where glycolysis occurs.",
              "sourceSection": "Glycolysis",
              "difficulty": "EASY"
            }
            ```
        """.trimIndent()

        val result = validator.parseAndValidateGrounded(raw, samplePassage, QuizDifficulty.EASY)

        assertEquals(1, result.validQuestions.size)
        val q = result.validQuestions.first()
        assertEquals(7L, q.handoutId)
        assertEquals("Page 2", q.sourcePage)
        assertEquals("Glycolysis", q.sourceSection)
        assertTrue(q.supportingPassage.contains("cytoplasm"))
        assertEquals("VERIFIED_LLM_GROUNDED", q.validationStatus)
    }

    @Test
    fun parseAndValidateGrounded_rejectsUnrelatedGeneralKnowledgeHallucination() {
        val raw = """
            {
              "topic": "History",
              "question": "In what year did Napoleon become emperor of France?",
              "options": ["1804", "1815", "1789", "1821"],
              "correctIndex": 0,
              "explanation": "Napoleon crowned himself emperor in 1804."
            }
        """.trimIndent()

        val result = validator.parseAndValidateGrounded(raw, samplePassage, QuizDifficulty.MEDIUM)

        assertTrue(result.validQuestions.isEmpty())
        assertEquals(1, result.invalidCount)
    }

    @Test
    fun parseAndValidateGrounded_rejectsHallucinatedNumberNotInPassage() {
        val raw = """
            {
              "topic": "Glycolysis",
              "question": "How many net ATP molecules does glycolysis produce per glucose?",
              "options": ["38 ATP", "12 ATP", "100 ATP", "0 ATP"],
              "correctIndex": 0,
              "explanation": "Glycolysis produces 38 ATP molecules."
            }
        """.trimIndent()

        val result = validator.parseAndValidateGrounded(raw, samplePassage, QuizDifficulty.EASY)

        assertTrue(result.validQuestions.isEmpty())
        assertEquals(1, result.invalidCount)
    }

    @Test
    fun parseAndValidateGrounded_rejectsAnswerLeakedInsideQuestion() {
        val raw = """
            {
              "topic": "Glycolysis",
              "question": "Where does glycolysis take place in the cytoplasm of cells?",
              "options": ["Cytoplasm", "Nucleus", "Ribosome", "Lysosome"],
              "correctIndex": 0,
              "explanation": "Glycolysis takes place in the cytoplasm."
            }
        """.trimIndent()

        val result = validator.parseAndValidateGrounded(raw, samplePassage, QuizDifficulty.EASY)

        assertTrue(result.validQuestions.isEmpty())
        assertEquals(1, result.invalidCount)
    }

    @Test
    fun parseAndValidateGrounded_rejectsNearDuplicateQuestions() {
        val firstRaw = """
            {
              "topic": "Glycolysis",
              "question": "Where does glycolysis occur inside a cell?",
              "options": ["Cytoplasm", "Mitochondrial matrix", "Nucleus", "Golgi apparatus"],
              "correctIndex": 0,
              "explanation": "Glycolysis takes place in the cytoplasm."
            }
        """.trimIndent()
        val first = validator.parseAndValidateGrounded(firstRaw, samplePassage, QuizDifficulty.EASY).validQuestions

        val nearDupRaw = """
            {
              "topic": "Glycolysis",
              "question": "Where inside a cell does glycolysis occur?",
              "options": ["Cytoplasm", "Mitochondrial matrix", "Nucleus", "Golgi apparatus"],
              "correctIndex": 0,
              "explanation": "Glycolysis takes place in the cytoplasm."
            }
        """.trimIndent()
        val second = validator.parseAndValidateGrounded(
            nearDupRaw,
            samplePassage,
            QuizDifficulty.EASY,
            existingQuestions = first,
        )

        assertTrue(second.validQuestions.isEmpty())
        assertEquals(1, second.invalidCount)
    }

    @Test
    fun buildOfflinePracticeQuestions_requiresFourRealDistinctOptionsAndRecordsSourceMetadata() {
        val sparseHandout = HandoutEntity(
            id = 1L,
            title = "Short Note",
            fileName = "short.txt",
            fileType = "TXT",
            localFilePath = "/tmp/short.txt",
            extractedText = "[Page 1]\nMitosis: division of the cell nucleus.",
            summary = "Short summary",
            definitionsJson = """["Mitosis: division of the cell nucleus."]""",
        )
        val sparseTopics = mapOf(
            1L to listOf(
                TopicEntity(
                    handoutId = 1L,
                    title = "Mitosis",
                    contentSnippet = "Mitosis divides the cell nucleus.",
                ),
            ),
        )

        val sparseQuestions = validator.buildOfflinePracticeQuestions(
            handouts = listOf(sparseHandout),
            topicsByHandout = sparseTopics,
            requestedCount = 3,
            difficulty = QuizDifficulty.EASY,
        )
        assertTrue(sparseQuestions.isEmpty())

        val richHandout = sparseHandout.copy(
            extractedText = "[Page 1]\nMitosis: division of a somatic cell nucleus into two identical nuclei\nMeiosis: reductive division that forms four haploid gametes",
            definitionsJson = """[
                "Mitosis: division of a somatic cell nucleus into two identical nuclei",
                "Meiosis: reductive division that forms four haploid gametes",
                "Cytokinesis: physical splitting of the cytoplasm into daughter cells",
                "Interphase: growth and DNA replication stage before mitosis"
            ]""",
        )
        val richQuestions = validator.buildOfflinePracticeQuestions(
            handouts = listOf(richHandout),
            topicsByHandout = sparseTopics,
            requestedCount = 2,
            difficulty = QuizDifficulty.MEDIUM,
        )

        assertEquals(2, richQuestions.size)
        richQuestions.forEach { q ->
            val options = listOf(q.optionA, q.optionB, q.optionC, q.optionD)
            assertEquals(4, options.distinct().size)
            assertEquals("VERIFIED_EXTRACTED", q.validationStatus)
            assertTrue(q.supportingPassage.isNotBlank())
        }
    }
}
