package com.example.studyappandroid.voice

import com.example.studyappandroid.domain.model.LectureAction
import com.example.studyappandroid.domain.model.StudyContextSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun parseQuizCommand_recognizesExplicitOptionAndPrefixedOptions() {
        val options = listOf(
            "A. Mitochondria",
            "B. Nucleus",
            "C. Ribosome",
            "D. Golgi apparatus",
        )
        assertEquals(
            VoiceQuizCommand.SelectOption(0),
            VoiceCommandParser.parseQuizCommand("option A", options),
        )
        assertEquals(
            VoiceQuizCommand.SelectOption(2),
            VoiceCommandParser.parseQuizCommand("ribosome", options),
        )
        assertEquals(
            VoiceQuizCommand.RepeatQuestion,
            VoiceCommandParser.parseQuizCommand("repeat the question", options),
        )
        assertEquals(
            VoiceQuizCommand.RequestHint,
            VoiceCommandParser.parseQuizCommand("give me a hint", options),
        )
        assertEquals(
            VoiceQuizCommand.NextQuestion,
            VoiceCommandParser.parseQuizCommand("next question", options),
        )
        assertEquals(
            VoiceQuizCommand.RevealAnswer,
            VoiceCommandParser.parseQuizCommand("show answer", options),
        )
    }

    @Test
    fun parseQuizCommand_flagsAmbiguousAndAvoidsFalseArticleMatch() {
        val ambiguous = VoiceCommandParser.parseQuizCommand("maybe b or c")
        assertTrue(ambiguous is VoiceQuizCommand.AmbiguousOption)

        val question = VoiceCommandParser.parseQuizCommand("what is a cell membrane?")
        assertTrue(question is VoiceQuizCommand.Unrecognized)
    }

    @Test
    fun parseLectureCommand_recognizesModesAndQuestions() {
        assertEquals(
            VoiceLectureCommand.SwitchMode(LectureAction.GIVE_EXAMPLE),
            VoiceCommandParser.parseLectureCommand("give me an example"),
        )
        assertEquals(
            VoiceLectureCommand.SwitchMode(LectureAction.EXPLAIN_SIMPLY),
            VoiceCommandParser.parseLectureCommand("explain it simpler"),
        )
        assertEquals(
            VoiceLectureCommand.PauseLecture,
            VoiceCommandParser.parseLectureCommand("pause"),
        )
        assertEquals(
            VoiceLectureCommand.QuizMe,
            VoiceCommandParser.parseLectureCommand("quiz me"),
        )
        val question = VoiceCommandParser.parseLectureCommand("why does ATP release energy")
        assertTrue(question is VoiceLectureCommand.AskQuestion)
    }

    @Test
    fun parseNavigationCommand_recognizesDestinationsAndIgnoresGeneralQuestions() {
        assertEquals(
            VoiceNavigationCommand.GoLibrary,
            VoiceCommandParser.parseNavigationCommand("open library"),
        )
        assertEquals(
            VoiceNavigationCommand.GoQuiz,
            VoiceCommandParser.parseNavigationCommand("go to quiz"),
        )
        assertEquals(
            VoiceNavigationCommand.GoTeachAi,
            VoiceCommandParser.parseNavigationCommand("teach the ai"),
        )
        assertEquals(
            VoiceNavigationCommand.GoBack,
            VoiceCommandParser.parseNavigationCommand("go back"),
        )
        assertNull(VoiceCommandParser.parseNavigationCommand("what is homeostasis?"))
    }

    @Test
    fun parseContextualCommand_routesByActiveScreenAndCompanionState() {
        val quizCtx = StudyContextSnapshot(
            currentScreen = "Quiz",
            currentQuestionText = "Which organelle produces ATP?",
            currentOptions = listOf("A. Mitochondria", "B. Lysosome", "C. Ribosome", "D. Vacuole"),
        )
        assertEquals(
            VoiceContextualCommand.Quiz(VoiceQuizCommand.SelectOption(0)),
            VoiceCommandParser.parseContextualCommand("letter A", quizCtx, isCompanionExpanded = false),
        )
        assertEquals(
            VoiceContextualCommand.Navigation(VoiceNavigationCommand.GoLibrary),
            VoiceCommandParser.parseContextualCommand("go to library", quizCtx, isCompanionExpanded = false),
        )

        val lectureCtx = StudyContextSnapshot(currentScreen = "Lecture")
        assertEquals(
            VoiceContextualCommand.Lecture(VoiceLectureCommand.QuizMe),
            VoiceCommandParser.parseContextualCommand("quiz me", lectureCtx, isCompanionExpanded = false),
        )
        assertEquals(
            VoiceContextualCommand.CompanionChat("why is ATP unstable?"),
            VoiceCommandParser.parseContextualCommand("why is ATP unstable?", lectureCtx, isCompanionExpanded = true),
        )
    }
}
