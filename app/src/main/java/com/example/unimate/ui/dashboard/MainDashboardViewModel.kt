package com.example.unimate.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.AcademicGpaCalculator
import com.example.unimate.data.model.AcademicSettings
import com.example.unimate.data.model.AcademicTask
import com.example.unimate.data.model.AddGoalRequest
import com.example.unimate.data.model.AddNoteRequest
import com.example.unimate.data.model.AddReminderRequest
import com.example.unimate.data.model.AddTaskRequest
import com.example.unimate.data.model.AssignmentStatus
import com.example.unimate.data.model.ExamPreparationStatus
import com.example.unimate.data.model.GpaCalculator
import com.example.unimate.data.model.GpaCourseEntry
import com.example.unimate.data.model.LetterGrade
import com.example.unimate.data.model.QuizPreparationStatus
import com.example.unimate.data.model.SampleWorkspaceData
import com.example.unimate.data.model.SemesterGpaRecord
import com.example.unimate.data.model.StudentAssignment
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.StudentExam
import com.example.unimate.data.model.StudentQuiz
import com.example.unimate.data.model.StudentReminder
import com.example.unimate.data.model.StudyGoal
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.model.TaskCategory
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.data.model.AppNotification
import com.example.unimate.data.model.ScheduledReminder
import com.example.unimate.data.model.StudySession
import com.example.unimate.data.repository.AcademicModulesRepository
import com.example.unimate.data.repository.AttendanceAndGpaRepository
import com.example.unimate.data.repository.CalendarRemindersRepository
import com.example.unimate.data.repository.NotesAndStudyPlannerRepository
import com.example.unimate.data.repository.WorkspaceRepository
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.SyncCoordinator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class DashboardTab(val routeKey: String) {
    HOME("home"),
    PLANNER("planner"),
    TASKS("tasks"),
    ACADEMICS("academics"),
    AI_ASSISTANT("ai_assistant"),
}

enum class PlannerSubTab(val label: String) {
    TIMETABLE("Timetable"),
    CALENDAR("Calendar"),
    REMINDERS("Reminders"),
    STUDY_PLANNER("Study"),
}

enum class TasksSubTab(val label: String) {
    ASSIGNMENTS("Assignments"),
    QUIZZES("Quizzes"),
    EXAMS("Exams"),
    GOALS("Goals"),
}

enum class AcademicsSubTab(val label: String) {
    COURSES("Courses"),
    ATTENDANCE("Attendance"),
    GPA_CGPA("GPA & CGPA"),
    NOTES("Notes"),
}

enum class TaskFilter(val label: String) {
    ALL("All"),
    ASSIGNMENT("Assignments"),
    QUIZ("Quizzes"),
    EXAM("Exams"),
}

enum class DashboardDialog {
    ADD_TASK,
    ADD_NOTE,
    ADD_REMINDER,
    ADD_GOAL,
    GPA_CALCULATOR,
}

data class DashboardMetrics(
    val pendingTasksCount: Int = 0,
    val upcomingAssessmentsCount: Int = 0,
    val attendancePercent: Int = 0,
    val activeRemindersCount: Int = 0,
    val coursesCount: Int = 0,
    val timetableCount: Int = 0,
    val notesCount: Int = 0,
    val averageGoalPercent: Int = 0,
)

private data class WorkspaceSnapshot(
    val tasks: List<AcademicTask> = emptyList(),
    val courses: List<StudentCourse> = emptyList(),
    val timetable: List<TimetableClass> = emptyList(),
    val notes: List<StudyNote> = emptyList(),
    val reminders: List<StudentReminder> = emptyList(),
    val goals: List<StudyGoal> = emptyList(),
    val isLoading: Boolean = true,
    val streamError: String? = null,
)

private data class AcademicModulesSnapshot(
    val assignments: List<StudentAssignment> = emptyList(),
    val quizzes: List<StudentQuiz> = emptyList(),
    val exams: List<StudentExam> = emptyList(),
)

private data class CalendarAlertsSnapshot(
    val scheduledReminders: List<ScheduledReminder> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
)

