package com.example.unimate.ui.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.ExamPreparationStatus
import com.example.unimate.data.model.ExamType
import com.example.unimate.data.model.ExamTypeFilter
import com.example.unimate.data.model.RevisionChecklistItem
import com.example.unimate.data.model.SaveExamRequest
import com.example.unimate.data.model.StudentExam
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

sealed interface ExamsUiState {
    data object Loading : ExamsUiState
    data object Empty : ExamsUiState
    data class Success(
        val allExams: List<StudentExam>,
        val filteredExams: List<StudentExam>,
        val typeFilter: ExamTypeFilter,
        val midtermCount: Int,
        val finalCount: Int,
    ) : ExamsUiState

    data class Error(val message: String) : ExamsUiState
}

data class ExamEditorState(
    val isOpen: Boolean = false,
    val editingExamId: String? = null,
    val draftExamId: String = "",
    val title: String = "",
    val examType: ExamType = ExamType.MIDTERM,
    val courseName: String = "",
    val examAtMillis: Long = 0L,
    val room: String = "",
    val preparationStatus: ExamPreparationStatus = ExamPreparationStatus.NOT_STARTED,
    val revisionChecklist: List<RevisionChecklistItem> = emptyList(),
    val checklistTopicDraft: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExamsViewModel(
    private val uid: String,
    private val repository: AcademicModulesRepository,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val typeFilter = MutableStateFlow(ExamTypeFilter.ALL)

    private sealed interface StreamResult {
        data object Loading : StreamResult
        data class Loaded(val items: List<StudentExam>) : StreamResult
        data class Failed(val message: String) : StreamResult
    }

    private val examsStream = retryTrigger.flatMapLatest {
        repository.observeExams(uid)
            .map<List<StudentExam>, StreamResult> { StreamResult.Loaded(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                emit(StreamResult.Failed(root.localizedMessage ?: "Failed to load exams."))
            }
    }

    val uiState: StateFlow<ExamsUiState> = combine(
        examsStream,
        typeFilter,
    ) { streamResult, filter ->
        when (streamResult) {
            StreamResult.Loading -> ExamsUiState.Loading
            is StreamResult.Failed -> ExamsUiState.Error(streamResult.message)
            is StreamResult.Loaded -> {
                val all = streamResult.items.sortedBy { it.examAtMillis }
                if (all.isEmpty()) {
                    ExamsUiState.Empty
                } else {
                    val filtered = when (filter) {
                        ExamTypeFilter.ALL -> all
                        ExamTypeFilter.MIDTERM -> all.filter { it.resolvedExamType() == ExamType.MIDTERM }
                        ExamTypeFilter.FINAL -> all.filter { it.resolvedExamType() == ExamType.FINAL }
                    }
                    ExamsUiState.Success(
                        allExams = all,
                        filteredExams = filtered,
                        typeFilter = filter,
                        midtermCount = all.count { it.resolvedExamType() == ExamType.MIDTERM },
                        finalCount = all.count { it.resolvedExamType() == ExamType.FINAL },
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ExamsUiState.Loading,
    )

    private val _editorState = MutableStateFlow(ExamEditorState())
    val editorState: StateFlow<ExamEditorState> = _editorState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    fun selectTypeFilter(filter: ExamTypeFilter) {
        typeFilter.value = filter
    }

    fun retry() {
        retryTrigger.value += 1
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun openAddDialog(defaultCourse: String = "") {
        _editorState.value = ExamEditorState(
            isOpen = true,
            editingExamId = null,
            draftExamId = UUID.randomUUID().toString(),
            examType = ExamType.MIDTERM,
            courseName = defaultCourse,
            examAtMillis = clockMillis() + DEFAULT_EXAM_OFFSET_MS,
            preparationStatus = ExamPreparationStatus.NOT_STARTED,
        )
    }

    fun openEditDialog(exam: StudentExam) {
        _editorState.value = ExamEditorState(
            isOpen = true,
            editingExamId = exam.id,
            title = exam.title,
            examType = exam.resolvedExamType(),
            courseName = exam.courseName,
            examAtMillis = if (exam.examAtMillis > 0L) exam.examAtMillis else clockMillis() + DEFAULT_EXAM_OFFSET_MS,
            room = exam.room,
            preparationStatus = exam.resolvedPreparationStatus(),
            revisionChecklist = exam.revisionChecklist,
        )
    }

    fun dismissDialog() {
        if (_editorState.value.isSaving) return
        _editorState.value = ExamEditorState(isOpen = false)
    }

    fun onTitleChanged(value: String) {
        _editorState.update { it.copy(title = value, errorMessage = null) }
    }

    fun onExamTypeChanged(type: ExamType) {
        _editorState.update { it.copy(examType = type, errorMessage = null) }
    }

    fun onCourseChanged(value: String) {
        _editorState.update { it.copy(courseName = value, errorMessage = null) }
    }

    fun onExamAtChanged(millis: Long) {
        _editorState.update { it.copy(examAtMillis = millis, errorMessage = null) }
    }

    fun onRoomChanged(value: String) {
        _editorState.update { it.copy(room = value, errorMessage = null) }
    }

    fun onPreparationStatusChanged(status: ExamPreparationStatus) {
        _editorState.update { it.copy(preparationStatus = status, errorMessage = null) }
    }

    fun onChecklistTopicDraftChanged(value: String) {
        _editorState.update { it.copy(checklistTopicDraft = value, errorMessage = null) }
    }

    fun addChecklistTopicInEditor() {
        val state = _editorState.value
        val topic = state.checklistTopicDraft.trim()
        if (topic.isEmpty()) {
            _editorState.update { it.copy(errorMessage = "Enter a revision topic first.") }
            return
        }
        _editorState.update {
            if (it.revisionChecklist.size >= 30) {
                it.copy(errorMessage = "Maximum 30 revision checklist items supported.")
            } else {
                it.copy(
                    revisionChecklist = it.revisionChecklist + RevisionChecklistItem(
                        id = UUID.randomUUID().toString().take(12),
                        title = topic.take(160),
                        done = false,
                    ),
                    checklistTopicDraft = "",
                    errorMessage = null,
                )
            }
        }
    }

    fun toggleChecklistTopicInEditor(topicId: String) {
        _editorState.update { state ->
            state.copy(
                revisionChecklist = state.revisionChecklist.map { item ->
                    if (item.id == topicId) item.copy(done = !item.done) else item
                },
            )
        }
    }

    fun removeChecklistTopicInEditor(topicId: String) {
        _editorState.update { state ->
            state.copy(
                revisionChecklist = state.revisionChecklist.filterNot { it.id == topicId },
            )
        }
    }

    fun saveExam() {
        val form = _editorState.value
        if (form.isSaving) return

        val cleanTitle = form.title.trim()
        val cleanCourse = form.courseName.trim()
        val cleanRoom = form.room.trim()

        val error = when {
            cleanTitle.length !in 1..160 -> "Exam title is required (1–160 characters)."
            cleanCourse.length !in 1..100 -> "Course name/code is required (1–100 characters)."
            cleanRoom.length !in 1..80 -> "Exam room / hall is required (1–80 characters)."
            form.examAtMillis <= 0L -> "Select a valid exam date and time."
            else -> null
        }

        if (error != null) {
            _editorState.update { it.copy(errorMessage = error) }
            return
        }

        val request = SaveExamRequest(
            title = cleanTitle,
            examType = form.examType,
            courseName = cleanCourse,
            examAtMillis = form.examAtMillis,
            room = cleanRoom,
            preparationStatus = form.preparationStatus,
            revisionChecklist = form.revisionChecklist,
        )

        _editorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val editingId = form.editingExamId
            val result = if (editingId == null) {
                repository.addExamWithId(
                    uid = uid,
                    examId = form.draftExamId.ifBlank { UUID.randomUUID().toString() },
                    request = request,
                )
            } else {
                repository.updateExam(uid, editingId, request)
            }
            result.fold(
                onSuccess = {
                    _editorState.value = ExamEditorState(isOpen = false)
                    _actionMessage.value = if (editingId == null) {
                        syncCoordinator.formatActionMessage("${form.examType.label} exam saved to Cloud Firestore.")
                    } else {
                        syncCoordinator.formatActionMessage("Exam updated.")
                    }
                },
                onFailure = { failure ->
                    _editorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = failure.localizedMessage ?: "Failed to save exam.",
                        )
                    }
                },
            )
        }
    }

    fun toggleChecklistTopicOnCard(exam: StudentExam, topicId: String) {
        if (_isMutating.value || exam.id.isBlank()) return
        val updatedChecklist = exam.revisionChecklist.map { item ->
            if (item.id == topicId) item.copy(done = !item.done) else item
        }
        val nextStatus = when {
            updatedChecklist.isNotEmpty() && updatedChecklist.all { it.done } &&
                exam.resolvedPreparationStatus() != ExamPreparationStatus.COMPLETED ->
                ExamPreparationStatus.READY
            updatedChecklist.any { it.done } &&
                exam.resolvedPreparationStatus() == ExamPreparationStatus.NOT_STARTED ->
                ExamPreparationStatus.REVISING
            else -> exam.resolvedPreparationStatus()
        }
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.updateExamChecklistAndStatus(
                uid = uid,
                examId = exam.id,
                checklist = updatedChecklist,
                preparationStatus = nextStatus,
            )
            _isMutating.value = false
            result.onFailure { failure ->
                _actionMessage.value = failure.localizedMessage ?: "Could not update revision checklist."
            }
        }
    }

    fun updatePreparationStatus(exam: StudentExam, status: ExamPreparationStatus) {
        if (_isMutating.value || exam.id.isBlank()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.updateExamChecklistAndStatus(
                uid = uid,
                examId = exam.id,
                checklist = exam.revisionChecklist,
                preparationStatus = status,
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage("${exam.title}: ${status.label}.")
                },
                onFailure = { failure ->
                    _actionMessage.value = failure.localizedMessage ?: "Failed to update exam status."
                },
            )
        }
    }

    fun deleteExam(examId: String) {
        if (_isMutating.value || examId.isBlank()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.deleteExam(uid, examId)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = syncCoordinator.formatActionMessage("Exam removed.") },
                onFailure = { failure ->
                    _actionMessage.value = failure.localizedMessage ?: "Could not delete exam."
                },
            )
        }
    }

    companion object {
        private const val DEFAULT_EXAM_OFFSET_MS = 7L * 24L * 60L * 60L * 1000L

        fun factory(
            uid: String,
            repository: AcademicModulesRepository,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ExamsViewModel(
                    uid = uid,
                    repository = repository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
