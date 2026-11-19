package com.example.studyappandroid.voice

import com.example.studyappandroid.domain.model.LectureAction
import com.example.studyappandroid.domain.model.StudyContextSnapshot

sealed class VoiceQuizCommand {
    data class SelectOption(val index: Int) : VoiceQuizCommand()
    data class AmbiguousOption(val candidateIndex: Int?, val rawTranscript: String) : VoiceQuizCommand()
    data object RepeatQuestion : VoiceQuizCommand()
    data object RequestHint : VoiceQuizCommand()
    data object NextQuestion : VoiceQuizCommand()
    data object RevealAnswer : VoiceQuizCommand()
    data class Unrecognized(val rawTranscript: String) : VoiceQuizCommand()
}

sealed class VoiceLectureCommand {
    data class SwitchMode(val action: LectureAction) : VoiceLectureCommand()
    data object ContinueLecture : VoiceLectureCommand()
    data object PauseLecture : VoiceLectureCommand()
    data object RestartLecture : VoiceLectureCommand()
    data object QuizMe : VoiceLectureCommand()
    data class AskQuestion(val question: String) : VoiceLectureCommand()
}

sealed class VoiceNavigationCommand {
    data object GoHome : VoiceNavigationCommand()
    data object GoQuiz : VoiceNavigationCommand()
    data object GoLecture : VoiceNavigationCommand()
    data object GoTeachAi : VoiceNavigationCommand()
    data object GoLibrary : VoiceNavigationCommand()
    data object GoSettings : VoiceNavigationCommand()
    data object GoBack : VoiceNavigationCommand()
    data object MinimizeCompanion : VoiceNavigationCommand()
    data object ExpandCompanion : VoiceNavigationCommand()
    data object StartNewChat : VoiceNavigationCommand()
    data object ClearChat : VoiceNavigationCommand()
    data object StopSpeaking : VoiceNavigationCommand()
}

sealed class VoiceContextualCommand {
    data class Navigation(val command: VoiceNavigationCommand) : VoiceContextualCommand()
    data class Quiz(val command: VoiceQuizCommand) : VoiceContextualCommand()
    data class Lecture(val command: VoiceLectureCommand) : VoiceContextualCommand()
    data class CompanionChat(val message: String) : VoiceContextualCommand()
}

object VoiceCommandParser {

    private val explicitOptionPrefixPatterns = mapOf(
        0 to listOf(
            Regex("\\b(letter|option|choice|answer\\s+is|my\\s+answer\\s+is|pick|select|choose|it\\s+is|it's|maybe)\\s+a\\b"),
            Regex("\\b(first|number\\s*1|number\\s*one)\\b"),
        ),
        1 to listOf(
            Regex("\\b(letter|option|choice|answer\\s+is|my\\s+answer\\s+is|pick|select|choose|it\\s+is|it's|maybe)\\s+b\\b"),
            Regex("\\b(second|number\\s*2|number\\s*two|bee)\\b"),
        ),
        2 to listOf(
            Regex("\\b(letter|option|choice|answer\\s+is|my\\s+answer\\s+is|pick|select|choose|it\\s+is|it's|maybe)\\s+c\\b"),
            Regex("\\b(third|number\\s*3|number\\s*three)\\b"),
        ),
        3 to listOf(
            Regex("\\b(letter|option|choice|answer\\s+is|my\\s+answer\\s+is|pick|select|choose|it\\s+is|it's|maybe)\\s+d\\b"),
            Regex("\\b(fourth|number\\s*4|number\\s*four|dee)\\b"),
        ),
    )

    private val shortLetterPatterns = mapOf(
        0 to Regex("(^|\\s)a(\\s|$)"),
        1 to Regex("(^|\\s)(b|bee)(\\s|$)"),
        2 to Regex("(^|\\s)(c|see|sea)(\\s|$)"),
        3 to Regex("(^|\\s)(d|dee)(\\s|$)"),
    )

