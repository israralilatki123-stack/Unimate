package com.example.unimate

import com.example.unimate.data.model.AcademicTask
import com.example.unimate.data.model.AddGoalRequest
import com.example.unimate.data.model.AddNoteRequest
import com.example.unimate.data.model.AddReminderRequest
import com.example.unimate.data.model.AddTaskRequest
import com.example.unimate.data.model.DayOfWeekOption
import com.example.unimate.data.model.SaveCourseRequest
import com.example.unimate.data.model.SaveTimetableClassRequest
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.StudentReminder
import com.example.unimate.data.model.StudyGoal
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.model.TimeFormatUtils
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.data.model.TimetableConflictDetector
import com.example.unimate.data.model.WorkspaceMappers
import com.example.unimate.data.repository.WorkspaceRepository
import com.example.unimate.ui.courses.CoursesUiState
import com.example.unimate.ui.courses.CoursesViewModel
import com.example.unimate.ui.timetable.TimetableUiState
import com.example.unimate.ui.timetable.TimetableViewMode
import com.example.unimate.ui.timetable.TimetableViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableAndCoursesViewModelTest {

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
    fun `time format utils format clock and durations accurately`() {
        assertEquals("12:00 AM", TimeFormatUtils.formatMinutes(0L))
        assertEquals("9:05 AM", TimeFormatUtils.formatMinutes(545L))
        assertEquals("12:30 PM", TimeFormatUtils.formatMinutes(750L))
        assertEquals("1:15 PM", TimeFormatUtils.formatMinutes(795L))

        assertEquals("1h 30m", TimeFormatUtils.formatDuration(540L, 630L))
        assertEquals("1h", TimeFormatUtils.formatDuration(600L, 660L))
        assertEquals("45m", TimeFormatUtils.formatDuration(540L, 585L))
    }

    @Test
    fun `conflict detector catches same day overlaps and ignores adjacent or other days`() {
        val mondayMorning = TimetableClass(
            id = "c1",
            dayOfWeek = "MONDAY",
            subjectName = "DSA",
            courseCode = "CS-301",
            startTimeMinutes = 540L, // 9:00 AM
            endTimeMinutes = 630L,   // 10:30 AM
        )
        val mondayAdjacent = TimetableClass(
            id = "c2",
            dayOfWeek = "MONDAY",
            subjectName = "Architecture",
            courseCode = "SE-312",
            startTimeMinutes = 630L, // 10:30 AM
            endTimeMinutes = 720L,   // 12:00 PM
        )
        val tuesdaySameSlot = TimetableClass(
            id = "c3",
            dayOfWeek = "TUESDAY",
            subjectName = "Calculus",
            courseCode = "MT-101",
            startTimeMinutes = 540L,
            endTimeMinutes = 630L,
        )

        // Adjacent class on Monday should not overlap
        val adjacentResult = TimetableConflictDetector.findConflicts(
            day = DayOfWeekOption.MONDAY,
            startTimeMinutes = 630L,
            endTimeMinutes = 720L,
            excludeClassId = null,
            existingClasses = listOf(mondayMorning, tuesdaySameSlot),
        )
        assertTrue(adjacentResult.isEmpty())

        // Overlapping class on Monday catches mondayMorning only
        val overlappingResult = TimetableConflictDetector.findConflicts(
            day = DayOfWeekOption.MONDAY,
            startTimeMinutes = 600L, // 10:00 AM
            endTimeMinutes = 660L,   // 11:00 AM
            excludeClassId = null,
            existingClasses = listOf(mondayMorning, mondayAdjacent, tuesdaySameSlot),
        )
        assertEquals(listOf("c1", "c2"), overlappingResult.map { it.id })

        // Editing c1 ignores itself
        val selfExcluded = TimetableConflictDetector.findConflicts(
            day = DayOfWeekOption.MONDAY,
            startTimeMinutes = 540L,
            endTimeMinutes = 630L,
            excludeClassId = "c1",
            existingClasses = listOf(mondayMorning, mondayAdjacent),
        )
        assertTrue(selfExcluded.isEmpty())

        // Map computation includes both overlapping entries
        val mondayOverlap = TimetableClass(
            id = "c4",
            dayOfWeek = "MONDAY",
            subjectName = "Networks",
            courseCode = "CS-401",
            startTimeMinutes = 570L,
            endTimeMinutes = 640L,
        )
        val conflictMap = TimetableConflictDetector.computeConflictMap(
            listOf(mondayMorning, mondayAdjacent, tuesdaySameSlot, mondayOverlap),
        )
        assertTrue(conflictMap.containsKey("c1"))
        assertTrue(conflictMap.containsKey("c4"))
        assertTrue(conflictMap.containsKey("c2"))
        assertFalse(conflictMap.containsKey("c3"))
    }

    @Test
    fun `timetable viewmodel transitions loading empty success error and detects live conflicts`() = runTest {
        val repo = InMemoryWorkspaceRepository()
        val viewModel = TimetableViewModel(uid = "uid-1", workspaceRepository = repo)
        assertTrue(viewModel.uiState.value is TimetableUiState.Loading)

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Initially empty
        val emptyState = viewModel.uiState.value
        assertTrue(emptyState is TimetableUiState.Empty)

        // Open add dialog and validate required fields
        viewModel.openAddClassDialog(DayOfWeekOption.WEDNESDAY)
        viewModel.saveClass()
        advanceUntilIdle()
        assertNotNull(viewModel.editorState.value.errorMessage)

        // Fill valid fields and save
        viewModel.onSubjectNameChanged("Operating Systems")
        viewModel.onCourseCodeChanged("CS-320")
        viewModel.onInstructorChanged("Dr. Tariq")
        viewModel.onClassroomChanged("Room 302")
        viewModel.onStartTimeChanged(540L) // 9:00 AM
        viewModel.onEndTimeChanged(630L)   // 10:30 AM
        viewModel.onColorSelected(2L)
        viewModel.saveClass()
        advanceUntilIdle()

        assertFalse(viewModel.editorState.value.isOpen)
        val success1 = viewModel.uiState.value as TimetableUiState.Success
        assertEquals(1, success1.allClasses.size)
        assertEquals(0, success1.totalConflictingClasses)
        assertEquals(DayOfWeekOption.WEDNESDAY, success1.selectedDay)

        // Open add dialog for same day overlapping time -> live conflict detected!
        viewModel.openAddClassDialog(DayOfWeekOption.WEDNESDAY)
        viewModel.onSubjectNameChanged("Linear Algebra")
        viewModel.onCourseCodeChanged("MT-201")
        viewModel.onInstructorChanged("Prof. Raza")
        viewModel.onClassroomChanged("Hall A")
        viewModel.onStartTimeChanged(600L) // 10:00 AM overlaps 9:00-10:30
        viewModel.onEndTimeChanged(690L)
        advanceUntilIdle()

        assertEquals(1, viewModel.editorState.value.liveConflicts.size)
        assertEquals("Operating Systems", viewModel.editorState.value.liveConflicts.first().subjectName)

        // Save overlapping class -> flagged in Success state
        viewModel.saveClass()
        advanceUntilIdle()
        val success2 = viewModel.uiState.value as TimetableUiState.Success
        assertEquals(2, success2.allClasses.size)
        assertEquals(2, success2.totalConflictingClasses)

        // Switch to Weekly mode and edit second class to resolve conflict
        viewModel.selectViewMode(TimetableViewMode.WEEKLY)
        advanceUntilIdle()
        assertEquals(TimetableViewMode.WEEKLY, (viewModel.uiState.value as TimetableUiState.Success).viewMode)

        val secondClass = success2.allClasses.first { it.courseCode == "MT-201" }
        viewModel.openEditClassDialog(secondClass)
        viewModel.onStartTimeChanged(660L) // 11:00 AM -> no conflict
        viewModel.onEndTimeChanged(750L)
        advanceUntilIdle()
        assertTrue(viewModel.editorState.value.liveConflicts.isEmpty())

        viewModel.saveClass()
        advanceUntilIdle()
        val success3 = viewModel.uiState.value as TimetableUiState.Success
        assertEquals(0, success3.totalConflictingClasses)

        // Delete classes returns to Empty
        success3.allClasses.forEach { item ->
            viewModel.deleteClass(item.id)
            advanceUntilIdle()
        }
        assertTrue(viewModel.uiState.value is TimetableUiState.Empty)

        // Simulate Firestore failure and retry recovery
        repo.failTimetableStream = true
        viewModel.retry()
        advanceUntilIdle()
        val errorState = viewModel.uiState.value
        assertTrue(errorState is TimetableUiState.Error)
        assertEquals("Firestore offline", (errorState as TimetableUiState.Error).message)

        repo.failTimetableStream = false
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is TimetableUiState.Empty)

        collectJob.cancel()
    }

    @Test
    fun `courses viewmodel transitions loading empty success error and manages full lifecycle`() = runTest {
        val repo = InMemoryWorkspaceRepository()
        val viewModel = CoursesViewModel(uid = "uid-1", workspaceRepository = repo)
        assertTrue(viewModel.uiState.value is CoursesUiState.Loading)

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CoursesUiState.Empty)

        // Open add course dialog and verify validation rejects invalid credit hours
        viewModel.openAddCourseDialog(defaultSemester = "5th Semester")
        viewModel.onCourseCodeChanged("CS-301")
        viewModel.onCourseNameChanged("Data Structures")
        viewModel.onCreditHoursChanged("20.0") // invalid (> 12.0)
        viewModel.onInstructorChanged("Dr. Sana")
        viewModel.onClassroomChanged("AB-2 204")
        viewModel.onScheduleChanged("Mon & Wed 09:00 AM")
        viewModel.saveCourse()
        advanceUntilIdle()
        assertNotNull(viewModel.editorState.value.errorMessage)

        // Fix credit hours and save
        viewModel.onCreditHoursChanged("4.0")
        viewModel.onDescriptionChanged("Trees, graphs, and dynamic programming.")
        viewModel.onAttendedChanged("8")
        viewModel.onTotalChanged("10")
        viewModel.saveCourse()
        advanceUntilIdle()

        assertFalse(viewModel.editorState.value.isOpen)
        val success1 = viewModel.uiState.value as CoursesUiState.Success
        assertEquals(1, success1.courses.size)
        assertEquals(4.0, success1.totalCreditHours, 0.001)
        assertEquals(80, success1.courses.first().attendancePercentage())

        // Mark attendance present -> 9/11
        viewModel.markAttendance(success1.courses.first(), present = true)
        advanceUntilIdle()
        val success2 = viewModel.uiState.value as CoursesUiState.Success
        assertEquals(9L, success2.courses.first().attendedClasses)
        assertEquals(11L, success2.courses.first().totalClasses)

        // Edit course description & credit hours
        viewModel.openEditCourseDialog(success2.courses.first())
        viewModel.onCreditHoursChanged("3.5")
        viewModel.onClassroomChanged("CS Lab 1")
        viewModel.saveCourse()
        advanceUntilIdle()
        val success3 = viewModel.uiState.value as CoursesUiState.Success
        assertEquals(3.5, success3.totalCreditHours, 0.001)
        assertEquals("CS Lab 1", success3.courses.first().classroom)

        // Delete course returns to Empty
        viewModel.deleteCourse(success3.courses.first().id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CoursesUiState.Empty)

        // Simulate Firestore failure and retry recovery
        repo.failCoursesStream = true
        viewModel.retry()
        advanceUntilIdle()
        val errorState = viewModel.uiState.value
        assertTrue(errorState is CoursesUiState.Error)
        assertEquals("Permission denied", (errorState as CoursesUiState.Error).message)

        repo.failCoursesStream = false
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CoursesUiState.Empty)

        collectJob.cancel()
    }

    @Test
    fun `timetable and course payloads match firestore schema keys`() {
        val ttPayload = WorkspaceMappers.toCreateTimetableClassPayload(
            uid = "uid-1",
            request = SaveTimetableClassRequest(
                dayOfWeek = DayOfWeekOption.FRIDAY,
                subjectName = "  Machine Learning ",
                courseCode = " AI-305 ",
                instructor = " Dr. Bilal ",
                classroom = " Auditorium B ",
                startTimeMinutes = 840L,
                endTimeMinutes = 960L,
                colorIndex = 5L,
            ),
        )
        assertEquals("FRIDAY", ttPayload["dayOfWeek"])
        assertEquals("Machine Learning", ttPayload["subjectName"])
        assertEquals("AI-305", ttPayload["courseCode"])
        assertEquals(840L, ttPayload["startTimeMinutes"])
        assertEquals(960L, ttPayload["endTimeMinutes"])
        assertEquals(5L, ttPayload["colorIndex"])
        assertFalse(ttPayload.containsKey("id"))
    }
}

