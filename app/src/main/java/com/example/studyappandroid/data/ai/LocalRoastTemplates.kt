package com.example.studyappandroid.data.ai

import com.example.studyappandroid.domain.model.AiPersonality
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates personality-appropriate reactions locally without spending API tokens.
 * Tracks recently used lines to avoid repetitive jokes.
 */
@Singleton
class LocalRoastTemplates @Inject constructor() {
    private val recentLines = ArrayDeque<String>(12)

    fun correctReaction(personality: AiPersonality, streak: Int): String {
        val pool = when (personality) {
            AiPersonality.FRIENDLY -> listOf(
                "Awesome job! You nailed that concept cleanly.",
                "Spot on! Your studying is really paying off.",
                "Correct! Keep that momentum going!",
                "Nice work! You clearly understood that section.",
                "Great recall! Ready for the next one?"
            )
            AiPersonality.SARCASTIC -> listOf(
                "WAIT, YOU ACTUALLY GOT IT RIGHT?! 🔥 I'm witnessing history!",
                "Look at you using your brain! I'm proud, but don't get too comfortable.",
                "FINALLY! Your neurons are cooperating today!",
                "Did you actually read the handout, or did you guess with elite luck?",
                "Okay, professor, calm down—one right answer doesn't mean you can skip review."
            )
            AiPersonality.SAVAGE -> listOf(
                "HOLD EVERYTHING. Someone alert the dean—you got one RIGHT! 🔥",
                "Okay, Einstein, who possessed your thumb?! That was actually correct!",
                "Wait, you didn't fumble?! Even a broken clock gets 100% twice a day, let's keep going!",
                "Miracles DO happen! Your brain finally clocked in for its shift!",
                "Respect. That answer actually had braincells behind it."
            )
            AiPersonality.SERIOUS -> listOf(
                "Correct. Your reasoning aligns with the source material.",
                "Accurate response. Let's proceed to the next concept.",
                "Well done. That concept appears solid.",
                "Correct selection. Moving forward."
            )
        }
        val streakPrefix = if (streak >= 3 && personality != AiPersonality.SERIOUS) {
            "($streak in a row!) "
        } else ""
        return streakPrefix + pickNonRepeating(pool)
    }

    fun wrongReaction(personality: AiPersonality, wrongStreak: Int): String {
        if (wrongStreak >= 3 && personality == AiPersonality.SAVAGE) {
            return "THREE WRONG ANSWERS IN A ROW?! 😭 Bro, the handout is begging you to read it!"
        }
        val pool = when (personality) {
            AiPersonality.FRIENDLY -> listOf(
                "Not quite! Don't worry, I'll explain it another way.",
                "Close, but that's a common trap. Let's review why!",
                "Good try! Mistakes are literally how memory locks in. Here's the breakdown:",
                "Almost there—let's clear up that detail together."
            )
            AiPersonality.SARCASTIC -> listOf(
                "Interesting answer. Unfortunately, the laws of the subject disagree with you. 💀",
                "Congratulations, you just invented a completely new answer. Unfortunately, your professor won't accept it.",
                "Your brain really said random answer generator today. Let's fix that.",
                "Bold strategy picking that option. Sadly, accuracy wasn't part of the strategy.",
                "I admire the confidence, even if the handout says the exact opposite."
            )
            AiPersonality.SAVAGE -> listOf(
                "BRO, WHAT WAS THAT ANSWER?! 💀 Did you even open the handout? Here's what you missed.",
                "HOW DID YOU GET THAT WRONG?! 😭 We literally studied this! Listen carefully.",
                "My expectations were low, yet you still brought a shovel. Let's rescue your grade:",
                "Did you close your eyes and let gravity tap the screen?! Read this explanation RIGHT NOW.",
                "That answer just set academic civilization back 200 years. Let's fix it."
            )
            AiPersonality.SERIOUS -> listOf(
                "That answer is incorrect. Let's examine the concept carefully.",
                "Incorrect selection. Review the core principle below.",
                "Not accurate. Note the distinction explained below.",
                "Incorrect. Pay close attention to the source material."
            )
        }
        return pickNonRepeating(pool)
    }

    fun summaryComment(personality: AiPersonality, accuracyPercent: Float): String {
        val pct = accuracyPercent.toInt()
        return when (personality) {
            AiPersonality.FRIENDLY -> when {
                pct >= 85 -> "Amazing $pct%! You're really mastering this material!"
                pct >= 65 -> "Solid $pct%! Review the missed topics once more and you'll crush it."
                else -> "You scored $pct%—every missed question just showed us what to focus on next!"
            }
            AiPersonality.SARCASTIC -> when {
                pct >= 85 -> "$pct%?! Suspiciously competent. Did you actually study?! 🏆"
                pct >= 65 -> "$pct%?! Not bad, but those remaining ${100 - pct}% are plotting against your exam score. Let's fix them. 💀"
                else -> "$pct%... Well, at least we found out BEFORE the actual exam. Time for a rematch!"
            }
            AiPersonality.SAVAGE -> when {
                pct >= 85 -> "$pct%?! Okay okay, I can't even roast that—you cooked! 🔥"
                pct >= 65 -> "$pct%?! Passing isn't thriving, champ. Let's bully those weak topics into submission."
                else -> "$pct%?! 💀 The handout filed a restraining order against you. Hit 'Retry Wrong Questions' immediately!"
            }
            AiPersonality.SERIOUS -> when {
                pct >= 85 -> "Strong performance at $pct% accuracy. Continue spaced repetition to retain mastery."
                pct >= 65 -> "$pct% accuracy achieved. Focus review on your weakest identified topics."
                else -> "$pct% accuracy. Recommend reviewing the handout explanations and retrying missed questions."
            }
        }
    }

    fun hintRefusalPrefix(personality: AiPersonality): String = when (personality) {
        AiPersonality.FRIENDLY -> "Here's a helpful nudge without spoiling the answer: "
        AiPersonality.SARCASTIC -> "Already struggling? 💀 Fine, here's a hint. But I'm not giving you the answer: "
        AiPersonality.SAVAGE -> "Asking for a lifeline already?! 😭 Alright, take this hint and lock in: "
        AiPersonality.SERIOUS -> "Hint: "
    }

    private fun pickNonRepeating(pool: List<String>): String {
        val candidates = pool.filterNot { it in recentLines }
        val chosen = (if (candidates.isNotEmpty()) candidates else pool).random()
        if (recentLines.size >= 10) recentLines.removeFirst()
        recentLines.addLast(chosen)
        return chosen
    }
}
