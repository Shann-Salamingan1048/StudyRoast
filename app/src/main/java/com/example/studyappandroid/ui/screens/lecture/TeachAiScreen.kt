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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.example.studyappandroid.domain.model.StudyContextSnapshot
import com.example.studyappandroid.ui.components.ErrorBanner
import com.example.studyappandroid.ui.components.RoastCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachAiScreen(
    modifier: Modifier = Modifier,
    initialHandoutId: Long? = null,
    initialTopicTitle: String? = null,
    isSpeaking: Boolean = false,
    onBack: () -> Unit = {},
    onSpeakText: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onStartVoiceExplain: (() -> Unit)? = null,
    onUpdateContext: (StudyContextSnapshot) -> Unit = {},
    viewModel: LectureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var explanationInput by remember { mutableStateOf("") }

    LaunchedEffect(initialHandoutId, initialTopicTitle) {
        viewModel.initialize(initialHandoutId, initialTopicTitle)
    }

    LaunchedEffect(state.selectedHandout?.id, state.selectedTopic) {
        onUpdateContext(
            StudyContextSnapshot(
                currentScreen = "TeachAi",
                handoutTitle = state.selectedHandout?.title,
                topicTitle = state.selectedTopic,
            ),
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Teach the AI (Feynman Mode)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSpeaking) {
                        IconButton(onClick = onStopSpeaking) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop Speaking")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RoastCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Explain the concept in your own words",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Teach StudyRoast as if it's a curious student. It will check your reasoning for gaps and misconceptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.handouts.isNotEmpty()) {
                item {
                    Text("Handout", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.handouts.forEach { handout ->
                            FilterChip(
                                selected = state.selectedHandout?.id == handout.id,
                                onClick = { viewModel.selectHandout(handout) },
                                label = { Text(handout.title) },
                            )
                        }
                    }
                }
            }

            if (state.topics.isNotEmpty()) {
                item {
                    Text("Topic", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.topics.forEach { topic ->
                            FilterChip(
                                selected = state.selectedTopic == topic.title,
                                onClick = { viewModel.selectTopic(topic.title) },
                                label = { Text(topic.title) },
                            )
                        }
                    }
                }
            }

            state.errorMessage?.let { err ->
                item {
                    ErrorBanner(message = err, onDismiss = viewModel::clearError)
                }
            }

            items(state.teachHistory) { (speaker, message) ->
                val isUser = speaker == "You"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = speaker,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.teachEvaluation?.let { eval ->
                item {
                    RoastCard {
                        Text(
                            text = "Evaluation: ${eval.scoreSummary.ifBlank { "Reviewed" }}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (eval.whatYouGotRight.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("What You Nailed", style = MaterialTheme.typography.labelLarge)
                            eval.whatYouGotRight.forEach { point ->
                                Text("• $point", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (eval.missingDetails.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Missing Details", style = MaterialTheme.typography.labelLarge)
                            eval.missingDetails.forEach { detail ->
                                Text("• $detail", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (eval.misconceptions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Misconceptions", style = MaterialTheme.typography.labelLarge)
                            eval.misconceptions.forEach { item ->
                                Text("• $item", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (eval.modelExplanation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Ideal Explanation", style = MaterialTheme.typography.labelLarge)
                            Text(eval.modelExplanation, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = explanationInput,
                    onValueChange = { explanationInput = it },
                    placeholder = { Text("Explain ${state.selectedTopic.ifBlank { "this topic" }} in your own words...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 3,
                    maxLines = 6,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onStartVoiceExplain != null) {
                        IconButton(onClick = onStartVoiceExplain) {
                            Icon(Icons.Filled.Mic, contentDescription = "Explain by Voice")
                        }
                    }
                    Button(
                        onClick = {
                            val text = explanationInput
                            explanationInput = ""
                            viewModel.submitTeachExplanation(text) { feedback ->
                                onSpeakText(feedback)
                            }
                        },
                        enabled = explanationInput.isNotBlank() && !state.isEvaluatingTeach,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isEvaluatingTeach) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Evaluating...")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Submit Explanation")
                        }
                    }
                }
            }
        }
    }
}
