package com.example.unimate.ui.gpa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.AcademicGpaCalculator
import com.example.unimate.data.model.AcademicSettings
import com.example.unimate.data.model.CgpaDashboardSummary
import com.example.unimate.data.model.DefaultGradeScales
import com.example.unimate.data.model.GradeScaleItem
import com.example.unimate.data.model.SaveAcademicSettingsRequest
import com.example.unimate.data.model.SaveSemesterGpaRequest
import com.example.unimate.data.model.SemesterCourseGrade
import com.example.unimate.data.model.SemesterEntryMode
import com.example.unimate.data.model.SemesterGpaRecord
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
import java.util.Locale
import java.util.UUID

sealed interface GpaCgpaUiState {
    data object Loading : GpaCgpaUiState
    data class Empty(
        val settings: AcademicSettings,
        val enrolledCourses: List<StudentCourse>,
    ) : GpaCgpaUiState

    data class Success(
        val summary: CgpaDashboardSummary,
        val settings: AcademicSettings,
        val enrolledCourses: List<StudentCourse>,
        val selectedSemesterId: String?,
    ) : GpaCgpaUiState

    data class Error(val message: String) : GpaCgpaUiState
}

data class SemesterEditorState(
    val isOpen: Boolean = false,
    val editingRecordId: String? = null,
    val draftRecordId: String = "",
    val semesterName: String = "",
    val orderIndexInput: String = "1",
    val activeSemester: Boolean = true,
    val entryMode: SemesterEntryMode = SemesterEntryMode.COURSES,
    val summaryCreditsInput: String = "",
    val summaryGpaInput: String = "",
    val courses: List<SemesterCourseGrade> = emptyList(),
    val draftCourseName: String = "",
    val draftCourseCode: String = "",
    val draftCreditHours: String = "3.0",
    val draftLetterGrade: String = "A",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    fun livePreviewGpa(): Double = when (entryMode) {
        SemesterEntryMode.SUMMARY -> {
            val cr = summaryCreditsInput.trim().toDoubleOrNull() ?: 0.0
            val gpa = summaryGpaInput.trim().toDoubleOrNull() ?: 0.0
            if (cr > 0.0 && gpa >= 0.0) gpa else 0.0
        }
        SemesterEntryMode.COURSES -> AcademicGpaCalculator.calculateCourseListGpa(courses)
    }

    fun livePreviewCredits(): Double = when (entryMode) {
        SemesterEntryMode.SUMMARY -> summaryCreditsInput.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        SemesterEntryMode.COURSES -> AcademicGpaCalculator.calculateCourseListCredits(courses)
    }
}

data class ScaleRowDraft(
    val id: String = UUID.randomUUID().toString(),
    val letter: String,
    val pointsInput: String,
)

