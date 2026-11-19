package com.example.studyappandroid.ui.screens.lecture

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.studyappandroid.domain.model.LectureAction
import com.example.studyappandroid.domain.model.StudyContextSnapshot
import com.example.studyappandroid.ui.components.ErrorBanner
import com.example.studyappandroid.ui.components.RoastCard
import com.example.studyappandroid.voice.VoiceLectureCommand
import kotlinx.coroutines.flow.Flow

@Composable
fun LectureScreen(
    modifier: Modifier = Modifier,
    initialHandoutId: Long? = null,
    initialTopicTitle: String? = null,
    isSpeaking: Boolean = false,
    onSpeakText: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onVoiceInterruptListen: (() -> Unit)? = null,
    onOpenQuizForHandout: (Long) -> Unit = {},
    onOpenTeachAi: (Long?, String?) -> Unit = { _, _ -> },
    onOpenLibrary: () -> Unit = {},
    onUpdateContext: (StudyContextSnapshot) -> Unit = {},
    voiceCommandFlow: Flow<VoiceLectureCommand>? = null,
    viewModel: LectureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var questionInput by remember { mutableStateOf("") }

    LaunchedEffect(initialHandoutId, initialTopicTitle) {
        viewModel.initialize(initialHandoutId, initialTopicTitle)
    }

    LaunchedEffect(state.selectedHandout?.id, state.selectedTopic) {
        onUpdateContext(
            StudyContextSnapshot(
                currentScreen = "Lecture",
                handoutTitle = state.selectedHandout?.title,
                topicTitle = state.selectedTopic,
            ),
        )
    }

    LaunchedEffect(voiceCommandFlow) {
        voiceCommandFlow?.collect { command ->
            val latestState = viewModel.uiState.value
            when (command) {
                is VoiceLectureCommand.SwitchMode -> {
                    viewModel.requestLectureAction(command.action)
                }
                VoiceLectureCommand.ContinueLecture -> {
                    if (latestState.lectureContent.isNotBlank()) {
                        onSpeakText(latestState.lectureContent)
                    } else {
                        viewModel.requestLectureAction(latestState.activeAction)
                    }
                }
                VoiceLectureCommand.PauseLecture -> {
                    onStopSpeaking()
                }
                VoiceLectureCommand.RestartLecture -> {
                    onStopSpeaking()
                    if (latestState.lectureContent.isNotBlank()) {
                        onSpeakText(latestState.lectureContent)
                    } else {
                        viewModel.requestLectureAction(latestState.activeAction, forceRefresh = true)
                    }
                }
                VoiceLectureCommand.QuizMe -> {
                    latestState.selectedHandout?.id?.let(onOpenQuizForHandout)
                }
                is VoiceLectureCommand.AskQuestion -> {
                    viewModel.askFollowUp(command.question) { reply ->
                        onSpeakText(reply)
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interactive Lecture",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Explain, interrupt, ask examples, or Teach the AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onOpenTeachAi(state.selectedHandout?.id, state.selectedTopic) }
                ) {
                    Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Teach AI")
                }
            }
        }

        if (state.handouts.isEmpty()) {
            item {
                RoastCard {
                    Text("No handouts found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Upload a PDF, DOCX, or TXT handout in the Library to start an AI lecture.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = onOpenLibrary) { Text("Upload Handout") }
                }
            }
            return@LazyColumn
        }

        item {
            Text("Handout", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.handouts.forEach { handout ->
                    FilterChip(
                        selected = state.selectedHandout?.id == handout.id,
                        onClick = { viewModel.selectHandout(handout) },
                        label = { Text(handout.title) }
                    )
                }
            }
        }

        if (state.topics.isNotEmpty()) {
            item {
                Text("Topics", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.topics.forEach { topic ->
                        FilterChip(
                            selected = state.selectedTopic == topic.title,
                            onClick = { viewModel.selectTopic(topic.title) },
                            label = { Text(topic.title) }
                        )
                    }
                }
            }
        }

        item {
            Text("Lesson Mode", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LectureAction.entries.forEach { action ->
                    FilterChip(
                        selected = state.activeAction == action,
                        onClick = { viewModel.requestLectureAction(action) },
                        label = { Text(action.label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.requestLectureAction(state.activeAction, forceRefresh = false) },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Explain Topic")
                }
                state.selectedHandout?.let { handout ->
                    OutlinedButton(onClick = { onOpenQuizForHandout(handout.id) }) {
                        Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Quiz Me")
                    }
                }
            }
        }

        state.errorMessage?.let { err ->
            item {
                ErrorBanner(
                    message = err,
                    onDismiss = viewModel::clearError,
                    onRetry = { viewModel.requestLectureAction(state.activeAction, forceRefresh = true) }
                )
            }
        }

        if (state.isLoading) {
            item {
                RoastCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Text("Preparing your lecture on ${state.selectedTopic}...")
                    }
                }
            }
        }

        if (state.lectureContent.isNotBlank()) {
            item {
                RoastCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.selectedTopic.ifBlank { "Lecture" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.activeAction.label + if (state.isFromCache) " • Saved Offline" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    if (isSpeaking) onStopSpeaking() else onSpeakText(state.lectureContent)
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isSpeaking) "Stop Speaking" else "Listen to Lecture",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (onVoiceInterruptListen != null) {
                                IconButton(
                                    onClick = {
                                        onStopSpeaking()
                                        onVoiceInterruptListen()
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = "Interrupt & Ask by Voice"
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.requestLectureAction(state.activeAction, forceRefresh = true) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Regenerate")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.lectureContent,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Text(
                text = "Ask the Tutor During Lecture",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(state.qaMessages.takeLast(8), key = { "qa_${it.id}" }) { msg ->
            val isUser = msg.role == "USER"
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isUser) "You" else "StudyRoast Tutor",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = questionInput,
                    onValueChange = { questionInput = it },
                    placeholder = { Text("Wait, give me a simpler example...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 3
                )
                IconButton(
                    onClick = {
                        val q = questionInput
                        questionInput = ""
                        viewModel.askFollowUp(q) { reply ->
                            if (isSpeaking) onSpeakText(reply)
                        }
                    },
                    enabled = questionInput.isNotBlank() && !state.isLoading
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
