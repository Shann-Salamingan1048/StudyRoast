package com.example.studyappandroid.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun observeAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: SubjectEntity): Long

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface HandoutDao {
    @Query("SELECT * FROM handouts ORDER BY createdAt DESC")
    fun observeAllHandouts(): Flow<List<HandoutEntity>>

    @Query("SELECT * FROM handouts ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentHandouts(limit: Int): Flow<List<HandoutEntity>>

    @Query(
        """
        SELECT * FROM handouts
        WHERE (:subjectName IS NULL OR subjectName = :subjectName)
        AND (:query = '' OR title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' OR extractedText LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
        """
    )
    fun searchHandouts(subjectName: String?, query: String): Flow<List<HandoutEntity>>

    @Query("SELECT * FROM handouts WHERE id = :id LIMIT 1")
    suspend fun getHandoutById(id: Long): HandoutEntity?

    @Query("SELECT * FROM handouts WHERE title = :title LIMIT 1")
    suspend fun getHandoutByTitle(title: String): HandoutEntity?

    @Query("SELECT * FROM handouts WHERE id = :id LIMIT 1")
    fun observeHandoutById(id: Long): Flow<HandoutEntity?>

    @Query("SELECT * FROM handouts WHERE id IN (:ids)")
    suspend fun getHandoutsByIds(ids: List<Long>): List<HandoutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHandout(handout: HandoutEntity): Long

    @Update
    suspend fun updateHandout(handout: HandoutEntity)

    @Query("DELETE FROM handouts WHERE id = :id")
    suspend fun deleteHandoutById(id: Long)

    @Query("DELETE FROM handouts")
    suspend fun deleteAllHandouts()

    @Query("SELECT * FROM topics WHERE handoutId = :handoutId ORDER BY orderIndex ASC")
    fun observeTopicsForHandout(handoutId: Long): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE handoutId = :handoutId ORDER BY orderIndex ASC")
    suspend fun getTopicsForHandout(handoutId: Long): List<TopicEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<TopicEntity>)

    @Query("DELETE FROM topics WHERE handoutId = :handoutId")
    suspend fun deleteTopicsForHandout(handoutId: Long)
}

@Dao
interface QuizDao {
    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun observeAllQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE id = :quizId LIMIT 1")
    suspend fun getQuizById(quizId: Long): QuizEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Transaction
    suspend fun insertQuizWithQuestions(quiz: QuizEntity, questions: List<QuestionEntity>): Long {
        val quizId = insertQuiz(quiz)
        insertQuestions(questions.mapIndexed { idx, q -> q.copy(quizId = quizId, orderIndex = idx) })
        return quizId
    }

    @Query("SELECT * FROM questions WHERE quizId = :quizId ORDER BY orderIndex ASC")
    suspend fun getQuestionsForQuiz(quizId: Long): List<QuestionEntity>

    @Query("DELETE FROM quizzes WHERE id = :quizId")
    suspend fun deleteQuizById(quizId: Long)

    @Query("DELETE FROM quizzes")
    suspend fun deleteAllQuizzes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: QuizAttemptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponses(responses: List<QuestionResponseEntity>)

    @Query("SELECT * FROM quiz_attempts ORDER BY completedAt DESC")
    fun observeAllAttempts(): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempts WHERE id = :attemptId LIMIT 1")
    suspend fun getAttemptById(attemptId: Long): QuizAttemptEntity?

    @Query("SELECT * FROM question_responses WHERE attemptId = :attemptId")
    suspend fun getResponsesForAttempt(attemptId: Long): List<QuestionResponseEntity>

    @Query(
        """
        SELECT q.* FROM questions q
        INNER JOIN question_responses r ON r.questionId = q.id
        WHERE r.attemptId = (
            SELECT id FROM quiz_attempts WHERE quizId = :quizId ORDER BY completedAt DESC LIMIT 1
        ) AND r.isCorrect = 0
        ORDER BY q.orderIndex ASC
        """
    )
    suspend fun getLastIncorrectQuestionsForQuiz(quizId: Long): List<QuestionEntity>
}

@Dao
interface LectureDao {
    @Query("SELECT * FROM lectures WHERE handoutId = :handoutId ORDER BY createdAt DESC")
    fun observeLecturesForHandout(handoutId: Long): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures ORDER BY createdAt DESC")
    fun observeAllLectures(): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE handoutId = :handoutId AND topicTitle = :topicTitle AND actionType = :actionType LIMIT 1")
    suspend fun getCachedLecture(handoutId: Long, topicTitle: String, actionType: String): LectureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecture(lecture: LectureEntity): Long

    @Query("DELETE FROM lectures WHERE id = :id")
    suspend fun deleteLectureById(id: Long)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeSessionMessages(sessionId: String): Flow<List<ConversationMessageEntity>>

    @Query("SELECT DISTINCT sessionId FROM conversation_messages ORDER BY timestamp DESC")
    fun observeAllSessionIds(): Flow<List<String>>

    @Query("SELECT * FROM conversation_messages ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentMessages(): Flow<List<ConversationMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessageEntity): Long

    @Query("DELETE FROM conversation_messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("DELETE FROM conversation_messages")
    suspend fun clearAll()
}

@Dao
interface ReviewScheduleDao {
    @Query("SELECT * FROM review_schedules ORDER BY nextReviewAt ASC")
    fun observeAllSchedules(): Flow<List<ReviewScheduleEntity>>

    @Query("SELECT * FROM review_schedules WHERE nextReviewAt <= :nowMillis OR mistakeCount > correctCount ORDER BY mistakeCount DESC, nextReviewAt ASC LIMIT :limit")
    fun observeRecommendedTopics(nowMillis: Long, limit: Int = 6): Flow<List<ReviewScheduleEntity>>

    @Query("SELECT * FROM review_schedules WHERE topicTitle = :topicTitle LIMIT 1")
    suspend fun getByTopicTitle(topicTitle: String): ReviewScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ReviewScheduleEntity): Long

    @Query("DELETE FROM review_schedules")
    suspend fun clearAll()
}
