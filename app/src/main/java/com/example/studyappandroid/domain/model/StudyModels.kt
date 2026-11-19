package com.example.studyappandroid.domain.model

import kotlinx.serialization.Serializable

enum class ThemeMode(val label: String) {
    DARK("Dark (Default)"),
    LIGHT("Light"),
    SYSTEM("System")
}

enum class AiPersonality(
    val displayName: String,
    val tagline: String,
    val systemPromptDirective: String
) {
    FRIENDLY(
        displayName = "Friendly",
        tagline = "Supportive, warm, and encouraging",
        systemPromptDirective = "You are a warm, supportive, encouraging study partner. Celebrate wins and gently guide the student through mistakes without teasing."
    ),
    SARCASTIC(
        displayName = "Sarcastic",
        tagline = "Playful teasing and witty comebacks",
        systemPromptDirective = "You are a witty, playfully sarcastic, knowledgeable study partner. Tease the student lightly about academic slip-ups, use dry humor, but always give clear, accurate explanations immediately afterward. Never attack personal identity or sensitive traits."
    ),
    SAVAGE(
        displayName = "Savage",
        tagline = "Exaggerated comedic academic roasting",
        systemPromptDirective = "You are an over-the-top comedic study roaster who dramatically reacts to wrong answers like a disappointed best friend, then immediately delivers razor-sharp, memorable explanations. Keep all roasting strictly about academic mistakes and study habits—never personal identity or genuinely degrading attacks."
    ),
    SERIOUS(
        displayName = "Serious",
        tagline = "Professional, direct, and academic",
        systemPromptDirective = "You are a focused, professional, academic tutor. Keep feedback concise, direct, structured, and free of slang or jokes."
    )
}

enum class AvatarExpression {
    IDLE,
    HAPPY,
    EXCITED,
    DISAPPOINTED,
    SHOCKED,
    LAUGHING,
    THINKING,
    CONFUSED,
    PROUD,
    FACEPALMING,
    SPEAKING,
    LISTENING
}

enum class AvatarStyle(val displayName: String) {
    SPARK("RoastBot Classic"),
    SCHOLAR("Professor Glasses"),
    FLAME("Spicy Flame")
}

enum class QuizDifficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    MIXED("Mixed")
}

enum class VoiceInteractionMode(val label: String) {
    PUSH_TO_TALK("Push to Talk"),
    HANDS_FREE("Hands-Free")
}

@Serializable
data class StudyContextSnapshot(
    val currentScreen: String = "Home",
    val handoutTitle: String? = null,
    val handoutSummary: String? = null,
    val topicTitle: String? = null,
    val currentQuestionText: String? = null,
    val currentOptions: List<String> = emptyList(),
    val isQuizAnswered: Boolean = false,
    val lastMistakeNote: String? = null
)

enum class LectureAction(val label: String, val promptInstruction: String) {
    EXPLAIN_SIMPLY("Explain Simply", "Explain this concept in simple, everyday language with an analogy a beginner can grasp immediately."),
    EXPLAIN_DETAIL("Explain in Detail", "Provide a thorough, structured, in-depth academic explanation covering mechanisms, nuances, and key terms."),
    GIVE_EXAMPLE("Give an Example", "Give 1-2 concrete, memorable real-world examples illustrating this concept clearly."),
    STEP_BY_STEP("Step by Step", "Break down this process or concept into clear numbered steps from start to finish."),
    COMPARE("Compare Concepts", "Compare and contrast the main related concepts or easily confused terms in this topic."),
    SUMMARIZE("Summarize Topic", "Summarize the most testable key takeaways, definitions, and formulas in concise bullet points.")
}