    fun parseQuizCommand(transcript: String, options: List<String> = emptyList()): VoiceQuizCommand {
        val normalized = transcript.trim().lowercase()
        if (normalized.isBlank()) return VoiceQuizCommand.Unrecognized(transcript)

        if (normalized.contains("repeat") || normalized.contains("say again") || normalized.contains("read again")) {
            return VoiceQuizCommand.RepeatQuestion
        }
        if (normalized.contains("hint") || normalized.contains("clue")) {
            return VoiceQuizCommand.RequestHint
        }
        if (normalized.contains("next") || normalized.contains("continue") || normalized.contains("move on")) {
            return VoiceQuizCommand.NextQuestion
        }
        if (normalized.contains("reveal") || normalized.contains("show answer") || normalized.contains("skip")) {
            return VoiceQuizCommand.RevealAnswer
        }

        when (normalized) {
            "a", "letter a", "option a", "choice a", "my answer is a" -> return VoiceQuizCommand.SelectOption(0)
            "b", "letter b", "option b", "choice b", "my answer is b", "bee" -> return VoiceQuizCommand.SelectOption(1)
            "c", "letter c", "option c", "choice c", "my answer is c", "see", "sea" -> return VoiceQuizCommand.SelectOption(2)
            "d", "letter d", "option d", "choice d", "my answer is d", "dee" -> return VoiceQuizCommand.SelectOption(3)
        }

        val matchedIndices = mutableSetOf<Int>()
        explicitOptionPrefixPatterns.forEach { (idx, regexList) ->
            if (regexList.any { it.containsMatchIn(normalized) }) {
                matchedIndices += idx
            }
        }

        val wordCount = normalized.split(Regex("\\s+")).count { it.isNotBlank() }
        val looksLikeQuestion = normalized.endsWith("?") ||
            normalized.startsWith("what ") ||
            normalized.startsWith("why ") ||
            normalized.startsWith("how ") ||
            normalized.startsWith("explain ")

        if (matchedIndices.isEmpty() && !looksLikeQuestion && wordCount <= 4) {
            shortLetterPatterns.forEach { (idx, regex) ->
                if (regex.containsMatchIn(normalized)) {
                    matchedIndices += idx
                }
            }
        }

        if (matchedIndices.isEmpty() && options.size == 4) {
            options.forEachIndexed { idx, optText ->
                val cleanOpt = optText.trim().lowercase().replace(Regex("^[a-d]\\.\\s*"), "")
                if (cleanOpt.length >= 4 &&
                    (normalized.contains(cleanOpt) || (normalized.length >= 4 && cleanOpt.contains(normalized)))
                ) {
                    matchedIndices += idx
                }
            }
        }

        val uncertainWords = listOf("maybe", "or", "not sure", "guess", "either", "wait")
        val soundsUncertain = uncertainWords.any { normalized.contains(it) }

        return when {
            matchedIndices.size == 1 && !soundsUncertain -> VoiceQuizCommand.SelectOption(matchedIndices.first())
            matchedIndices.size == 1 && soundsUncertain -> VoiceQuizCommand.AmbiguousOption(matchedIndices.first(), transcript)
            matchedIndices.size > 1 -> VoiceQuizCommand.AmbiguousOption(matchedIndices.last(), transcript)
            else -> VoiceQuizCommand.Unrecognized(transcript)
        }
    }

    fun parseLectureCommand(transcript: String): VoiceLectureCommand {
        val norm = transcript.trim().lowercase()
        return when {
            norm.contains("continue") || norm.contains("resume") -> VoiceLectureCommand.ContinueLecture
            norm.contains("pause") || norm.contains("stop") -> VoiceLectureCommand.PauseLecture
            norm.contains("restart") || norm.contains("start over") -> VoiceLectureCommand.RestartLecture
            norm.contains("quiz me") || norm.contains("test me") -> VoiceLectureCommand.QuizMe
            norm.contains("example") -> VoiceLectureCommand.SwitchMode(LectureAction.GIVE_EXAMPLE)
            norm.contains("beginner") || norm.contains("simpler") || norm.contains("simple") ->
                VoiceLectureCommand.SwitchMode(LectureAction.EXPLAIN_SIMPLY)
            norm.contains("step by step") -> VoiceLectureCommand.SwitchMode(LectureAction.STEP_BY_STEP)
            norm.contains("difference") || norm.contains("compare") -> VoiceLectureCommand.SwitchMode(LectureAction.COMPARE)
            norm.contains("summarize") || norm.contains("summary") -> VoiceLectureCommand.SwitchMode(LectureAction.SUMMARIZE)
            else -> VoiceLectureCommand.AskQuestion(transcript.trim())
        }
    }

