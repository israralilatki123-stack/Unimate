package com.example.unimate.ui.quizzes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.QuizPreparationStatus
import com.example.unimate.data.model.QuizSectionFilter
import com.example.unimate.data.model.SaveQuizRequest
import com.example.unimate.data.model.StudentQuiz
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

sealed interface QuizzesUiState {
    data object Loading : QuizzesUiState
    data object Empty : QuizzesUiState
    data class Success(
        val allQuizzes: List<StudentQuiz>,
        val upcomingQuizzes: List<StudentQuiz>,
        val historyQuizzes: List<StudentQuiz>,
        val visibleQuizzes: List<StudentQuiz>,
        val activeSection: QuizSectionFilter,
    ) : QuizzesUiState

    data class Error(val message: String) : QuizzesUiState
}

data class QuizEditorState(
    val isOpen: Boolean = false,
    val editingQuizId: String? = null,
    val draftQuizId: String = "",
    val title: String = "",
    val courseName: String = "",
    val scheduledAtMillis: Long = 0L,
    val totalMarksText: String = "10",
    val scoreRecorded: Boolean = false,
    val obtainedMarksText: String = "",
    val preparationStatus: QuizPreparationStatus = QuizPreparationStatus.NOT_STARTED,
    val notes: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class QuizzesViewModel(
    private val uid: String,
    private val repository: AcademicModulesRepository,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val activeSection = MutableStateFlow(QuizSectionFilter.UPCOMING)

    private sealed interface StreamResult {
        data object Loading : StreamResult
        data class Loaded(val items: List<StudentQuiz>) : StreamResult
        data class Failed(val message: String) : StreamResult
    }

    private val quizzesStream = retryTrigger.flatMapLatest {
        repository.observeQuizzes(uid)
            .map<List<StudentQuiz>, StreamResult> { StreamResult.Loaded(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                emit(StreamResult.Failed(root.localizedMessage ?: "Failed to load quizzes."))
            }
    }

    val uiState: StateFlow<QuizzesUiState> = combine(
        quizzesStream,
        activeSection,
    ) { streamResult, section ->
        when (streamResult) {
            StreamResult.Loading -> QuizzesUiState.Loading
            is StreamResult.Failed -> QuizzesUiState.Error(streamResult.message)
            is StreamResult.Loaded -> {
                val all = streamResult.items
                if (all.isEmpty()) {
                    QuizzesUiState.Empty
                } else {
                    val now = clockMillis()
                    val upcoming = all
                        .filterNot { it.isHistory(now) }
                        .sortedBy { it.scheduledAtMillis }
                    val history = all
                        .filter { it.isHistory(now) }
                        .sortedByDescending { it.scheduledAtMillis }
                    val visible = when (section) {
                        QuizSectionFilter.UPCOMING -> upcoming
                        QuizSectionFilter.HISTORY -> history
                        QuizSectionFilter.ALL -> upcoming + history
                    }
                    QuizzesUiState.Success(
                        allQuizzes = all,
                        upcomingQuizzes = upcoming,
                        historyQuizzes = history,
                        visibleQuizzes = visible,
                        activeSection = section,
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = QuizzesUiState.Loading,
    )

    private val _editorState = MutableStateFlow(QuizEditorState())
    val editorState: StateFlow<QuizEditorState> = _editorState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    fun selectSection(section: QuizSectionFilter) {
        activeSection.value = section
    }

    fun retry() {
        retryTrigger.value += 1
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun openAddDialog(defaultCourse: String = "") {
        _editorState.value = QuizEditorState(
            isOpen = true,
            editingQuizId = null,
            draftQuizId = UUID.randomUUID().toString(),
            courseName = defaultCourse,
            scheduledAtMillis = clockMillis() + DEFAULT_QUIZ_OFFSET_MS,
            totalMarksText = "10",
            scoreRecorded = false,
            obtainedMarksText = "",
            preparationStatus = QuizPreparationStatus.NOT_STARTED,
        )
    }

    fun openEditDialog(quiz: StudentQuiz) {
        _editorState.value = QuizEditorState(
            isOpen = true,
            editingQuizId = quiz.id,
            title = quiz.title,
            courseName = quiz.courseName,
            scheduledAtMillis = if (quiz.scheduledAtMillis > 0L) quiz.scheduledAtMillis else clockMillis() + DEFAULT_QUIZ_OFFSET_MS,
            totalMarksText = formatMarks(quiz.totalMarks),
            scoreRecorded = quiz.scoreRecorded,
            obtainedMarksText = if (quiz.scoreRecorded) formatMarks(quiz.obtainedMarks) else "",
            preparationStatus = quiz.resolvedPreparationStatus(),
            notes = quiz.notes,
        )
    }

    fun dismissDialog() {
        if (_editorState.value.isSaving) return
        _editorState.value = QuizEditorState(isOpen = false)
    }

    fun onTitleChanged(value: String) {
        _editorState.update { it.copy(title = value, errorMessage = null) }
    }

    fun onCourseChanged(value: String) {
        _editorState.update { it.copy(courseName = value, errorMessage = null) }
    }

    fun onScheduledAtChanged(millis: Long) {
        _editorState.update { it.copy(scheduledAtMillis = millis, errorMessage = null) }
    }

    fun onTotalMarksChanged(value: String) {
        _editorState.update {
            it.copy(totalMarksText = value.filter { ch -> ch.isDigit() || ch == '.' }, errorMessage = null)
        }
    }

    fun onScoreRecordedChanged(recorded: Boolean) {
        _editorState.update { state ->
            state.copy(
                scoreRecorded = recorded,
                preparationStatus = if (recorded) QuizPreparationStatus.COMPLETED else state.preparationStatus,
                errorMessage = null,
            )
        }
    }

    fun onObtainedMarksChanged(value: String) {
        _editorState.update {
            it.copy(obtainedMarksText = value.filter { ch -> ch.isDigit() || ch == '.' }, errorMessage = null)
        }
    }

    fun onPreparationStatusChanged(status: QuizPreparationStatus) {
        _editorState.update { it.copy(preparationStatus = status, errorMessage = null) }
    }

    fun onNotesChanged(value: String) {
        _editorState.update { it.copy(notes = value, errorMessage = null) }
    }

    fun saveQuiz() {
        val form = _editorState.value
        if (form.isSaving) return

        val cleanTitle = form.title.trim()
        val cleanCourse = form.courseName.trim()
        val total = form.totalMarksText.toDoubleOrNull()
        val obtained = if (form.scoreRecorded) form.obtainedMarksText.toDoubleOrNull() else 0.0

        val error = when {
            cleanTitle.length !in 1..160 -> "Quiz title is required (1–160 characters)."
            cleanCourse.length !in 1..100 -> "Course name/code is required (1–100 characters)."
            form.scheduledAtMillis <= 0L -> "Select a valid quiz date and time."
            total == null || total !in 0.5..1000.0 -> "Total marks must be between 0.5 and 1000."
            form.scoreRecorded && (obtained == null || obtained < 0.0 || obtained > total) ->
                "Obtained marks must be between 0 and $total."
            form.notes.trim().length > 500 -> "Notes must be 500 characters or fewer."
            else -> null
        }

        if (error != null) {
            _editorState.update { it.copy(errorMessage = error) }
            return
        }

        val request = SaveQuizRequest(
            title = cleanTitle,
            courseName = cleanCourse,
            scheduledAtMillis = form.scheduledAtMillis,
            totalMarks = total!!,
            obtainedMarks = obtained ?: 0.0,
            scoreRecorded = form.scoreRecorded,
            preparationStatus = form.preparationStatus,
            notes = form.notes.trim(),
        )

        _editorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val editingId = form.editingQuizId
            val result = if (editingId == null) {
                repository.addQuizWithId(
                    uid = uid,
                    quizId = form.draftQuizId.ifBlank { UUID.randomUUID().toString() },
                    request = request,
                )
            } else {
                repository.updateQuiz(uid, editingId, request)
            }
            result.fold(
                onSuccess = {
                    _editorState.value = QuizEditorState(isOpen = false)
                    _actionMessage.value = if (editingId == null) {
                        syncCoordinator.formatActionMessage("Quiz added to Cloud Firestore.")
                    } else {
                        syncCoordinator.formatActionMessage("Quiz updated.")
                    }
                },
                onFailure = { failure ->
                    _editorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = failure.localizedMessage ?: "Failed to save quiz.",
                        )
                    }
                },
            )
        }
    }

    fun updatePreparationStatus(quiz: StudentQuiz, nextStatus: QuizPreparationStatus) {
        if (_isMutating.value || quiz.id.isBlank()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.updateQuizStatusAndScore(
                uid = uid,
                quizId = quiz.id,
                preparationStatus = nextStatus,
                scoreRecorded = quiz.scoreRecorded,
                obtainedMarks = quiz.obtainedMarks,
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        "${quiz.title}: ${nextStatus.label}.",
                    )
                },
                onFailure = { failure ->
                    _actionMessage.value = failure.localizedMessage ?: "Failed to update quiz status."
                },
            )
        }
    }

    fun deleteQuiz(quizId: String) {
        if (_isMutating.value || quizId.isBlank()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.deleteQuiz(uid, quizId)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = syncCoordinator.formatActionMessage("Quiz deleted.") },
                onFailure = { failure ->
                    _actionMessage.value = failure.localizedMessage ?: "Could not delete quiz."
                },
            )
        }
    }

    private fun formatMarks(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    companion object {
        private const val DEFAULT_QUIZ_OFFSET_MS = 3L * 24L * 60L * 60L * 1000L

        fun factory(
            uid: String,
            repository: AcademicModulesRepository,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return QuizzesViewModel(
                    uid = uid,
                    repository = repository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
