package com.example.unimate.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unimate.data.model.DayOfWeekOption
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.UserProfile
import com.example.unimate.ui.ai.AiAssistantTabContent
import com.example.unimate.ui.ai.AiAssistantViewModel
import com.example.unimate.ui.assignments.AssignmentsViewModel
import com.example.unimate.ui.attendance.AttendanceViewModel
import com.example.unimate.ui.calendar.CalendarViewModel
import com.example.unimate.ui.courses.CoursesViewModel
import com.example.unimate.ui.exams.ExamsViewModel
import com.example.unimate.ui.gpa.GpaCgpaViewModel
import com.example.unimate.ui.notes.NotesViewModel
import com.example.unimate.ui.planner.PlannerHubContent
import com.example.unimate.ui.quizzes.QuizzesViewModel
import com.example.unimate.ui.reminders.RemindersViewModel
import com.example.unimate.ui.studyplanner.StudyPlannerViewModel
import com.example.unimate.ui.timetable.TimetableViewModel

@Composable
fun HomeTabHost(
    profile: UserProfile,
    workspaceState: MainDashboardUiState,
    statusBannerMessage: String?,
    onDismissStatusBanner: () -> Unit,
    mainDashboardViewModel: MainDashboardViewModel,
    coursesViewModel: CoursesViewModel,
    timetableViewModel: TimetableViewModel,
    assignmentsViewModel: AssignmentsViewModel,
    examsViewModel: ExamsViewModel,
    remindersViewModel: RemindersViewModel,
    notesViewModel: NotesViewModel,
) {
    HomeTabContent(
        profile = profile,
        uiState = workspaceState,
        statusBannerMessage = statusBannerMessage,
        onDismissStatusBanner = onDismissStatusBanner,
        onNotificationsClick = { mainDashboardViewModel.setNotificationsSheetVisible(true) },
        onProfileAvatarClick = { mainDashboardViewModel.setProfileSheetVisible(true) },
        onToggleDemoPreview = mainDashboardViewModel::setDemoPreviewEnabled,
        onOpenDialog = mainDashboardViewModel::openDialog,
        onOpenAddCourseDialog = { coursesViewModel.openAddCourseDialog(profile.semester) },
        onOpenAddClassDialog = { timetableViewModel.openAddClassDialog(DayOfWeekOption.today()) },
        onOpenAddAssignmentDialog = { assignmentsViewModel.openAddDialog() },
        onOpenAddExamDialog = { examsViewModel.openAddDialog() },
        onOpenAddReminderDialog = { remindersViewModel.openAddDialog() },
        onOpenCreateNoteDialog = { notesViewModel.openCreateDialog() },
        onOpenStudyPlanner = { mainDashboardViewModel.openPlannerSection(PlannerSubTab.STUDY_PLANNER) },
        onNavigateToTab = mainDashboardViewModel::selectTab,
        onOpenTasksSection = mainDashboardViewModel::openTasksSection,
        onOpenAcademicsSection = mainDashboardViewModel::openAcademicsSection,
        onToggleTaskCompleted = mainDashboardViewModel::toggleTaskCompleted,
        onToggleAssignmentCompleted = assignmentsViewModel::toggleCompleted,
        onIncrementGoalStep = mainDashboardViewModel::incrementGoalStep,
    )
}

