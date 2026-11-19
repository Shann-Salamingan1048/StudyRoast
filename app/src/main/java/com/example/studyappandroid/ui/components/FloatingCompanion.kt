package com.example.studyappandroid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.studyappandroid.domain.model.AiPersonality
import com.example.studyappandroid.ui.screens.companion.CompanionUiState
import kotlin.math.roundToInt

@Composable
fun FloatingCompanionOverlay(
    state: CompanionUiState,
    onToggleExpanded: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onStopVoice: () -> Unit,
    onSelectPersonality: (AiPersonality) -> Unit,
    onNewChat: () -> Unit,
    onClearChat: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val avatarPx = with(density) { 72.dp.toPx() }

        var offsetX by remember(maxW) { mutableFloatStateOf((maxW - avatarPx - 28f).coerceAtLeast(16f)) }
        var offsetY by remember(maxH) { mutableFloatStateOf((maxH - avatarPx - 240f).coerceAtLeast(120f)) }

        // Draggable Floating Avatar Bubble (when minimized)
        AnimatedVisibility(
            visible = !state.isPanelExpanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(72.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(maxW, maxH) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(12f, (maxW - avatarPx - 12f).coerceAtLeast(12f))
                            offsetY = (offsetY + dragAmount.y).coerceIn(60f, (maxH - avatarPx - 120f).coerceAtLeast(60f))
                        }
                    }
                    .clickable { onToggleExpanded(true) },
                contentAlignment = Alignment.Center
            ) {
                RoastAvatar(
                    expression = state.avatarExpression,
                    style = state.avatarStyle,
                    size = 66.dp
                )
            }
        }

        // Expandable Conversation Panel
        AnimatedVisibility(
            visible = state.isPanelExpanded,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 78.dp)
                .imePadding()
        ) {
            CompanionChatPanel(
                state = state,
                onMinimize = { onToggleExpanded(false) },
                onSendMessage = onSendMessage,
                onMicClick = onMicClick,
                onStopVoice = onStopVoice,
                onSelectPersonality = onSelectPersonality,
                onNewChat = onNewChat,
                onClearChat = onClearChat,
                onDismissError = onDismissError
            )
        }
    }
}

@Composable
private fun CompanionChatPanel(
    state: CompanionUiState,
    onMinimize: () -> Unit,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onStopVoice: () -> Unit,
    onSelectPersonality: (AiPersonality) -> Unit,
    onNewChat: () -> Unit,
    onClearChat: () -> Unit,
    onDismissError: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 14.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 340.dp, max = 520.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with Animated Avatar + Context + Minimize
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoastAvatar(
                    expression = state.avatarExpression,
                    style = state.avatarStyle,
                    size = 52.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "StudyRoast Tutor (${state.personality.displayName})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val ctx = state.contextSnapshot
                    val contextDesc = buildString {
                        append(ctx.currentScreen)
                        ctx.topicTitle?.let { append(" • $it") }
                    }
                    Text(
                        text = when {
                            state.isListening -> "Listening..."
                            state.isSpeaking -> "Speaking (tap mic to interrupt)"
                            state.isThinking -> "Thinking..."
                            else -> "Context: $contextDesc"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Filled.AddComment, contentDescription = "New Chat", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClearChat) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear Chat", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Filled.Minimize, contentDescription = "Minimize")
                }
            }

            // Personality Quick Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiPersonality.entries.forEach { p ->
                    FilterChip(
                        selected = state.personality == p,
                        onClick = { onSelectPersonality(p) },
                        label = { Text(p.displayName, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            state.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(6.dp))
                ErrorBanner(message = err, onDismiss = onDismissError)
            }

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Ask me anything about your handout, request a quiz hint without spoiling the answer, or tap the mic to talk naturally!",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                items(state.messages, key = { "${it.id}_${it.timestamp}" }) { msg ->
                    val isUser = msg.role == "USER"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(0.86f)
                        ) {
                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
                if (state.livePartialSpeech.isNotBlank()) {
                    item {
                        Text(
                            text = "Hearing: \"${state.livePartialSpeech}\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Quick Study Prompts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickPrompts = if (!state.contextSnapshot.currentQuestionText.isNullOrBlank()) {
                    listOf("Give me a hint", "Explain the question", "I still don't understand")
                } else {
                    listOf("Explain simply", "Give an example", "Quiz me verbally")
                }
                quickPrompts.forEach { prompt ->
                    AssistChip(
                        onClick = { onSendMessage(prompt) },
                        label = { Text(prompt, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Input Row with Mic + Text + Send
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        if (state.isSpeaking) {
                            onStopVoice()
                            onMicClick()
                        } else if (state.isListening) {
                            onStopVoice()
                        } else {
                            onMicClick()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (state.isListening) Icons.Filled.Close
                        else if (state.isSpeaking) Icons.Filled.Stop
                        else Icons.Filled.Mic,
                        contentDescription = "Voice Input",
                        tint = if (state.isListening) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask StudyRoast...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 2
                )
                IconButton(
                    onClick = {
                        val txt = inputText
                        inputText = ""
                        onSendMessage(txt)
                    },
                    enabled = inputText.isNotBlank() && !state.isThinking
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
