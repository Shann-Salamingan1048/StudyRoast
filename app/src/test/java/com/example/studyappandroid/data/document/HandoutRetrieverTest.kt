package com.example.studyappandroid.data.document

import com.example.studyappandroid.data.local.db.HandoutEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoutRetrieverTest {

    private val retriever = HandoutRetriever()

    private fun sampleHandout(): HandoutEntity {
        val text = """
            [Page 1]
            Table of Contents
            1. Glycolysis ............ 2
            2. Citric Acid Cycle ..... 3

            [Page 2]
            Cellular Respiration

            Glycolysis occurs in the cytoplasm and breaks one glucose molecule into two pyruvate molecules, producing a net gain of 2 ATP and 2 NADH.

            [Page 3]
            Oxidative Phosphorylation

            The electron transport chain in the inner mitochondrial membrane drives ATP synthase using a proton gradient across the cristae.
        """.trimIndent()

        return HandoutEntity(
            id = 1L,
            title = "Biology Lecture 4",
            fileName = "bio4.pdf",
            fileType = "PDF",
            localFilePath = "/tmp/bio4.pdf",
            extractedText = text,
            summary = "Cellular respiration overview",
        )
    }

    @Test
    fun retrievePassages_tracksPageNumberAndPrioritizesRelevantSection() {
        val handout = sampleHandout()
        val results = retriever.retrievePassages(
            handouts = listOf(handout),
            query = "Where does the proton gradient drive ATP synthase?",
            topicFilter = "Oxidative Phosphorylation",
            maxPassages = 1,
            maxTotalChars = 900,
        )

        assertEquals(1, results.size)
        assertEquals("Page 3", results.first().pageLabel)
        assertTrue(results.first().text.contains("ATP synthase"))
    }

    @Test
    fun selectDistinctPassagesForQuiz_filtersTableOfContentsBoilerplate() {
        val handout = sampleHandout()
        val passages = retriever.selectDistinctPassagesForQuiz(
            handouts = listOf(handout),
            topicFilter = null,
            desiredCount = 3,
        )

        assertTrue(passages.isNotEmpty())
        assertEquals(passages.distinct().size, passages.size)
        assertFalse(passages.any { it.text.contains("Table of Contents") })
    }
}