private data class GpaRecordsSnapshot(
    val semesters: List<SemesterGpaRecord> = emptyList(),
    val settings: AcademicSettings = AcademicSettings(),
)

private data class InteractionState(
    val activeTab: DashboardTab = DashboardTab.HOME,
    val plannerSubTab: PlannerSubTab = PlannerSubTab.TIMETABLE,
    val tasksSubTab: TasksSubTab = TasksSubTab.ASSIGNMENTS,
    val academicsSubTab: AcademicsSubTab = AcademicsSubTab.COURSES,
    val taskFilter: TaskFilter = TaskFilter.ALL,
    val demoPreviewChoice: Boolean? = null,
    val activeDialog: DashboardDialog? = null,
    val draftDocId: String = "",
    val showNotificationsSheet: Boolean = false,
    val showProfileSheet: Boolean = false,
    val isMutating: Boolean = false,
    val feedbackMessage: String? = null,
    val mutationErrorMessage: String? = null,
    val gpaEntries: List<GpaCourseEntry> = SampleWorkspaceData.defaultGpaEntries,
)

data class MainDashboardUiState(
    val activeTab: DashboardTab = DashboardTab.HOME,
    val plannerSubTab: PlannerSubTab = PlannerSubTab.TIMETABLE,
    val tasksSubTab: TasksSubTab = TasksSubTab.ASSIGNMENTS,
    val academicsSubTab: AcademicsSubTab = AcademicsSubTab.COURSES,
    val taskFilter: TaskFilter = TaskFilter.ALL,
    val isWorkspaceLoading: Boolean = true,
    val hasRealData: Boolean = false,
    val isShowingDemoData: Boolean = false,
    val courses: List<StudentCourse> = emptyList(),
    val timetable: List<TimetableClass> = emptyList(),
    val tasks: List<AcademicTask> = emptyList(),
    val realAssignments: List<StudentAssignment> = emptyList(),
    val realQuizzes: List<StudentQuiz> = emptyList(),
    val realExams: List<StudentExam> = emptyList(),
    val notes: List<StudyNote> = emptyList(),
    val reminders: List<StudentReminder> = emptyList(),
    val scheduledReminders: List<ScheduledReminder> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val goals: List<StudyGoal> = emptyList(),
    val metrics: DashboardMetrics = DashboardMetrics(),
    val activeDialog: DashboardDialog? = null,
    val showNotificationsSheet: Boolean = false,
    val showProfileSheet: Boolean = false,
    val isMutating: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val gpaEntries: List<GpaCourseEntry> = SampleWorkspaceData.defaultGpaEntries,
    val calculatedGpa: Double = GpaCalculator.calculateGpa(SampleWorkspaceData.defaultGpaEntries),
    val totalGpaCredits: Double = GpaCalculator.totalCredits(SampleWorkspaceData.defaultGpaEntries),
) {
    val unreadNotificationCount: Int
        get() = notifications.count { !it.read } +
            reminders.count { !it.done } +
            scheduledReminders.count { it.enabled && !it.completed } +
            tasks.count {
                !it.completed && it.resolvedCategory() != TaskCategory.ASSIGNMENT
            }
}