private class InMemoryWorkspaceRepository : WorkspaceRepository {
    var failTimetableStream = false
    var failCoursesStream = false

    private val coursesFlow = MutableStateFlow<List<StudentCourse>>(emptyList())
    private val timetableFlow = MutableStateFlow<List<TimetableClass>>(emptyList())
    private val tasksFlow = MutableStateFlow<List<AcademicTask>>(emptyList())
    private val notesFlow = MutableStateFlow<List<StudyNote>>(emptyList())
    private val remindersFlow = MutableStateFlow<List<StudentReminder>>(emptyList())
    private val goalsFlow = MutableStateFlow<List<StudyGoal>>(emptyList())

    override fun observeTasks(uid: String): Flow<List<AcademicTask>> = tasksFlow

    override fun observeCourses(uid: String): Flow<List<StudentCourse>> = flow {
        if (failCoursesStream) throw IllegalStateException("Permission denied")
        coursesFlow.collect { emit(it) }
    }

    override fun observeTimetable(uid: String): Flow<List<TimetableClass>> = flow {
        if (failTimetableStream) throw IllegalStateException("Firestore offline")
        timetableFlow.collect { emit(it) }
    }

    override fun observeNotes(uid: String): Flow<List<StudyNote>> = notesFlow
    override fun observeReminders(uid: String): Flow<List<StudentReminder>> = remindersFlow
    override fun observeGoals(uid: String): Flow<List<StudyGoal>> = goalsFlow

