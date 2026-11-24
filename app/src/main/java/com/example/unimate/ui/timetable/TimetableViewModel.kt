package com.example.unimate.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.DayOfWeekOption
import com.example.unimate.data.model.SaveTimetableClassRequest
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.SubjectColorPalette
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.data.model.TimetableConflictDetector
import com.example.unimate.data.repository.WorkspaceRepository
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

enum class TimetableViewMode(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly (Mon–Sun)"),
}

sealed interface TimetableUiState {
    data object Loading : TimetableUiState
    data class Empty(
        val selectedDay: DayOfWeekOption,
        val viewMode: TimetableViewMode,
    ) : TimetableUiState

    data class Success(
        val allClasses: List<TimetableClass>,
        val selectedDay: DayOfWeekOption,
        val viewMode: TimetableViewMode,
        val conflictMap: Map<String, List<TimetableClass>>,
        val totalConflictingClasses: Int,
    ) : TimetableUiState

    data class Error(val message: String) : TimetableUiState
}

data class TimetableEditorState(
    val isOpen: Boolean = false,
    val editingClassId: String? = null,
    val draftClassId: String = "",
    val dayOfWeek: DayOfWeekOption = DayOfWeekOption.today(),
    val subjectName: String = "",
    val courseCode: String = "",
    val instructor: String = "",
    val classroom: String = "",
    val startTimeMinutes: Long = 540L,
    val endTimeMinutes: Long = 630L,
    val colorIndex: Long = 0L,
    val liveConflicts: List<TimetableClass> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel(
    private val uid: String,
    private val workspaceRepository: WorkspaceRepository,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val selectedDay = MutableStateFlow(DayOfWeekOption.today())
    private val viewMode = MutableStateFlow(TimetableViewMode.DAILY)

    private sealed interface TimetableStreamResult {
        data object Loading : TimetableStreamResult
        data class Loaded(val classes: List<TimetableClass>) : TimetableStreamResult
        data class Failed(val message: String) : TimetableStreamResult
    }

    private val timetableStream = retryTrigger.flatMapLatest {
        workspaceRepository.observeTimetable(uid)
            .map<List<TimetableClass>, TimetableStreamResult> { list ->
                TimetableStreamResult.Loaded(
                    list.sortedWith(
                        compareBy<TimetableClass> { it.resolvedDay().ordinal }
                            .thenBy { it.startTimeMinutes }
                            .thenBy { it.subjectName },
                    ),
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(TimetableStreamResult.Failed(rootError.localizedMessage ?: "Failed to load timetable."))
            }
    }

    val uiState: StateFlow<TimetableUiState> = combine(
        timetableStream,
        selectedDay,
        viewMode,
    ) { streamResult, day, mode ->
        when (streamResult) {
            TimetableStreamResult.Loading -> TimetableUiState.Loading
            is TimetableStreamResult.Failed -> TimetableUiState.Error(streamResult.message)
            is TimetableStreamResult.Loaded -> {
                if (streamResult.classes.isEmpty()) {
                    TimetableUiState.Empty(selectedDay = day, viewMode = mode)
                } else {
                    val conflicts = TimetableConflictDetector.computeConflictMap(streamResult.classes)
                    TimetableUiState.Success(
                        allClasses = streamResult.classes,
                        selectedDay = day,
                        viewMode = mode,
                        conflictMap = conflicts,
                        totalConflictingClasses = conflicts.size,
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TimetableUiState.Loading,
    )

    private val _editorState = MutableStateFlow(TimetableEditorState())
    val editorState: StateFlow<TimetableEditorState> = _editorState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    fun selectDay(day: DayOfWeekOption) {
        selectedDay.value = day
    }

    fun selectViewMode(mode: TimetableViewMode) {
        viewMode.value = mode
    }

    fun retry() {
        retryTrigger.value += 1
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun openAddClassDialog(prefillDay: DayOfWeekOption = selectedDay.value) {
        val currentClasses = currentClassesSnapshot()
        val draft = TimetableEditorState(
            isOpen = true,
            editingClassId = null,
            draftClassId = UUID.randomUUID().toString(),
            dayOfWeek = prefillDay,
            startTimeMinutes = 540L,
            endTimeMinutes = 630L,
            colorIndex = 0L,
        )
        _editorState.value = draft.withComputedConflicts(currentClasses)
    }

    fun openEditClassDialog(item: TimetableClass) {
        val currentClasses = currentClassesSnapshot()
        val draft = TimetableEditorState(
            isOpen = true,
            editingClassId = item.id,
            dayOfWeek = item.resolvedDay(),
            subjectName = item.subjectName,
            courseCode = item.courseCode,
            instructor = item.instructor,
            classroom = item.classroom,
            startTimeMinutes = item.startTimeMinutes,
            endTimeMinutes = item.endTimeMinutes,
            colorIndex = item.colorIndex,
        )
        _editorState.value = draft.withComputedConflicts(currentClasses)
    }

    fun dismissEditorDialog() {
        if (_editorState.value.isSaving) return
        _editorState.value = TimetableEditorState(isOpen = false)
    }

    fun applyCourseTemplate(course: StudentCourse) {
        _editorState.update { state ->
            state.copy(
                subjectName = course.courseName,
                courseCode = course.courseCode,
                instructor = course.instructor,
                classroom = course.classroom,
                colorIndex = course.colorIndex,
                errorMessage = null,
            )
        }
    }

    fun onDayChanged(day: DayOfWeekOption) {
        val classes = currentClassesSnapshot()
        _editorState.update {
            it.copy(dayOfWeek = day, errorMessage = null).withComputedConflicts(classes)
        }
    }

    fun onSubjectNameChanged(value: String) {
        _editorState.update { state ->
            val suggested = if (state.editingClassId == null) {
                SubjectColorPalette.suggestedIndex(value)
            } else {
                state.colorIndex
            }
            state.copy(
                subjectName = value,
                colorIndex = suggested,
                errorMessage = null,
            )
        }
    }

    fun onCourseCodeChanged(value: String) {
        _editorState.update { it.copy(courseCode = value, errorMessage = null) }
    }

    fun onInstructorChanged(value: String) {
        _editorState.update { it.copy(instructor = value, errorMessage = null) }
    }

    fun onClassroomChanged(value: String) {
        _editorState.update { it.copy(classroom = value, errorMessage = null) }
    }

    fun onStartTimeChanged(minutes: Long) {
        val classes = currentClassesSnapshot()
        _editorState.update { state ->
            val clampedStart = minutes.coerceIn(0L, 1425L)
            val adjustedEnd = if (state.endTimeMinutes <= clampedStart) {
                (clampedStart + 60L).coerceAtMost(1439L)
            } else {
                state.endTimeMinutes
            }
            state.copy(
                startTimeMinutes = clampedStart,
                endTimeMinutes = adjustedEnd,
                errorMessage = null,
            ).withComputedConflicts(classes)
        }
    }

    fun onEndTimeChanged(minutes: Long) {
        val classes = currentClassesSnapshot()
        _editorState.update { state ->
            state.copy(
                endTimeMinutes = minutes.coerceIn(1L, 1439L),
                errorMessage = null,
            ).withComputedConflicts(classes)
        }
    }

    fun onColorSelected(index: Long) {
        _editorState.update { it.copy(colorIndex = index.coerceIn(0L, 7L)) }
    }

    fun saveClass() {
        val form = _editorState.value
        if (form.isSaving) return

        val subject = form.subjectName.trim()
        val code = form.courseCode.trim()
        val instructor = form.instructor.trim()
        val classroom = form.classroom.trim()

        val validationError = when {
            subject.length !in 1..120 -> "Subject name is required (1–120 characters)."
            code.length !in 1..24 -> "Course code is required (1–24 characters)."
            instructor.length !in 1..100 -> "Instructor name is required (1–100 characters)."
            classroom.length !in 1..60 -> "Classroom is required (1–60 characters)."
            form.endTimeMinutes <= form.startTimeMinutes -> "End time must be after start time."
            else -> null
        }

        if (validationError != null) {
            _editorState.update { it.copy(errorMessage = validationError) }
            return
        }

        val request = SaveTimetableClassRequest(
            dayOfWeek = form.dayOfWeek,
            subjectName = subject,
            courseCode = code,
            instructor = instructor,
            classroom = classroom,
            startTimeMinutes = form.startTimeMinutes,
            endTimeMinutes = form.endTimeMinutes,
            colorIndex = form.colorIndex,
        )

        _editorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val editingId = form.editingClassId
            val result = if (editingId == null) {
                workspaceRepository.addTimetableClassWithId(
                    uid = uid,
                    classId = form.draftClassId.ifBlank { UUID.randomUUID().toString() },
                    request = request,
                )
            } else {
                workspaceRepository.updateTimetableClass(uid, editingId, request)
            }
            result.fold(
                onSuccess = {
                    val conflictCount = form.liveConflicts.size
                    selectedDay.value = form.dayOfWeek
                    _editorState.value = TimetableEditorState(isOpen = false)
                    val baseMessage = when {
                        conflictCount > 0 && editingId == null ->
                            "Class added with $conflictCount schedule conflict(s) flagged."
                        conflictCount > 0 ->
                            "Class updated with $conflictCount schedule conflict(s) flagged."
                        editingId == null -> "Class added to timetable."
                        else -> "Class updated."
                    }
                    _actionMessage.value = syncCoordinator.formatActionMessage(baseMessage)
                },
                onFailure = { error ->
                    _editorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Failed to save class.",
                        )
                    }
                },
            )
        }
    }

    fun deleteClass(classId: String) {
        if (_isMutating.value || classId.isBlank()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = workspaceRepository.deleteTimetableClass(uid, classId)
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage("Class removed from timetable.")
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Could not delete class."
                },
            )
        }
    }

    private fun currentClassesSnapshot(): List<TimetableClass> {
        return (uiState.value as? TimetableUiState.Success)?.allClasses.orEmpty()
    }

    private fun TimetableEditorState.withComputedConflicts(
        existing: List<TimetableClass>,
    ): TimetableEditorState {
        val conflicts = TimetableConflictDetector.findConflicts(
            day = dayOfWeek,
            startTimeMinutes = startTimeMinutes,
            endTimeMinutes = endTimeMinutes,
            excludeClassId = editingClassId,
            existingClasses = existing,
        )
        return copy(liveConflicts = conflicts)
    }

    companion object {
        fun factory(
            uid: String,
            workspaceRepository: WorkspaceRepository,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TimetableViewModel(
                    uid = uid,
                    workspaceRepository = workspaceRepository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