@Composable
fun PlannerTabHost(
    workspaceState: MainDashboardUiState,
    enrolledCourses: List<StudentCourse>,
    mainDashboardViewModel: MainDashboardViewModel,
    timetableViewModel: TimetableViewModel,
    calendarViewModel: CalendarViewModel,
    remindersViewModel: RemindersViewModel,
    studyPlannerViewModel: StudyPlannerViewModel,
) {
    val timetableState by timetableViewModel.uiState.collectAsStateWithLifecycle()
    val timetableActionMessage by timetableViewModel.actionMessage.collectAsStateWithLifecycle()
    val calendarState by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val calendarActionMessage by calendarViewModel.actionMessage.collectAsStateWithLifecycle()
    val remindersState by remindersViewModel.uiState.collectAsStateWithLifecycle()
    val remindersActionMessage by remindersViewModel.actionMessage.collectAsStateWithLifecycle()
    val studyPlannerState by studyPlannerViewModel.uiState.collectAsStateWithLifecycle()
    val pomodoroState by studyPlannerViewModel.pomodoroState.collectAsStateWithLifecycle()
    val studyPlannerActionMessage by studyPlannerViewModel.actionMessage.collectAsStateWithLifecycle()

    PlannerHubContent(
        activeSubTab = workspaceState.plannerSubTab,
        unreadNotificationCount = workspaceState.unreadNotificationCount,
        onSelectSubTab = mainDashboardViewModel::selectPlannerSubTab,
        onOpenNotificationsClick = { mainDashboardViewModel.setNotificationsSheetVisible(true) },
        timetableState = timetableState,
        timetableActionMessage = timetableActionMessage,
        legacyReminders = workspaceState.reminders,
        isDemoReminders = workspaceState.isShowingDemoData,
        onSelectTimetableViewMode = timetableViewModel::selectViewMode,
        onSelectTimetableDay = timetableViewModel::selectDay,
        onAddClassClick = timetableViewModel::openAddClassDialog,
        onEditClassClick = timetableViewModel::openEditClassDialog,
        onDeleteClassClick = timetableViewModel::deleteClass,
        onRetryTimetable = timetableViewModel::retry,
        onToggleLegacyReminderDone = mainDashboardViewModel::toggleReminderDone,
        onDeleteLegacyReminder = mainDashboardViewModel::deleteReminder,
        calendarState = calendarState,
        calendarActionMessage = calendarActionMessage,
        onSelectCalendarViewMode = calendarViewModel::selectViewMode,
        onSelectCalendarDate = calendarViewModel::selectDate,
        onSelectCalendarCategory = calendarViewModel::selectCategoryFilter,
        onPreviousCalendarPeriod = calendarViewModel::navigatePreviousPeriod,
        onNextCalendarPeriod = calendarViewModel::navigateNextPeriod,
        onJumpCalendarToday = calendarViewModel::jumpToToday,
        onAddCalendarEventClick = calendarViewModel::openAddEventDialog,
        onEditCalendarEventClick = calendarViewModel::openEditEventDialog,
        onDeleteCalendarEvent = calendarViewModel::deleteEvent,
        onRetryCalendar = calendarViewModel::retry,
        remindersState = remindersState,
        remindersActionMessage = remindersActionMessage,
        onSelectReminderCategory = remindersViewModel::selectCategoryFilter,
        onAddReminderClick = remindersViewModel::openAddDialog,
        onEditReminderClick = remindersViewModel::openEditDialog,
        onToggleReminderEnabled = remindersViewModel::toggleEnabled,
        onToggleReminderCompleted = remindersViewModel::toggleCompleted,
        onDeleteReminder = remindersViewModel::deleteReminder,
        onRetryReminders = remindersViewModel::retry,
        studyPlannerState = studyPlannerState,
        pomodoroState = pomodoroState,
        enrolledCourses = enrolledCourses,
        studyPlannerActionMessage = studyPlannerActionMessage,
        onClearStudyPlannerMessage = studyPlannerViewModel::clearActionMessage,
        onSelectStudySubjectFilter = studyPlannerViewModel::selectSubjectFilter,
        onOpenAddStudySession = { completed ->
            studyPlannerViewModel.openAddSessionDialog(completedByDefault = completed)
        },
        onOpenEditStudySession = studyPlannerViewModel::openEditSessionDialog,
        onToggleStudySessionCompleted = studyPlannerViewModel::toggleSessionCompleted,
        onDeleteStudySession = studyPlannerViewModel::deleteSession,
        onLaunchStudySessionPomodoro = studyPlannerViewModel::launchSessionInPomodoro,
        onOpenStudyGoalsDialog = studyPlannerViewModel::openGoalsDialog,
        onPomodoroSubjectSelected = studyPlannerViewModel::selectPomodoroSubject,
        onPomodoroTitleChanged = studyPlannerViewModel::onPomodoroTitleChanged,
        onPomodoroDurationSelected = { mins -> studyPlannerViewModel.selectPomodoroDuration(mins) },
        onPomodoroStart = studyPlannerViewModel::startPomodoro,
        onPomodoroPause = studyPlannerViewModel::pausePomodoro,
        onPomodoroReset = studyPlannerViewModel::resetPomodoro,
        onPomodoroSwitchPhase = studyPlannerViewModel::switchPomodoroPhase,
        onPomodoroCompleteNow = studyPlannerViewModel::completePomodoroNow,
        onRetryStudyPlanner = studyPlannerViewModel::retry,
    )
}

