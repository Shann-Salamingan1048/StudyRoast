package com.example.studyappandroid.data.ai

import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.data.local.db.TopicEntity
import com.example.studyappandroid.domain.model.AiPersonality
import com.example.studyappandroid.domain.model.LectureAction
import com.example.studyappandroid.domain.model.StudyContextSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFallbackResponderTest {

    private val responder = LocalFallbackResponder(
        roastTemplates = LocalRoastTemplates(),
        json = Json { ignoreUnknownKeys = true },
    )

    @Test
    fun replyAsTutor_doesNotSpoilActiveQuizQuestion() {
        val snapshot = StudyContextSnapshot(
            currentScreen = "Quiz",
            handoutTitle = "Cell Biology",
            topicTitle = "Organelles",
            currentQuestionText = "Which organelle generates ATP via oxidative phosphorylation?",
            currentOptions = listOf("A. Mitochondria", "B. Ribosome", "C. Golgi", "D. Lysosome"),
            isQuizAnswered = false,
        )
        val handoutText = "Mitochondria generate most of the cell's supply of ATP during cellular respiration."

        val reply = responder.replyAsTutor(
            personality = AiPersonality.SARCASTIC,
            userMessage = "Give me a hint",
            contextSnapshot = snapshot,
            relevantHandoutText = handoutText,
        )

        assertTrue(reply.contains("Organelles"))
        assertTrue(reply.contains("Offline Local Mode"))
    }

    @Test
    fun buildOfflineLecture_includesSummaryDefinitionsAndFormulas() {
        val handout = HandoutEntity(
            id = 1L,
            title = "Thermodynamics",
            fileName = "thermo.pdf",
            fileType = "PDF",
            localFilePath = "/tmp/thermo.pdf",
            extractedText = "Entropy measures disorder in a thermodynamic system.",
            summary = "Overview of entropy and Gibbs free energy.",
            definitionsJson = """["Entropy: measure of energy dispersal in a system"]""",
            formulasJson = """["dG = dH - T * dS"]""",
            keyObjectivesJson = """["Understand spontaneity via Gibbs free energy"]""",
        )
        val topics = listOf(
            TopicEntity(
                handoutId = 1L,
                title = "Entropy",
                contentSnippet = "Entropy increases in spontaneous isolated processes.",
            ),
        )

        val lecture = responder.buildOfflineLecture(
            handout = handout,
            topics = topics,
            topicTitle = "Entropy",
            action = LectureAction.SUMMARIZE,
        )

        assertTrue(lecture.contains("Offline Study Mode"))
        assertTrue(lecture.contains("Entropy increases"))
        assertTrue(lecture.contains("dG = dH - T * dS"))
    }

    @Test
    fun evaluateTeachOffline_checksKeywordCoverage() {
        val handout = HandoutEntity(
            id = 1L,
            title = "Photosynthesis",
            fileName = "photo.txt",
            fileType = "TXT",
            localFilePath = "/tmp/photo.txt",
            extractedText = "Photosynthesis converts sunlight energy into glucose using chlorophyll inside chloroplasts.",
            summary = "Photosynthesis summary",
        )

        val result = responder.evaluateTeachOffline(
            handout = handout,
            topicTitle = "Photosynthesis",
            studentExplanation = "Chlorophyll captures sunlight inside chloroplasts to produce glucose.",
        )

        assertFalse(result.whatYouGotRight.isEmpty())
        assertTrue(result.followUpQuestion.isNotBlank())
    }
}
