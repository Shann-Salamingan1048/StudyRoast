package com.example.studyappandroid.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.data.local.db.QuizAttemptEntity
import com.example.studyappandroid.data.local.db.ReviewScheduleEntity
import com.example.studyappandroid.data.local.preferences.UserPreferences
import com.example.studyappandroid.data.local.preferences.UserPreferencesRepository
import com.example.studyappandroid.data.repository.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val prefs: UserPreferences = UserPreferences(),
    val recentHandouts: List<HandoutEntity> = emptyList(),
    val attempts: List<QuizAttemptEntity> = emptyList(),
    val recommendedTopics: List<ReviewScheduleEntity> = emptyList(),
    val totalQuizzesCompleted: Int = 0,
    val averageAccuracy: Float = 0f
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: StudyRepository,
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        preferencesRepository.preferencesFlow,
        repository.observeRecentHandouts(5),
        repository.observeAllAttempts(),
        repository.observeRecommendedTopics(6)
    ) { prefs, handouts, attempts, recommended ->
        val avg = if (attempts.isEmpty()) 0f
        else attempts.map { it.accuracyPercent }.average().toFloat()

        HomeUiState(
            prefs = prefs,
            recentHandouts = handouts,
            attempts = attempts,
            recommendedTopics = recommended,
            totalQuizzesCompleted = attempts.size,
            averageAccuracy = avg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}
