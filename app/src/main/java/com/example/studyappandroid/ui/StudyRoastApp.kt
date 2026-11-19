package com.example.studyappandroid.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.studyappandroid.domain.model.StudyContextSnapshot
import com.example.studyappandroid.ui.components.FloatingCompanionOverlay
import com.example.studyappandroid.ui.navigation.BottomNavTab
import com.example.studyappandroid.ui.navigation.StudyRoastRoute
import com.example.studyappandroid.ui.screens.companion.CompanionEffect
import com.example.studyappandroid.ui.screens.companion.CompanionViewModel
import com.example.studyappandroid.ui.screens.home.HomeScreen
import com.example.studyappandroid.ui.screens.lecture.LectureScreen
import com.example.studyappandroid.ui.screens.lecture.TeachAiScreen
import com.example.studyappandroid.ui.screens.library.HandoutDetailScreen
import com.example.studyappandroid.ui.screens.library.LibraryScreen
import com.example.studyappandroid.ui.screens.quiz.QuizHubScreen
import com.example.studyappandroid.ui.screens.quiz.QuizPlayScreen
import com.example.studyappandroid.ui.screens.quiz.QuizSummaryScreen
import com.example.studyappandroid.ui.screens.settings.SettingsScreen
import com.example.studyappandroid.ui.screens.settings.SettingsViewModel
import com.example.studyappandroid.ui.theme.StudyRoastTheme
import com.example.studyappandroid.voice.VoiceLectureCommand
import com.example.studyappandroid.voice.VoiceNavigationCommand
import com.example.studyappandroid.voice.VoiceQuizCommand
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
fun StudyRoastApp(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    companionViewModel: CompanionViewModel = hiltViewModel(),
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val companionState by companionViewModel.uiState.collectAsStateWithLifecycle()

    val quizVoiceCommands = remember { MutableSharedFlow<VoiceQuizCommand>(extraBufferCapacity = 8) }
    val lectureVoiceCommands = remember { MutableSharedFlow<VoiceLectureCommand>(extraBufferCapacity = 8) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            companionViewModel.startVoiceListening()
        } else {
            companionViewModel.reportVoiceError("Microphone permission denied. Enable it in system settings to use voice commands.")
        }
    }

    val launchVoiceInput: () -> Unit = {
        if (companionViewModel.hasRecordAudioPermission()) {
            companionViewModel.startVoiceListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    StudyRoastTheme(themeMode = settingsState.prefs.themeMode) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        LaunchedEffect(Unit) {
            companionViewModel.effects.collect { effect ->
                when (effect) {
                    is CompanionEffect.Navigate -> {
                        when (effect.command) {
                            VoiceNavigationCommand.GoHome -> navigateToTopLevelTab(navController, StudyRoastRoute.Home)
                            VoiceNavigationCommand.GoQuiz -> navigateToTopLevelTab(navController, StudyRoastRoute.Quiz())
                            VoiceNavigationCommand.GoLecture -> navigateToTopLevelTab(navController, StudyRoastRoute.Lecture())
                            VoiceNavigationCommand.GoTeachAi -> navController.navigate(StudyRoastRoute.TeachAi())
                            VoiceNavigationCommand.GoLibrary -> navigateToTopLevelTab(navController, StudyRoastRoute.Library)
                            VoiceNavigationCommand.GoSettings -> navigateToTopLevelTab(navController, StudyRoastRoute.Settings)
                            VoiceNavigationCommand.GoBack -> navController.popBackStack()
                            else -> Unit
                        }
                    }
                    is CompanionEffect.QuizCommand -> quizVoiceCommands.emit(effect.command)
                    is CompanionEffect.LectureCommand -> lectureVoiceCommands.emit(effect.command)
                }
            }
        }

        val showBottomBar = BottomNavTab.entries.any { tab ->
            currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
        }
        val showFloatingCompanion = currentDestination?.hierarchy?.any {
            it.hasRoute(StudyRoastRoute.Settings::class)
        } != true

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            BottomNavTab.entries.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.hasRoute(tab.route::class)
                                } == true

                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTopLevelTab(navController, tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label,
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = StudyRoastRoute.Home,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable<StudyRoastRoute.Home> {
                        LaunchedEffect(Unit) {
                            companionViewModel.updateStudyContext(StudyContextSnapshot(currentScreen = "Home"))
                        }
                        HomeScreen(
                            onContinueStudying = {
                                navigateToTopLevelTab(navController, StudyRoastRoute.Quiz())
                            },
                            onNavigateToLibrary = {
                                navigateToTopLevelTab(navController, StudyRoastRoute.Library)
                            },
                            onOpenHandout = { handoutId ->
                                navController.navigate(StudyRoastRoute.HandoutDetail(handoutId))
                            },
                            onOpenLectureForTopic = { handoutId, topic ->
                                navController.navigate(
                                    StudyRoastRoute.Lecture(handoutId = handoutId, topicTitle = topic),
                                )
                            },
                            onOpenCompanionChat = {
                                companionViewModel.setPanelExpanded(expanded = true)
                            },
                        )
                    }
                    composable<StudyRoastRoute.Quiz> { backStackEntry ->
                        LaunchedEffect(Unit) {
                            companionViewModel.updateStudyContext(StudyContextSnapshot(currentScreen = "Quiz Config"))
                        }
                        val args = backStackEntry.toRoute<StudyRoastRoute.Quiz>()
                        val preselected = args.preselectedIdsCsv
                            .split(",")
                            .mapNotNull { it.toLongOrNull() }
                        QuizHubScreen(
                            preselectedHandoutIds = preselected,
                            onLaunchQuiz = { quizId, retryWrong, voiceMode ->
                                navController.navigate(
                                    StudyRoastRoute.QuizPlay(
                                        quizId = quizId,
                                        retryWrongOnly = retryWrong,
                                        voiceMode = voiceMode,
                                    ),
                                )
                            },
                            onOpenLibrary = {
                                navigateToTopLevelTab(navController, StudyRoastRoute.Library)
                            },
                            onViewAttemptSummary = { attemptId ->
                                navController.navigate(StudyRoastRoute.QuizSummary(attemptId))
                            },
                        )
                    }
                    composable<StudyRoastRoute.QuizPlay> { backStackEntry ->
                        val args = backStackEntry.toRoute<StudyRoastRoute.QuizPlay>()
                        QuizPlayScreen(
                            quizId = args.quizId,
                            retryWrongOnly = args.retryWrongOnly,
                            voiceQuizEnabled = args.voiceMode,
                            onBack = {
                                companionViewModel.stopSpeaking()
                                navController.popBackStack()
                            },
                            onCompleted = { attemptId ->
                                companionViewModel.stopSpeaking()
                                navController.navigate(StudyRoastRoute.QuizSummary(attemptId)) {
                                    popUpTo(StudyRoastRoute.QuizPlay::class) { inclusive = true }
                                }
                            },
                            onAskHintInCompanion = {
                                companionViewModel.setPanelExpanded(expanded = true)
                                companionViewModel.sendMessage(
                                    text = "Can you give me a hint on this question without spoiling the answer?",
                                    speakReply = args.voiceMode,
                                )
                            },
                            onUpdateContext = { snapshot ->
                                companionViewModel.updateStudyContext(snapshot)
                            },
                            onSpeakQuestion = { text ->
                                companionViewModel.speakText(text, force = false)
                            },
                            onStartVoiceAnswer = launchVoiceInput,
                            voiceCommandFlow = quizVoiceCommands,
                        )
                    }
                    composable<StudyRoastRoute.QuizSummary> { backStackEntry ->
                        LaunchedEffect(Unit) {
                            companionViewModel.updateStudyContext(StudyContextSnapshot(currentScreen = "Quiz Summary"))
                        }
                        val args = backStackEntry.toRoute<StudyRoastRoute.QuizSummary>()
                        QuizSummaryScreen(
                            attemptId = args.attemptId,
                            onRetryIncorrect = { quizId ->
                                navController.navigate(
                                    StudyRoastRoute.QuizPlay(quizId = quizId, retryWrongOnly = true),
                                ) {
                                    popUpTo(StudyRoastRoute.QuizSummary::class) { inclusive = true }
                                }
                            },
                            onRetakeFull = { quizId ->
                                navController.navigate(
                                    StudyRoastRoute.QuizPlay(quizId = quizId, retryWrongOnly = false),
                                ) {
                                    popUpTo(StudyRoastRoute.QuizSummary::class) { inclusive = true }
                                }
                            },
                            onDone = {
                                navController.popBackStack(StudyRoastRoute.Quiz::class, inclusive = false)
                            },
                        )
                    }
                    composable<StudyRoastRoute.Lecture> { backStackEntry ->
                        val args = backStackEntry.toRoute<StudyRoastRoute.Lecture>()
                        LectureScreen(
                            initialHandoutId = args.handoutId,
                            initialTopicTitle = args.topicTitle,
                            isSpeaking = companionState.isSpeaking,
                            onSpeakText = { text -> companionViewModel.speakText(text, force = true) },
                            onStopSpeaking = companionViewModel::stopSpeaking,
                            onVoiceInterruptListen = launchVoiceInput,
                            onOpenQuizForHandout = { handoutId ->
                                navController.navigate(StudyRoastRoute.Quiz(handoutId.toString()))
                            },
                            onOpenTeachAi = { handoutId, topic ->
                                navController.navigate(StudyRoastRoute.TeachAi(handoutId = handoutId, topicTitle = topic))
                            },
                            onOpenLibrary = {
                                navigateToTopLevelTab(navController, StudyRoastRoute.Library)
                            },
                            onUpdateContext = { snapshot ->
                                companionViewModel.updateStudyContext(snapshot)
                            },
                            voiceCommandFlow = lectureVoiceCommands,
                        )
                    }
                    composable<StudyRoastRoute.TeachAi> { backStackEntry ->
                        val args = backStackEntry.toRoute<StudyRoastRoute.TeachAi>()
                        TeachAiScreen(
                            initialHandoutId = args.handoutId,
                            initialTopicTitle = args.topicTitle,
                            isSpeaking = companionState.isSpeaking,
                            onBack = {
                                companionViewModel.stopSpeaking()
                                navController.popBackStack()
                            },
                            onSpeakText = { text -> companionViewModel.speakText(text, force = false) },
                            onStopSpeaking = companionViewModel::stopSpeaking,
                            onStartVoiceExplain = launchVoiceInput,
                            onUpdateContext = { snapshot ->
                                companionViewModel.updateStudyContext(snapshot)
                            },
                        )
                    }
                    composable<StudyRoastRoute.Library> {
                        LaunchedEffect(Unit) {
                            companionViewModel.updateStudyContext(StudyContextSnapshot(currentScreen = "Library"))
                        }
                        LibraryScreen(
                            onOpenHandoutDetail = { handoutId ->
                                navController.navigate(StudyRoastRoute.HandoutDetail(handoutId))
                            },
                            onStartQuizWithHandouts = { ids ->
                                navController.navigate(StudyRoastRoute.Quiz(ids.joinToString(",")))
                            },
                            onOpenLecture = { handoutId ->
                                navController.navigate(StudyRoastRoute.Lecture(handoutId = handoutId))
                            },
                        )
                    }
                    composable<StudyRoastRoute.HandoutDetail> { backStackEntry ->
                        LaunchedEffect(Unit) {
                            companionViewModel.updateStudyContext(StudyContextSnapshot(currentScreen = "Handout Detail"))
                        }
                        val args = backStackEntry.toRoute<StudyRoastRoute.HandoutDetail>()
                        HandoutDetailScreen(
                            handoutId = args.handoutId,
                            onBack = { navController.popBackStack() },
                            onStartQuiz = { handoutId ->
                                navController.navigate(StudyRoastRoute.Quiz(handoutId.toString()))
                            },
                            onStartLecture = { handoutId, topic ->
                                navController.navigate(
                                    StudyRoastRoute.Lecture(handoutId = handoutId, topicTitle = topic),
                                )
                            },
                        )
                    }
                    composable<StudyRoastRoute.Settings> {
                        LaunchedEffect(Unit) {
                            companionViewModel.updateStudyContext(StudyContextSnapshot(currentScreen = "Settings"))
                        }
                        SettingsScreen()
                    }
                }
            }

            if (showFloatingCompanion) {
                FloatingCompanionOverlay(
                    state = companionState,
                    onToggleExpanded = companionViewModel::setPanelExpanded,
                    onSendMessage = { msg -> companionViewModel.sendMessage(msg) },
                    onMicClick = launchVoiceInput,
                    onStopVoice = companionViewModel::stopVoice,
                    onSelectPersonality = companionViewModel::setPersonality,
                    onNewChat = companionViewModel::startNewSession,
                    onClearChat = companionViewModel::clearCurrentSession,
                    onDismissError = companionViewModel::clearError,
                )
            }
        }
    }
}

private fun navigateToTopLevelTab(
    navController: NavHostController,
    route: StudyRoastRoute,
) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
