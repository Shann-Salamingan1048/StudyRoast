package com.example.studyappandroid.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.studyappandroid.domain.model.AiPersonality
import com.example.studyappandroid.domain.model.AvatarStyle
import com.example.studyappandroid.domain.model.QuizDifficulty
import com.example.studyappandroid.domain.model.ThemeMode
import com.example.studyappandroid.domain.model.VoiceInteractionMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "studyroast_prefs")

data class UserPreferences(
    val userName: String = "Scholar",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val personality: AiPersonality = AiPersonality.SARCASTIC,
    val avatarStyle: AvatarStyle = AvatarStyle.SPARK,
    val voiceEnabled: Boolean = true,
    val voiceInteractionMode: VoiceInteractionMode = VoiceInteractionMode.PUSH_TO_TALK,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val persistentMemoryEnabled: Boolean = true,
    val defaultQuizCount: Int = 5,
    val defaultDifficulty: QuizDifficulty = QuizDifficulty.MIXED,
    val showExplanationsImmediately: Boolean = true,
    val streakDays: Int = 0,
    val lastStudyEpochDay: Long = 0L,
    val totalStudySeconds: Long = 0L,
    val preferredExplanationStyle: String = "Conversational with concrete examples"
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PERSONALITY = stringPreferencesKey("personality")
        val AVATAR_STYLE = stringPreferencesKey("avatar_style")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val VOICE_MODE = stringPreferencesKey("voice_mode")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val PERSISTENT_MEMORY = booleanPreferencesKey("persistent_memory")
        val DEFAULT_QUIZ_COUNT = intPreferencesKey("default_quiz_count")
        val DEFAULT_DIFFICULTY = stringPreferencesKey("default_difficulty")
        val SHOW_EXPLANATIONS = booleanPreferencesKey("show_explanations")
        val STREAK_DAYS = intPreferencesKey("streak_days")
        val LAST_STUDY_EPOCH_DAY = longPreferencesKey("last_study_epoch_day")
        val TOTAL_STUDY_SECONDS = longPreferencesKey("total_study_seconds")
        val EXPLANATION_STYLE = stringPreferencesKey("explanation_style")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            userName = prefs[Keys.USER_NAME] ?: "Scholar",
            themeMode = enumOrDefault(prefs[Keys.THEME_MODE], ThemeMode.DARK),
            personality = enumOrDefault(prefs[Keys.PERSONALITY], AiPersonality.SARCASTIC),
            avatarStyle = enumOrDefault(prefs[Keys.AVATAR_STYLE], AvatarStyle.SPARK),
            voiceEnabled = prefs[Keys.VOICE_ENABLED] ?: true,
            voiceInteractionMode = enumOrDefault(prefs[Keys.VOICE_MODE], VoiceInteractionMode.PUSH_TO_TALK),
            ttsSpeechRate = prefs[Keys.TTS_RATE] ?: 1.0f,
            ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.0f,
            persistentMemoryEnabled = prefs[Keys.PERSISTENT_MEMORY] ?: true,
            defaultQuizCount = prefs[Keys.DEFAULT_QUIZ_COUNT] ?: 5,
            defaultDifficulty = enumOrDefault(prefs[Keys.DEFAULT_DIFFICULTY], QuizDifficulty.MIXED),
            showExplanationsImmediately = prefs[Keys.SHOW_EXPLANATIONS] ?: true,
            streakDays = prefs[Keys.STREAK_DAYS] ?: 0,
            lastStudyEpochDay = prefs[Keys.LAST_STUDY_EPOCH_DAY] ?: 0L,
            totalStudySeconds = prefs[Keys.TOTAL_STUDY_SECONDS] ?: 0L,
            preferredExplanationStyle = prefs[Keys.EXPLANATION_STYLE] ?: "Conversational with concrete examples"
        )
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name.trim().ifEmpty { "Scholar" } }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPersonality(personality: AiPersonality) {
        context.dataStore.edit { it[Keys.PERSONALITY] = personality.name }
    }

    suspend fun setAvatarStyle(style: AvatarStyle) {
        context.dataStore.edit { it[Keys.AVATAR_STYLE] = style.name }
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOICE_ENABLED] = enabled }
    }

    suspend fun setVoiceInteractionMode(mode: VoiceInteractionMode) {
        context.dataStore.edit { it[Keys.VOICE_MODE] = mode.name }
    }

    suspend fun setTtsSettings(rate: Float, pitch: Float) {
        context.dataStore.edit {
            it[Keys.TTS_RATE] = rate.coerceIn(0.6f, 1.8f)
            it[Keys.TTS_PITCH] = pitch.coerceIn(0.6f, 1.6f)
        }
    }

    suspend fun setPersistentMemoryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PERSISTENT_MEMORY] = enabled }
    }

    suspend fun setPreferredExplanationStyle(style: String) {
        context.dataStore.edit { it[Keys.EXPLANATION_STYLE] = style }
    }

    suspend fun recordStudyActivity(additionalSeconds: Long = 0L) {
        val today = LocalDate.now().toEpochDay()
        context.dataStore.edit { prefs ->
            val lastDay = prefs[Keys.LAST_STUDY_EPOCH_DAY] ?: 0L
            val currentStreak = prefs[Keys.STREAK_DAYS] ?: 0
            val newStreak = when {
                lastDay == today -> currentStreak.coerceAtLeast(1)
                lastDay == today - 1L -> currentStreak + 1
                else -> 1
            }
            prefs[Keys.STREAK_DAYS] = newStreak
            prefs[Keys.LAST_STUDY_EPOCH_DAY] = today
            if (additionalSeconds > 0) {
                val total = prefs[Keys.TOTAL_STUDY_SECONDS] ?: 0L
                prefs[Keys.TOTAL_STUDY_SECONDS] = total + additionalSeconds
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T {
        if (raw.isNullOrBlank()) return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
