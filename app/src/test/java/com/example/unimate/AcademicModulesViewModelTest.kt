package com.example.unimate

import com.example.unimate.data.model.AcademicDateFormatters
import com.example.unimate.data.model.AcademicModulesMappers
import com.example.unimate.data.model.AssignmentAttachment
import com.example.unimate.data.model.AssignmentFilter
import com.example.unimate.data.model.AssignmentPriority
import com.example.unimate.data.model.AssignmentSortOrder
import com.example.unimate.data.model.AssignmentStatus
import com.example.unimate.data.model.ExamPreparationStatus
import com.example.unimate.data.model.ExamType
import com.example.unimate.data.model.ExamTypeFilter
import com.example.unimate.data.model.QuizPreparationStatus
import com.example.unimate.data.model.QuizSectionFilter
import com.example.unimate.data.model.RevisionChecklistItem
import com.example.unimate.data.model.SaveAssignmentRequest
import com.example.unimate.data.model.SaveExamRequest
import com.example.unimate.data.model.SaveQuizRequest
import com.example.unimate.data.model.StudentAssignment
import com.example.unimate.data.model.StudentExam
import com.example.unimate.data.model.StudentQuiz
import com.example.unimate.data.repository.AcademicModulesRepository
import com.example.unimate.ui.assignments.AssignmentsUiState
import com.example.unimate.ui.assignments.AssignmentsViewModel
import com.example.unimate.ui.dashboard.DashboardTab
import com.example.unimate.ui.dashboard.MainDashboardViewModel
import com.example.unimate.ui.dashboard.TasksSubTab
import com.example.unimate.ui.exams.ExamsUiState
import com.example.unimate.ui.exams.ExamsViewModel
import com.example.unimate.ui.quizzes.QuizzesUiState
import com.example.unimate.ui.quizzes.QuizzesViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcademicModulesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedNow = 1_700_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `mappers omit document ids and date formatters render countdowns accurately`() {
        val assignMap = AcademicModulesMappers.toCreateAssignmentPayload(
            uid = "uid-1",
            request = SaveAssignmentRequest(
                title = "  Parser Lab ",
                description = "Implement LL(1)",
                courseName = "CS-312",
                deadlineMillis = fixedNow + 86_400_000L,
                priority = AssignmentPriority.HIGH,
                status = AssignmentStatus.PENDING,
                attachments = listOf(AssignmentAttachment("spec.pdf", "https://example.com/spec.pdf", "text/uri-list")),
            ),
        )
        assertFalse(assignMap.containsKey("id"))
        assertEquals("Parser Lab", assignMap["title"])
        assertEquals("HIGH", assignMap["priority"])
        assertTrue(assignMap["createdAt"] is FieldValue)

        assertEquals("In 2d 3h", AcademicDateFormatters.formatCountdown(fixedNow + (2 * 24 + 3) * 3_600_000L, fixedNow))
        assertEquals("1d overdue", AcademicDateFormatters.formatCountdown(fixedNow - 25 * 3_600_000L, fixedNow))
    }

    @Test
    fun `assignments viewmodel supports crud, attachments, search, filters, sorting, and error retry`() = runTest {
        val repo = FakeAcademicModulesRepository()
        val vm = AssignmentsViewModel(uid = "uid-1", repository = repo, clockMillis = { fixedNow })
        val collectJob = launch { vm.uiState.collect {} }

        assertEquals(AssignmentsUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(AssignmentsUiState.Empty, vm.uiState.value)

        // Add overdue high-priority assignment with file + URL attachments
        vm.openAddDialog()
        vm.onTitleChanged("Alpha Compiler Lab")
        vm.onCourseChanged("CS-312")
        vm.onDescriptionChanged("Build AST lexer")
        vm.onDeadlineChanged(fixedNow - 2 * 86_400_000L) // overdue
        vm.onPriorityChanged(AssignmentPriority.HIGH)
        vm.addPickedAttachment("rubric.pdf", "content://docs/rubric.pdf", "application/pdf")
        vm.onLinkDraftUriChanged("kotlinlang.org/docs")
        vm.onLinkDraftNameChanged("Kotlin Docs")
        vm.addLinkAttachmentFromDraft()
        vm.saveAssignment()
        advanceUntilIdle()

        // Add future low-priority completed & pending assignments
        vm.openAddDialog()
        vm.onTitleChanged("Beta OS Essay")
        vm.onCourseChanged("CS-320")
        vm.onDeadlineChanged(fixedNow + 4 * 86_400_000L)
        vm.onPriorityChanged(AssignmentPriority.LOW)
        vm.saveAssignment()
        advanceUntilIdle()

        val success = vm.uiState.value as AssignmentsUiState.Success
        assertEquals(2, success.allAssignments.size)
        assertEquals(2, success.pendingCount)
        assertEquals(1, success.overdueCount)
        assertEquals(2, success.allAssignments.first().attachments.size)

        // Filter Overdue
        vm.selectFilter(AssignmentFilter.OVERDUE)
        advanceUntilIdle()
        val overdueOnly = vm.uiState.value as AssignmentsUiState.Success
        assertEquals(1, overdueOnly.filteredAssignments.size)
        assertEquals("Alpha Compiler Lab", overdueOnly.filteredAssignments.first().title)

        // Search + Sort by Title / Deadline Desc
        vm.selectFilter(AssignmentFilter.ALL)
        vm.selectSortOrder(AssignmentSortOrder.DEADLINE_DESC)
        advanceUntilIdle()
        assertEquals("Beta OS Essay", (vm.uiState.value as AssignmentsUiState.Success).filteredAssignments.first().title)

        vm.onSearchQueryChanged("lexer")
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as AssignmentsUiState.Success).filteredAssignments.size)

        // Toggle Completed & Edit & Delete
        vm.onSearchQueryChanged("")
        val alpha = (vm.uiState.value as AssignmentsUiState.Success).allAssignments.first { it.title == "Alpha Compiler Lab" }
        vm.toggleCompleted(alpha)
        advanceUntilIdle()

        val afterToggle = vm.uiState.value as AssignmentsUiState.Success
        assertEquals(1, afterToggle.completedCount)
        assertEquals(1, afterToggle.pendingCount)
        assertEquals(0, afterToggle.overdueCount)

        vm.openEditDialog(alpha)
        vm.onTitleChanged("Alpha Compiler Lab v2")
        vm.saveAssignment()
        advanceUntilIdle()
        assertTrue((vm.uiState.value as AssignmentsUiState.Success).allAssignments.any { it.title == "Alpha Compiler Lab v2" })

        vm.deleteAssignment(alpha.id)
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as AssignmentsUiState.Success).allAssignments.size)

        // Error + Retry
        repo.failAssignmentsOnce = true
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is AssignmentsUiState.Error)

        repo.failAssignmentsOnce = false
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is AssignmentsUiState.Success)

        collectJob.cancel()
    }

    @Test
    fun `quizzes viewmodel handles upcoming, history with marks validation, preparation status, and crud`() = runTest {
        val repo = FakeAcademicModulesRepository()
        val vm = QuizzesViewModel(uid = "uid-1", repository = repo, clockMillis = { fixedNow })
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(QuizzesUiState.Empty, vm.uiState.value)

        // Add upcoming quiz
        vm.openAddDialog()
        vm.onTitleChanged("Quiz 1: ER Diagrams")
        vm.onCourseChanged("CS-310")
        vm.onScheduledAtChanged(fixedNow + 3 * 86_400_000L)
        vm.onTotalMarksChanged("20")
        vm.onPreparationStatusChanged(QuizPreparationStatus.IN_PROGRESS)
        vm.saveQuiz()
        advanceUntilIdle()

        val state1 = vm.uiState.value as QuizzesUiState.Success
        assertEquals(1, state1.upcomingQuizzes.size)
        assertEquals(0, state1.historyQuizzes.size)

        val quiz1 = state1.upcomingQuizzes.first()
        vm.updatePreparationStatus(quiz1, QuizPreparationStatus.READY)
        advanceUntilIdle()
        assertEquals(
            QuizPreparationStatus.READY,
            (vm.uiState.value as QuizzesUiState.Success).upcomingQuizzes.first().resolvedPreparationStatus(),
        )

        // Obtained marks > total marks shows validation error
        vm.openEditDialog(quiz1)
        vm.onScoreRecordedChanged(true)
        vm.onObtainedMarksChanged("25")
        vm.saveQuiz()
        assertNotNull(vm.editorState.value.errorMessage)

        // Valid score moves quiz to Quiz History
        vm.onObtainedMarksChanged("18")
        vm.saveQuiz()
        advanceUntilIdle()

        vm.selectSection(QuizSectionFilter.HISTORY)
        advanceUntilIdle()
        val historyState = vm.uiState.value as QuizzesUiState.Success
        assertEquals(1, historyState.historyQuizzes.size)
        assertEquals(90, historyState.historyQuizzes.first().scorePercent())
        assertEquals(1, historyState.visibleQuizzes.size)

        vm.deleteQuiz(quiz1.id)
        advanceUntilIdle()
        assertEquals(QuizzesUiState.Empty, vm.uiState.value)

        // Error + Retry
        repo.failQuizzesOnce = true
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is QuizzesUiState.Error)

        repo.failQuizzesOnce = false
        vm.retry()
        advanceUntilIdle()
        assertEquals(QuizzesUiState.Empty, vm.uiState.value)

        collectJob.cancel()
    }

    @Test
    fun `exams viewmodel handles midterm final filters, checklist progress, countdowns, and crud`() = runTest {
        val repo = FakeAcademicModulesRepository()
        val vm = ExamsViewModel(uid = "uid-1", repository = repo, clockMillis = { fixedNow })
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(ExamsUiState.Empty, vm.uiState.value)

        // Add Midterm with 2 checklist topics
        vm.openAddDialog()
        vm.onExamTypeChanged(ExamType.MIDTERM)
        vm.onTitleChanged("OS Midterm")
        vm.onCourseChanged("CS-320")
        vm.onRoomChanged("Hall A-101")
        vm.onExamAtChanged(fixedNow + 5 * 86_400_000L)
        vm.onChecklistTopicDraftChanged("CPU Scheduling")
        vm.addChecklistTopicInEditor()
        vm.onChecklistTopicDraftChanged("Deadlocks")
        vm.addChecklistTopicInEditor()
        vm.saveExam()
        advanceUntilIdle()

        // Add Final exam
        vm.openAddDialog()
        vm.onExamTypeChanged(ExamType.FINAL)
        vm.onTitleChanged("Algorithms Final")
        vm.onCourseChanged("CS-301")
        vm.onRoomChanged("Auditorium")
        vm.onExamAtChanged(fixedNow + 14 * 86_400_000L)
        vm.saveExam()
        advanceUntilIdle()

        val allSuccess = vm.uiState.value as ExamsUiState.Success
        assertEquals(1, allSuccess.midtermCount)
        assertEquals(1, allSuccess.finalCount)

        // Filter Midterm only
        vm.selectTypeFilter(ExamTypeFilter.MIDTERM)
        advanceUntilIdle()
        val midterms = (vm.uiState.value as ExamsUiState.Success).filteredExams
        assertEquals(1, midterms.size)
        val osExam = midterms.first()
        assertEquals("In 5d 0h", osExam.countdownLabel(fixedNow))

        // Toggle 1st checklist topic -> REVISING (0.5f)
        vm.toggleChecklistTopicOnCard(osExam, osExam.revisionChecklist[0].id)
        advanceUntilIdle()
        val afterFirstTopic = (vm.uiState.value as ExamsUiState.Success).filteredExams.first()
        assertEquals(1, afterFirstTopic.revisedCount())
        assertEquals(0.5f, afterFirstTopic.revisionProgressFraction(), 0.01f)
        assertEquals(ExamPreparationStatus.REVISING, afterFirstTopic.resolvedPreparationStatus())

        // Toggle 2nd checklist topic -> READY (1.0f)
        vm.toggleChecklistTopicOnCard(afterFirstTopic, afterFirstTopic.revisionChecklist[1].id)
        advanceUntilIdle()
        val afterSecondTopic = (vm.uiState.value as ExamsUiState.Success).filteredExams.first()
        assertEquals(2, afterSecondTopic.revisedCount())
        assertEquals(1.0f, afterSecondTopic.revisionProgressFraction(), 0.01f)
        assertEquals(ExamPreparationStatus.READY, afterSecondTopic.resolvedPreparationStatus())

        // Update preparation status & Delete
        vm.updatePreparationStatus(afterSecondTopic, ExamPreparationStatus.COMPLETED)
        advanceUntilIdle()
        assertEquals(
            ExamPreparationStatus.COMPLETED,
            (vm.uiState.value as ExamsUiState.Success).filteredExams.first().resolvedPreparationStatus(),
        )

        vm.deleteExam(osExam.id)
        advanceUntilIdle()
        assertEquals(0, (vm.uiState.value as ExamsUiState.Success).midtermCount)

        // Error + Retry
        repo.failExamsOnce = true
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is ExamsUiState.Error)

        collectJob.cancel()
    }

    @Test
    fun `dashboard viewmodel routes openTasksSection directly to requested academic module`() = runTest {
        val fakeModulesRepo = FakeAcademicModulesRepository()
        val vm = MainDashboardViewModel(
            uid = "uid-1",
            workspaceRepository = NoOpWorkspaceRepository(),
            academicModulesRepository = fakeModulesRepo,
        )
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openTasksSection(TasksSubTab.EXAMS)
        advanceUntilIdle()
        assertEquals(DashboardTab.TASKS, vm.uiState.value.activeTab)
        assertEquals(TasksSubTab.EXAMS, vm.uiState.value.tasksSubTab)

        vm.selectTasksSubTab(TasksSubTab.QUIZZES)
        advanceUntilIdle()
        assertEquals(TasksSubTab.QUIZZES, vm.uiState.value.tasksSubTab)

        job.cancel()
    }
}

