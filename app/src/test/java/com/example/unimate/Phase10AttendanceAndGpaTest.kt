package com.example.unimate

import com.example.unimate.data.model.AcademicGpaCalculator
import com.example.unimate.data.model.AcademicSettings
import com.example.unimate.data.model.AcademicTask
import com.example.unimate.data.model.AddGoalRequest
import com.example.unimate.data.model.AddNoteRequest
import com.example.unimate.data.model.AddReminderRequest
import com.example.unimate.data.model.AddTaskRequest
import com.example.unimate.data.model.AttendanceCalculator
import com.example.unimate.data.model.AttendanceGpaMappers
import com.example.unimate.data.model.AttendanceLog
import com.example.unimate.data.model.AttendanceStatus
import com.example.unimate.data.model.DefaultGradeScales
import com.example.unimate.data.model.GradeScaleItem
import com.example.unimate.data.model.SaveAcademicSettingsRequest
import com.example.unimate.data.model.SaveAttendanceLogRequest
import com.example.unimate.data.model.SaveCourseRequest
import com.example.unimate.data.model.SaveSemesterGpaRequest
import com.example.unimate.data.model.SaveTimetableClassRequest
import com.example.unimate.data.model.SemesterCourseGrade
import com.example.unimate.data.model.SemesterEntryMode
import com.example.unimate.data.model.SemesterGpaRecord
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.StudentReminder
import com.example.unimate.data.model.StudyGoal
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.data.repository.AttendanceAndGpaRepository
import com.example.unimate.data.repository.WorkspaceRepository
import com.example.unimate.ui.attendance.AttendanceUiState
import com.example.unimate.ui.attendance.AttendanceViewModel
import com.example.unimate.ui.gpa.GpaCgpaUiState
import com.example.unimate.ui.gpa.GpaCgpaViewModel
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase10AttendanceAndGpaTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `attendance calculator handles zero classes without division by zero`() {
        val zeroCourse = StudentCourse(
            id = "c0",
            courseCode = "CS-101",
            courseName = "Intro to Computing",
            attendedClasses = 0L,
            totalClasses = 0L,
        )
        assertEquals(0.0, AttendanceCalculator.calculatePercentage(0L, 0L), 0.0001)
        assertEquals(0L, AttendanceCalculator.calculateRemainingAllowableAbsences(0L, 0L, 75))
        assertEquals(0L, AttendanceCalculator.calculateClassesNeededToRecover(0L, 0L, 75))

        val summary = AttendanceCalculator.summarizeCourse(
            course = zeroCourse,
            courseLogs = emptyList(),
            thresholdPercent = 75,
        )
        assertFalse(summary.hasSessions)
        assertEquals(0.0, summary.percentage, 0.0001)
        assertEquals(0L, summary.absentClasses)
        assertFalse(summary.shortageWarning)
        assertTrue(summary.meetsThreshold)
        assertEquals(0L, summary.allowableAbsencesRemaining)
    }

    @Test
    fun `attendance calculator computes shortage warnings allowable absences and recovery classes accurately`() {
        // 16/20 = 80% against 75% threshold -> 16/21 = 76.19% >= 75%, 16/22 = 72.7% < 75% -> 1 allowable absence
        assertEquals(80.0, AttendanceCalculator.calculatePercentage(16L, 20L), 0.001)
        assertEquals(1L, AttendanceCalculator.calculateRemainingAllowableAbsences(16L, 20L, 75))
        assertEquals(0L, AttendanceCalculator.calculateClassesNeededToRecover(16L, 20L, 75))

        // Exact boundary: 15/20 = 75% -> 0 allowable absences (near threshold)
        assertEquals(0L, AttendanceCalculator.calculateRemainingAllowableAbsences(15L, 20L, 75))

        // Below threshold: 7/10 = 70% vs 75% -> needs 2 consecutive present classes ((7+2)/(10+2) = 9/12 = 75%)
        assertEquals(0L, AttendanceCalculator.calculateRemainingAllowableAbsences(7L, 10L, 75))
        assertEquals(2L, AttendanceCalculator.calculateClassesNeededToRecover(7L, 10L, 75))

        // Threshold 100% with absence -> unrecoverable (null)
        assertNull(AttendanceCalculator.calculateClassesNeededToRecover(9L, 10L, 100))

        val course = StudentCourse(
            id = "c1",
            courseCode = "CS-301",
            courseName = "DSA",
            attendedClasses = 7L,
            totalClasses = 10L,
        )
        val summary = AttendanceCalculator.summarizeCourse(course, emptyList(), thresholdPercent = 75)
        assertTrue(summary.shortageWarning)
        assertFalse(summary.meetsThreshold)
        assertEquals(3L, summary.absentClasses)
        assertEquals(2L, summary.classesNeededToRecover)
    }

    @Test
    fun `attendance monthly statistics group logs cleanly by month`() {
        val logs = listOf(
            AttendanceLog(id = "1", courseId = "c1", status = "PRESENT", monthKey = "2026-11", sessionDateMillis = 1794000000000L),
            AttendanceLog(id = "2", courseId = "c1", status = "ABSENT", monthKey = "2026-11", sessionDateMillis = 1793900000000L),
            AttendanceLog(id = "3", courseId = "c1", status = "PRESENT", monthKey = "2026-10", sessionDateMillis = 1791000000000L),
        )
        val stats = AttendanceCalculator.summarizeMonthlyLogs(logs)
        assertEquals(2, stats.size)
        assertEquals("2026-11", stats[0].monthKey)
        assertEquals(2L, stats[0].totalClasses)
        assertEquals(1L, stats[0].attendedClasses)
        assertEquals(1L, stats[0].absentClasses)
        assertEquals(50.0, stats[0].percentage, 0.01)

        assertEquals("2026-10", stats[1].monthKey)
        assertEquals(100.0, stats[1].percentage, 0.01)
    }

    @Test
    fun `gpa and cgpa calculator handles empty data different credit hours and cumulative records`() {
        // Empty case -> zero division safe
        assertEquals(0.0, AcademicGpaCalculator.calculateCourseListGpa(emptyList()), 0.0001)
        val emptySummary = AcademicGpaCalculator.calculateCgpaSummary(emptyList(), DefaultGradeScales.standard4PointTemplate)
        assertEquals(0.0, emptySummary.cgpa, 0.0001)
        assertEquals(0.0, emptySummary.totalCredits, 0.0001)

        // Different credit hours: (4.0 * 4.0) + (1.5 * 3.0) + (3.0 * 3.3) = 16 + 4.5 + 9.9 = 30.4 / 8.5 = 3.57647
        val mixedCourses = listOf(
            SemesterCourseGrade("1", "", "CS-301", "DSA", 4.0, "A", 4.0),
            SemesterCourseGrade("2", "", "CS-301L", "DSA Lab", 1.5, "B", 3.0),
            SemesterCourseGrade("3", "", "SE-312", "Architecture", 3.0, "B+", 3.3),
        )
        val semGpa = AcademicGpaCalculator.calculateCourseListGpa(mixedCourses)
        assertEquals(30.4 / 8.5, semGpa, 0.0001)

        // Multi-semester CGPA: Past semester (15 credits @ 3.20 = 48.0 QP) + Current (8.5 credits @ 30.4 QP)
        // Total QP = 78.4, Total Credits = 23.5 -> CGPA = 78.4 / 23.5 = 3.33617
        val semesters = listOf(
            SemesterGpaRecord(
                id = "s1",
                semesterName = "Sem 1",
                orderIndex = 1,
                activeSemester = false,
                entryMode = SemesterEntryMode.SUMMARY.wireValue,
                summaryCredits = 15.0,
                summaryGpa = 3.20,
            ),
            SemesterGpaRecord(
                id = "s2",
                semesterName = "Sem 2",
                orderIndex = 2,
                activeSemester = true,
                entryMode = SemesterEntryMode.COURSES.wireValue,
                courses = mixedCourses,
            ),
        )
        val summary = AcademicGpaCalculator.calculateCgpaSummary(semesters, DefaultGradeScales.standard4PointTemplate)
        assertEquals(23.5, summary.totalCredits, 0.0001)
        assertEquals(78.4, summary.totalQualityPoints, 0.0001)
        assertEquals(78.4 / 23.5, summary.cgpa, 0.0001)
        assertEquals(2, summary.semesterMetrics.size)
        assertEquals(3.20, summary.semesterMetrics[0].cumulativeCgpaAfter, 0.0001)
        assertEquals(78.4 / 23.5, summary.semesterMetrics[1].cumulativeCgpaAfter, 0.0001)
    }

    @Test
    fun `firestore mappers match rules keys without id or is-prefixed booleans`() {
        val logPayload = AttendanceGpaMappers.toCreateAttendanceLogPayload(
            uid = "u1",
            request = SaveAttendanceLogRequest(
                courseId = "c1",
                courseCode = "CS-301",
                courseName = "DSA",
                status = AttendanceStatus.ABSENT,
                sessionDateMillis = 1794000000000L,
                notes = "Missed lecture",
            ),
        )
        assertFalse(logPayload.containsKey("id"))
        assertEquals("ABSENT", logPayload["status"])
        assertTrue(logPayload["createdAt"] is FieldValue)

        val settingsPayload = AttendanceGpaMappers.toSaveAcademicSettingsPayload(
            uid = "u1",
            request = SaveAcademicSettingsRequest(
                attendanceThresholdPercent = 80,
                scaleName = "Custom Scale",
                customScale = true,
                gradingScale = listOf(GradeScaleItem("A", 4.0)),
            ),
            isCreate = true,
        )
        assertTrue(settingsPayload.containsKey("customScale"))
        assertFalse(settingsPayload.containsKey("isCustomScale"))

        val semPayload = AttendanceGpaMappers.toCreateSemesterGpaPayload(
            uid = "u1",
            request = SaveSemesterGpaRequest(
                semesterName = "Sem 1",
                orderIndex = 1,
                activeSemester = true,
                entryMode = SemesterEntryMode.SUMMARY,
                summaryCredits = 16.0,
                summaryGpa = 3.5,
                courses = emptyList(),
            ),
        )
        assertTrue(semPayload.containsKey("activeSemester"))
        assertFalse(semPayload.containsKey("isActiveSemester"))
        assertFalse(semPayload.containsKey("id"))
    }

    @Test
    fun `attendance viewmodel manages manual logs threshold config direct totals and error recovery`() = runTest {
        val workspaceRepo = FakePhase10WorkspaceRepository()
        val attGpaRepo = FakePhase10AttendanceAndGpaRepository(workspaceRepo)
        val viewModel = AttendanceViewModel("u1", workspaceRepo, attGpaRepo)

        assertTrue(viewModel.uiState.value is AttendanceUiState.Loading)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AttendanceUiState.Empty)

        // Add enrolled course with 0/0 initially
        val course = StudentCourse(
            id = "c1",
            uid = "u1",
            courseCode = "CS-301",
            courseName = "Algorithms",
            attendedClasses = 0L,
            totalClasses = 0L,
        )
        workspaceRepo.seedCourses(listOf(course))
        advanceUntilIdle()

        val initialSuccess = viewModel.uiState.value as AttendanceUiState.Success
        assertEquals(0.0, initialSuccess.courses.first().percentage, 0.001)
        assertFalse(initialSuccess.courses.first().hasSessions)

        // Log Present + Absent sessions manually
        viewModel.openLogAttendanceDialog(course, AttendanceStatus.PRESENT)
        viewModel.onLogNotesChanged("Lecture 1")
        viewModel.saveManualAttendanceLog()
        advanceUntilIdle()

        viewModel.openLogAttendanceDialog(course, AttendanceStatus.ABSENT)
        viewModel.onLogNotesChanged("Sick day")
        viewModel.saveManualAttendanceLog()
        advanceUntilIdle()

        val afterLogs = viewModel.uiState.value as AttendanceUiState.Success
        assertEquals(2, afterLogs.filteredLogs.size)
        assertEquals(1L, afterLogs.courses.first().attendedClasses)
        assertEquals(2L, afterLogs.courses.first().totalClasses)
        assertEquals(50.0, afterLogs.courses.first().percentage, 0.01)
        assertTrue(afterLogs.courses.first().shortageWarning)

        // Update threshold to 80%
        viewModel.openThresholdDialog()
        viewModel.onThresholdChanged(80)
        viewModel.saveThresholdSettings()
        advanceUntilIdle()

        val afterThreshold = viewModel.uiState.value as AttendanceUiState.Success
        assertEquals(80, afterThreshold.overall.thresholdPercent)

        // Direct totals validation rejects attended > total
        viewModel.openDirectTotalsDialog(afterThreshold.courses.first().course)
        viewModel.onDirectAttendedChanged("12")
        viewModel.onDirectTotalChanged("10")
        viewModel.saveDirectCourseTotals()
        advanceUntilIdle()
        assertNotNull(viewModel.totalsEditorState.value.errorMessage)

        // Valid direct totals update -> 9/10 = 90% >= 80% -> 1 allowable absence
        viewModel.onDirectAttendedChanged("9")
        viewModel.onDirectTotalChanged("10")
        viewModel.saveDirectCourseTotals()
        advanceUntilIdle()
        val afterDirectUpdate = viewModel.uiState.value as AttendanceUiState.Success
        assertEquals(90.0, afterDirectUpdate.courses.first().percentage, 0.01)
        assertEquals(1L, afterDirectUpdate.courses.first().allowableAbsencesRemaining)
        assertFalse(afterDirectUpdate.courses.first().shortageWarning)

        // Error & Retry recovery
        attGpaRepo.failLogsStream = true
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AttendanceUiState.Error)

        attGpaRepo.failLogsStream = false
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AttendanceUiState.Success)

        collectJob.cancel()
    }

    @Test
    fun `gpa cgpa viewmodel validates inputs computes cgpa across semesters and saves custom grading scale`() = runTest {
        val workspaceRepo = FakePhase10WorkspaceRepository()
        val attGpaRepo = FakePhase10AttendanceAndGpaRepository(workspaceRepo)
        workspaceRepo.seedCourses(
            listOf(
                StudentCourse(id = "c1", courseCode = "CS-301", courseName = "Algorithms", creditHours = 3.0),
                StudentCourse(id = "c2", courseCode = "AI-305", courseName = "Machine Learning", creditHours = 4.0),
            ),
        )
        val viewModel = GpaCgpaViewModel("u1", workspaceRepo, attGpaRepo)

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is GpaCgpaUiState.Empty)

        // Customize grading scale first (e.g., set B+ = 3.50)
        viewModel.openGradingScaleDialog()
        viewModel.onScaleNameChanged("Custom Dept Scale")
        val bPlusRow = viewModel.scaleEditorState.value.rows.first { it.letter == "B+" }
        viewModel.onScaleRowPointsChanged(bPlusRow.id, "3.50")
        viewModel.saveGradingScaleSettings()
        advanceUntilIdle()

        // Add Previous Semester (12 credits @ 3.00 = 36.0 QP) with validation check first
        viewModel.openAddSemesterDialog(SemesterEntryMode.SUMMARY)
        viewModel.onSemesterNameChanged("Fall 2025")
        viewModel.onSummaryCreditsChanged("12.0")
        viewModel.onSummaryGpaChanged("5.5") // invalid (> 4.0 max scale)
        viewModel.saveSemesterRecord()
        advanceUntilIdle()
        assertNotNull(viewModel.semesterEditorState.value.errorMessage)

        viewModel.onSummaryGpaChanged("3.00")
        viewModel.saveSemesterRecord()
        advanceUntilIdle()

        // Add Current Semester using enrolled courses + custom course
        viewModel.openAddSemesterDialog(SemesterEntryMode.COURSES)
        viewModel.onSemesterNameChanged("Spring 2026")
        viewModel.importAllEnrolledCoursesToDraft()
        val coursesDraft = viewModel.semesterEditorState.value.courses
        assertEquals(2, coursesDraft.size)
        // CS-301 (3 cr): A (4.0) = 12.0; AI-305 (4 cr): B+ (custom 3.50) = 14.0 -> 26.0 / 7.0
        viewModel.updateDraftCourseGrade(coursesDraft[0].id, "A")
        viewModel.updateDraftCourseGrade(coursesDraft[1].id, "B+")
        viewModel.saveSemesterRecord()
        advanceUntilIdle()

        val success = viewModel.uiState.value as GpaCgpaUiState.Success
        assertEquals(19.0, success.summary.totalCredits, 0.001)
        assertEquals(62.0, success.summary.totalQualityPoints, 0.001)
        assertEquals(62.0 / 19.0, success.summary.cgpa, 0.001)
        assertEquals("Custom Dept Scale", success.settings.scaleName)

        // Simulate Firestore failure & retry
        attGpaRepo.failGpaStream = true
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is GpaCgpaUiState.Error)

        attGpaRepo.failGpaStream = false
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is GpaCgpaUiState.Success)

        collectJob.cancel()
    }
}

