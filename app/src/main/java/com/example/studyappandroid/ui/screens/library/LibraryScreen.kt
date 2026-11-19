package com.example.studyappandroid.ui.screens.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.ui.components.ErrorBanner
import com.example.studyappandroid.ui.components.RoastCard

@Composable
fun LibraryScreen(
    onOpenHandoutDetail: (Long) -> Unit,
    onStartQuizWithHandouts: (List<Long>) -> Unit,
    onOpenLecture: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showNewSubjectDialog by remember { mutableStateOf(false) }
    var handoutToDelete by remember { mutableStateOf<HandoutEntity?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePicker.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/plain"
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = "Upload Handout")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Handout Library",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.handouts.size} saved handouts • PDF, DOCX, TXT",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { showNewSubjectDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Subject")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search handouts, topics, keywords...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedSubjectFilter == null,
                        onClick = { viewModel.setSubjectFilter(null) },
                        label = { Text("All Subjects") }
                    )
                    state.subjects.forEach { subject ->
                        FilterChip(
                            selected = state.selectedSubjectFilter == subject.name,
                            onClick = {
                                val next = if (state.selectedSubjectFilter == subject.name) null else subject.name
                                viewModel.setSubjectFilter(next)
                            },
                            label = { Text(subject.name) }
                        )
                    }
                }
            }

            if (state.isImporting) {
                item {
                    RoastCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Column {
                                Text("Extracting handout text...", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Parsing headings, definitions, and formulas locally.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            state.importStatusMessage?.let { msg ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = viewModel::clearMessages) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
            }

            state.errorMessage?.let { err ->
                item {
                    ErrorBanner(
                        message = err,
                        onDismiss = viewModel::clearMessages
                    )
                }
            }

            if (state.selectedHandoutIds.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${state.selectedHandoutIds.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = viewModel::clearSelection) {
                                    Text("Clear")
                                }
                                Button(
                                    onClick = {
                                        onStartQuizWithHandouts(state.selectedHandoutIds.toList())
                                    }
                                ) {
                                    Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Quiz Selected")
                                }
                            }
                        }
                    }
                }
            }

            if (state.handouts.isEmpty()) {
                item {
                    RoastCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No handouts yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the Upload button to import PDF, DOCX, or TXT study handouts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    filePicker.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "text/plain"
                                        )
                                    )
                                }
                            ) {
                                Text("Pick a Handout")
                            }
                        }
                    }
                }
            } else {
                items(state.handouts, key = { it.id }) { handout ->
                    val isSelected = handout.id in state.selectedHandoutIds
                    HandoutCard(
                        handout = handout,
                        isSelected = isSelected,
                        onToggleSelect = { viewModel.toggleHandoutSelection(handout.id) },
                        onOpen = { onOpenHandoutDetail(handout.id) },
                        onLecture = { onOpenLecture(handout.id) },
                        onQuiz = { onStartQuizWithHandouts(listOf(handout.id)) },
                        onDelete = { handoutToDelete = handout }
                    )
                }
            }

            if (state.savedLectures.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Saved Study Explanations (${state.savedLectures.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(state.savedLectures.take(5), key = { "lec_${it.id}" }) { lecture ->
                    RoastCard(
                        modifier = Modifier.clickable { onOpenLecture(lecture.handoutId) }
                    ) {
                        Text(
                            text = "${lecture.topicTitle} • ${lecture.actionType}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lecture.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    pendingUri?.let { uri ->
        SubjectPickerDialog(
            existingSubjects = state.subjects.map { it.name },
            initialSubject = state.selectedSubjectFilter ?: "General",
            onDismiss = { pendingUri = null },
            onConfirm = { subject ->
                pendingUri = null
                viewModel.importHandout(uri, subject)
            }
        )
    }

    if (showNewSubjectDialog) {
        var subjectInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewSubjectDialog = false },
            title = { Text("Create Subject") },
            text = {
                OutlinedTextField(
                    value = subjectInput,
                    onValueChange = { subjectInput = it },
                    label = { Text("Subject Name") },
                    placeholder = { Text("e.g., Biology, Calculus, Electronics") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createSubject(subjectInput)
                        showNewSubjectDialog = false
                    },
                    enabled = subjectInput.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSubjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    handoutToDelete?.let { handout ->
        AlertDialog(
            onDismissRequest = { handoutToDelete = null },
            title = { Text("Delete Handout?") },
            text = { Text("Remove '${handout.title}' and its extracted topics from local storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteHandout(handout)
                        handoutToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { handoutToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HandoutCard(
    handout: HandoutEntity,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onLecture: () -> Unit,
    onQuiz: () -> Unit,
    onDelete: () -> Unit
) {
    RoastCard(
        modifier = Modifier.clickable(onClick = onOpen),
        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = handout.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(handout.subjectName, style = MaterialTheme.typography.labelSmall) }
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "${handout.fileType} • ${handout.wordCount}w" + if (handout.isOcrUsed) " • OCR" else "",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = handout.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onLecture, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lecture", maxLines = 1)
                    }
                    Button(onClick = onQuiz, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Quiz", maxLines = 1)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectPickerDialog(
    existingSubjects: List<String>,
    initialSubject: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var subjectText by remember { mutableStateOf(initialSubject) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Organize this handout under a subject so you can combine multiple handouts into one quiz.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = subjectText,
                    onValueChange = { subjectText = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existingSubjects.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        existingSubjects.forEach { subj ->
                            FilterChip(
                                selected = subjectText == subj,
                                onClick = { subjectText = subj },
                                label = { Text(subj) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(subjectText.ifBlank { "General" }) }) {
                Text("Import Handout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