data class GradingScaleEditorState(
    val isOpen: Boolean = false,
    val scaleName: String = AcademicSettings.DEFAULT_SCALE_NAME,
    val rows: List<ScaleRowDraft> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

private data class GpaStreamBundle(
    val courses: List<StudentCourse>,
    val semesters: List<SemesterGpaRecord>,
    val settings: AcademicSettings,
)

private sealed interface GpaLoadResult {
    data class Loaded(val bundle: GpaStreamBundle) : GpaLoadResult
    data class Failed(val message: String) : GpaLoadResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class GpaCgpaViewModel(
    private val uid: String,
    private val workspaceRepository: WorkspaceRepository,
    private val attendanceAndGpaRepository: AttendanceAndGpaRepository,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val selectedSemesterId = MutableStateFlow<String?>(null)

    private val _semesterEditorState = MutableStateFlow(SemesterEditorState())
    val semesterEditorState: StateFlow<SemesterEditorState> = _semesterEditorState

    private val _scaleEditorState = MutableStateFlow(GradingScaleEditorState())
    val scaleEditorState: StateFlow<GradingScaleEditorState> = _scaleEditorState

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    private var latestSettings: AcademicSettings = AcademicSettings(uid = uid)
    private var latestSemesters: List<SemesterGpaRecord> = emptyList()
    private var latestEnrolledCourses: List<StudentCourse> = emptyList()

    val uiState: StateFlow<GpaCgpaUiState> = retryTrigger
        .flatMapLatest {
            combine(
                workspaceRepository.observeCourses(uid),
                attendanceAndGpaRepository.observeSemesterGpaRecords(uid),
                attendanceAndGpaRepository.observeAcademicSettings(uid),
            ) { courses, semesters, settings ->
                GpaLoadResult.Loaded(
                    GpaStreamBundle(
                        courses = courses,
                        semesters = semesters,
                        settings = settings,
                    ),
                ) as GpaLoadResult
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(
                    GpaLoadResult.Failed(
                        (firestoreError ?: error).localizedMessage
                            ?: "Unable to load GPA and CGPA records.",
                    ),
                )
            }
        }
        .combine(selectedSemesterId) { loadResult, selectedId ->
            when (loadResult) {
                is GpaLoadResult.Failed -> GpaCgpaUiState.Error(loadResult.message)
                is GpaLoadResult.Loaded -> {
                    val bundle = loadResult.bundle
                    latestSettings = bundle.settings
                    latestSemesters = bundle.semesters
                    latestEnrolledCourses = bundle.courses
                    if (bundle.semesters.isEmpty()) {
                        GpaCgpaUiState.Empty(
                            settings = bundle.settings,
                            enrolledCourses = bundle.courses,
                        )
                    } else {
                        val summary = AcademicGpaCalculator.calculateCgpaSummary(
                            semesters = bundle.semesters,
                            scale = bundle.settings.gradingScale,
                        )
                        val validSelectedId = selectedId?.takeIf { id ->
                            summary.semesterMetrics.any { it.semester.id == id }
                        } ?: summary.activeSemesterMetrics?.semester?.id

                        GpaCgpaUiState.Success(
                            summary = summary,
                            settings = bundle.settings,
                            enrolledCourses = bundle.courses,
                            selectedSemesterId = validSelectedId,
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = GpaCgpaUiState.Loading,
        )

    fun selectSemester(semesterId: String) {
        selectedSemesterId.value = semesterId
    }

    fun retry() {
        retryTrigger.update { it + 1 }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun openAddSemesterDialog(entryMode: SemesterEntryMode = SemesterEntryMode.COURSES) {
        val nextOrder = (latestSemesters.maxOfOrNull { it.orderIndex } ?: 0) + 1
        val defaultGrade = latestSettings.gradingScale.firstOrNull()?.letter ?: "A"
        _semesterEditorState.value = SemesterEditorState(
            isOpen = true,
            draftRecordId = UUID.randomUUID().toString(),
            semesterName = if (entryMode == SemesterEntryMode.COURSES) {
                "Current Semester ($nextOrder)"
            } else {
                "Previous Semester $nextOrder"
            },
            orderIndexInput = nextOrder.toString(),
            activeSemester = entryMode == SemesterEntryMode.COURSES,
            entryMode = entryMode,
            draftLetterGrade = defaultGrade,
        )
    }

    fun openEditSemesterDialog(record: SemesterGpaRecord) {
        val defaultGrade = latestSettings.gradingScale.firstOrNull()?.letter ?: "A"
        _semesterEditorState.value = SemesterEditorState(
            isOpen = true,
            editingRecordId = record.id,
            semesterName = record.semesterName,
            orderIndexInput = record.orderIndex.toString(),
            activeSemester = record.activeSemester,
            entryMode = record.resolvedEntryMode(),
            summaryCreditsInput = if (record.summaryCredits > 0.0) {
                String.format(Locale.US, "%.1f", record.summaryCredits)
            } else {
                ""
            },
            summaryGpaInput = if (record.summaryGpa > 0.0) {
                String.format(Locale.US, "%.2f", record.summaryGpa)
            } else {
                ""
            },
            courses = record.courses,
            draftLetterGrade = defaultGrade,
        )
    }

    fun dismissSemesterDialog() {
        _semesterEditorState.value = SemesterEditorState()
    }

    fun onSemesterNameChanged(value: String) {
        _semesterEditorState.update { it.copy(semesterName = value, errorMessage = null) }
    }

    fun onSemesterOrderChanged(value: String) {
        _semesterEditorState.update { it.copy(orderIndexInput = value, errorMessage = null) }
    }

    fun onActiveSemesterChanged(active: Boolean) {
        _semesterEditorState.update { it.copy(activeSemester = active, errorMessage = null) }
    }

    fun onSemesterEntryModeChanged(mode: SemesterEntryMode) {
        _semesterEditorState.update { it.copy(entryMode = mode, errorMessage = null) }
    }

    fun onSummaryCreditsChanged(value: String) {
        _semesterEditorState.update { it.copy(summaryCreditsInput = value, errorMessage = null) }
    }

    fun onSummaryGpaChanged(value: String) {
        _semesterEditorState.update { it.copy(summaryGpaInput = value, errorMessage = null) }
    }

    fun onDraftCourseNameChanged(value: String) {
        _semesterEditorState.update { it.copy(draftCourseName = value, errorMessage = null) }
    }

    fun onDraftCourseCodeChanged(value: String) {
        _semesterEditorState.update { it.copy(draftCourseCode = value, errorMessage = null) }
    }

    fun onDraftCreditHoursChanged(value: String) {
        _semesterEditorState.update { it.copy(draftCreditHours = value, errorMessage = null) }
    }

    fun onDraftLetterGradeChanged(letter: String) {
        _semesterEditorState.update { it.copy(draftLetterGrade = letter, errorMessage = null) }
    }

    fun addEnrolledCourseToDraft(course: StudentCourse) {
        val form = _semesterEditorState.value
        val alreadyExists = form.courses.any { it.courseId == course.id && course.id.isNotBlank() }
        if (alreadyExists) {
            _semesterEditorState.update {
                it.copy(errorMessage = "${course.courseCode} is already added to this semester.")
            }
            return
        }
        val scale = latestSettings.gradingScale
        val defaultGrade = scale.firstOrNull()?.letter ?: "A"
        val points = AcademicGpaCalculator.resolveGradePoints(defaultGrade, scale) ?: 4.0
        val newEntry = SemesterCourseGrade(
            id = UUID.randomUUID().toString(),
            courseId = course.id,
            courseCode = course.courseCode,
            courseName = course.courseName,
            creditHours = course.creditHours.coerceIn(0.5, 12.0),
            letterGrade = defaultGrade,
            gradePoints = points,
        )
        _semesterEditorState.update {
            it.copy(courses = it.courses + newEntry, errorMessage = null)
        }
    }

    fun addDraftCustomCourse() {
        val form = _semesterEditorState.value
        val cleanName = form.draftCourseName.trim()
        val cleanCode = form.draftCourseCode.trim()
        val credits = form.draftCreditHours.trim().toDoubleOrNull()
        val points = AcademicGpaCalculator.resolveGradePoints(
            letter = form.draftLetterGrade,
            scale = latestSettings.gradingScale,
        )
        if (cleanName.isEmpty()) {
            _semesterEditorState.update { it.copy(errorMessage = "Enter a course title.") }
            return
        }
        if (credits == null || !credits.isFinite() || credits !in 0.5..12.0) {
            _semesterEditorState.update { it.copy(errorMessage = "Credit hours must be between 0.5 and 12.0.") }
            return
        }
        if (points == null) {
            _semesterEditorState.update { it.copy(errorMessage = "Select a valid grade from your grading scale.") }
            return
        }
        if (form.courses.size >= 24) {
            _semesterEditorState.update { it.copy(errorMessage = "Maximum 24 courses per semester.") }
            return
        }

        val entry = SemesterCourseGrade(
            id = UUID.randomUUID().toString(),
            courseId = "",
            courseCode = cleanCode,
            courseName = cleanName,
            creditHours = credits,
            letterGrade = form.draftLetterGrade,
            gradePoints = points,
        )
        _semesterEditorState.update {
            it.copy(
                courses = it.courses + entry,
                draftCourseName = "",
                draftCourseCode = "",
                draftCreditHours = "3.0",
                errorMessage = null,
            )
        }
    }

    fun updateDraftCourseGrade(entryId: String, letter: String) {
        val points = AcademicGpaCalculator.resolveGradePoints(letter, latestSettings.gradingScale) ?: return
        _semesterEditorState.update { state ->
            state.copy(
                courses = state.courses.map { item ->
                    if (item.id == entryId) {
                        item.copy(letterGrade = letter, gradePoints = points)
                    } else {
                        item
                    }
                },
                errorMessage = null,
            )
        }
    }

    fun removeDraftCourse(entryId: String) {
        _semesterEditorState.update { state ->
            state.copy(
                courses = state.courses.filterNot { it.id == entryId },
                errorMessage = null,
            )
        }
    }

    fun importAllEnrolledCoursesToDraft() {
        if (latestEnrolledCourses.isEmpty()) {
            _semesterEditorState.update {
                it.copy(errorMessage = "No enrolled courses found yet. Add courses manually or in the Courses tab.")
            }
            return
        }
        val scale = latestSettings.gradingScale
        val defaultGrade = scale.firstOrNull()?.letter ?: "A"
        val points = AcademicGpaCalculator.resolveGradePoints(defaultGrade, scale) ?: 4.0
        val currentIds = _semesterEditorState.value.courses.map { it.courseId }.toSet()
        val additions = latestEnrolledCourses
            .filter { it.id !in currentIds }
            .map { course ->
                SemesterCourseGrade(
                    id = UUID.randomUUID().toString(),
                    courseId = course.id,
                    courseCode = course.courseCode,
                    courseName = course.courseName,
                    creditHours = course.creditHours.coerceIn(0.5, 12.0),
                    letterGrade = defaultGrade,
                    gradePoints = points,
                )
            }
        _semesterEditorState.update {
            it.copy(
                courses = (it.courses + additions).take(24),
                errorMessage = null,
            )
        }
    }

    fun saveSemesterRecord() {
        val form = _semesterEditorState.value
        if (form.isSaving) return

        val cleanName = form.semesterName.trim()
        val order = form.orderIndexInput.trim().toIntOrNull()
        val maxScalePoints = latestSettings.gradingScale.maxOfOrNull { it.points } ?: 4.0

        if (cleanName.isEmpty()) {
            _semesterEditorState.update { it.copy(errorMessage = "Enter a semester name.") }
            return
        }
        if (order == null || order !in 0..100) {
            _semesterEditorState.update { it.copy(errorMessage = "Semester order must be between 0 and 100.") }
            return
        }

        val summaryCredits: Double
        val summaryGpa: Double
        if (form.entryMode == SemesterEntryMode.SUMMARY) {
            val cr = form.summaryCreditsInput.trim().toDoubleOrNull()
            val gpa = form.summaryGpaInput.trim().toDoubleOrNull()
            if (cr == null || !cr.isFinite() || cr !in 0.5..100.0) {
                _semesterEditorState.update {
                    it.copy(errorMessage = "Enter valid completed credit hours (0.5–100.0).")
                }
                return
            }
            if (gpa == null || !gpa.isFinite() || gpa < 0.0 || gpa > maxScalePoints) {
                _semesterEditorState.update {
                    it.copy(
                        errorMessage = "Semester GPA must be between 0.00 and ${
                            String.format(Locale.US, "%.2f", maxScalePoints)
                        } for your active scale.",
                    )
                }
                return
            }
            summaryCredits = cr
            summaryGpa = gpa
        } else {
            if (form.courses.isEmpty()) {
                _semesterEditorState.update {
                    it.copy(errorMessage = "Add at least one course or switch to Previous Semester Totals mode.")
                }
                return
            }
            summaryCredits = AcademicGpaCalculator.calculateCourseListCredits(form.courses)
            summaryGpa = AcademicGpaCalculator.calculateCourseListGpa(form.courses)
        }

        val request = SaveSemesterGpaRequest(
            semesterName = cleanName,
            orderIndex = order,
            activeSemester = form.activeSemester,
            entryMode = form.entryMode,
            summaryCredits = summaryCredits,
            summaryGpa = summaryGpa,
            courses = if (form.entryMode == SemesterEntryMode.COURSES) form.courses else emptyList(),
        )

        _semesterEditorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = if (form.editingRecordId == null) {
                attendanceAndGpaRepository.addSemesterGpaRecordWithId(
                    uid = uid,
                    recordId = form.draftRecordId.ifBlank { UUID.randomUUID().toString() },
                    request = request,
                )
            } else {
                attendanceAndGpaRepository.updateSemesterGpaRecord(uid, form.editingRecordId, request)
            }
            result.fold(
                onSuccess = {
                    _semesterEditorState.value = SemesterEditorState()
                    val baseMessage = if (form.editingRecordId == null) {
                        "Added semester \"$cleanName\"."
                    } else {
                        "Updated semester \"$cleanName\"."
                    }
                    _actionMessage.value = syncCoordinator.formatActionMessage(baseMessage)
                },
                onFailure = { error ->
                    _semesterEditorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Failed to save semester record.",
                        )
                    }
                },
            )
        }
    }

    fun updateSavedSemesterCourseGrade(
        semester: SemesterGpaRecord,
        courseEntryId: String,
        newLetterGrade: String,
    ) {
        val points = AcademicGpaCalculator.resolveGradePoints(newLetterGrade, latestSettings.gradingScale) ?: return
        val updatedCourses = semester.courses.map { course ->
            if (course.id == courseEntryId) {
                course.copy(letterGrade = newLetterGrade, gradePoints = points)
            } else {
                course
            }
        }
        val request = SaveSemesterGpaRequest(
            semesterName = semester.semesterName,
            orderIndex = semester.orderIndex,
            activeSemester = semester.activeSemester,
            entryMode = semester.resolvedEntryMode(),
            summaryCredits = AcademicGpaCalculator.calculateCourseListCredits(updatedCourses),
            summaryGpa = AcademicGpaCalculator.calculateCourseListGpa(updatedCourses),
            courses = updatedCourses,
        )
        viewModelScope.launch {
            attendanceAndGpaRepository.updateSemesterGpaRecord(uid, semester.id, request).fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        "Updated grade in ${semester.semesterName}.",
                    )
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Could not update course grade."
                },
            )
        }
    }

    fun deleteSemesterRecord(recordId: String) {
        viewModelScope.launch {
            attendanceAndGpaRepository.deleteSemesterGpaRecord(uid, recordId).fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage("Deleted semester record.")
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Failed to delete semester record."
                },
            )
        }
    }

    fun openGradingScaleDialog() {
        _scaleEditorState.value = GradingScaleEditorState(
            isOpen = true,
            scaleName = latestSettings.scaleName,
            rows = latestSettings.gradingScale.map { item ->
                ScaleRowDraft(
                    letter = item.letter,
                    pointsInput = String.format(Locale.US, "%.2f", item.points),
                )
            },
        )
    }

    fun dismissGradingScaleDialog() {
        _scaleEditorState.value = GradingScaleEditorState()
    }

    fun onScaleNameChanged(name: String) {
        _scaleEditorState.update { it.copy(scaleName = name, errorMessage = null) }
    }

    fun onScaleRowLetterChanged(rowId: String, letter: String) {
        _scaleEditorState.update { state ->
            state.copy(
                rows = state.rows.map { if (it.id == rowId) it.copy(letter = letter) else it },
                errorMessage = null,
            )
        }
    }

    fun onScaleRowPointsChanged(rowId: String, pointsInput: String) {
        _scaleEditorState.update { state ->
            state.copy(
                rows = state.rows.map { if (it.id == rowId) it.copy(pointsInput = pointsInput) else it },
                errorMessage = null,
            )
        }
    }

    fun addScaleRow() {
        _scaleEditorState.update { state ->
            if (state.rows.size >= 20) {
                state.copy(errorMessage = "Maximum 20 grade rows allowed.")
            } else {
                state.copy(
                    rows = state.rows + ScaleRowDraft(letter = "", pointsInput = "0.00"),
                    errorMessage = null,
                )
            }
        }
    }

    fun removeScaleRow(rowId: String) {
        _scaleEditorState.update { state ->
            if (state.rows.size <= 1) {
                state.copy(errorMessage = "At least 1 grade row is required.")
            } else {
                state.copy(rows = state.rows.filterNot { it.id == rowId }, errorMessage = null)
            }
        }
    }

    fun applyScalePreset(standardMinusPlus: Boolean) {
        val preset = if (standardMinusPlus) {
            DefaultGradeScales.standard4PointTemplate
        } else {
            DefaultGradeScales.plusOnly4PointTemplate
        }
        val label = if (standardMinusPlus) {
            AcademicSettings.DEFAULT_SCALE_NAME
        } else {
            "4.0 Plus-Only Template (Customizable)"
        }
        _scaleEditorState.update { state ->
            state.copy(
                scaleName = label,
                rows = preset.map {
                    ScaleRowDraft(
                        letter = it.letter,
                        pointsInput = String.format(Locale.US, "%.2f", it.points),
                    )
                },
                errorMessage = null,
            )
        }
    }

    fun saveGradingScaleSettings() {
        val form = _scaleEditorState.value
        if (form.isSaving) return
        val cleanScaleName = form.scaleName.trim()
        if (cleanScaleName.isEmpty()) {
            _scaleEditorState.update { it.copy(errorMessage = "Enter a grading scale name.") }
            return
        }

        val parsed = mutableListOf<GradeScaleItem>()
        for (row in form.rows) {
            val cleanLetter = row.letter.trim().uppercase(Locale.US)
            val points = row.pointsInput.trim().toDoubleOrNull()
            if (cleanLetter.isEmpty() || cleanLetter.length > 6) {
                _scaleEditorState.update {
                    it.copy(errorMessage = "Every grade letter must be 1–6 characters.")
                }
                return
            }
            if (points == null || !points.isFinite() || points !in 0.0..10.0) {
                _scaleEditorState.update {
                    it.copy(errorMessage = "Grade points for $cleanLetter must be between 0.00 and 10.00.")
                }
                return
            }
            parsed += GradeScaleItem(letter = cleanLetter, points = points)
        }
        if (parsed.map { it.letter }.distinct().size != parsed.size) {
            _scaleEditorState.update { it.copy(errorMessage = "Remove duplicate grade letters.") }
            return
        }

        val sortedScale = parsed.sortedByDescending { it.points }
        _scaleEditorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = attendanceAndGpaRepository.saveAcademicSettings(
                uid = uid,
                request = SaveAcademicSettingsRequest(
                    attendanceThresholdPercent = latestSettings.attendanceThresholdPercent,
                    scaleName = cleanScaleName,
                    customScale = true,
                    gradingScale = sortedScale,
                ),
            )
            result.fold(
                onSuccess = {
                    _scaleEditorState.value = GradingScaleEditorState()
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        "Saved configurable grading scale \"$cleanScaleName\".",
                    )
                },
                onFailure = { error ->
                    _scaleEditorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Failed to save grading scale.",
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
                return GpaCgpaViewModel(
                    uid = uid,
                    workspaceRepository = workspaceRepository,
                    attendanceAndGpaRepository = attendanceAndGpaRepository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