    override suspend fun addTask(uid: String, request: AddTaskRequest): Result<Unit> = Result.success(Unit)
    override suspend fun toggleTaskCompleted(uid: String, taskId: String, completed: Boolean): Result<Unit> =
        Result.success(Unit)
    override suspend fun deleteTask(uid: String, taskId: String): Result<Unit> = Result.success(Unit)

    override suspend fun addCourse(uid: String, request: SaveCourseRequest): Result<Unit> {
        val next = StudentCourse(
            id = "course-${coursesFlow.value.size + 1}",
            uid = uid,
            courseCode = request.courseCode,
            courseName = request.courseName,
            creditHours = request.creditHours,
            instructor = request.instructor,
            semester = request.semester,
            description = request.description,
            classroom = request.classroom,
            schedule = request.schedule,
            attendedClasses = request.attendedClasses,
            totalClasses = request.totalClasses,
            colorIndex = request.colorIndex,
        )
        coursesFlow.value = coursesFlow.value + next
        return Result.success(Unit)
    }

    override suspend fun updateCourse(uid: String, courseId: String, request: SaveCourseRequest): Result<Unit> {
        coursesFlow.value = coursesFlow.value.map { existing ->
            if (existing.id == courseId) {
                existing.copy(
                    courseCode = request.courseCode,
                    courseName = request.courseName,
                    creditHours = request.creditHours,
                    instructor = request.instructor,
                    semester = request.semester,
                    description = request.description,
                    classroom = request.classroom,
                    schedule = request.schedule,
                    attendedClasses = request.attendedClasses,
                    totalClasses = request.totalClasses,
                    colorIndex = request.colorIndex,
                )
            } else {
                existing
            }
        }
        return Result.success(Unit)
    }

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

