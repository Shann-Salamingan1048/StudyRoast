package com.example.studyappandroid.ui.screens.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.studyappandroid.ui.components.RoastCard
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoutDetailScreen(
    handoutId: Long,
    onBack: () -> Unit,
    onStartQuiz: (Long) -> Unit,
    onStartLecture: (Long, String?) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val handout by viewModel.observeHandout(handoutId).collectAsStateWithLifecycle(initialValue = null)
    val topics by viewModel.observeTopics(handoutId).collectAsStateWithLifecycle(initialValue = emptyList())
    val json = remember { Json { ignoreUnknownKeys = true } }

    var previewSearchQuery by remember { mutableStateOf("") }
    var selectedPageLabel by remember { mutableStateOf<String?>(null) }
    var showFullText by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(handout?.title ?: "Handout Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        val item = handout
        if (item == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                Text("Loading handout...")
            }
            return@Scaffold
        }

        val definitions = remember(item.definitionsJson) {
            runCatching { json.decodeFromString<List<String>>(item.definitionsJson) }.getOrDefault(emptyList())
        }
        val formulas = remember(item.formulasJson) {
            runCatching { json.decodeFromString<List<String>>(item.formulasJson) }.getOrDefault(emptyList())
        }
        val objectives = remember(item.keyObjectivesJson) {
            runCatching { json.decodeFromString<List<String>>(item.keyObjectivesJson) }.getOrDefault(emptyList())
        }

        val pageSections = remember(item.extractedText) {
            splitExtractedPages(item.extractedText)
        }

        val filteredPreviewText = remember(item.extractedText, pageSections, selectedPageLabel, previewSearchQuery) {
            val baseText = if (selectedPageLabel != null && pageSections.isNotEmpty()) {
                pageSections.firstOrNull { it.first == selectedPageLabel }?.second.orEmpty()
            } else {
                item.extractedText
            }
            val query = previewSearchQuery.trim()
            if (query.isEmpty()) {
                baseText
            } else {
                baseText.split("\n\n")
                    .filter { it.contains(query, ignoreCase = true) }
                    .joinToString("\n\n")
                    .ifBlank { "No extracted text matching \"$query\"." }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { onStartQuiz(item.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Quiz, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Quiz")
                    }
                    OutlinedButton(
                        onClick = { onStartLecture(item.id, topics.firstOrNull()?.title) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lecture")
                    }
                }
            }

            item {
                RoastCard {
                    val pageNote = if (pageSections.size > 1) " • ${pageSections.size} pages" else ""
                    val ocrNote = if (item.isOcrUsed) " • OCR" else ""
                    Text(
                        text = "${item.subjectName} • ${item.fileType} • ${item.wordCount} words$pageNote$ocrNote",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(item.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Interactive Extracted-Text Preview
            item {
                RoastCard {
                    Text(
                        text = "Extracted Text Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Inspect the exact text StudyRoast extracted from ${item.fileName} (${item.extractedText.length} characters).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = previewSearchQuery,
                        onValueChange = { previewSearchQuery = it },
                        placeholder = { Text("Search extracted text...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (previewSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { previewSearchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    if (pageSections.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = selectedPageLabel == null,
                                onClick = { selectedPageLabel = null },
                                label = { Text("All Pages") },
                            )
                            pageSections.forEach { (label, _) ->
                                FilterChip(
                                    selected = selectedPageLabel == label,
                                    onClick = { selectedPageLabel = label },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val displayedText = if (showFullText || filteredPreviewText.length <= 2400) {
                        filteredPreviewText
                    } else {
                        filteredPreviewText.take(2400) + "\n\n... (preview truncated)"
                    }
                    Text(
                        text = displayedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (filteredPreviewText.length > 2400) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = { showFullText = !showFullText }) {
                            Text(if (showFullText) "Collapse Full Text" else "Show Full Extracted Text (${filteredPreviewText.length} chars)")
                        }
                    }
                }
            }

            if (topics.isNotEmpty()) {
                item {
                    Text(
                        text = "Detected Topics (${topics.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(topics, key = { it.id }) { topic ->
                    RoastCard {
                        Text(
                            text = topic.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = topic.contentSnippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { onStartLecture(item.id, topic.title) }) {
                            Text("Study This Topic")
                        }
                    }
                }
            }

            if (definitions.isNotEmpty()) {
                item {
                    RoastCard {
                        Text(
                            text = "Key Definitions & Terms",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        definitions.forEach { def ->
                            Text("• $def", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            if (formulas.isNotEmpty()) {
                item {
                    RoastCard {
                        Text(
                            text = "Detected Formulas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        formulas.forEach { formula ->
                            Text("• $formula", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            if (objectives.isNotEmpty()) {
                item {
                    RoastCard {
                        Text(
                            text = "Learning Objectives / Key Points",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        objectives.forEach { obj ->
                            Text("• $obj", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun splitExtractedPages(text: String): List<Pair<String, String>> {
    val pageRegex = Regex("(?m)^\\[Page\\s+(\\d+)]$")
    val matches = pageRegex.findAll(text).toList()
    if (matches.isEmpty()) return emptyList()

    val pages = mutableListOf<Pair<String, String>>()
    matches.forEachIndexed { idx, match ->
        val pageLabel = "Page ${match.groupValues[1]}"
        val start = match.range.last + 1
        val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else text.length
        val content = text.substring(start, end).trim()
        if (content.isNotEmpty()) {
            pages += pageLabel to content
        }
    }
    return pages
}
