package com.example.unimate.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.SaveCourseRequest
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.SubjectColorPalette
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface CoursesUiState {
    data object Loading : CoursesUiState
    data object Empty : CoursesUiState
    data class Success(
        val courses: List<StudentCourse>,
        val totalCreditHours: Double,
    ) : CoursesUiState

    data class Error(val message: String) : CoursesUiState
}

data class CourseEditorState(
    val isOpen: Boolean = false,
    val editingCourseId: String? = null,
    val draftCourseId: String = "",
    val courseCode: String = "",
    val courseName: String = "",
    val creditHoursText: String = "3.0",
    val instructor: String = "",
    val semester: String = "",
    val description: String = "",
    val classroom: String = "",
    val schedule: String = "",
    val attendedClassesText: String = "0",
    val totalClassesText: String = "0",
    val colorIndex: Long = 0L,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CoursesViewModel(
    private val uid: String,
    private val workspaceRepository: WorkspaceRepository,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<CoursesUiState> = retryTrigger.flatMapLatest {
        workspaceRepository.observeCourses(uid)
            .map<List<StudentCourse>, CoursesUiState> { courses ->
                if (courses.isEmpty()) {
                    CoursesUiState.Empty
                } else {
                    CoursesUiState.Success(
                        courses = courses,
                        totalCreditHours = courses.sumOf { it.creditHours },
                    )
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(CoursesUiState.Error(rootError.localizedMessage ?: "Failed to load courses."))
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CoursesUiState.Loading,
    )

    private val _editorState = MutableStateFlow(CourseEditorState())
    val editorState: StateFlow<CourseEditorState> = _editorState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    fun retry() {
        retryTrigger.value += 1
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun openAddCourseDialog(defaultSemester: String = "") {
        _editorState.value = CourseEditorState(
            isOpen = true,
            editingCourseId = null,
            draftCourseId = UUID.randomUUID().toString(),
            semester = defaultSemester,
            creditHoursText = "3.0",
            attendedClassesText = "0",
            totalClassesText = "0",
            colorIndex = 0L,
        )
    }

    fun openEditCourseDialog(course: StudentCourse) {
        _editorState.value = CourseEditorState(
            isOpen = true,
            editingCourseId = course.id,
            courseCode = course.courseCode,
            courseName = course.courseName,
            creditHoursText = course.creditHours.toString(),
            instructor = course.instructor,
            semester = course.semester,
            description = course.description,
            classroom = course.classroom,
            schedule = course.schedule,
            attendedClassesText = course.attendedClasses.toString(),
            totalClassesText = course.totalClasses.toString(),
            colorIndex = course.colorIndex,
        )
    }

    fun dismissEditorDialog() {
        if (_editorState.value.isSaving) return
        _editorState.value = CourseEditorState(isOpen = false)
    }

    fun onCourseCodeChanged(value: String) {
        _editorState.update { state ->
            val suggested = if (state.editingCourseId == null && state.courseName.isBlank()) {
                SubjectColorPalette.suggestedIndex(value)
            } else {
                state.colorIndex
            }
            state.copy(courseCode = value, colorIndex = suggested, errorMessage = null)
        }
    }

    fun onCourseNameChanged(value: String) {
        _editorState.update { state ->
            val suggested = if (state.editingCourseId == null) {
                SubjectColorPalette.suggestedIndex(value)
            } else {
                state.colorIndex
            }
            state.copy(courseName = value, colorIndex = suggested, errorMessage = null)
        }
    }

    fun onCreditHoursChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _editorState.update { it.copy(creditHoursText = filtered, errorMessage = null) }
    }

    fun onInstructorChanged(value: String) {
        _editorState.update { it.copy(instructor = value, errorMessage = null) }
    }

    fun onSemesterChanged(value: String) {
        _editorState.update { it.copy(semester = value, errorMessage = null) }
    }

    fun onDescriptionChanged(value: String) {
        _editorState.update { it.copy(description = value, errorMessage = null) }
    }

    fun onClassroomChanged(value: String) {
        _editorState.update { it.copy(classroom = value, errorMessage = null) }
    }

    fun onScheduleChanged(value: String) {
        _editorState.update { it.copy(schedule = value, errorMessage = null) }
    }

    fun onAttendedChanged(value: String) {
        _editorState.update {
            it.copy(attendedClassesText = value.filter { ch -> ch.isDigit() }, errorMessage = null)
        }
    }

    fun onTotalChanged(value: String) {
        _editorState.update {
            it.copy(totalClassesText = value.filter { ch -> ch.isDigit() }, errorMessage = null)
        }
    }

    fun onColorSelected(index: Long) {
        _editorState.update { it.copy(colorIndex = index.coerceIn(0L, 7L)) }
    }

    fun saveCourse() {
        val form = _editorState.value
        if (form.isSaving) return

        val code = form.courseCode.trim()
        val title = form.courseName.trim()
        val credits = form.creditHoursText.toDoubleOrNull()
        val instructor = form.instructor.trim()
        val semester = form.semester.trim()
        val description = form.description.trim()
        val classroom = form.classroom.trim()
        val schedule = form.schedule.trim()
        val attended = form.attendedClassesText.toLongOrNull() ?: 0L
        val total = form.totalClassesText.toLongOrNull() ?: 0L

        val validationError = when {
            code.length !in 1..24 -> "Course code is required (1–24 characters)."
            title.length !in 1..120 -> "Course title is required (1–120 characters)."
            credits == null || credits !in 0.5..12.0 -> "Credit hours must be between 0.5 and 12.0."
            instructor.length !in 1..100 -> "Instructor name is required (1–100 characters)."
            semester.length !in 1..60 -> "Semester is required (1–60 characters)."
            description.length > 500 -> "Description must be 500 characters or fewer."
            classroom.length !in 1..60 -> "Classroom is required (1–60 characters)."
            schedule.length !in 1..120 -> "Schedule is required (1–120 characters)."
            attended !in 0L..1000L || total !in 0L..1000L -> "Attendance counts must be 0–1000."
            attended > total -> "Attended classes cannot exceed total classes."
            else -> null
        }

        if (validationError != null) {
            _editorState.update { it.copy(errorMessage = validationError) }
            return
        }

        val request = SaveCourseRequest(
            courseCode = code,
            courseName = title,
            creditHours = credits!!,
            instructor = instructor,
            semester = semester,
            description = description,
            classroom = classroom,
            schedule = schedule,
            attendedClasses = attended,
            totalClasses = total,
            colorIndex = form.colorIndex,
        )

        _editorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val editingId = form.editingCourseId
            val result = if (editingId == null) {
                workspaceRepository.addCourseWithId(
                    uid = uid,
                    courseId = form.draftCourseId.ifBlank { UUID.randomUUID().toString() },
                    request = request,
                )
            } else {
                workspaceRepository.updateCourse(uid, editingId, request)
            }
            result.fold(
                onSuccess = {
                    _editorState.value = CourseEditorState(isOpen = false)
                    _actionMessage.value = if (editingId == null) {
                        syncCoordinator.formatActionMessage("Course added to Cloud Firestore.")
                    } else {
                        syncCoordinator.formatActionMessage("Course updated.")
                    }
                },
                onFailure = { error ->
                    _editorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Failed to save course.",
                        )
                    }
                },
            )
        }
    }

    fun markAttendance(course: StudentCourse, present: Boolean) {
        if (_isMutating.value || course.id.isBlank()) return
        val nextTotal = (course.totalClasses + 1L).coerceAtMost(1000L)
        val nextAttended = if (present) {
            (course.attendedClasses + 1L).coerceAtMost(nextTotal)
        } else {
            course.attendedClasses.coerceAtMost(nextTotal)
        }
        _isMutating.value = true
        viewModelScope.launch {
            val result = workspaceRepository.updateCourseAttendance(
                uid = uid,
                courseId = course.id,
                attendedClasses = nextAttended,
                totalClasses = nextTotal,
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        "Attendance updated for ${course.courseCode}.",
                    )
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Failed to update attendance."
                },
            )
        }
    }

    fun deleteCourse(courseId: String) {
        if (_isMutating.value || courseId.isBlank()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = workspaceRepository.deleteCourse(uid, courseId)
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage("Course removed.")
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Failed to remove course."
                },
            )
        }
    }

    companion object {
        fun factory(
            uid: String,
            workspaceRepository: WorkspaceRepository,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CoursesViewModel(
                    uid = uid,
                    workspaceRepository = workspaceRepository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
