package com.example.unimate.ui.assignments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.AssignmentAttachment
import com.example.unimate.data.model.AssignmentFilter
import com.example.unimate.data.model.AssignmentPriority
import com.example.unimate.data.model.AssignmentQueryEngine
import com.example.unimate.data.model.AssignmentSortOrder
import com.example.unimate.data.model.AssignmentStatus
import com.example.unimate.data.model.SaveAssignmentRequest
import com.example.unimate.data.model.StudentAssignment
import com.example.unimate.data.repository.AcademicModulesRepository
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

sealed interface AssignmentsUiState {
    data object Loading : AssignmentsUiState
    data object Empty : AssignmentsUiState
    data class Success(
        val allAssignments: List<StudentAssignment>,
        val filteredAssignments: List<StudentAssignment>,
        val activeFilter: AssignmentFilter,
        val sortOrder: AssignmentSortOrder,
        val searchQuery: String,
        val pendingCount: Int,
        val completedCount: Int,
        val overdueCount: Int,
    ) : AssignmentsUiState

    data class Error(val message: String) : AssignmentsUiState
}

data class AssignmentEditorState(
    val isOpen: Boolean = false,
    val editingAssignmentId: String? = null,
    val draftAssignmentId: String = "",
    val title: String = "",
    val description: String = "",
    val courseName: String = "",
    val deadlineMillis: Long = 0L,
    val priority: AssignmentPriority = AssignmentPriority.MEDIUM,
    val status: AssignmentStatus = AssignmentStatus.PENDING,
    val attachments: List<AssignmentAttachment> = emptyList(),
    val linkDraftName: String = "",
    val linkDraftUri: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AssignmentsViewModel(
    private val uid: String,
    private val repository: AcademicModulesRepository,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val activeFilter = MutableStateFlow(AssignmentFilter.ALL)
    private val sortOrder = MutableStateFlow(AssignmentSortOrder.DEADLINE_ASC)
    private val searchQuery = MutableStateFlow("")

    private sealed interface StreamResult {
        data object Loading : StreamResult
        data class Loaded(val items: List<StudentAssignment>) : StreamResult
        data class Failed(val message: String) : StreamResult
    }

    private val assignmentsStream = retryTrigger.flatMapLatest {
        repository.observeAssignments(uid)
            .map<List<StudentAssignment>, StreamResult> { StreamResult.Loaded(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                emit(StreamResult.Failed(root.localizedMessage ?: "Failed to load assignments."))
            }
    }

    val uiState: StateFlow<AssignmentsUiState> = combine(
        assignmentsStream,
        activeFilter,
        sortOrder,
        searchQuery,
    ) { streamResult, filter, sort, query ->
        when (streamResult) {
            StreamResult.Loading -> AssignmentsUiState.Loading
            is StreamResult.Failed -> AssignmentsUiState.Error(streamResult.message)
            is StreamResult.Loaded -> {
                val all = streamResult.items
                if (all.isEmpty()) {
                    AssignmentsUiState.Empty
                } else {
                    val now = clockMillis()
                    val filtered = AssignmentQueryEngine.filterAndSort(
                        assignments = all,
                        filter = filter,
                        sortOrder = sort,
                        searchQuery = query,
                        nowMillis = now,
                    )
                    AssignmentsUiState.Success(
                        allAssignments = all,
                        filteredAssignments = filtered,
                        activeFilter = filter,
                        sortOrder = sort,
                        searchQuery = query,
                        pendingCount = all.count { it.resolvedStatus() == AssignmentStatus.PENDING },
                        completedCount = all.count { it.resolvedStatus() == AssignmentStatus.COMPLETED },
                        overdueCount = all.count { it.isOverdue(now) },
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AssignmentsUiState.Loading,
    )

    private val _editorState = MutableStateFlow(AssignmentEditorState())
    val editorState: StateFlow<AssignmentEditorState> = _editorState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    fun selectFilter(filter: AssignmentFilter) {
        activeFilter.value = filter
    }

    fun selectSortOrder(order: AssignmentSortOrder) {
        sortOrder.value = order
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun retry() {
        retryTrigger.value += 1
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun openAddDialog(defaultCourse: String = "") {
        _editorState.value = AssignmentEditorState(
            isOpen = true,
            editingAssignmentId = null,
            draftAssignmentId = UUID.randomUUID().toString(),
            courseName = defaultCourse,
            deadlineMillis = clockMillis() + DEFAULT_DEADLINE_OFFSET_MS,
            priority = AssignmentPriority.MEDIUM,
            status = AssignmentStatus.PENDING,
        )
    }

    fun openEditDialog(item: StudentAssignment) {
        _editorState.value = AssignmentEditorState(
            isOpen = true,
            editingAssignmentId = item.id,
            title = item.title,
            description = item.description,
            courseName = item.courseName,
            deadlineMillis = if (item.deadlineMillis > 0L) item.deadlineMillis else clockMillis() + DEFAULT_DEADLINE_OFFSET_MS,
            priority = item.resolvedPriority(),
            status = item.resolvedStatus(),
            attachments = item.attachments,
        )
    }

    fun dismissDialog() {
        if (_editorState.value.isSaving) return
        _editorState.value = AssignmentEditorState(isOpen = false)
    }

    fun onTitleChanged(value: String) {
        _editorState.update { it.copy(title = value, errorMessage = null) }
    }

    fun onDescriptionChanged(value: String) {
        _editorState.update { it.copy(description = value, errorMessage = null) }
    }

    fun onCourseChanged(value: String) {
        _editorState.update { it.copy(courseName = value, errorMessage = null) }
    }

    fun onDeadlineChanged(millis: Long) {
        _editorState.update { it.copy(deadlineMillis = millis, errorMessage = null) }
    }

    fun onPriorityChanged(priority: AssignmentPriority) {
        _editorState.update { it.copy(priority = priority, errorMessage = null) }
    }

    fun onStatusChanged(status: AssignmentStatus) {
        _editorState.update { it.copy(status = status, errorMessage = null) }
    }

    fun onLinkDraftNameChanged(value: String) {
        _editorState.update { it.copy(linkDraftName = value, errorMessage = null) }
    }

    fun onLinkDraftUriChanged(value: String) {
        _editorState.update { it.copy(linkDraftUri = value, errorMessage = null) }
    }

    fun addPickedAttachment(name: String, uri: String, mimeType: String) {
        val cleanUri = uri.trim()
        val cleanName = name.trim().ifEmpty { "Attached File" }
        if (cleanUri.isEmpty()) return
        _editorState.update { state ->
            if (state.attachments.size >= 10) {
                state.copy(errorMessage = "Maximum 10 attachments supported per assignment.")
            } else if (state.attachments.any { it.uri == cleanUri }) {
                state.copy(errorMessage = "This attachment is already added.")
            } else {
                state.copy(
                    attachments = state.attachments + AssignmentAttachment(
                        name = cleanName.take(120),
                        uri = cleanUri.take(1000),
                        mimeType = mimeType.ifBlank { "application/octet-stream" }.take(100),
                    ),
                    errorMessage = null,
                )
            }
        }
    }

    fun addLinkAttachmentFromDraft() {
        val state = _editorState.value
        val rawUri = state.linkDraftUri.trim()
        if (rawUri.isEmpty()) {
            _editorState.update { it.copy(errorMessage = "Enter a valid URL or resource link.") }
            return
        }
        val normalizedUri = if (rawUri.startsWith("http://") || rawUri.startsWith("https://")) {
            rawUri
        } else {
            "https://$rawUri"
        }
        val label = state.linkDraftName.trim().ifEmpty { normalizedUri }
        _editorState.update {
            if (it.attachments.size >= 10) {
                it.copy(errorMessage = "Maximum 10 attachments supported.")
            } else {
                it.copy(
                    attachments = it.attachments + AssignmentAttachment(
                        name = label.take(120),
                        uri = normalizedUri.take(1000),
                        mimeType = "text/uri-list",
                    ),
                    linkDraftName = "",
                    linkDraftUri = "",
                    errorMessage = null,
                )
            }
        }
    }

    fun removeAttachment(uri: String) {
        _editorState.update { state ->
            state.copy(attachments = state.attachments.filterNot { it.uri == uri })
        }
    }

    fun saveAssignment() {
        val form = _editorState.value
        if (form.isSaving) return

        val cleanTitle = form.title.trim()
        val cleanCourse = form.courseName.trim()
        val cleanDesc = form.description.trim()

        val error = when {
            cleanTitle.length !in 1..160 -> "Assignment title is required (1–160 characters)."
            cleanCourse.length !in 1..100 -> "Course name/code is required (1–100 characters)."
            cleanDesc.length > 2000 -> "Description must be 2000 characters or fewer."
            form.deadlineMillis <= 0L -> "Please select a valid deadline."
            else -> null
        }

        if (error != null) {
            _editorState.update { it.copy(errorMessage = error) }
            return
        }

        val request = SaveAssignmentRequest(
            title = cleanTitle,
            description = cleanDesc,
            courseName = cleanCourse,
            deadlineMillis = form.deadlineMillis,
            priority = form.priority,
            status = form.status,
            attachments = form.attachments,
        )

        _editorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val editingId = form.editingAssignmentId
            val result = if (editingId == null) {
                repository.addAssignmentWithId(
                    uid = uid,
                    assignmentId = form.draftAssignmentId.ifBlank { UUID.randomUUID().toString() },
                    request = request,
                )
            } else {
                repository.updateAssignment(uid, editingId, request)
            }
            result.fold(
                onSuccess = {
                    _editorState.value = AssignmentEditorState(isOpen = false)
                    _actionMessage.value = if (editingId == null) {
                        syncCoordinator.formatActionMessage("Assignment saved to Cloud Firestore.")
                    } else {
                        syncCoordinator.formatActionMessage("Assignment updated.")
                    }
                },
                onFailure = { failure ->
                    _editorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = failure.localizedMessage ?: "Failed to save assignment.",
                        )
                    }
                },
            )
        }
    }

    fun toggleCompleted(item: StudentAssignment) {
        if (_isMutating.value || item.id.isBlank()) return
        val nextStatus = if (item.resolvedStatus() == AssignmentStatus.COMPLETED) {
            AssignmentStatus.PENDING
        } else {
            AssignmentStatus.COMPLETED
        }
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.updateAssignmentStatus(uid, item.id, nextStatus)
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    val baseMessage = if (nextStatus == AssignmentStatus.COMPLETED) {
                        "Marked \"${item.title}\" as completed."
                    } else {
                        "Reopened \"${item.title}\" as pending."
                    }
                    _actionMessage.value = syncCoordinator.formatActionMessage(baseMessage)
                },
                onFailure = { failure ->
                    _actionMessage.value = failure.localizedMessage ?: "Could not update assignment status."
                },
            )
        }
    }

    fun deleteAssignment(assignmentId: String) {
        if (_isMutating.value || assignmentId.isBlank()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.deleteAssignment(uid, assignmentId)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = syncCoordinator.formatActionMessage("Assignment deleted.") },
                onFailure = { failure ->
                    _actionMessage.value = failure.localizedMessage ?: "Could not delete assignment."
                },
            )
        }
    }

    companion object {
        private const val DEFAULT_DEADLINE_OFFSET_MS = 2L * 24L * 60L * 60L * 1000L

        fun factory(
            uid: String,
            repository: AcademicModulesRepository,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AssignmentsViewModel(
                    uid = uid,
                    repository = repository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
