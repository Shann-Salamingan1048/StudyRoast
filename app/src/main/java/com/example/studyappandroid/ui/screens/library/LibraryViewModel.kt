package com.example.studyappandroid.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studyappandroid.data.local.db.HandoutEntity
import com.example.studyappandroid.data.local.db.LectureEntity
import com.example.studyappandroid.data.local.db.SubjectEntity
import com.example.studyappandroid.data.local.db.TopicEntity
import com.example.studyappandroid.data.repository.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val subjects: List<SubjectEntity> = emptyList(),
    val handouts: List<HandoutEntity> = emptyList(),
    val savedLectures: List<LectureEntity> = emptyList(),
    val selectedSubjectFilter: String? = null,
    val searchQuery: String = "",
    val selectedHandoutIds: Set<Long> = emptySet(),
    val isImporting: Boolean = false,
    val importStatusMessage: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: StudyRepository
) : ViewModel() {

    private val selectedSubject = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val statusState = MutableStateFlow<Triple<Boolean, String?, String?>>(Triple(false, null, null))

    private val filteredHandoutsFlow = combine(selectedSubject, searchQuery) { subj, q ->
        subj to q.trim()
    }.flatMapLatest { (subj, q) ->
        repository.searchHandouts(subj, q)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observeSubjects(),
        filteredHandoutsFlow,
        repository.observeAllLectures(),
        selectedSubject,
        searchQuery
    ) { subjects, handouts, lectures, subj, q ->
        LibraryUiState(
            subjects = subjects,
            handouts = handouts,
            savedLectures = lectures,
            selectedSubjectFilter = subj,
            searchQuery = q
        )
    }.combine(combine(selectedIds, statusState) { ids, status -> ids to status }) { base, extra ->
        base.copy(
            selectedHandoutIds = extra.first,
            isImporting = extra.second.first,
            importStatusMessage = extra.second.second,
            errorMessage = extra.second.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun setSubjectFilter(subject: String?) {
        selectedSubject.value = subject
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleHandoutSelection(handoutId: Long) {
        selectedIds.update { current ->
            if (handoutId in current) current - handoutId else current + handoutId
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun createSubject(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.ensureSubject(name)
            selectedSubject.value = name.trim()
        }
    }

    fun importHandout(uri: Uri, subjectName: String) {
        viewModelScope.launch {
            statusState.value = Triple(true, null, null)
            when (val res = repository.importHandout(uri, subjectName)) {
                is StudyRepository.ImportHandoutResult.Success -> {
                    val ocrNote = if (res.handout.isOcrUsed) " (OCR used)" else ""
                    statusState.value = Triple(
                        false,
                        "Imported '${res.handout.title}' with ${res.topicCount} topics$ocrNote.",
                        null
                    )
                }
                is StudyRepository.ImportHandoutResult.Error -> {
                    statusState.value = Triple(false, null, res.message)
                }
            }
        }
    }

    fun deleteHandout(handout: HandoutEntity) {
        viewModelScope.launch {
            repository.deleteHandout(handout)
            selectedIds.update { it - handout.id }
        }
    }

    fun clearMessages() {
        statusState.value = Triple(false, null, null)
    }

    fun observeHandout(handoutId: Long) = repository.observeHandout(handoutId)
    fun observeTopics(handoutId: Long): kotlinx.coroutines.flow.Flow<List<TopicEntity>> =
        repository.observeTopicsForHandout(handoutId)
}