class MainDashboardViewModel(
    private val uid: String,
    private val workspaceRepository: WorkspaceRepository,
    academicModulesRepository: AcademicModulesRepository? = null,
    calendarRemindersRepository: CalendarRemindersRepository? = null,
    attendanceAndGpaRepository: AttendanceAndGpaRepository? = null,
    notesAndStudyPlannerRepository: NotesAndStudyPlannerRepository? = null,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val interactionState = MutableStateFlow(InteractionState())

    private val primaryStream = combine(
        workspaceRepository.observeTasks(uid),
        workspaceRepository.observeCourses(uid),
        workspaceRepository.observeTimetable(uid),
    ) { tasks, courses, timetable ->
        Triple(tasks, courses, timetable)
    }

    private val secondaryStream = combine(
        workspaceRepository.observeNotes(uid),
        workspaceRepository.observeReminders(uid),
        workspaceRepository.observeGoals(uid),
    ) { notes, reminders, goals ->
        Triple(notes, reminders, goals)
    }

    private val workspaceStream: Flow<WorkspaceSnapshot> = combine(
        primaryStream,
        secondaryStream,
    ) { (tasks, courses, timetable), (notes, reminders, goals) ->
        WorkspaceSnapshot(
            tasks = tasks,
            courses = courses,
            timetable = timetable.sortedBy { it.startTimeMinutes },
            notes = notes,
            reminders = reminders,
            goals = goals,
            isLoading = false,
            streamError = null,
        )
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        emit(
            WorkspaceSnapshot(
                isLoading = false,
                streamError = rootError.localizedMessage ?: "Failed to sync academic workspace.",
            ),
        )
    }

    private val modulesStream: Flow<AcademicModulesSnapshot> = if (academicModulesRepository != null) {
        combine(
            academicModulesRepository.observeAssignments(uid).catch { emit(emptyList()) },
            academicModulesRepository.observeQuizzes(uid).catch { emit(emptyList()) },
            academicModulesRepository.observeExams(uid).catch { emit(emptyList()) },
        ) { assignments, quizzes, exams ->
            AcademicModulesSnapshot(
                assignments = assignments,
                quizzes = quizzes,
                exams = exams,
            )
        }
    } else {
        flowOf(AcademicModulesSnapshot())
    }

    private val calendarAlertsStream: Flow<CalendarAlertsSnapshot> = if (calendarRemindersRepository != null) {
        combine(
            calendarRemindersRepository.observeScheduledReminders(uid).catch { emit(emptyList()) },
            calendarRemindersRepository.observeNotifications(uid).catch { emit(emptyList()) },
        ) { scheduled, notifications ->
            CalendarAlertsSnapshot(
                scheduledReminders = scheduled,
                notifications = notifications,
            )
        }
    } else {
        flowOf(CalendarAlertsSnapshot())
    }

    private val gpaStream: Flow<GpaRecordsSnapshot> = if (attendanceAndGpaRepository != null) {
        combine(
            attendanceAndGpaRepository.observeSemesterGpaRecords(uid).catch { emit(emptyList()) },
            attendanceAndGpaRepository.observeAcademicSettings(uid).catch { emit(AcademicSettings(uid = uid)) },
        ) { semesters, settings ->
            GpaRecordsSnapshot(
                semesters = semesters,
                settings = settings,
            )
        }
    } else {
        flowOf(GpaRecordsSnapshot())
    }

    private val studySessionsStream: Flow<List<StudySession>> =
        notesAndStudyPlannerRepository?.observeStudySessions(uid)?.catch { emit(emptyList()) }
            ?: flowOf(emptyList())

    val uiState: StateFlow<MainDashboardUiState> = combine(
        workspaceStream,
        modulesStream,
        calendarAlertsStream,
        combine(gpaStream, studySessionsStream) { gpa, sessions -> gpa to sessions },
        interactionState,
    ) { snapshot, modules, calendarAlerts, (gpaSnapshot, studySessions), interaction ->
        val hasRealData = snapshot.tasks.isNotEmpty() ||
            snapshot.courses.isNotEmpty() ||
            snapshot.timetable.isNotEmpty() ||
            snapshot.notes.isNotEmpty() ||
            snapshot.reminders.isNotEmpty() ||
            snapshot.goals.isNotEmpty() ||
            modules.assignments.isNotEmpty() ||
            modules.quizzes.isNotEmpty() ||
            modules.exams.isNotEmpty() ||
            calendarAlerts.scheduledReminders.isNotEmpty() ||
            gpaSnapshot.semesters.isNotEmpty() ||
            studySessions.isNotEmpty()

        val showDemo = !snapshot.isLoading &&
            !hasRealData &&
            snapshot.streamError == null &&
            (interaction.demoPreviewChoice ?: true)

        val visibleCourses = if (showDemo) SampleWorkspaceData.sampleCourses else snapshot.courses
        val visibleTimetable = if (showDemo) SampleWorkspaceData.sampleTimetable else snapshot.timetable
        val visibleTasks = if (showDemo) SampleWorkspaceData.sampleTasks else snapshot.tasks
        val visibleNotes = if (showDemo) SampleWorkspaceData.sampleNotes else snapshot.notes
        val visibleReminders = if (showDemo) SampleWorkspaceData.sampleReminders else snapshot.reminders
        val visibleGoals = if (showDemo) SampleWorkspaceData.sampleGoals else snapshot.goals

        val metrics = computeMetrics(
            courses = visibleCourses,
            timetable = visibleTimetable,
            tasks = visibleTasks,
            assignments = modules.assignments,
            quizzes = modules.quizzes,
            exams = modules.exams,
            notes = visibleNotes,
            reminders = visibleReminders,
            scheduledReminders = calendarAlerts.scheduledReminders,
            goals = visibleGoals,
        )

        val realCgpaSummary = if (gpaSnapshot.semesters.isNotEmpty()) {
            AcademicGpaCalculator.calculateCgpaSummary(
                semesters = gpaSnapshot.semesters,
                scale = gpaSnapshot.settings.gradingScale,
            )
        } else {
            null
        }

        MainDashboardUiState(
            activeTab = interaction.activeTab,
            plannerSubTab = interaction.plannerSubTab,
            tasksSubTab = interaction.tasksSubTab,
            academicsSubTab = interaction.academicsSubTab,
            taskFilter = interaction.taskFilter,
            isWorkspaceLoading = snapshot.isLoading,
            hasRealData = hasRealData,
            isShowingDemoData = showDemo,
            courses = visibleCourses,
            timetable = visibleTimetable,
            tasks = visibleTasks,
            realAssignments = modules.assignments,
            realQuizzes = modules.quizzes,
            realExams = modules.exams,
            notes = visibleNotes,
            reminders = visibleReminders,
            scheduledReminders = calendarAlerts.scheduledReminders,
            notifications = calendarAlerts.notifications,
            goals = visibleGoals,
            metrics = metrics,
            activeDialog = interaction.activeDialog,
            showNotificationsSheet = interaction.showNotificationsSheet,
            showProfileSheet = interaction.showProfileSheet,
            isMutating = interaction.isMutating,
            feedbackMessage = interaction.feedbackMessage,
            errorMessage = interaction.mutationErrorMessage ?: snapshot.streamError,
            gpaEntries = interaction.gpaEntries,
            calculatedGpa = realCgpaSummary?.cgpa ?: GpaCalculator.calculateGpa(interaction.gpaEntries),
            totalGpaCredits = realCgpaSummary?.totalCredits ?: GpaCalculator.totalCredits(interaction.gpaEntries),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainDashboardUiState(),
    )

    fun selectTab(tab: DashboardTab) {
        interactionState.update {
            it.copy(
                activeTab = tab,
                feedbackMessage = null,
                mutationErrorMessage = null,
            )
        }
    }

    fun selectPlannerSubTab(subTab: PlannerSubTab) {
        interactionState.update { it.copy(plannerSubTab = subTab) }
    }

    fun openPlannerSection(subTab: PlannerSubTab) {
        interactionState.update {
            it.copy(
                activeTab = DashboardTab.PLANNER,
                plannerSubTab = subTab,
                feedbackMessage = null,
                mutationErrorMessage = null,
            )
        }
    }

    fun selectTasksSubTab(subTab: TasksSubTab) {
        interactionState.update { it.copy(tasksSubTab = subTab) }
    }

    fun openTasksSection(subTab: TasksSubTab) {
        interactionState.update {
            it.copy(
                activeTab = DashboardTab.TASKS,
                tasksSubTab = subTab,
                feedbackMessage = null,
                mutationErrorMessage = null,
            )
        }
    }

    fun selectAcademicsSubTab(subTab: AcademicsSubTab) {
        interactionState.update { it.copy(academicsSubTab = subTab) }
    }

    fun openAcademicsSection(subTab: AcademicsSubTab) {
        interactionState.update {
            it.copy(
                activeTab = DashboardTab.ACADEMICS,
                academicsSubTab = subTab,
                feedbackMessage = null,
                mutationErrorMessage = null,
            )
        }
    }

    fun selectTaskFilter(filter: TaskFilter) {
        interactionState.update { it.copy(taskFilter = filter) }
    }

    fun setDemoPreviewEnabled(enabled: Boolean) {
        interactionState.update {
            it.copy(
                demoPreviewChoice = enabled,
                feedbackMessage = if (enabled) {
                    "Demo Preview enabled. Sample items are marked DEMO until you add real data."
                } else {
                    "Demo Preview hidden. Add your own courses, classes, tasks, notes, and reminders."
                },
            )
        }
    }

    fun openDialog(dialog: DashboardDialog) {
        interactionState.update {
            it.copy(
                activeDialog = dialog,
                draftDocId = UUID.randomUUID().toString(),
                mutationErrorMessage = null,
            )
        }
    }

    fun dismissDialog() {
        interactionState.update {
            it.copy(
                activeDialog = null,
                draftDocId = "",
                mutationErrorMessage = null,
            )
        }
    }

    fun setNotificationsSheetVisible(visible: Boolean) {
        interactionState.update { it.copy(showNotificationsSheet = visible) }
    }

    fun setProfileSheetVisible(visible: Boolean) {
        interactionState.update { it.copy(showProfileSheet = visible) }
    }

    fun clearMessages() {
        interactionState.update {
            it.copy(feedbackMessage = null, mutationErrorMessage = null)
        }
    }

    fun addTask(
        title: String,
        courseName: String,
        category: TaskCategory,
        dueDateText: String,
    ) {
        val cleanTitle = title.trim()
        val cleanCourse = courseName.trim()
        val cleanDue = dueDateText.trim()
        if (cleanTitle.isEmpty() || cleanCourse.isEmpty() || cleanDue.isEmpty()) {
            interactionState.update {
                it.copy(mutationErrorMessage = "Please fill in title, course, and due date.")
            }
            return
        }
        val draftId = interactionState.value.draftDocId.ifBlank { UUID.randomUUID().toString() }
        runMutation(
            successMessage = "${category.label} saved to Cloud Firestore.",
            closeDialogOnSuccess = true,
        ) {
            workspaceRepository.addTaskWithId(
                uid = uid,
                taskId = draftId,
                request = AddTaskRequest(
                    title = cleanTitle,
                    courseName = cleanCourse,
                    category = category,
                    dueDateText = cleanDue,
                ),
            )
        }
    }

    fun toggleTaskCompleted(task: AcademicTask) {
        if (task.id.startsWith(DEMO_ID_PREFIX)) {
            interactionState.update {
                it.copy(
                    feedbackMessage = "This is a DEMO preview item. Add a real task to save progress to Firestore.",
                )
            }
            return
        }
        runMutation(successMessage = null) {
            workspaceRepository.toggleTaskCompleted(uid, task.id, !task.completed)
        }
    }

    fun deleteTask(task: AcademicTask) {
        if (task.id.startsWith(DEMO_ID_PREFIX)) {
            setDemoPreviewEnabled(false)
            return
        }
        runMutation(successMessage = "Task removed.") {
            workspaceRepository.deleteTask(uid, task.id)
        }
    }

    fun addNote(title: String, courseName: String, content: String) {
        if (title.isBlank() || courseName.isBlank() || content.isBlank()) {
            interactionState.update {
                it.copy(mutationErrorMessage = "Please fill in note title, course, and content.")
            }
            return
        }
        val draftId = interactionState.value.draftDocId.ifBlank { UUID.randomUUID().toString() }
        runMutation(
            successMessage = "Note synced to Cloud Firestore.",
            closeDialogOnSuccess = true,
        ) {
            workspaceRepository.addNoteWithId(
                uid = uid,
                noteId = draftId,
                request = AddNoteRequest(
                    title = title,
                    courseName = courseName,
                    content = content,
                ),
            )
        }
    }

    fun deleteNote(note: StudyNote) {
        if (note.id.startsWith(DEMO_ID_PREFIX)) {
            setDemoPreviewEnabled(false)
            return
        }
        runMutation(successMessage = "Note deleted.") {
            workspaceRepository.deleteNote(uid, note.id)
        }
    }

    fun addReminder(title: String, remindAtText: String) {
        if (title.isBlank() || remindAtText.isBlank()) {
            interactionState.update {
                it.copy(mutationErrorMessage = "Please enter reminder title and time.")
            }
            return
        }
        val draftId = interactionState.value.draftDocId.ifBlank { UUID.randomUUID().toString() }
        runMutation(
            successMessage = "Reminder saved.",
            closeDialogOnSuccess = true,
        ) {
            workspaceRepository.addReminderWithId(
                uid = uid,
                reminderId = draftId,
                request = AddReminderRequest(
                    title = title,
                    remindAtText = remindAtText,
                ),
            )
        }
    }

    fun toggleReminderDone(reminder: StudentReminder) {
        if (reminder.id.startsWith(DEMO_ID_PREFIX)) {
            interactionState.update {
                it.copy(
                    feedbackMessage = "This is a DEMO preview reminder. Add a real reminder to save it.",
                )
            }
            return
        }
        runMutation(successMessage = null) {
            workspaceRepository.toggleReminderDone(uid, reminder.id, !reminder.done)
        }
    }

    fun deleteReminder(reminder: StudentReminder) {
        if (reminder.id.startsWith(DEMO_ID_PREFIX)) {
            setDemoPreviewEnabled(false)
            return
        }
        runMutation(successMessage = "Reminder removed.") {
            workspaceRepository.deleteReminder(uid, reminder.id)
        }
    }

    fun addGoal(title: String, targetSteps: Long) {
        if (title.isBlank() || targetSteps !in 1L..500L) {
            interactionState.update {
                it.copy(mutationErrorMessage = "Enter a goal title and target steps (1–500).")
            }
            return
        }
        val draftId = interactionState.value.draftDocId.ifBlank { UUID.randomUUID().toString() }
        runMutation(
            successMessage = "Study goal created.",
            closeDialogOnSuccess = true,
        ) {
            workspaceRepository.addGoalWithId(
                uid = uid,
                goalId = draftId,
                request = AddGoalRequest(
                    title = title,
                    completedSteps = 0L,
                    targetSteps = targetSteps,
                ),
            )
        }
    }

    fun incrementGoalStep(goal: StudyGoal) {
        if (goal.id.startsWith(DEMO_ID_PREFIX)) {
            interactionState.update {
                it.copy(
                    feedbackMessage = "This is a DEMO study goal. Create a real goal to track progress.",
                )
            }
            return
        }
        val nextSteps = (goal.completedSteps + 1L).coerceAtMost(goal.targetSteps)
        runMutation(successMessage = null) {
            workspaceRepository.updateGoalProgress(uid, goal.id, nextSteps, goal.targetSteps)
        }
    }

    fun addGpaCourse(courseName: String, creditHours: Double, grade: LetterGrade) {
        val cleanName = courseName.trim()
        if (cleanName.isEmpty() || creditHours <= 0.0) {
            interactionState.update {
                it.copy(mutationErrorMessage = "Enter a valid course name and credit hours (> 0).")
            }
            return
        }
        val entry = GpaCourseEntry(
            id = UUID.randomUUID().toString(),
            courseName = cleanName,
            creditHours = creditHours,
            grade = grade,
        )
        interactionState.update {
            it.copy(
                gpaEntries = it.gpaEntries + entry,
                mutationErrorMessage = null,
            )
        }
    }

    fun removeGpaCourse(entryId: String) {
        interactionState.update { state ->
            state.copy(gpaEntries = state.gpaEntries.filterNot { it.id == entryId })
        }
    }

    fun updateGpaEntryGrade(entryId: String, grade: LetterGrade) {
        interactionState.update { state ->
            state.copy(
                gpaEntries = state.gpaEntries.map { entry ->
                    if (entry.id == entryId) entry.copy(grade = grade) else entry
                },
            )
        }
    }

    private fun runMutation(
        successMessage: String?,
        closeDialogOnSuccess: Boolean = false,
        block: suspend () -> Result<Unit>,
    ) {
        if (interactionState.value.isMutating) return
        interactionState.update {
            it.copy(isMutating = true, mutationErrorMessage = null)
        }
        viewModelScope.launch {
            val result = block()
            result.fold(
                onSuccess = {
                    val formatted = successMessage?.let { msg -> syncCoordinator.formatActionMessage(msg) }
                    interactionState.update {
                        it.copy(
                            isMutating = false,
                            activeDialog = if (closeDialogOnSuccess) null else it.activeDialog,
                            draftDocId = if (closeDialogOnSuccess) "" else it.draftDocId,
                            feedbackMessage = formatted ?: it.feedbackMessage,
                            mutationErrorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    interactionState.update {
                        it.copy(
                            isMutating = false,
                            mutationErrorMessage = error.localizedMessage ?: "Cloud sync failed.",
                        )
                    }
                },
            )
        }
    }

    private fun computeMetrics(
        courses: List<StudentCourse>,
        timetable: List<TimetableClass>,
        tasks: List<AcademicTask>,
        assignments: List<StudentAssignment>,
        quizzes: List<StudentQuiz>,
        exams: List<StudentExam>,
        notes: List<StudyNote>,
        reminders: List<StudentReminder>,
        scheduledReminders: List<ScheduledReminder>,
        goals: List<StudyGoal>,
    ): DashboardMetrics {
        val pendingTasks = tasks.count { !it.completed } +
            assignments.count { it.resolvedStatus() == AssignmentStatus.PENDING }
        val upcomingAssessments = tasks.count {
            !it.completed && it.resolvedCategory() != TaskCategory.ASSIGNMENT
        } + quizzes.count {
            it.resolvedPreparationStatus() != QuizPreparationStatus.COMPLETED && !it.scoreRecorded
        } + exams.count {
            it.resolvedPreparationStatus() != ExamPreparationStatus.COMPLETED
        }
        val totalClassesSum = courses.sumOf { it.totalClasses }
        val attendedClassesSum = courses.sumOf { it.attendedClasses }
        val attendancePercent = if (totalClassesSum > 0L) {
            ((attendedClassesSum.toDouble() / totalClassesSum.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }
        val activeReminders = reminders.count { !it.done } +
            scheduledReminders.count { it.enabled && !it.completed }
        val averageGoalPercent = if (goals.isNotEmpty()) {
            (goals.map { it.progressFraction() }.average() * 100.0).toInt().coerceIn(0, 100)
        } else {
            0
        }
        return DashboardMetrics(
            pendingTasksCount = pendingTasks,
            upcomingAssessmentsCount = upcomingAssessments,
            attendancePercent = attendancePercent,
            activeRemindersCount = activeReminders,
            coursesCount = courses.size,
            timetableCount = timetable.size,
            notesCount = notes.size,
            averageGoalPercent = averageGoalPercent,
        )
    }

    companion object {
        private const val DEMO_ID_PREFIX = "demo-"

        fun factory(
            uid: String,
            workspaceRepository: WorkspaceRepository,
            academicModulesRepository: AcademicModulesRepository? = null,
            calendarRemindersRepository: CalendarRemindersRepository? = null,
            attendanceAndGpaRepository: AttendanceAndGpaRepository? = null,
            notesAndStudyPlannerRepository: NotesAndStudyPlannerRepository? = null,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainDashboardViewModel(
                    uid = uid,
                    workspaceRepository = workspaceRepository,
                    academicModulesRepository = academicModulesRepository,
                    calendarRemindersRepository = calendarRemindersRepository,
                    attendanceAndGpaRepository = attendanceAndGpaRepository,
                    notesAndStudyPlannerRepository = notesAndStudyPlannerRepository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