@Composable
fun TasksTabHost(
    workspaceState: MainDashboardUiState,
    mainDashboardViewModel: MainDashboardViewModel,
    assignmentsViewModel: AssignmentsViewModel,
    quizzesViewModel: QuizzesViewModel,
    examsViewModel: ExamsViewModel,
) {
    val assignmentsState by assignmentsViewModel.uiState.collectAsStateWithLifecycle()
    val assignmentActionMessage by assignmentsViewModel.actionMessage.collectAsStateWithLifecycle()
    val quizzesState by quizzesViewModel.uiState.collectAsStateWithLifecycle()
    val quizActionMessage by quizzesViewModel.actionMessage.collectAsStateWithLifecycle()
    val examsState by examsViewModel.uiState.collectAsStateWithLifecycle()
    val examActionMessage by examsViewModel.actionMessage.collectAsStateWithLifecycle()

    TasksTabContent(
        uiState = workspaceState,
        assignmentsState = assignmentsState,
        assignmentActionMessage = assignmentActionMessage,
        quizzesState = quizzesState,
        quizActionMessage = quizActionMessage,
        examsState = examsState,
        examActionMessage = examActionMessage,
        onSelectSubTab = mainDashboardViewModel::selectTasksSubTab,
        onSelectAssignmentFilter = assignmentsViewModel::selectFilter,
        onSelectAssignmentSortOrder = assignmentsViewModel::selectSortOrder,
        onSearchAssignmentsChanged = assignmentsViewModel::onSearchQueryChanged,
        onAddAssignmentClick = { assignmentsViewModel.openAddDialog() },
        onEditAssignmentClick = assignmentsViewModel::openEditDialog,
        onToggleAssignmentCompleted = assignmentsViewModel::toggleCompleted,
        onDeleteAssignment = assignmentsViewModel::deleteAssignment,
        onRetryAssignments = assignmentsViewModel::retry,
        onSelectQuizSection = quizzesViewModel::selectSection,
        onAddQuizClick = { quizzesViewModel.openAddDialog() },
        onEditQuizClick = quizzesViewModel::openEditDialog,
        onUpdateQuizPreparationStatus = quizzesViewModel::updatePreparationStatus,
        onDeleteQuiz = quizzesViewModel::deleteQuiz,
        onRetryQuizzes = quizzesViewModel::retry,
        onSelectExamTypeFilter = examsViewModel::selectTypeFilter,
        onAddExamClick = { examsViewModel.openAddDialog() },
        onEditExamClick = examsViewModel::openEditDialog,
        onToggleExamChecklistTopic = examsViewModel::toggleChecklistTopicOnCard,
        onUpdateExamPreparationStatus = examsViewModel::updatePreparationStatus,
        onDeleteExam = examsViewModel::deleteExam,
        onRetryExams = examsViewModel::retry,
        onSelectFilter = mainDashboardViewModel::selectTaskFilter,
        onToggleDemoPreview = mainDashboardViewModel::setDemoPreviewEnabled,
        onAddTaskClick = { mainDashboardViewModel.openDialog(DashboardDialog.ADD_TASK) },
        onAddGoalClick = { mainDashboardViewModel.openDialog(DashboardDialog.ADD_GOAL) },
        onToggleTaskCompleted = mainDashboardViewModel::toggleTaskCompleted,
        onDeleteTask = mainDashboardViewModel::deleteTask,
        onIncrementGoalStep = mainDashboardViewModel::incrementGoalStep,
    )
}

