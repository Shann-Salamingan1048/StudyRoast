package com.example.studyappandroid.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.studyappandroid.domain.model.StudyContextSnapshot
import com.example.studyappandroid.ui.components.RoastAvatar
import com.example.studyappandroid.ui.components.RoastCard
import com.example.studyappandroid.ui.theme.RoastAmber
import com.example.studyappandroid.ui.theme.RoastCrimson
import com.example.studyappandroid.ui.theme.RoastCrimsonContainer
import com.example.studyappandroid.ui.theme.RoastMint
import com.example.studyappandroid.ui.theme.RoastMintContainer
import com.example.studyappandroid.voice.VoiceQuizCommand
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizPlayScreen(
    quizId: Long,
    retryWrongOnly: Boolean,
    voiceQuizEnabled: Boolean = false,
    onBack: () -> Unit,
    onCompleted: (Long) -> Unit,
    onAskHintInCompanion: () -> Unit,
    onUpdateContext: (StudyContextSnapshot) -> Unit,
    onSpeakQuestion: ((String) -> Unit)? = null,
    onStartVoiceAnswer: (() -> Unit)? = null,
    voiceCommandFlow: Flow<VoiceQuizCommand>? = null,
    viewModel: QuizViewModel = hiltViewModel(),
) {
    val state by viewModel.playState.collectAsStateWithLifecycle()
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(quizId, retryWrongOnly) {
        viewModel.loadQuizForPlay(quizId, retryWrongOnly)
    }

    val currentQuestion = state.currentQuestion
    LaunchedEffect(currentQuestion?.id, state.isSubmitted) {
        currentQuestion?.let { question ->
            onUpdateContext(
                StudyContextSnapshot(
                    currentScreen = "Quiz",
                    handoutTitle = state.quiz?.title,
                    topicTitle = question.topicTitle,
                    currentQuestionText = question.questionText,
                    currentOptions = listOf(
                        "A. ${question.optionA}",
                        "B. ${question.optionB}",
                        "C. ${question.optionC}",
                        "D. ${question.optionD}",
                    ),
                    isQuizAnswered = state.isSubmitted,
                ),
            )
        }
    }

    LaunchedEffect(currentQuestion?.id, voiceQuizEnabled) {
        if (voiceQuizEnabled && (currentQuestion != null) && (onSpeakQuestion != null)) {
            val spoken = "Question ${state.currentIndex + 1}. ${currentQuestion.questionText}. " +
                "A, ${currentQuestion.optionA}. B, ${currentQuestion.optionB}. " +
                "C, ${currentQuestion.optionC}. D, ${currentQuestion.optionD}."
            onSpeakQuestion(spoken)
        }
    }

    LaunchedEffect(voiceCommandFlow) {
        voiceCommandFlow?.collect { command ->
            val latestState = viewModel.playState.value
            val latestQuestion = latestState.currentQuestion
            when (command) {
                is VoiceQuizCommand.SelectOption -> {
                    if (!latestState.isSubmitted) {
                        viewModel.selectOption(command.index)
                        viewModel.submitAnswer()
                    }
                }
                is VoiceQuizCommand.AmbiguousOption -> {
                    if (!latestState.isSubmitted) {
                        val candidate = command.candidateIndex
                        if (candidate != null) {
                            viewModel.selectOption(candidate)
                            val letter = listOf("A", "B", "C", "D").getOrElse(candidate) { "" }
                            onSpeakQuestion?.invoke("Did you mean option $letter? Say letter $letter clearly to submit.")
                        } else {
                            onSpeakQuestion?.invoke("Please say option A, B, C, or D clearly.")
                        }
                    }
                }
                VoiceQuizCommand.RepeatQuestion -> {
                    if (latestQuestion != null) {
                        val spoken = "Question ${latestState.currentIndex + 1}. ${latestQuestion.questionText}. " +
                            "A, ${latestQuestion.optionA}. B, ${latestQuestion.optionB}. " +
                            "C, ${latestQuestion.optionC}. D, ${latestQuestion.optionD}."
                        onSpeakQuestion?.invoke(spoken)
                    }
                }
                VoiceQuizCommand.RequestHint -> {
                    onAskHintInCompanion()
                }
                VoiceQuizCommand.NextQuestion -> {
                    if (latestState.isSubmitted) {
                        viewModel.nextQuestion(onCompleted)
                    } else {
                        onSpeakQuestion?.invoke("Select and submit an answer first, or say reveal answer.")
                    }
                }
                VoiceQuizCommand.RevealAnswer -> {
                    if (!latestState.isSubmitted) {
                        viewModel.revealAnswerAssisted()
                    }
                }
                is VoiceQuizCommand.Unrecognized -> {
                    onSpeakQuestion?.invoke("I didn't catch a quiz command. Say A, B, C, D, repeat, hint, reveal, or next.")
                }
            }
        }
    }

    LaunchedEffect(state.isSubmitted, state.isCorrect) {
        if (state.isSubmitted && !state.isCorrect) {
            for (delta in listOf(-14f, 14f, -10f, 10f, -5f, 0f)) {
                shakeOffset.animateTo(delta, tween(45))
            }
        }
        if (state.isSubmitted && voiceQuizEnabled && (onSpeakQuestion != null) && (currentQuestion != null)) {
            val feedback = "${state.reactionMessage} ${currentQuestion.explanation}"
            onSpeakQuestion(feedback)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.quiz?.title ?: "Quiz",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.remainingSeconds?.let { sec ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sec <= 10) RoastCrimsonContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Timer,
                                    contentDescription = null,
                                    tint = if (sec <= 10) RoastCrimson else RoastAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${sec}s",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Text(state.errorMessage ?: "Error", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Back") }
            }
            return@Scaffold
        }

        if (currentQuestion == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Text("Loading questions...")
            }
            return@Scaffold
        }

        val options = listOf(
            currentQuestion.optionA,
            currentQuestion.optionB,
            currentQuestion.optionC,
            currentQuestion.optionD
        )
        val letters = listOf("A", "B", "C", "D")

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val progress = (state.currentIndex + 1).toFloat() / state.questions.size.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Question ${state.currentIndex + 1} of ${state.questions.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Score: ${state.correctCount} ✓ / ${state.wrongCount} ✗",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                }
            }

            item {
                RoastCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoastAvatar(
                            expression = state.avatarExpression,
                            size = 58.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentQuestion.topicTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentQuestion.questionText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            items(options.size) { index ->
                val isSelected = state.selectedOptionIndex == index
                val isCorrectOption = currentQuestion.correctOptionIndex == index

                val bgColor: Color
                val borderColor: Color
                when {
                    !state.isSubmitted -> {
                        bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                        borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    }
                    isCorrectOption -> {
                        bgColor = RoastMintContainer
                        borderColor = RoastMint
                    }
                    isSelected && !isCorrectOption -> {
                        bgColor = RoastCrimsonContainer
                        borderColor = RoastCrimson
                    }
                    else -> {
                        bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        borderColor = MaterialTheme.colorScheme.outlineVariant
                    }
                }

                Surface(
                    onClick = { if (!state.isSubmitted) viewModel.selectOption(index) },
                    enabled = !state.isSubmitted,
                    shape = RoundedCornerShape(16.dp),
                    color = bgColor,
                    border = BorderStroke(if (isSelected || (state.isSubmitted && isCorrectOption)) 2.dp else 1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = letters[index],
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = options[index],
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.isSubmitted) {
                            if (isCorrectOption) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Correct", tint = RoastMint)
                            } else if (isSelected) {
                                Icon(Icons.Filled.Close, contentDescription = "Incorrect", tint = RoastCrimson)
                            }
                        }
                    }
                }
            }

            if (!state.isSubmitted) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.submitAnswer() },
                            enabled = state.selectedOptionIndex != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Submit Answer")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = onAskHintInCompanion) {
                                Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hint")
                            }
                            if (voiceQuizEnabled && onStartVoiceAnswer != null) {
                                OutlinedButton(onClick = onStartVoiceAnswer) {
                                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Answer by Voice")
                                }
                            }
                            TextButton(onClick = { viewModel.revealAnswerAssisted() }) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reveal")
                            }
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = state.isSubmitted,
                    enter = fadeIn() + slideInVertically { it / 4 }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RoastCard(
                            containerColor = if (state.isCorrect) RoastMintContainer.copy(alpha = 0.55f)
                            else RoastCrimsonContainer.copy(alpha = 0.55f),
                            borderColor = if (state.isCorrect) RoastMint else RoastCrimson
                        ) {
                            Text(
                                text = state.reactionMessage,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Explanation",
                                style = MaterialTheme.typography.labelLarge,
                                color = RoastMint
                            )
                            Text(
                                text = currentQuestion.explanation,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (currentQuestion.wrongOptionsExplanation.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Why other options are wrong",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentQuestion.wrongOptionsExplanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val sourceLabel = listOfNotNull(
                                currentQuestion.sourcePage.takeIf { it.isNotBlank() },
                                currentQuestion.sourceSection.takeIf { it.isNotBlank() },
                            ).joinToString(" • ")
                            if (sourceLabel.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Source: $sourceLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (currentQuestion.supportingPassage.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Evidence: \"${currentQuestion.supportingPassage.take(240)}${if (currentQuestion.supportingPassage.length > 240) "..." else ""}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.nextQuestion(onCompleted) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (state.currentIndex == state.questions.lastIndex) "Finish & View Summary"
                                else "Next Question"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
