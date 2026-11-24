package com.example.unimate.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.NoteAttachment
import com.example.unimate.data.model.SaveNoteRequest
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.repository.NotesAndStudyPlannerRepository
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.SyncCoordinator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class NotesQuickFilter(val label: String) {
    ALL("All"),
    PINNED("Pinned"),
    FAVORITES("Favorites"),
}

sealed interface NotesUiState {
    data object Loading : NotesUiState
    data object Empty : NotesUiState
    data class Success(
        val allNotes: List<StudyNote>,
        val filteredNotes: List<StudyNote>,
        val subjects: List<String>,
        val selectedSubject: String?,
        val quickFilter: NotesQuickFilter,
        val searchQuery: String,
        val pinnedCount: Int,
        val favoritesCount: Int,
    ) : NotesUiState

    data class Error(val message: String) : NotesUiState
}

data class NoteEditorState(
    val isOpen: Boolean = false,
    val editingNoteId: String? = null,
    val draftNoteId: String = "",
    val title: String = "",
    val courseName: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val attachments: List<NoteAttachment> = emptyList(),
    val linkDraftUri: String = "",
    val linkDraftName: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

private data class NotesFilterState(
    val selectedSubject: String? = null,
    val quickFilter: NotesQuickFilter = NotesQuickFilter.ALL,
    val searchQuery: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val uid: String,
    private val repository: NotesAndStudyPlannerRepository,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val filterState = MutableStateFlow(NotesFilterState())

    private val _editorState = MutableStateFlow(NoteEditorState())
    val editorState: StateFlow<NoteEditorState> = _editorState.asStateFlow()

    private val _viewingNote = MutableStateFlow<StudyNote?>(null)
    val viewingNote: StateFlow<StudyNote?> = _viewingNote.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val rawNotesFlow = retryTrigger.flatMapLatest {
        repository.observeNotes(uid)
            .map<List<StudyNote>, Result<List<StudyNote>>> { Result.success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(Result.failure(firestoreError ?: error))
            }
    }

    val uiState: StateFlow<NotesUiState> = combine(
        rawNotesFlow,
        filterState,
    ) { result, filters ->
        result.fold(
            onFailure = { error ->
                NotesUiState.Error(error.localizedMessage ?: "Failed to load study notes.")
            },
            onSuccess = { allNotes ->
                if (allNotes.isEmpty()) {
                    NotesUiState.Empty
                } else {
                    val sortedAll = allNotes.sortedWith(
                        compareByDescending<StudyNote> { it.pinned }
                            .thenByDescending { it.effectiveUpdatedAtMillis() },
                    )
                    val subjects = sortedAll
                        .map { it.courseName.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()

                    val activeSubject = filters.selectedSubject?.takeIf { subjects.contains(it) }
                    val query = filters.searchQuery.trim().lowercase()

                    val filtered = sortedAll.filter { note ->
                        val matchesSubject = activeSubject == null ||
                            note.courseName.equals(activeSubject, ignoreCase = true)
                        val matchesQuickFilter = when (filters.quickFilter) {
                            NotesQuickFilter.ALL -> true
                            NotesQuickFilter.PINNED -> note.pinned
                            NotesQuickFilter.FAVORITES -> note.favorite
                        }
                        val matchesQuery = query.isEmpty() ||
                            note.title.lowercase().contains(query) ||
                            note.courseName.lowercase().contains(query) ||
                            note.content.lowercase().contains(query) ||
                            note.attachments.any { it.name.lowercase().contains(query) }
                        matchesSubject && matchesQuickFilter && matchesQuery
                    }

                    NotesUiState.Success(
                        allNotes = sortedAll,
                        filteredNotes = filtered,
                        subjects = subjects,
                        selectedSubject = activeSubject,
                        quickFilter = filters.quickFilter,
                        searchQuery = filters.searchQuery,
                        pinnedCount = sortedAll.count { it.pinned },
                        favoritesCount = sortedAll.count { it.favorite },
                    )
                }
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = NotesUiState.Loading,
    )

    fun retry() {
        retryTrigger.update { it + 1 }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun selectSubjectFilter(subject: String?) {
        filterState.update { it.copy(selectedSubject = subject) }
    }

    fun selectQuickFilter(quickFilter: NotesQuickFilter) {
        filterState.update { it.copy(quickFilter = quickFilter) }
    }

    fun onSearchQueryChanged(query: String) {
        filterState.update { it.copy(searchQuery = query) }
    }

    fun openNoteDetail(note: StudyNote) {
        _viewingNote.value = note
    }

    fun dismissNoteDetail() {
        _viewingNote.value = null
    }

    fun openCreateDialog(defaultSubject: String = "") {
        _editorState.value = NoteEditorState(
            isOpen = true,
            draftNoteId = UUID.randomUUID().toString(),
            courseName = defaultSubject.trim(),
        )
    }

    fun openEditDialog(note: StudyNote) {
        _viewingNote.value = null
        _editorState.value = NoteEditorState(
            isOpen = true,
            editingNoteId = note.id,
            title = note.title,
            courseName = note.courseName,
            content = note.content,
            pinned = note.pinned,
            favorite = note.favorite,
            attachments = note.attachments,
        )
    }

    fun dismissEditorDialog() {
        _editorState.value = NoteEditorState()
    }

    fun onTitleChanged(value: String) {
        _editorState.update { it.copy(title = value, errorMessage = null) }
    }

    fun onSubjectChanged(value: String) {
        _editorState.update { it.copy(courseName = value, errorMessage = null) }
    }

    fun onContentChanged(value: String) {
        _editorState.update { it.copy(content = value, errorMessage = null) }
    }

    fun onEditorPinnedChanged(pinned: Boolean) {
        _editorState.update { it.copy(pinned = pinned) }
    }

    fun onEditorFavoriteChanged(favorite: Boolean) {
        _editorState.update { it.copy(favorite = favorite) }
    }

    fun onLinkDraftUriChanged(value: String) {
        _editorState.update { it.copy(linkDraftUri = value, errorMessage = null) }
    }

    fun onLinkDraftNameChanged(value: String) {
        _editorState.update { it.copy(linkDraftName = value, errorMessage = null) }
    }

    fun addPickedAttachment(
        name: String,
        uri: String,
        mimeType: String,
        sizeBytes: Long = 0L,
    ) {
        val cleanUri = uri.trim()
        val cleanName = name.trim().ifBlank { "Document" }
        if (cleanUri.isBlank()) return
        _editorState.update { current ->
            if (current.attachments.size >= 10) {
                return@update current.copy(errorMessage = "Maximum 10 attachments per note.")
            }
            if (current.attachments.any { it.uri == cleanUri }) {
                return@update current.copy(errorMessage = "This file is already attached.")
            }
            current.copy(
                attachments = current.attachments + NoteAttachment(
                    name = cleanName,
                    uri = cleanUri,
                    mimeType = mimeType.ifBlank { "application/octet-stream" },
                    sizeBytes = sizeBytes.coerceAtLeast(0L),
                ),
                errorMessage = null,
            )
        }
    }

    fun addLinkAttachmentFromDraft() {
        val current = _editorState.value
        val uri = current.linkDraftUri.trim()
        if (!uri.startsWith("http://") && !uri.startsWith("https://")) {
            _editorState.update {
                it.copy(errorMessage = "Enter a valid http:// or https:// resource link.")
            }
            return
        }
        val label = current.linkDraftName.trim().ifBlank {
            uri.removePrefix("https://").removePrefix("http://").take(40)
        }
        _editorState.update { state ->
            if (state.attachments.size >= 10) {
                return@update state.copy(errorMessage = "Maximum 10 attachments per note.")
            }
            if (state.attachments.any { it.uri == uri }) {
                return@update state.copy(errorMessage = "This link is already attached.")
            }
            state.copy(
                attachments = state.attachments + NoteAttachment(
                    name = label,
                    uri = uri,
                    mimeType = if (uri.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "text/uri-list",
                    sizeBytes = 0L,
                ),
                linkDraftUri = "",
                linkDraftName = "",
                errorMessage = null,
            )
        }
    }

    fun removeAttachment(uri: String) {
        _editorState.update { current ->
            current.copy(attachments = current.attachments.filterNot { it.uri == uri })
        }
    }

    fun saveNote() {
        val draft = _editorState.value
        if (draft.isSaving) return

        val cleanTitle = draft.title.trim()
        val cleanSubject = draft.courseName.trim()
        val cleanContent = draft.content.trim()

        if (cleanTitle.isEmpty()) {
            _editorState.update { it.copy(errorMessage = "Enter a note title.") }
            return
        }
        if (cleanSubject.isEmpty()) {
            _editorState.update { it.copy(errorMessage = "Select or enter a subject/course.") }
            return
        }
        if (cleanContent.isEmpty()) {
            _editorState.update { it.copy(errorMessage = "Write note content before saving.") }
            return
        }

        val request = SaveNoteRequest(
            title = cleanTitle,
            courseName = cleanSubject,
            content = cleanContent,
            pinned = draft.pinned,
            favorite = draft.favorite,
            attachments = draft.attachments,
        )

        _editorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = if (draft.editingNoteId == null) {
                repository.createNoteWithId(
                    uid = uid,
                    noteId = draft.draftNoteId.ifBlank { UUID.randomUUID().toString() },
                    request = request,
                ).map { Unit }
            } else {
                repository.updateNote(uid, draft.editingNoteId, request)
            }
            result.fold(
                onSuccess = {
                    _editorState.value = NoteEditorState()
                    _actionMessage.value = if (draft.editingNoteId == null) {
                        syncCoordinator.formatActionMessage("Note saved to Cloud Firestore.")
                    } else {
                        syncCoordinator.formatActionMessage("Note updated.")
                    }
                },
                onFailure = { error ->
                    _editorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Failed to save note.",
                        )
                    }
                },
            )
        }
    }

    fun togglePinned(note: StudyNote) {
        viewModelScope.launch {
            val nextPinned = !note.pinned
            repository.toggleNotePinned(uid, note.id, nextPinned).fold(
                onSuccess = {
                    if (_viewingNote.value?.id == note.id) {
                        _viewingNote.value = note.copy(pinned = nextPinned)
                    }
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        if (nextPinned) "Note pinned." else "Note unpinned.",
                    )
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Could not update pin status."
                },
            )
        }
    }

    fun toggleFavorite(note: StudyNote) {
        viewModelScope.launch {
            val nextFav = !note.favorite
            repository.toggleNoteFavorite(uid, note.id, nextFav).fold(
                onSuccess = {
                    if (_viewingNote.value?.id == note.id) {
                        _viewingNote.value = note.copy(favorite = nextFav)
                    }
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        if (nextFav) "Added to favorites." else "Removed from favorites.",
                    )
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Could not update favorite status."
                },
            )
        }
    }

    fun deleteNote(noteId: String) {
        if (noteId.isBlank()) return
        viewModelScope.launch {
            repository.deleteNote(uid, noteId).fold(
                onSuccess = {
                    if (_viewingNote.value?.id == noteId) {
                        _viewingNote.value = null
                    }
                    _actionMessage.value = syncCoordinator.formatActionMessage("Note deleted.")
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Failed to delete note."
                },
            )
        }
    }

    companion object {
        fun factory(
            uid: String,
            repository: NotesAndStudyPlannerRepository,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NotesViewModel(
                    uid = uid,
                    repository = repository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
