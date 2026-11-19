package com.example.studyappandroid.data.repository

import com.example.studyappandroid.data.local.db.ReviewScheduleEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * SM-2 inspired spaced repetition scheduler for adaptive weak-topic detection and review scheduling.
 */
@Singleton
class SpacedRepetitionEngine @Inject constructor() {

    fun nextSchedule(
        existing: ReviewScheduleEntity?,
        handoutId: Long?,
        subjectName: String,
        topicTitle: String,
        isCorrect: Boolean,
        wasAssisted: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): ReviewScheduleEntity {
        val base = existing ?: ReviewScheduleEntity(
            handoutId = handoutId,
            subjectName = subjectName,
            topicTitle = topicTitle
        )
        // Quality score 0..5: 5 = unassisted correct, 3 = assisted correct, 1 = incorrect
        val quality = when {
            isCorrect && !wasAssisted -> 5
            isCorrect && wasAssisted -> 3
            else -> 1
        }

        var ef = base.easinessFactor + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f))
        if (ef < 1.3f) ef = 1.3f

        val newRepetitions: Int
        val newIntervalDays: Int
        if (quality < 3) {
            newRepetitions = 0
            newIntervalDays = 1
        } else {
            newRepetitions = base.repetitions + 1
            newIntervalDays = when (newRepetitions) {
                1 -> 1
                2 -> 3
                else -> (base.intervalDays * ef).roundToInt().coerceAtLeast(3)
            }
        }

        val dayMillis = 24L * 60L * 60L * 1000L
        val nextReviewAt = if (quality < 3) {
            // Review missed concepts much sooner (4 hours)
            nowMillis + (4L * 60L * 60L * 1000L)
        } else {
            nowMillis + newIntervalDays * dayMillis
        }

        return base.copy(
            handoutId = handoutId ?: base.handoutId,
            subjectName = subjectName.ifBlank { base.subjectName },
            easinessFactor = ef,
            intervalDays = newIntervalDays,
            repetitions = newRepetitions,
            mistakeCount = base.mistakeCount + if (!isCorrect) 1 else 0,
            correctCount = base.correctCount + if (isCorrect) 1 else 0,
            lastReviewedAt = nowMillis,
            nextReviewAt = nextReviewAt
        )
    }
}