private class FakePhase10WorkspaceRepository : WorkspaceRepository {
    private val coursesFlow = MutableStateFlow<List<StudentCourse>>(emptyList())

    fun seedCourses(courses: List<StudentCourse>) {
        coursesFlow.value = courses
    }

    override fun observeCourses(uid: String): Flow<List<StudentCourse>> = coursesFlow
    override fun observeTasks(uid: String): Flow<List<AcademicTask>> = MutableStateFlow(emptyList())
    override fun observeTimetable(uid: String): Flow<List<TimetableClass>> = MutableStateFlow(emptyList())
    override fun observeNotes(uid: String): Flow<List<StudyNote>> = MutableStateFlow(emptyList())
    override fun observeReminders(uid: String): Flow<List<StudentReminder>> = MutableStateFlow(emptyList())
    override fun observeGoals(uid: String): Flow<List<StudyGoal>> = MutableStateFlow(emptyList())

    override suspend fun updateCourseAttendance(
        uid: String,
        courseId: String,
        attendedClasses: Long,
        totalClasses: Long,
    ): Result<Unit> {
        coursesFlow.value = coursesFlow.value.map {
            if (it.id == courseId) it.copy(attendedClasses = attendedClasses, totalClasses = totalClasses) else it
        }
        return Result.success(Unit)
    }