@Composable
fun AcademicsTabHost(
    profile: UserProfile,
    workspaceState: MainDashboardUiState,
    coursesState: com.example.unimate.ui.courses.CoursesUiState,
    mainDashboardViewModel: MainDashboardViewModel,
    coursesViewModel: CoursesViewModel,
    attendanceViewModel: AttendanceViewModel,
    gpaCgpaViewModel: GpaCgpaViewModel,
    notesViewModel: NotesViewModel,
) {
    val courseActionMessage by coursesViewModel.actionMessage.collectAsStateWithLifecycle()
    val attendanceState by attendanceViewModel.uiState.collectAsStateWithLifecycle()
    val attendanceActionMessage by attendanceViewModel.actionMessage.collectAsStateWithLifecycle()
    val gpaState by gpaCgpaViewModel.uiState.collectAsStateWithLifecycle()
    val gpaActionMessage by gpaCgpaViewModel.actionMessage.collectAsStateWithLifecycle()
    val notesState by notesViewModel.uiState.collectAsStateWithLifecycle()
    val notesActionMessage by notesViewModel.actionMessage.collectAsStateWithLifecycle()

    AcademicsTabContent(
        profile = profile,
        uiState = workspaceState,
        coursesState = coursesState,
        courseActionMessage = courseActionMessage,
        attendanceState = attendanceState,
        attendanceActionMessage = attendanceActionMessage,
        gpaState = gpaState,
        gpaActionMessage = gpaActionMessage,
        notesState = notesState,
        notesActionMessage = notesActionMessage,
        onSelectSubTab = mainDashboardViewModel::selectAcademicsSubTab,
        onAddCourseClick = { coursesViewModel.openAddCourseDialog(profile.semester) },
        onEditCourseClick = coursesViewModel::openEditCourseDialog,
        onDeleteCourseClick = coursesViewModel::deleteCourse,
        onMarkCourseAttendance = coursesViewModel::markAttendance,
        onRetryCourses = coursesViewModel::retry,
        onClearAttendanceMessage = attendanceViewModel::clearActionMessage,
        onOpenLogAttendance = attendanceViewModel::openLogAttendanceDialog,
        onOpenEditAttendanceTotals = attendanceViewModel::openDirectTotalsDialog,
        onOpenThresholdDialog = attendanceViewModel::openThresholdDialog,
        onSelectAttendanceCourseFilter = attendanceViewModel::selectCourseFilter,
        onSelectAttendanceMonthFilter = attendanceViewModel::selectMonthFilter,
        onToggleAttendanceLogStatus = attendanceViewModel::toggleLogStatus,
        onDeleteAttendanceLog = attendanceViewModel::deleteLog,
        onRetryAttendance = attendanceViewModel::retry,
        onClearGpaMessage = gpaCgpaViewModel::clearActionMessage,
        onAddSemesterGpaClick = gpaCgpaViewModel::openAddSemesterDialog,
        onEditSemesterGpaClick = gpaCgpaViewModel::openEditSemesterDialog,
        onDeleteSemesterGpaClick = gpaCgpaViewModel::deleteSemesterRecord,
        onUpdateSemesterCourseGrade = gpaCgpaViewModel::updateSavedSemesterCourseGrade,
        onOpenGradingScaleDialog = gpaCgpaViewModel::openGradingScaleDialog,
        onRetryGpa = gpaCgpaViewModel::retry,
        onClearNotesMessage = notesViewModel::clearActionMessage,
        onSearchNotesChanged = notesViewModel::onSearchQueryChanged,
        onSelectNotesSubjectFilter = notesViewModel::selectSubjectFilter,
        onSelectNotesQuickFilter = notesViewModel::selectQuickFilter,
        onCreateNoteClick = { notesViewModel.openCreateDialog() },
        onOpenNoteDetail = notesViewModel::openNoteDetail,
        onEditNoteClick = notesViewModel::openEditDialog,
        onToggleNotePin = notesViewModel::togglePinned,
        onToggleNoteFavorite = notesViewModel::toggleFavorite,
        onDeleteNote = notesViewModel::deleteNote,
        onRetryNotes = notesViewModel::retry,
        onOpenProfileSheet = { mainDashboardViewModel.setProfileSheetVisible(true) },
    )
}

@Composable
fun AiAssistantTabHost(
    profile: UserProfile,
    workspaceState: MainDashboardUiState,
    aiAssistantViewModel: AiAssistantViewModel,
) {
    val aiState by aiAssistantViewModel.uiState.collectAsStateWithLifecycle()
    val contextSummary = "${profile.department} (${profile.semester}) at ${profile.university}; " +
        "${workspaceState.metrics.pendingTasksCount} pending tasks, " +
        "${workspaceState.metrics.upcomingAssessmentsCount} upcoming quizzes/exams"
    AiAssistantTabContent(
        uiState = aiState,
        studentContextSummary = contextSummary,
        onInputChanged = aiAssistantViewModel::onInputChanged,
        onSendPrompt = { overridePrompt ->
            aiAssistantViewModel.sendPrompt(
                promptOverride = overridePrompt,
                studentContextSummary = contextSummary,
            )
        },
        onClearChat = aiAssistantViewModel::clearConversation,
    )
}
