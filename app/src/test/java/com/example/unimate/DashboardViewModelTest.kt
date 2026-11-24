package com.example.unimate

import com.example.unimate.data.model.AcademicTask
import com.example.unimate.data.model.AddGoalRequest
import com.example.unimate.data.model.AddNoteRequest
import com.example.unimate.data.model.AddReminderRequest
import com.example.unimate.data.model.AddTaskRequest
import com.example.unimate.data.model.GpaCalculator
import com.example.unimate.data.model.GpaCourseEntry
import com.example.unimate.data.model.LetterGrade
import com.example.unimate.data.model.SaveCourseRequest
import com.example.unimate.data.model.SaveTimetableClassRequest
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.StudentReminder
import com.example.unimate.data.model.StudyGoal
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.model.TaskCategory
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.data.model.WorkspaceMappers
import com.example.unimate.data.repository.WorkspaceRepository
import com.example.unimate.ui.ai.AiAssistantViewModel
import com.example.unimate.ui.ai.AiStudyEngine
import com.example.unimate.ui.ai.AiUiState
import com.example.unimate.ui.dashboard.DashboardDialog
import com.example.unimate.ui.dashboard.DashboardTab
import com.example.unimate.ui.dashboard.MainDashboardViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

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
    fun `workspace mappers omit document id and use server timestamps`() {
        val taskMap = WorkspaceMappers.toCreateTaskPayload(
            uid = "uid-1",
            request = AddTaskRequest(
                title = "  OS Lab 3 ",
                courseName = "CS-320",
                category = TaskCategory.EXAM,
                dueDateText = "Next Tue",
            ),
        )
        assertFalse(taskMap.containsKey("id"))
        assertEquals("OS Lab 3", taskMap["title"])
        assertEquals("EXAM", taskMap["category"])
        assertEquals(false, taskMap["completed"])
        assertTrue(taskMap["createdAt"] is FieldValue)
        assertTrue(taskMap["updatedAt"] is FieldValue)

        val courseMap = WorkspaceMappers.toCreateCoursePayload(
            uid = "uid-1",
            request = SaveCourseRequest(
                courseCode = "CS-320",
                courseName = "Operating Systems",
                creditHours = 3.0,
                instructor = "Dr. Ahmed",
                semester = "5th Semester",
                description = "Processes, threads, scheduling, and memory management.",
                classroom = "Lab 2",
                schedule = "Mon & Wed • 10:00 AM - 11:30 AM",
                attendedClasses = 9L,
                totalClasses = 10L,
                colorIndex = 2L,
            ),
        )
        assertFalse(courseMap.containsKey("id"))
        assertEquals(3.0, courseMap["creditHours"])
        assertEquals("Dr. Ahmed", courseMap["instructor"])
        assertEquals(9L, courseMap["attendedClasses"])
        assertEquals(10L, courseMap["totalClasses"])
        assertEquals(2L, courseMap["colorIndex"])
    }

    @Test
    fun `gpa calculator computes weighted semester gpa accurately`() {
        val entries = listOf(
            GpaCourseEntry("1", "DSA", 4.0, LetterGrade.A), // 16.0
            GpaCourseEntry("2", "DB", 3.0, LetterGrade.B), // 9.0
        )
        assertEquals(7.0, GpaCalculator.totalCredits(entries), 0.001)
        assertEquals(25.0 / 7.0, GpaCalculator.calculateGpa(entries), 0.001)
        assertEquals(0.0, GpaCalculator.calculateGpa(emptyList()), 0.001)
    }

    @Test
    fun `dashboard clearly identifies demo mode when empty and switches to real data when saved`() = runTest {
        val repo = FakeWorkspaceRepository()
        val viewModel = MainDashboardViewModel(uid = "uid-1", workspaceRepository = repo)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasRealData)
        assertTrue(viewModel.uiState.value.isShowingDemoData)
        assertTrue(viewModel.uiState.value.tasks.all { it.id.startsWith("demo-") })

        // User can explicitly hide demo preview
        viewModel.setDemoPreviewEnabled(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isShowingDemoData)
        assertTrue(viewModel.uiState.value.tasks.isEmpty())

        // Adding a real task exits demo mode and updates live metrics
        viewModel.openDialog(DashboardDialog.ADD_TASK)
        viewModel.addTask("Kernel Lab", "CS-320", TaskCategory.ASSIGNMENT, "Tomorrow")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeDialog)
        assertTrue(viewModel.uiState.value.hasRealData)
        assertFalse(viewModel.uiState.value.isShowingDemoData)
        assertEquals(1, viewModel.uiState.value.metrics.pendingTasksCount)

        // Switching tabs updates activeTab cleanly
        viewModel.selectTab(DashboardTab.PLANNER)
        advanceUntilIdle()
        assertEquals(DashboardTab.PLANNER, viewModel.uiState.value.activeTab)

        collectJob.cancel()
    }

    @Test
    fun `ai assistant streams chunks and surfaces authentic errors`() = runTest {
        val successEngine = object : AiStudyEngine {
            override fun generateStream(prompt: String): Flow<String> = flow {
                emit("Step 1: Review graphs. ")
                emit("Step 2: Practice BFS.")
            }
        }
        val aiViewModel = AiAssistantViewModel(studyEngine = successEngine)
        aiViewModel.onInputChanged("Help me study graphs")
        aiViewModel.sendPrompt()
        advanceUntilIdle()

        val state = aiViewModel.uiState.value
        assertTrue(state.inferenceState is AiUiState.Success)
        assertEquals(2, state.messages.size)
        assertEquals("Step 1: Review graphs. Step 2: Practice BFS.", state.messages.last().text)

        val failingEngine = object : AiStudyEngine {
            override fun generateStream(prompt: String): Flow<String> = flow {
                throw IllegalStateException("App Check attestation unavailable")
            }
        }
        val errorViewModel = AiAssistantViewModel(studyEngine = failingEngine)
        errorViewModel.sendPrompt(promptOverride = "Test error")
        advanceUntilIdle()

        val errState = errorViewModel.uiState.value.inferenceState
        assertTrue(errState is AiUiState.Error)
        assertEquals("App Check attestation unavailable", (errState as AiUiState.Error).message)
    }
}

