package com.example.unimate.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.AcademicSettings
import com.example.unimate.data.model.AttendanceCalculator
import com.example.unimate.data.model.AttendanceLog
import com.example.unimate.data.model.AttendanceStatus
import com.example.unimate.data.model.CourseAttendanceSummary
import com.example.unimate.data.model.OverallAttendanceSummary
import com.example.unimate.data.model.SaveAcademicSettingsRequest
import com.example.unimate.data.model.SaveAttendanceLogRequest
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.repository.AttendanceAndGpaRepository
import com.example.unimate.data.repository.WorkspaceRepository
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.SyncCoordinator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AttendanceUiState {
    data object Loading : AttendanceUiState
    data class Empty(val settings: AcademicSettings) : AttendanceUiState
    data class Success(
        val courses: List<CourseAttendanceSummary>,
        val overall: OverallAttendanceSummary,
        val settings: AcademicSettings,
        val selectedCourseFilterId: String?,
        val selectedMonthFilter: String?,
        val availableMonthKeys: List<String>,
        val filteredLogs: List<AttendanceLog>,
    ) : AttendanceUiState

    data class Error(val message: String) : AttendanceUiState
}

data class AttendanceLogEditorState(
    val isOpen: Boolean = false,
    val draftLogId: String = "",
    val selectedCourseId: String = "",
    val status: AttendanceStatus = AttendanceStatus.PRESENT,
    val sessionDateMillis: Long = System.currentTimeMillis(),
    val notes: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

data class DirectTotalsEditorState(
    val isOpen: Boolean = false,
    val courseId: String = "",
    val courseLabel: String = "",
    val attendedInput: String = "",
    val totalInput: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

data class ThresholdEditorState(
    val isOpen: Boolean = false,
    val thresholdPercent: Int = AcademicSettings.DEFAULT_ATTENDANCE_THRESHOLD,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

private data class AttendanceStreamBundle(
    val courses: List<StudentCourse>,
    val logs: List<AttendanceLog>,
    val settings: AcademicSettings,
)

private sealed interface AttendanceLoadResult {
    data class Loaded(val bundle: AttendanceStreamBundle) : AttendanceLoadResult
    data class Failed(val message: String) : AttendanceLoadResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(
    private val uid: String,
    private val workspaceRepository: WorkspaceRepository,
    private val attendanceAndGpaRepository: AttendanceAndGpaRepository,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val courseFilter = MutableStateFlow<String?>(null)
    private val monthFilter = MutableStateFlow<String?>(null)

    private val _logEditorState = MutableStateFlow(AttendanceLogEditorState())
    val logEditorState: StateFlow<AttendanceLogEditorState> = _logEditorState

    private val _totalsEditorState = MutableStateFlow(DirectTotalsEditorState())
    val totalsEditorState: StateFlow<DirectTotalsEditorState> = _totalsEditorState

    private val _thresholdEditorState = MutableStateFlow(ThresholdEditorState())
    val thresholdEditorState: StateFlow<ThresholdEditorState> = _thresholdEditorState

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    private var latestSettings: AcademicSettings = AcademicSettings(uid = uid)
    private var latestCourses: List<StudentCourse> = emptyList()

    val uiState: StateFlow<AttendanceUiState> = retryTrigger
        .flatMapLatest {
            combine(
                workspaceRepository.observeCourses(uid),
                attendanceAndGpaRepository.observeAttendanceLogs(uid),
                attendanceAndGpaRepository.observeAcademicSettings(uid),
            ) { courses, logs, settings ->
                AttendanceLoadResult.Loaded(
                    AttendanceStreamBundle(
                        courses = courses,
                        logs = logs,
                        settings = settings,
                    ),
                ) as AttendanceLoadResult
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(
                    AttendanceLoadResult.Failed(
                        (firestoreError ?: error).localizedMessage
                            ?: "Unable to load attendance data.",
                    ),
                )
            }
        }
        .combine(combine(courseFilter, monthFilter) { c, m -> c to m }) { loadResult, (cFilter, mFilter) ->
            when (loadResult) {
                is AttendanceLoadResult.Failed -> AttendanceUiState.Error(loadResult.message)
                is AttendanceLoadResult.Loaded -> {
                    val bundle = loadResult.bundle
                    latestSettings = bundle.settings
                    latestCourses = bundle.courses
                    if (bundle.courses.isEmpty() && bundle.logs.isEmpty()) {
                        AttendanceUiState.Empty(settings = bundle.settings)
                    } else {
                        val threshold = bundle.settings.attendanceThresholdPercent.coerceIn(1, 100)
                        val logsByCourse = bundle.logs.groupBy { it.courseId }
                        val courseSummaries = bundle.courses.map { course ->
                            AttendanceCalculator.summarizeCourse(
                                course = course,
                                courseLogs = logsByCourse[course.id].orEmpty(),
                                thresholdPercent = threshold,
                            )
                        }
                        val overall = AttendanceCalculator.summarizeOverall(
                            courseSummaries = courseSummaries,
                            allLogs = bundle.logs,
                            thresholdPercent = threshold,
                        )
                        val availableMonths = bundle.logs
                            .map { it.monthKey.ifBlank { AttendanceCalculator.formatMonthKey(it.sessionDateMillis) } }
                            .distinct()
                            .sortedDescending()
                        val effectiveCourseFilter = cFilter?.takeIf { id ->
                            bundle.courses.any { it.id == id }
                        }
                        val effectiveMonthFilter = mFilter?.takeIf { key ->
                            availableMonths.contains(key)
                        }
                        val filteredLogs = bundle.logs.filter { log ->
                            val matchesCourse = effectiveCourseFilter == null || log.courseId == effectiveCourseFilter
                            val key = log.monthKey.ifBlank { AttendanceCalculator.formatMonthKey(log.sessionDateMillis) }
                            val matchesMonth = effectiveMonthFilter == null || key == effectiveMonthFilter
                            matchesCourse && matchesMonth
                        }.sortedByDescending { it.sessionDateMillis }

                        AttendanceUiState.Success(
                            courses = courseSummaries,
                            overall = overall,
                            settings = bundle.settings,
                            selectedCourseFilterId = effectiveCourseFilter,
                            selectedMonthFilter = effectiveMonthFilter,
                            availableMonthKeys = availableMonths,
                            filteredLogs = filteredLogs,
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AttendanceUiState.Loading,
        )

    fun selectCourseFilter(courseId: String?) {
        courseFilter.value = courseId
    }

    fun selectMonthFilter(monthKey: String?) {
        monthFilter.value = monthKey
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun retry() {
        retryTrigger.update { it + 1 }
    }

    fun openLogAttendanceDialog(
        course: StudentCourse? = null,
        defaultStatus: AttendanceStatus = AttendanceStatus.PRESENT,
    ) {
        val defaultCourseId = course?.id ?: latestCourses.firstOrNull()?.id.orEmpty()
        _logEditorState.value = AttendanceLogEditorState(
            isOpen = true,
            draftLogId = UUID.randomUUID().toString(),
            selectedCourseId = defaultCourseId,
            status = defaultStatus,
            sessionDateMillis = System.currentTimeMillis(),
            notes = "",
        )
    }

    fun dismissLogAttendanceDialog() {
        _logEditorState.value = AttendanceLogEditorState()
    }

    fun onLogCourseSelected(courseId: String) {
        _logEditorState.update { it.copy(selectedCourseId = courseId, errorMessage = null) }
    }

    fun onLogStatusSelected(status: AttendanceStatus) {
        _logEditorState.update { it.copy(status = status, errorMessage = null) }
    }

    fun onLogDateSelected(dateMillis: Long) {
        _logEditorState.update { it.copy(sessionDateMillis = dateMillis, errorMessage = null) }
    }

    fun onLogNotesChanged(notes: String) {
        _logEditorState.update { it.copy(notes = notes, errorMessage = null) }
    }

    fun saveManualAttendanceLog() {
        val form = _logEditorState.value
        if (form.isSaving) return
        val course = latestCourses.firstOrNull { it.id == form.selectedCourseId }
        if (course == null) {
            _logEditorState.update { it.copy(errorMessage = "Please add and select an enrolled course first.") }
            return
        }
        if (form.sessionDateMillis <= 0L) {
            _logEditorState.update { it.copy(errorMessage = "Select a valid session date.") }
            return
        }
        if (course.totalClasses >= 1000L) {
            _logEditorState.update { it.copy(errorMessage = "Maximum 1000 tracked classes per course reached.") }
            return
        }

        _logEditorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = attendanceAndGpaRepository.logAttendanceSessionWithId(
                uid = uid,
                logId = form.draftLogId.ifBlank { UUID.randomUUID().toString() },
                request = SaveAttendanceLogRequest(
                    courseId = course.id,
                    courseCode = course.courseCode,
                    courseName = course.courseName,
                    status = form.status,
                    sessionDateMillis = form.sessionDateMillis,
                    notes = form.notes,
                ),
            )
            result.fold(
                onSuccess = {
                    _logEditorState.value = AttendanceLogEditorState()
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        "Logged ${form.status.label} session for ${course.courseCode}.",
                    )
                },
                onFailure = { error ->
                    _logEditorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Failed to record attendance.",
                        )
                    }
                },
            )
        }
    }

    fun toggleLogStatus(log: AttendanceLog) {
        val nextStatus = if (log.resolvedStatus() == AttendanceStatus.PRESENT) {
            AttendanceStatus.ABSENT
        } else {
            AttendanceStatus.PRESENT
        }
        viewModelScope.launch {
            val result = attendanceAndGpaRepository.updateAttendanceLogStatus(
                uid = uid,
                log = log,
                newStatus = nextStatus,
            )
            result.fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        "Updated ${log.courseCode} session to ${nextStatus.label}.",
                    )
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Could not update session log."
                },
            )
        }
    }

    fun deleteLog(log: AttendanceLog) {
        viewModelScope.launch {
            val result = attendanceAndGpaRepository.deleteAttendanceLog(uid, log)
            result.fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        "Removed attendance entry for ${log.courseCode}.",
                    )
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Could not delete session log."
                },
            )
        }
    }

    fun openDirectTotalsDialog(course: StudentCourse) {
        _totalsEditorState.value = DirectTotalsEditorState(
            isOpen = true,
            courseId = course.id,
            courseLabel = "${course.courseCode} • ${course.courseName}",
            attendedInput = course.attendedClasses.toString(),
            totalInput = course.totalClasses.toString(),
        )
    }

    fun dismissDirectTotalsDialog() {
        _totalsEditorState.value = DirectTotalsEditorState()
    }

    fun onDirectAttendedChanged(value: String) {
        _totalsEditorState.update { it.copy(attendedInput = value, errorMessage = null) }
    }

    fun onDirectTotalChanged(value: String) {
        _totalsEditorState.update { it.copy(totalInput = value, errorMessage = null) }
    }

    fun saveDirectCourseTotals() {
        val form = _totalsEditorState.value
        if (form.isSaving) return
        val attended = form.attendedInput.trim().toLongOrNull()
        val total = form.totalInput.trim().toLongOrNull()
        if (attended == null || total == null || attended < 0L || total < 0L || total > 1000L) {
            _totalsEditorState.update {
                it.copy(errorMessage = "Enter valid counts between 0 and 1000.")
            }
            return
        }
        if (attended > total) {
            _totalsEditorState.update {
                it.copy(errorMessage = "Attended classes cannot exceed total classes.")
            }
            return
        }

        _totalsEditorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = workspaceRepository.updateCourseAttendance(
                uid = uid,
                courseId = form.courseId,
                attendedClasses = attended,
                totalClasses = total,
            )
            result.fold(
                onSuccess = {
                    _totalsEditorState.value = DirectTotalsEditorState()
                    _actionMessage.value = syncCoordinator.formatActionMessage("Updated course attendance totals.")
                },
                onFailure = { error ->
                    _totalsEditorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Failed to update course totals.",
                        )
                    }
                },
            )
        }
    }

    fun openThresholdDialog() {
        _thresholdEditorState.value = ThresholdEditorState(
            isOpen = true,
            thresholdPercent = latestSettings.attendanceThresholdPercent.coerceIn(1, 100),
        )
    }

    fun dismissThresholdDialog() {
        _thresholdEditorState.value = ThresholdEditorState()
    }

    fun onThresholdChanged(percent: Int) {
        _thresholdEditorState.update {
            it.copy(thresholdPercent = percent.coerceIn(1, 100), errorMessage = null)
        }
    }

    fun saveThresholdSettings() {
        val form = _thresholdEditorState.value
        if (form.isSaving) return
        if (form.thresholdPercent !in 1..100) {
            _thresholdEditorState.update {
                it.copy(errorMessage = "Threshold must be between 1% and 100%.")
            }
            return
        }

        _thresholdEditorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = attendanceAndGpaRepository.saveAcademicSettings(
                uid = uid,
                request = SaveAcademicSettingsRequest(
                    attendanceThresholdPercent = form.thresholdPercent,
                    scaleName = latestSettings.scaleName,
                    customScale = latestSettings.customScale,
                    gradingScale = latestSettings.gradingScale,
                ),
            )
            result.fold(
                onSuccess = {
                    _thresholdEditorState.value = ThresholdEditorState()
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        "Attendance threshold updated to ${form.thresholdPercent}%.",
                    )
                },
                onFailure = { error ->
                    _thresholdEditorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Could not save threshold.",
                        )
                    }
                },
            )
        }
    }

    companion object {
        fun factory(
            uid: String,
            workspaceRepository: WorkspaceRepository,
            attendanceAndGpaRepository: AttendanceAndGpaRepository,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AttendanceViewModel(
                    uid = uid,
                    workspaceRepository = workspaceRepository,
                    attendanceAndGpaRepository = attendanceAndGpaRepository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
