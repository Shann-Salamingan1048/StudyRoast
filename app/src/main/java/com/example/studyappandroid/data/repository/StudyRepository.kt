package com.example.studyappandroid.data.repository

import android.content.Context
import android.net.Uri
import com.example.studyappandroid.data.document.DocumentProcessingOutcome
import com.example.studyappandroid.data.document.DocumentProcessor
import com.example.studyappandroid.data.local.db.ConversationDao
import com.example.studyappandroid.data.local.db.ConversationMessageEntity
import com.example.studyappandroid.data.local.db.HandoutDao
import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.data.local.db.LectureDao
import com.example.studyappandroid.data.local.db.LectureEntity
import com.example.studyappandroid.data.local.db.QuestionEntity
import com.example.studyappandroid.data.local.db.QuestionResponseEntity
import com.example.studyappandroid.data.local.db.QuizAttemptEntity
import com.example.studyappandroid.data.local.db.QuizDao
import com.example.studyappandroid.data.local.db.QuizEntity
import com.example.studyappandroid.data.local.db.ReviewScheduleDao
import com.example.studyappandroid.data.local.db.ReviewScheduleEntity
import com.example.studyappandroid.data.local.db.SubjectDao
import com.example.studyappandroid.data.local.db.SubjectEntity
import com.example.studyappandroid.data.local.db.TopicEntity
import com.example.studyappandroid.data.local.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudyRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val subjectDao: SubjectDao,
    private val handoutDao: HandoutDao,
    private val quizDao: QuizDao,
    private val lectureDao: LectureDao,
    private val conversationDao: ConversationDao,
    private val reviewScheduleDao: ReviewScheduleDao,
    private val documentProcessor: DocumentProcessor,
    private val preferencesRepository: UserPreferencesRepository
) {
    fun observeSubjects(): Flow<List<SubjectEntity>> = subjectDao.observeAllSubjects()

    fun observeAllHandouts(): Flow<List<HandoutEntity>> = handoutDao.observeAllHandouts()

    fun observeRecentHandouts(limit: Int = 5): Flow<List<HandoutEntity>> =
        handoutDao.observeRecentHandouts(limit)

    fun searchHandouts(subjectName: String?, query: String): Flow<List<HandoutEntity>> =
        handoutDao.searchHandouts(subjectName, query)

    fun observeHandout(handoutId: Long): Flow<HandoutEntity?> = handoutDao.observeHandoutById(handoutId)

    suspend fun getHandout(handoutId: Long): HandoutEntity? = handoutDao.getHandoutById(handoutId)

    suspend fun getHandoutByTitle(title: String): HandoutEntity? = handoutDao.getHandoutByTitle(title)

    suspend fun getHandoutsByIds(ids: List<Long>): List<HandoutEntity> =
        if (ids.isEmpty()) emptyList() else handoutDao.getHandoutsByIds(ids)

    fun observeTopicsForHandout(handoutId: Long): Flow<List<TopicEntity>> =
        handoutDao.observeTopicsForHandout(handoutId)

    suspend fun getTopicsForHandout(handoutId: Long): List<TopicEntity> =
        handoutDao.getTopicsForHandout(handoutId)

    suspend fun ensureSubject(name: String, colorHex: Long = 0xFFFF6B4A): SubjectEntity {
        val clean = name.trim().ifEmpty { "General" }
        val existing = subjectDao.getByName(clean)
        if (existing != null) return existing
        val id = subjectDao.insert(SubjectEntity(name = clean, colorHex = colorHex))
        return SubjectEntity(id = id, name = clean, colorHex = colorHex)
    }

    sealed class ImportHandoutResult {
        data class Success(val handout: HandoutEntity, val topicCount: Int) : ImportHandoutResult()
        data class Error(val message: String) : ImportHandoutResult()
    }

    suspend fun importHandout(uri: Uri, subjectName: String): ImportHandoutResult {
        return when (val outcome = documentProcessor.processUri(uri)) {
            is DocumentProcessingOutcome.Failure -> ImportHandoutResult.Error(outcome.userMessage)
            is DocumentProcessingOutcome.Success -> {
                val res = outcome.result
                val subject = ensureSubject(subjectName)
                val entity = HandoutEntity(
                    subjectId = subject.id,
                    subjectName = subject.name,
                    title = res.title,
                    fileName = res.fileName,
                    fileType = res.fileType,
                    localFilePath = res.localFilePath,
                    extractedText = res.extractedText,
                    summary = res.summary,
                    definitionsJson = res.definitionsJson,
                    formulasJson = res.formulasJson,
                    keyObjectivesJson = res.keyObjectivesJson,
                    isOcrUsed = res.isOcrUsed,
                    wordCount = res.wordCount
                )
                val handoutId = handoutDao.insertHandout(entity)
                val topicEntities = res.topics.mapIndexed { idx, topic ->
                    TopicEntity(
                        handoutId = handoutId,
                        title = topic.title,
                        contentSnippet = topic.contentSnippet,
                        orderIndex = idx
                    )
                }
                handoutDao.insertTopics(topicEntities)
                preferencesRepository.recordStudyActivity()
                ImportHandoutResult.Success(entity.copy(id = handoutId), topicEntities.size)
            }
        }
    }

    suspend fun deleteHandout(handout: HandoutEntity) {
        runCatching { File(handout.localFilePath).delete() }
        handoutDao.deleteHandoutById(handout.id)
    }

    // Quiz DAOs
    fun observeAllQuizzes(): Flow<List<QuizEntity>> = quizDao.observeAllQuizzes()
    fun observeAllAttempts(): Flow<List<QuizAttemptEntity>> = quizDao.observeAllAttempts()
    suspend fun getQuiz(quizId: Long): QuizEntity? = quizDao.getQuizById(quizId)
    suspend fun getQuestionsForQuiz(quizId: Long): List<QuestionEntity> = quizDao.getQuestionsForQuiz(quizId)
    suspend fun saveQuiz(quiz: QuizEntity, questions: List<QuestionEntity>): Long =
        quizDao.insertQuizWithQuestions(quiz, questions)

    suspend fun deleteQuiz(quizId: Long) = quizDao.deleteQuizById(quizId)
    suspend fun getAttempt(attemptId: Long): QuizAttemptEntity? = quizDao.getAttemptById(attemptId)
    suspend fun getResponsesForAttempt(attemptId: Long): List<QuestionResponseEntity> =
        quizDao.getResponsesForAttempt(attemptId)

    suspend fun getLastIncorrectQuestions(quizId: Long): List<QuestionEntity> =
        quizDao.getLastIncorrectQuestionsForQuiz(quizId)

    suspend fun saveQuizAttempt(
        attempt: QuizAttemptEntity,
        responses: List<QuestionResponseEntity>
    ): Long {
        val attemptId = quizDao.insertAttempt(attempt)
        quizDao.insertResponses(responses.map { it.copy(attemptId = attemptId) })
        preferencesRepository.recordStudyActivity(attempt.durationSeconds.toLong())
        return attemptId
    }

    // Lectures
    fun observeLecturesForHandout(handoutId: Long): Flow<List<LectureEntity>> =
        lectureDao.observeLecturesForHandout(handoutId)

    fun observeAllLectures(): Flow<List<LectureEntity>> = lectureDao.observeAllLectures()

    suspend fun getCachedLecture(handoutId: Long, topicTitle: String, actionType: String): LectureEntity? =
        lectureDao.getCachedLecture(handoutId, topicTitle, actionType)

    suspend fun saveLecture(lecture: LectureEntity): Long {
        preferencesRepository.recordStudyActivity()
        return lectureDao.insertLecture(lecture)
    }

    // Conversations
    fun observeConversation(sessionId: String): Flow<List<ConversationMessageEntity>> =
        conversationDao.observeSessionMessages(sessionId)

    fun observeSessionIds(): Flow<List<String>> = conversationDao.observeAllSessionIds()

    suspend fun saveConversationMessage(message: ConversationMessageEntity): Long =
        conversationDao.insertMessage(message)

    suspend fun clearConversation(sessionId: String) = conversationDao.clearSession(sessionId)
    suspend fun clearAllConversations() = conversationDao.clearAll()

    // Adaptive Learning / Review Schedule
    fun observeReviewSchedules(): Flow<List<ReviewScheduleEntity>> =
        reviewScheduleDao.observeAllSchedules()

    fun observeRecommendedTopics(limit: Int = 6): Flow<List<ReviewScheduleEntity>> =
        reviewScheduleDao.observeRecommendedTopics(System.currentTimeMillis(), limit)

    suspend fun getReviewSchedule(topicTitle: String): ReviewScheduleEntity? =
        reviewScheduleDao.getByTopicTitle(topicTitle)

    suspend fun upsertReviewSchedule(schedule: ReviewScheduleEntity): Long =
        reviewScheduleDao.upsert(schedule)

    suspend fun clearAllStudyData() {
        val handoutsDir = File(context.filesDir, "handouts")
        runCatching { handoutsDir.deleteRecursively() }
        handoutDao.deleteAllHandouts()
        quizDao.deleteAllQuizzes()
        conversationDao.clearAll()
        reviewScheduleDao.clearAll()
    }
}