    fun adjustCourseAttendance(courseId: String, deltaAttended: Long, deltaTotal: Long) {
        coursesFlow.value = coursesFlow.value.map {
            if (it.id == courseId) {
                val nextTotal = (it.totalClasses + deltaTotal).coerceIn(0L, 1000L)
                val nextAttended = (it.attendedClasses + deltaAttended).coerceIn(0L, nextTotal)
                it.copy(attendedClasses = nextAttended, totalClasses = nextTotal)
            } else {
                it
            }
        }
    }

    override suspend fun addTask(uid: String, request: AddTaskRequest) = Result.success(Unit)
    override suspend fun toggleTaskCompleted(uid: String, taskId: String, completed: Boolean) = Result.success(Unit)
    override suspend fun deleteTask(uid: String, taskId: String) = Result.success(Unit)
    override suspend fun addCourse(uid: String, request: SaveCourseRequest) = Result.success(Unit)
    override suspend fun updateCourse(uid: String, courseId: String, request: SaveCourseRequest) = Result.success(Unit)
    override suspend fun deleteCourse(uid: String, courseId: String) = Result.success(Unit)
    override suspend fun addTimetableClass(uid: String, request: SaveTimetableClassRequest) = Result.success(Unit)
    override suspend fun updateTimetableClass(uid: String, classId: String, request: SaveTimetableClassRequest) = Result.success(Unit)
    override suspend fun deleteTimetableClass(uid: String, classId: String) = Result.success(Unit)
    override suspend fun addNote(uid: String, request: AddNoteRequest) = Result.success(Unit)
    override suspend fun deleteNote(uid: String, noteId: String) = Result.success(Unit)
    override suspend fun addReminder(uid: String, request: AddReminderRequest) = Result.success(Unit)
    override suspend fun toggleReminderDone(uid: String, reminderId: String, done: Boolean) = Result.success(Unit)
    override suspend fun deleteReminder(uid: String, reminderId: String) = Result.success(Unit)
    override suspend fun addGoal(uid: String, request: AddGoalRequest) = Result.success(Unit)
    override suspend fun updateGoalProgress(uid: String, goalId: String, completedSteps: Long, targetSteps: Long) = Result.success(Unit)
    override suspend fun deleteGoal(uid: String, goalId: String) = Result.success(Unit)
}