    fun parseNavigationCommand(transcript: String): VoiceNavigationCommand? {
        val norm = transcript.trim().lowercase()
        if (norm.isBlank()) return null

        return when {
            norm == "stop speaking" || norm == "stop talking" || norm == "be quiet" || norm == "mute voice" ->
                VoiceNavigationCommand.StopSpeaking
            norm == "minimize" || norm.contains("minimize chat") || norm.contains("close chat") ||
                norm.contains("hide chat") || norm.contains("hide companion") || norm.contains("close assistant") ->
                VoiceNavigationCommand.MinimizeCompanion
            norm.contains("open chat") || norm.contains("open companion") || norm.contains("show chat") ||
                norm.contains("expand chat") ->
                VoiceNavigationCommand.ExpandCompanion
            norm == "new chat" || norm.contains("start new chat") || norm.contains("new conversation") ->
                VoiceNavigationCommand.StartNewChat
            norm == "clear chat" || norm.contains("clear conversation") || norm.contains("delete chat") ->
                VoiceNavigationCommand.ClearChat
            norm == "go back" || norm == "navigate back" || norm == "previous screen" ->
                VoiceNavigationCommand.GoBack
            norm == "home" || norm.contains("go home") || norm.contains("go to home") ||
                norm.contains("open home") || norm.contains("home screen") ->
                VoiceNavigationCommand.GoHome
            norm.contains("teach ai") || norm.contains("teach the ai") || norm.contains("feynman mode") ->
                VoiceNavigationCommand.GoTeachAi
            norm == "quiz" || norm.contains("open quiz") || norm.contains("go to quiz") ||
                norm.contains("quiz arena") || norm.contains("quiz hub") ||
                norm.contains("start quiz") || norm.contains("start a quiz") || norm.contains("take a quiz") ->
                VoiceNavigationCommand.GoQuiz
            norm == "lecture" || norm.contains("open lecture") || norm.contains("go to lecture") ||
                norm.contains("lecture screen") ->
                VoiceNavigationCommand.GoLecture
            norm == "library" || norm.contains("open library") || norm.contains("go to library") ||
                norm.contains("show library") || norm.contains("my handouts") ||
                norm.contains("open handouts") || norm.contains("go to handouts") ->
                VoiceNavigationCommand.GoLibrary
            norm == "settings" || norm == "profile" || norm.contains("open settings") ||
                norm.contains("go to settings") || norm.contains("open profile") || norm.contains("go to profile") ->
                VoiceNavigationCommand.GoSettings
            else -> null
        }
    }

    fun parseContextualCommand(
        transcript: String,
        context: StudyContextSnapshot,
        isCompanionExpanded: Boolean
    ): VoiceContextualCommand {
        val trimmed = transcript.trim()
        val norm = trimmed.lowercase()

        // Preserve Lecture screen's handout-scoped "quiz me" / "test me"
        if (context.currentScreen == "Lecture" && (norm.contains("quiz me") || norm.contains("test me"))) {
            return VoiceContextualCommand.Lecture(VoiceLectureCommand.QuizMe)
        }

        parseNavigationCommand(trimmed)?.let { nav ->
            return VoiceContextualCommand.Navigation(nav)
        }

        if (context.currentScreen == "Quiz" && !context.currentQuestionText.isNullOrBlank()) {
            val quizCmd = parseQuizCommand(trimmed, context.currentOptions)
            val looksConversational = isCompanionExpanded && quizCmd is VoiceQuizCommand.Unrecognized
            return if (looksConversational) {
                VoiceContextualCommand.CompanionChat(trimmed)
            } else {
                VoiceContextualCommand.Quiz(quizCmd)
            }
        }

        if (context.currentScreen == "Lecture") {
            val lectureCmd = parseLectureCommand(trimmed)
            return if (lectureCmd is VoiceLectureCommand.AskQuestion && isCompanionExpanded) {
                VoiceContextualCommand.CompanionChat(lectureCmd.question)
            } else {
                VoiceContextualCommand.Lecture(lectureCmd)
            }
        }

        return VoiceContextualCommand.CompanionChat(trimmed)
    }
}
