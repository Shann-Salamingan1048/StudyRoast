package com.example.studyappandroid.ui.screens.quiz

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.example.studyappandroid.data.ai.local.LocalModelState
import com.example.studyappandroid.domain.model.QuizDifficulty
import com.example.studyappandroid.ui.components.ErrorBanner
import com.example.studyappandroid.ui.components.RoastCard
import com.example.studyappandroid.ui.theme.RoastMint

@Composable
fun QuizHubScreen(
    preselectedHandoutIds: List<Long> = emptyList(),
    onLaunchQuiz: (quizId: Long, retryWrongOnly: Boolean, voiceMode: Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
    onViewAttemptSummary: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuizViewModel = hiltViewModel(),
) {
    val state by viewModel.hubState.collectAsStateWithLifecycle()
    var customCountText by remember { mutableStateOf("") }

    LaunchedEffect(preselectedHandoutIds) {
        if (preselectedHandoutIds.isNotEmpty()) {
            viewModel.preselectHandouts(preselectedHandoutIds)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "AI Quiz Arena",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.aiStatusText.ifBlank { "Configure your offline active recall session." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val modelState = state.modelState
            if (modelState is LocalModelState.Preparing) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { modelState.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        state.errorMessage?.let { err ->
            item {
                ErrorBanner(
                    message = err,
                    onDismiss = viewModel::clearHubError,
                    onRetry = {
                        viewModel.generateQuiz { quizId ->
                            onLaunchQuiz(quizId, false, state.voiceQuizMode)
                        }
                    },
                )
            }
        }

        item {
            RoastCard {
                Text(
                    text = "1. Select Handouts (${state.selectedHandoutIds.size} selected)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (state.handouts.isEmpty()) {
                    Text(
                        text = "No handouts uploaded yet. Upload a PDF, DOCX, or TXT in Library first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenLibrary) {
                        Text("Go to Library")
                    }
                } else {
                    state.handouts.forEach { handout ->
                        val checked = handout.id in state.selectedHandoutIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleHandout(handout.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.toggleHandout(handout.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = handout.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${handout.subjectName} • ${handout.wordCount} words",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.availableTopics.isNotEmpty()) {
            item {
                RoastCard {
                    Text(
                        text = "Topic Scope",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.selectedTopicFilter == null,
                            onClick = { viewModel.setTopicFilter(null) },
                            label = { Text("Entire Handout") },
                        )
                        state.availableTopics.distinctBy { it.title }.forEach { topic ->
                            FilterChip(
                                selected = state.selectedTopicFilter == topic.title,
                                onClick = { viewModel.setTopicFilter(topic.title) },
                                label = { Text(topic.title) },
                            )
                        }
                    }
                }
            }
        }

        item {
            RoastCard {
                Text(
                    text = "2. Quiz Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text("Questions: ${state.questionCount}", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 10, 20, 30, 50).forEach { count ->
                        FilterChip(
                            selected = state.questionCount == count,
                            onClick = {
                                customCountText = ""
                                viewModel.setQuestionCount(count)
                            },
                            label = { Text("$count") },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Slider(
                        value = state.questionCount.toFloat(),
                        onValueChange = { viewModel.setQuestionCount(it.toInt()) },
                        valueRange = 1f..50f,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = customCountText,
                        onValueChange = { txt ->
                            customCountText = txt.filter { it.isDigit() }.take(2)
                            customCountText.toIntOrNull()?.let { viewModel.setQuestionCount(it) }
                        },
                        placeholder = { Text("${state.questionCount}") },
                        singleLine = true,
                        modifier = Modifier.width(68.dp),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Difficulty", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuizDifficulty.entries.forEach { diff ->
                        FilterChip(
                            selected = state.difficulty == diff,
                            onClick = { viewModel.setDifficulty(diff) },
                            label = { Text(diff.label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                QuizToggleRow("Timed Quiz (${state.secondsPerQuestion}s/question)", state.isTimed, viewModel::setTimed)
                QuizToggleRow("Randomize Question Order", state.randomizeOrder, viewModel::setRandomizeOrder)
                QuizToggleRow("Show Explanations After Answering", state.showExplanationsImmediately, viewModel::setShowExplanations)
                QuizToggleRow("Voice Quiz Mode (Spoken Questions & Voice Answers)", state.voiceQuizMode, viewModel::setVoiceQuizMode)

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        viewModel.generateQuiz { quizId ->
                            onLaunchQuiz(quizId, false, state.voiceQuizMode)
                        }
                    },
                    enabled = !state.isGenerating && state.selectedHandoutIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating Offline Quiz...")
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate & Start Quiz")
                    }
                }
            }
        }

        if (state.savedQuizzes.isNotEmpty()) {
            item {
                Text(
                    text = "Saved Quizzes (${state.savedQuizzes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(state.savedQuizzes, key = { it.id }) { quiz ->
                RoastCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quiz.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${quiz.questionCount} questions • ${quiz.difficulty}" +
                                    if (quiz.isTimed) " • Timed" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteQuiz(quiz.id) }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete quiz",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onLaunchQuiz(quiz.id, false, state.voiceQuizMode) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retake")
                        }
                        OutlinedButton(
                            onClick = { onLaunchQuiz(quiz.id, true, state.voiceQuizMode) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry Missed")
                        }
                    }
                }
            }
        }

        if (state.recentAttempts.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Quiz Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(state.recentAttempts.take(5), key = { "att_${it.id}" }) { attempt ->
                RoastCard(
                    modifier = Modifier.clickable { onViewAttemptSummary(attempt.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attempt.quizTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${attempt.correctCount}/${attempt.totalQuestions} correct • Weakest: ${attempt.weakestTopic}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${attempt.accuracyPercent.toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (attempt.accuracyPercent >= 70f) RoastMint else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
