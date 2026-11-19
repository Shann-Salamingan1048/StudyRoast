package com.example.studyappandroid.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

sealed interface StudyRoastRoute {
    @Serializable
    data object Home : StudyRoastRoute

    @Serializable
    data class Quiz(val preselectedIdsCsv: String = "") : StudyRoastRoute

    @Serializable
    data class QuizPlay(
        val quizId: Long,
        val retryWrongOnly: Boolean = false,
        val voiceMode: Boolean = false
    ) : StudyRoastRoute

    @Serializable
    data class QuizSummary(val attemptId: Long) : StudyRoastRoute

    @Serializable
    data class Lecture(val handoutId: Long? = null, val topicTitle: String? = null) : StudyRoastRoute

    @Serializable
    data class TeachAi(val handoutId: Long? = null, val topicTitle: String? = null) : StudyRoastRoute

    @Serializable
    data object Library : StudyRoastRoute

    @Serializable
    data class HandoutDetail(val handoutId: Long) : StudyRoastRoute

    @Serializable
    data object Settings : StudyRoastRoute
}

enum class BottomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: StudyRoastRoute
) {
    HOME(
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        route = StudyRoastRoute.Home
    ),
    QUIZ(
        label = "Quiz",
        selectedIcon = Icons.Filled.Quiz,
        unselectedIcon = Icons.Outlined.Quiz,
        route = StudyRoastRoute.Quiz()
    ),
    LECTURE(
        label = "Lecture",
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook,
        route = StudyRoastRoute.Lecture()
    ),
    LIBRARY(
        label = "Library",
        selectedIcon = Icons.AutoMirrored.Filled.LibraryBooks,
        unselectedIcon = Icons.AutoMirrored.Outlined.LibraryBooks,
        route = StudyRoastRoute.Library
    ),
    SETTINGS(
        label = "Profile",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        route = StudyRoastRoute.Settings
    )
}
