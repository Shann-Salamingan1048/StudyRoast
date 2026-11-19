package com.example.studyappandroid.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class,
        HandoutEntity::class,
        TopicEntity::class,
        QuizEntity::class,
        QuestionEntity::class,
        QuizAttemptEntity::class,
        QuestionResponseEntity::class,
        LectureEntity::class,
        ConversationMessageEntity::class,
        ReviewScheduleEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class StudyRoastDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun handoutDao(): HandoutDao
    abstract fun quizDao(): QuizDao
    abstract fun lectureDao(): LectureDao
    abstract fun conversationDao(): ConversationDao
    abstract fun reviewScheduleDao(): ReviewScheduleDao
}
