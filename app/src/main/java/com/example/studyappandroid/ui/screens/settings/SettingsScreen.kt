package com.example.studyappandroid.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.studyappandroid.domain.model.AiPersonality
import com.example.studyappandroid.domain.model.AvatarStyle
import com.example.studyappandroid.domain.model.ThemeMode
import com.example.studyappandroid.domain.model.VoiceInteractionMode
import com.example.studyappandroid.ui.components.RoastCard
import com.example.studyappandroid.ui.components.StatBadgeCard
import com.example.studyappandroid.ui.theme.RoastAmber
import com.example.studyappandroid.ui.theme.RoastIndigo
import com.example.studyappandroid.ui.theme.RoastMint
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = state.prefs

    var nameInput by remember(prefs.userName) { mutableStateOf(prefs.userName) }
    var showLicenses by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "Profile & Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Learning Statistics
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatBadgeCard(
                    label = "Streak",
                    value = "${prefs.streakDays}d",
                    icon = Icons.Filled.LocalFireDepartment,
                    accentColor = RoastAmber,
                    modifier = Modifier.weight(1f),
                )
                StatBadgeCard(
                    label = "Quizzes",
                    value = "${state.attempts.size}",
                    icon = Icons.Filled.Quiz,
                    accentColor = RoastIndigo,
                    modifier = Modifier.weight(1f),
                )
                StatBadgeCard(
                    label = "Avg Score",
                    value = "${state.averageAccuracy.toInt()}%",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accentColor = RoastMint,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Profile Name & Theme
        item {
            RoastCard {
                Text("Student Profile & Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Button(onClick = { viewModel.setUserName(nameInput) }) {
                        Text("Save")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }

        // AI Personality & Avatar
        item {
            RoastCard {
                Text("AI Tutor Personality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    prefs.personality.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiPersonality.entries.forEach { personality ->
                        FilterChip(
                            selected = prefs.personality == personality,
                            onClick = { viewModel.setPersonality(personality) },
                            label = { Text(personality.displayName) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Avatar Style", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AvatarStyle.entries.forEach { style ->
                        FilterChip(
                            selected = prefs.avatarStyle == style,
                            onClick = { viewModel.setAvatarStyle(style) },
                            label = { Text(style.displayName) },
                        )
                    }
                }
            }
        }

        // Built-In Offline AI Teacher & Open-Source Notices
        item {
            RoastCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state.modelState is LocalModelState.Ready) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Filled.Memory
                        },
                        contentDescription = null,
                        tint = if (state.modelState is LocalModelState.Ready) {
                            RoastMint
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Built-In Offline AI Teacher",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Model: ${state.modelName} — runs 100% locally on your device with no internet, servers, or API keys required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))

                when (val modelState = state.modelState) {
                    is LocalModelState.Uninitialized -> {
                        Text(
                            text = "Status: Standby (loads automatically when you start a quiz, lecture, or chat)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = viewModel::prepareOfflineModel,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Preload AI Teacher into Memory")
                        }
                    }
                    is LocalModelState.Preparing -> {
                        Text(
                            text = modelState.stage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { modelState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is LocalModelState.Ready -> {
                        Text(
                            text = "Status: Ready in RAM (${modelState.contextTokens} token context • loaded in ${modelState.loadTimeMs}ms)",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoastMint,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = viewModel::unloadOfflineModel,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Free AI RAM (Reloads Automatically Later)")
                        }
                    }
                    is LocalModelState.Error -> {
                        Text(
                            text = "Status: ${modelState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::prepareOfflineModel,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Retry Loading AI Teacher")
                        }
                    }
                }

                state.statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { showLicenses = !showLicenses },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open-Source AI & Speech Licenses")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showLicenses) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                AnimatedVisibility(visible = showLicenses) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "• Qwen2.5-0.5B-Instruct (Q4_K_M GGUF) — Apache License 2.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "• llama.cpp on-device inference engine — MIT License",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "• Vosk & vosk-model-small-en-us — Apache License 2.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "• Apache PDFBox Android — Apache License 2.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Voice Settings
        item {
            RoastCard {
                Text("Voice & Speech", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSwitchRow("Enable Voice Output (TTS)", prefs.voiceEnabled, viewModel::setVoiceEnabled)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Microphone Mode", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceInteractionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.voiceInteractionMode == mode,
                            onClick = { viewModel.setVoiceMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Speech Rate: ${String.format(Locale.getDefault(), "%.1fx", prefs.ttsSpeechRate)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = prefs.ttsSpeechRate,
                    onValueChange = { viewModel.setTtsSettings(it, prefs.ttsPitch) },
                    valueRange = 0.7f..1.6f,
                )
                Text(
                    "Voice Pitch: ${String.format(Locale.getDefault(), "%.1f", prefs.ttsPitch)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = prefs.ttsPitch,
                    onValueChange = { viewModel.setTtsSettings(prefs.ttsSpeechRate, it) },
                    valueRange = 0.7f..1.4f,
                )
            }
        }

        // Memory & Data Management
        item {
            RoastCard {
                Text("Conversation Memory & Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSwitchRow(
                    "Persistent Conversation Memory",
                    prefs.persistentMemoryEnabled,
                    viewModel::setPersistentMemory,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::clearConversationHistory,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear Chat History")
                }
            }
        }

        // Quiz History
        if (state.attempts.isNotEmpty()) {
            item {
                RoastCard {
                    Text("Recent Quiz History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    state.attempts.take(8).forEach { attempt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(attempt.quizTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${attempt.correctCount}/${attempt.totalQuestions} • ${attempt.durationSeconds}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${attempt.accuracyPercent.toInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (attempt.accuracyPercent >= 70f) RoastMint else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