private class FakeAcademicModulesRepository : AcademicModulesRepository {
    private val assignments = MutableStateFlow<List<StudentAssignment>>(emptyList())
    private val quizzes = MutableStateFlow<List<StudentQuiz>>(emptyList())
    private val exams = MutableStateFlow<List<StudentExam>>(emptyList())

    var failAssignmentsOnce = false
    var failQuizzesOnce = false
    var failExamsOnce = false

    override fun observeAssignments(uid: String): Flow<List<StudentAssignment>> =
        if (failAssignmentsOnce) flow { throw IllegalStateException("Network unavailable") } else assignments

    override fun observeQuizzes(uid: String): Flow<List<StudentQuiz>> =
        if (failQuizzesOnce) flow { throw IllegalStateException("Firestore timeout") } else quizzes

    override fun observeExams(uid: String): Flow<List<StudentExam>> =
        if (failExamsOnce) flow { throw IllegalStateException("Permission denied") } else exams

    override suspend fun addAssignment(uid: String, request: SaveAssignmentRequest): Result<Unit> {
        val next = StudentAssignment(
            id = "a-${assignments.value.size + 1}",
            uid = uid,
            title = request.title,
            description = request.description,
            courseName = request.courseName,
            deadlineMillis = request.deadlineMillis,
            priority = request.priority.wireValue,
            status = request.status.wireValue,
            attachments = request.attachments,
        )
        assignments.value = assignments.value + next
        return Result.success(Unit)
    }