    override suspend fun deleteCourse(uid: String, courseId: String): Result<Unit> {
        coursesFlow.value = coursesFlow.value.filterNot { it.id == courseId }
        return Result.success(Unit)
    }

    override suspend fun addTimetableClass(uid: String, request: SaveTimetableClassRequest): Result<Unit> {
        val next = TimetableClass(
            id = "tt-${timetableFlow.value.size + 1}",
            uid = uid,
            dayOfWeek = request.dayOfWeek.wireValue,
            subjectName = request.subjectName,
            courseCode = request.courseCode,
            instructor = request.instructor,
            classroom = request.classroom,
            startTimeMinutes = request.startTimeMinutes,
            endTimeMinutes = request.endTimeMinutes,
            colorIndex = request.colorIndex,
        )
        timetableFlow.value = timetableFlow.value + next
        return Result.success(Unit)
    }

    override suspend fun updateTimetableClass(
        uid: String,
        classId: String,
        request: SaveTimetableClassRequest,
    ): Result<Unit> {
        timetableFlow.value = timetableFlow.value.map { existing ->
            if (existing.id == classId) {
                existing.copy(
                    dayOfWeek = request.dayOfWeek.wireValue,
                    subjectName = request.subjectName,
                    courseCode = request.courseCode,
                    instructor = request.instructor,
                    classroom = request.classroom,
                    startTimeMinutes = request.startTimeMinutes,
                    endTimeMinutes = request.endTimeMinutes,
                    colorIndex = request.colorIndex,
                )
            } else {
                existing
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteTimetableClass(uid: String, classId: String): Result<Unit> {
        timetableFlow.value = timetableFlow.value.filterNot { it.id == classId }
        return Result.success(Unit)
    }

    override suspend fun addNote(uid: String, request: AddNoteRequest): Result<Unit> = Result.success(Unit)
    override suspend fun deleteNote(uid: String, noteId: String): Result<Unit> = Result.success(Unit)
    override suspend fun addReminder(uid: String, request: AddReminderRequest): Result<Unit> = Result.success(Unit)
    override suspend fun toggleReminderDone(uid: String, reminderId: String, done: Boolean): Result<Unit> =
        Result.success(Unit)
    override suspend fun deleteReminder(uid: String, reminderId: String): Result<Unit> = Result.success(Unit)
    override suspend fun addGoal(uid: String, request: AddGoalRequest): Result<Unit> = Result.success(Unit)
    override suspend fun updateGoalProgress(
        uid: String,
        goalId: String,
        completedSteps: Long,
        targetSteps: Long,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun deleteGoal(uid: String, goalId: String): Result<Unit> = Result.success(Unit)
}
