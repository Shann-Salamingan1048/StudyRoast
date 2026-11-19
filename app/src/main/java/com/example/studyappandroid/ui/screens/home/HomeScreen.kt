package com.example.studyappandroid.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.studyappandroid.R
import com.example.studyappandroid.domain.model.AiPersonality
import com.example.studyappandroid.domain.model.AvatarExpression
import com.example.studyappandroid.ui.components.RoastAvatar
import com.example.studyappandroid.ui.components.RoastCard
import com.example.studyappandroid.ui.components.RoastGradientCard
import com.example.studyappandroid.ui.components.StatBadgeCard
import com.example.studyappandroid.ui.theme.RoastAmber
import com.example.studyappandroid.ui.theme.RoastIndigo
import com.example.studyappandroid.ui.theme.RoastMint

@Composable
fun HomeScreen(
    onContinueStudying: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onOpenHandout: (Long) -> Unit,
    onOpenLectureForTopic: (Long?, String?) -> Unit,
    onOpenCompanionChat: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = state.prefs

    val welcomeQuip = when (prefs.personality) {
        AiPersonality.FRIENDLY -> "Great to see you, ${prefs.userName}! Let's learn something awesome today."
        AiPersonality.SARCASTIC -> "Oh look, ${prefs.userName} actually opened the study app. Let's make it count."
        AiPersonality.SAVAGE -> "Back again, ${prefs.userName}? Let's see if your brain remembers yesterday's material."
        AiPersonality.SERIOUS -> "Welcome back, ${prefs.userName}. Select a handout or review scheduled topics."
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_studyroast_logo),
                    contentDescription = "StudyRoast Logo",
                    modifier = Modifier.size(44.dp),
                )
                Column {
                    Text(
                        text = "StudyRoast",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "AI Study Companion",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            RoastGradientCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoastAvatar(
                        expression = if (prefs.streakDays > 0) AvatarExpression.PROUD else AvatarExpression.IDLE,
                        style = prefs.avatarStyle,
                        size = 82.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "StudyRoast • ${prefs.personality.displayName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = welcomeQuip,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onContinueStudying,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Continue Studying", maxLines = 1)
                    }
                    OutlinedButton(onClick = onOpenCompanionChat) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Talk to AI")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatBadgeCard(
                    label = "Day Streak",
                    value = "${prefs.streakDays}",
                    icon = Icons.Filled.LocalFireDepartment,
                    accentColor = RoastAmber,
                    modifier = Modifier.weight(1f)
                )
                StatBadgeCard(
                    label = "Quizzes",
                    value = "${state.totalQuizzesCompleted}",
                    icon = Icons.Filled.Quiz,
                    accentColor = RoastIndigo,
                    modifier = Modifier.weight(1f)
                )
                StatBadgeCard(
                    label = "Accuracy",
                    value = "${state.averageAccuracy.toInt()}%",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accentColor = RoastMint,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.recommendedTopics.isNotEmpty()) {
            item {
                Text(
                    text = "Recommended Review (Spaced Repetition)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(state.recommendedTopics.take(4), key = { "rec_${it.id}" }) { sched ->
                RoastCard(
                    modifier = Modifier.clickable {
                        onOpenLectureForTopic(sched.handoutId, sched.topicTitle)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sched.topicTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${sched.subjectName} • Missed ${sched.mistakeCount}x • Correct ${sched.correctCount}x",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Review",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Handouts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = onNavigateToLibrary) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Upload")
                }
            }
        }

        if (state.recentHandouts.isEmpty()) {
            item {
                RoastCard {
                    Text(
                        text = "Your study desk is empty!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Upload your first PDF, DOCX, or TXT handout to unlock AI quizzes, lectures, and roasting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(state.recentHandouts, key = { "h_${it.id}" }) { handout ->
                RoastCard(modifier = Modifier.clickable { onOpenHandout(handout.id) }) {
                    Text(
                        text = handout.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${handout.subjectName} • ${handout.fileType} • ${handout.wordCount} words",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