    override suspend fun updateAssignment(uid: String, assignmentId: String, request: SaveAssignmentRequest): Result<Unit> {
        assignments.value = assignments.value.map {
            if (it.id == assignmentId) {
                it.copy(
                    title = request.title,
                    description = request.description,
                    courseName = request.courseName,
                    deadlineMillis = request.deadlineMillis,
                    priority = request.priority.wireValue,
                    status = request.status.wireValue,
                    attachments = request.attachments,
                )
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun updateAssignmentStatus(uid: String, assignmentId: String, status: AssignmentStatus): Result<Unit> {
        assignments.value = assignments.value.map {
            if (it.id == assignmentId) it.copy(status = status.wireValue) else it
        }
        return Result.success(Unit)
    }

    override suspend fun deleteAssignment(uid: String, assignmentId: String): Result<Unit> {
        assignments.value = assignments.value.filterNot { it.id == assignmentId }
        return Result.success(Unit)
    }

    override suspend fun addQuiz(uid: String, request: SaveQuizRequest): Result<Unit> {
        val next = StudentQuiz(
            id = "q-${quizzes.value.size + 1}",
            uid = uid,
            title = request.title,
            courseName = request.courseName,
            scheduledAtMillis = request.scheduledAtMillis,
            totalMarks = request.totalMarks,
            obtainedMarks = request.obtainedMarks,
            scoreRecorded = request.scoreRecorded,
            preparationStatus = request.preparationStatus.wireValue,
            notes = request.notes,
        )
        quizzes.value = quizzes.value + next
        return Result.success(Unit)
    }

    override suspend fun updateQuiz(uid: String, quizId: String, request: SaveQuizRequest): Result<Unit> {
        quizzes.value = quizzes.value.map {
            if (it.id == quizId) {
                it.copy(
                    title = request.title,
                    courseName = request.courseName,
                    scheduledAtMillis = request.scheduledAtMillis,
                    totalMarks = request.totalMarks,
                    obtainedMarks = request.obtainedMarks,
                    scoreRecorded = request.scoreRecorded,
                    preparationStatus = request.preparationStatus.wireValue,
                    notes = request.notes,
                )
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun updateQuizStatusAndScore(
        uid: String,
        quizId: String,
        preparationStatus: QuizPreparationStatus,
        scoreRecorded: Boolean,
        obtainedMarks: Double,
    ): Result<Unit> {
        quizzes.value = quizzes.value.map {
            if (it.id == quizId) {
                it.copy(
                    preparationStatus = preparationStatus.wireValue,
                    scoreRecorded = scoreRecorded,
                    obtainedMarks = obtainedMarks,
                )
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteQuiz(uid: String, quizId: String): Result<Unit> {
        quizzes.value = quizzes.value.filterNot { it.id == quizId }
        return Result.success(Unit)
    }

    override suspend fun addExam(uid: String, request: SaveExamRequest): Result<Unit> {
        val next = StudentExam(
            id = "e-${exams.value.size + 1}",
            uid = uid,
            title = request.title,
            examType = request.examType.wireValue,
            courseName = request.courseName,
            examAtMillis = request.examAtMillis,
            room = request.room,
            preparationStatus = request.preparationStatus.wireValue,
            revisionChecklist = request.revisionChecklist,
        )
        exams.value = exams.value + next
        return Result.success(Unit)
    }

    override suspend fun updateExam(uid: String, examId: String, request: SaveExamRequest): Result<Unit> {
        exams.value = exams.value.map {
            if (it.id == examId) {
                it.copy(
                    title = request.title,
                    examType = request.examType.wireValue,
                    courseName = request.courseName,
                    examAtMillis = request.examAtMillis,
                    room = request.room,
                    preparationStatus = request.preparationStatus.wireValue,
                    revisionChecklist = request.revisionChecklist,
                )
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun updateExamChecklistAndStatus(
        uid: String,
        examId: String,
        checklist: List<RevisionChecklistItem>,
        preparationStatus: ExamPreparationStatus,
    ): Result<Unit> {
        exams.value = exams.value.map {
            if (it.id == examId) {
                it.copy(revisionChecklist = checklist, preparationStatus = preparationStatus.wireValue)
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteExam(uid: String, examId: String): Result<Unit> {
        exams.value = exams.value.filterNot { it.id == examId }
        return Result.success(Unit)
    }
}

private class NoOpWorkspaceRepository : com.example.unimate.data.repository.WorkspaceRepository {
    override fun observeTasks(uid: String) = MutableStateFlow(emptyList<com.example.unimate.data.model.AcademicTask>())
    override fun observeCourses(uid: String) = MutableStateFlow(emptyList<com.example.unimate.data.model.StudentCourse>())
    override fun observeTimetable(uid: String) = MutableStateFlow(emptyList<com.example.unimate.data.model.TimetableClass>())
    override fun observeNotes(uid: String) = MutableStateFlow(emptyList<com.example.unimate.data.model.StudyNote>())
    override fun observeReminders(uid: String) = MutableStateFlow(emptyList<com.example.unimate.data.model.StudentReminder>())
    override fun observeGoals(uid: String) = MutableStateFlow(emptyList<com.example.unimate.data.model.StudyGoal>())
    override suspend fun addTask(uid: String, request: com.example.unimate.data.model.AddTaskRequest) = Result.success(Unit)
    override suspend fun toggleTaskCompleted(uid: String, taskId: String, completed: Boolean) = Result.success(Unit)
    override suspend fun deleteTask(uid: String, taskId: String) = Result.success(Unit)
    override suspend fun addCourse(uid: String, request: com.example.unimate.data.model.SaveCourseRequest) = Result.success(Unit)
    override suspend fun updateCourse(uid: String, courseId: String, request: com.example.unimate.data.model.SaveCourseRequest) = Result.success(Unit)
    override suspend fun updateCourseAttendance(uid: String, courseId: String, attendedClasses: Long, totalClasses: Long) = Result.success(Unit)
    override suspend fun deleteCourse(uid: String, courseId: String) = Result.success(Unit)
    override suspend fun addTimetableClass(uid: String, request: com.example.unimate.data.model.SaveTimetableClassRequest) = Result.success(Unit)
    override suspend fun updateTimetableClass(uid: String, classId: String, request: com.example.unimate.data.model.SaveTimetableClassRequest) = Result.success(Unit)
    override suspend fun deleteTimetableClass(uid: String, classId: String) = Result.success(Unit)
    override suspend fun addNote(uid: String, request: com.example.unimate.data.model.AddNoteRequest) = Result.success(Unit)
    override suspend fun deleteNote(uid: String, noteId: String) = Result.success(Unit)
    override suspend fun addReminder(uid: String, request: com.example.unimate.data.model.AddReminderRequest) = Result.success(Unit)
    override suspend fun toggleReminderDone(uid: String, reminderId: String, done: Boolean) = Result.success(Unit)
    override suspend fun deleteReminder(uid: String, reminderId: String) = Result.success(Unit)
    override suspend fun addGoal(uid: String, request: com.example.unimate.data.model.AddGoalRequest) = Result.success(Unit)
    override suspend fun updateGoalProgress(uid: String, goalId: String, completedSteps: Long, targetSteps: Long) = Result.success(Unit)
    override suspend fun deleteGoal(uid: String, goalId: String) = Result.success(Unit)
}