private class FakeWorkspaceRepository : WorkspaceRepository {
    private val tasksFlow = MutableStateFlow<List<AcademicTask>>(emptyList())
    private val coursesFlow = MutableStateFlow<List<StudentCourse>>(emptyList())
    private val timetableFlow = MutableStateFlow<List<TimetableClass>>(emptyList())
    private val notesFlow = MutableStateFlow<List<StudyNote>>(emptyList())
    private val remindersFlow = MutableStateFlow<List<StudentReminder>>(emptyList())
    private val goalsFlow = MutableStateFlow<List<StudyGoal>>(emptyList())

    override fun observeTasks(uid: String): Flow<List<AcademicTask>> = tasksFlow
    override fun observeCourses(uid: String): Flow<List<StudentCourse>> = coursesFlow
    override fun observeTimetable(uid: String): Flow<List<TimetableClass>> = timetableFlow
    override fun observeNotes(uid: String): Flow<List<StudyNote>> = notesFlow
    override fun observeReminders(uid: String): Flow<List<StudentReminder>> = remindersFlow
    override fun observeGoals(uid: String): Flow<List<StudyGoal>> = goalsFlow

    override suspend fun addTask(uid: String, request: AddTaskRequest): Result<Unit> {
        val item = AcademicTask(
            id = "real-task-${tasksFlow.value.size + 1}",
            uid = uid,
            title = request.title,
            courseName = request.courseName,
            category = request.category.wireValue,
            dueDateText = request.dueDateText,
            completed = false,
        )
        tasksFlow.value = tasksFlow.value + item
        return Result.success(Unit)
    }

    override suspend fun toggleTaskCompleted(uid: String, taskId: String, completed: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteTask(uid: String, taskId: String): Result<Unit> = Result.success(Unit)

    override suspend fun addCourse(uid: String, request: SaveCourseRequest): Result<Unit> = Result.success(Unit)

    override suspend fun updateCourse(uid: String, courseId: String, request: SaveCourseRequest): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateCourseAttendance(
        uid: String,
        courseId: String,
        attendedClasses: Long,
        totalClasses: Long,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteCourse(uid: String, courseId: String): Result<Unit> = Result.success(Unit)

    override suspend fun addTimetableClass(uid: String, request: SaveTimetableClassRequest): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateTimetableClass(
        uid: String,
        classId: String,
        request: SaveTimetableClassRequest,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteTimetableClass(uid: String, classId: String): Result<Unit> = Result.success(Unit)

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