private class FakePhase10AttendanceAndGpaRepository(
    private val workspaceRepo: FakePhase10WorkspaceRepository,
) : AttendanceAndGpaRepository {

    var failLogsStream = false
    var failGpaStream = false

    private val logsFlow = MutableStateFlow<List<AttendanceLog>>(emptyList())
    private val settingsFlow = MutableStateFlow(AcademicSettings(uid = "u1"))
    private val gpaFlow = MutableStateFlow<List<SemesterGpaRecord>>(emptyList())

    override fun observeAttendanceLogs(uid: String): Flow<List<AttendanceLog>> = flow {
        if (failLogsStream) throw IllegalStateException("Firestore unavailable")
        logsFlow.collect { emit(it) }
    }

    override fun observeAcademicSettings(uid: String): Flow<AcademicSettings> = settingsFlow

    override fun observeSemesterGpaRecords(uid: String): Flow<List<SemesterGpaRecord>> = flow {
        if (failGpaStream) throw IllegalStateException("Network offline")
        gpaFlow.collect { emit(it) }
    }

    override suspend fun logAttendanceSession(uid: String, request: SaveAttendanceLogRequest): Result<Unit> {
        val next = AttendanceLog(
            id = "log-${logsFlow.value.size + 1}",
            uid = uid,
            courseId = request.courseId,
            courseCode = request.courseCode,
            courseName = request.courseName,
            status = request.status.wireValue,
            sessionDateMillis = request.sessionDateMillis,
            monthKey = AttendanceCalculator.formatMonthKey(request.sessionDateMillis),
            notes = request.notes,
        )
        logsFlow.value = logsFlow.value + next
        workspaceRepo.adjustCourseAttendance(
            courseId = request.courseId,
            deltaAttended = if (request.status == AttendanceStatus.PRESENT) 1L else 0L,
            deltaTotal = 1L,
        )
        return Result.success(Unit)
    }

    override suspend fun updateAttendanceLogStatus(
        uid: String,
        log: AttendanceLog,
        newStatus: AttendanceStatus,
        notes: String,
    ): Result<Unit> {
        val delta = when {
            log.resolvedStatus() == newStatus -> 0L
            newStatus == AttendanceStatus.PRESENT -> 1L
            else -> -1L
        }
        logsFlow.value = logsFlow.value.map {
            if (it.id == log.id) it.copy(status = newStatus.wireValue, notes = notes) else it
        }
        workspaceRepo.adjustCourseAttendance(log.courseId, deltaAttended = delta, deltaTotal = 0L)
        return Result.success(Unit)
    }

    override suspend fun deleteAttendanceLog(uid: String, log: AttendanceLog): Result<Unit> {
        logsFlow.value = logsFlow.value.filterNot { it.id == log.id }
        workspaceRepo.adjustCourseAttendance(
            courseId = log.courseId,
            deltaAttended = if (log.resolvedStatus() == AttendanceStatus.PRESENT) -1L else 0L,
            deltaTotal = -1L,
        )
        return Result.success(Unit)
    }

    override suspend fun saveAcademicSettings(uid: String, request: SaveAcademicSettingsRequest): Result<Unit> {
        settingsFlow.value = AcademicSettings(
            uid = uid,
            attendanceThresholdPercent = request.attendanceThresholdPercent,
            scaleName = request.scaleName,
            customScale = request.customScale,
            gradingScale = request.gradingScale,
        )
        return Result.success(Unit)
    }

    override suspend fun addSemesterGpaRecord(uid: String, request: SaveSemesterGpaRequest): Result<Unit> {
        val next = SemesterGpaRecord(
            id = "sem-${gpaFlow.value.size + 1}",
            uid = uid,
            semesterName = request.semesterName,
            orderIndex = request.orderIndex,
            activeSemester = request.activeSemester,
            entryMode = request.entryMode.wireValue,
            summaryCredits = request.summaryCredits,
            summaryGpa = request.summaryGpa,
            courses = request.courses,
        )
        gpaFlow.value = gpaFlow.value + next
        return Result.success(Unit)
    }

    override suspend fun updateSemesterGpaRecord(
        uid: String,
        recordId: String,
        request: SaveSemesterGpaRequest,
    ): Result<Unit> {
        gpaFlow.value = gpaFlow.value.map {
            if (it.id == recordId) {
                it.copy(
                    semesterName = request.semesterName,
                    orderIndex = request.orderIndex,
                    activeSemester = request.activeSemester,
                    entryMode = request.entryMode.wireValue,
                    summaryCredits = request.summaryCredits,
                    summaryGpa = request.summaryGpa,
                    courses = request.courses,
                )
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteSemesterGpaRecord(uid: String, recordId: String): Result<Unit> {
        gpaFlow.value = gpaFlow.value.filterNot { it.id == recordId }
        return Result.success(Unit)
    }
}
