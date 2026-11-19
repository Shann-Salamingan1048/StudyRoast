package com.example.studyappandroid.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.studyappandroid.data.local.db.ConversationDao
import com.example.studyappandroid.data.local.db.HandoutDao
import com.example.studyappandroid.data.local.db.LectureDao
import com.example.studyappandroid.data.local.db.QuizDao
import com.example.studyappandroid.data.local.db.ReviewScheduleDao
import com.example.studyappandroid.data.local.db.StudyRoastDatabase
import com.example.studyappandroid.data.local.db.SubjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE questions ADD COLUMN sourcePage TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE questions ADD COLUMN supportingPassage TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE questions ADD COLUMN validationStatus TEXT NOT NULL DEFAULT 'VERIFIED'")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StudyRoastDatabase {
        return Room.databaseBuilder(
            context,
            StudyRoastDatabase::class.java,
            "studyroast_db"
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideSubjectDao(db: StudyRoastDatabase): SubjectDao = db.subjectDao()

    @Provides
    fun provideHandoutDao(db: StudyRoastDatabase): HandoutDao = db.handoutDao()

    @Provides
    fun provideQuizDao(db: StudyRoastDatabase): QuizDao = db.quizDao()

    @Provides
    fun provideLectureDao(db: StudyRoastDatabase): LectureDao = db.lectureDao()

    @Provides
    fun provideConversationDao(db: StudyRoastDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideReviewScheduleDao(db: StudyRoastDatabase): ReviewScheduleDao = db.reviewScheduleDao()
}
