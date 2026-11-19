package com.example.studyappandroid.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: Long = 0xFFFF6B4A,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "handouts",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId")]
)
data class HandoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val subjectName: String = "General",
    val title: String,
    val fileName: String,
    val fileType: String,
    val localFilePath: String,
    val extractedText: String,
    val summary: String,
    val definitionsJson: String = "[]",
    val formulasJson: String = "[]",
    val keyObjectivesJson: String = "[]",
    val isOcrUsed: Boolean = false,
    val wordCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = HandoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["handoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("handoutId")]
)
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val handoutId: Long,
    val title: String,
    val contentSnippet: String,
    val orderIndex: Int = 0
)

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectName: String,
    val handoutIdsCsv: String,
    val topicFilter: String? = null,
    val difficulty: String,
    val questionCount: Int,
    val isTimed: Boolean = false,
    val timeLimitSeconds: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = QuizEntity::class,
            parentColumns = ["id"],
            childColumns = ["quizId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("quizId")]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: Long,
    val handoutId: Long? = null,
    val topicTitle: String,
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOptionIndex: Int,
    val explanation: String,
    val wrongOptionsExplanation: String,
    val sourceSection: String = "",
    val sourcePage: String = "",
    val supportingPassage: String = "",
    val validationStatus: String = "VERIFIED",
    val difficulty: String = "MEDIUM",
    val orderIndex: Int = 0
)

@Entity(
    tableName = "quiz_attempts",
    foreignKeys = [
        ForeignKey(
            entity = QuizEntity::class,
            parentColumns = ["id"],
            childColumns = ["quizId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("quizId")]
)
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: Long,
    val quizTitle: String,
    val subjectName: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val assistedCount: Int = 0,
    val accuracyPercent: Float,
    val durationSeconds: Int,
    val strongestTopic: String,
    val weakestTopic: String,
    val roastComment: String,
    val completedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "question_responses",
    foreignKeys = [
        ForeignKey(
            entity = QuizAttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("attemptId"), Index("questionId")]
)
data class QuestionResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attemptId: Long,
    val questionId: Long,
    val topicTitle: String,
    val selectedOptionIndex: Int,
    val isCorrect: Boolean,
    val wasAssisted: Boolean = false,
    val answeredAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "lectures",
    foreignKeys = [
        ForeignKey(
            entity = HandoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["handoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("handoutId")]
)
data class LectureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val handoutId: Long,
    val topicTitle: String,
    val actionType: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversation_messages", indices = [Index("sessionId")])
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val contextScreen: String = "Companion",
    val handoutId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "review_schedules", indices = [Index("handoutId"), Index("nextReviewAt")])
data class ReviewScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val handoutId: Long? = null,
    val subjectName: String = "General",
    val topicTitle: String,
    val easinessFactor: Float = 2.5f,
    val intervalDays: Int = 1,
    val repetitions: Int = 0,
    val mistakeCount: Int = 0,
    val correctCount: Int = 0,
    val lastReviewedAt: Long = System.currentTimeMillis(),
    val nextReviewAt: Long = System.currentTimeMillis()
)
