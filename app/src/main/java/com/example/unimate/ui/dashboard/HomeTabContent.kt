package com.example.unimate.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.data.model.AcademicTask
import com.example.unimate.data.model.AssignmentStatus
import com.example.unimate.data.model.ExamPreparationStatus
import com.example.unimate.data.model.QuizPreparationStatus
import com.example.unimate.data.model.StudentAssignment
import com.example.unimate.data.model.StudentExam
import com.example.unimate.data.model.StudentQuiz
import com.example.unimate.data.model.StudyGoal
import com.example.unimate.data.model.TaskCategory
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.data.model.UserProfile
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateCoralError
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary

@Composable
fun HomeTabContent(
    profile: UserProfile,
    uiState: MainDashboardUiState,
    statusBannerMessage: String?,
    onDismissStatusBanner: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileAvatarClick: () -> Unit,
    onToggleDemoPreview: (Boolean) -> Unit,
    onOpenDialog: (DashboardDialog) -> Unit,
    onOpenAddCourseDialog: () -> Unit,
    onOpenAddClassDialog: () -> Unit,
    onOpenAddAssignmentDialog: () -> Unit,
    onOpenAddExamDialog: () -> Unit,
    onOpenAddReminderDialog: (() -> Unit)? = null,
    onOpenCreateNoteDialog: (() -> Unit)? = null,
    onOpenStudyPlanner: (() -> Unit)? = null,
    onNavigateToTab: (DashboardTab) -> Unit,
    onOpenTasksSection: (TasksSubTab) -> Unit,
    onOpenAcademicsSection: ((AcademicsSubTab) -> Unit)? = null,
    onToggleTaskCompleted: (AcademicTask) -> Unit,
    onToggleAssignmentCompleted: (StudentAssignment) -> Unit,
    onIncrementGoalStep: (StudyGoal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDemo = uiState.isShowingDemoData
    val todaysClasses: List<TimetableClass> = remember(uiState.timetable) {
        uiState.timetable.filter { it.isToday() }.sortedBy { it.startTimeMinutes }
    }
    val realPendingAssignments = remember(uiState.realAssignments) {
        uiState.realAssignments
            .filter { it.resolvedStatus() == AssignmentStatus.PENDING }
            .sortedBy { it.deadlineMillis }
    }
    val pendingAssignments = remember(uiState.tasks) {
        uiState.tasks.filter { !it.completed && it.resolvedCategory() == TaskCategory.ASSIGNMENT }
    }
    val realUpcomingQuizzes = remember(uiState.realQuizzes) {
        uiState.realQuizzes
            .filter { it.resolvedPreparationStatus() != QuizPreparationStatus.COMPLETED && !it.scoreRecorded }
            .sortedBy { it.scheduledAtMillis }
    }
    val realUpcomingExams = remember(uiState.realExams) {
        uiState.realExams
            .filter { it.resolvedPreparationStatus() != ExamPreparationStatus.COMPLETED }
            .sortedBy { it.examAtMillis }
    }
    val upcomingAssessments = remember(uiState.tasks) {
        uiState.tasks.filter { !it.completed && it.resolvedCategory() != TaskCategory.ASSIGNMENT }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_tab_content"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DashboardTopHeaderCard(
                profile = profile,
                unreadNotificationCount = uiState.unreadNotificationCount,
                onNotificationsClick = onNotificationsClick,
                onProfileAvatarClick = onProfileAvatarClick,
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        if (!statusBannerMessage.isNullOrBlank()) {
            item {
                Column(modifier = Modifier.widthIn(max = 760.dp)) {
                    AuthStatusBanner(
                        message = statusBannerMessage,
                        type = AuthBannerType.SUCCESS,
                        testTag = "dashboard_status_banner",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismissStatusBanner) {
                            Text(stringResource(R.string.dialog_close))
                        }
                    }
                }
            }
        }

        if (!uiState.errorMessage.isNullOrBlank()) {
            item {
                AuthStatusBanner(
                    message = uiState.errorMessage,
                    type = AuthBannerType.ERROR,
                    modifier = Modifier.widthIn(max = 760.dp),
                    testTag = "workspace_error_banner",
                )
            }
        }

        if (!uiState.feedbackMessage.isNullOrBlank()) {
            item {
                AuthStatusBanner(
                    message = uiState.feedbackMessage,
                    type = AuthBannerType.INFO,
                    modifier = Modifier.widthIn(max = 760.dp),
                    testTag = "workspace_feedback_banner",
                )
            }
        }

        item {
            DemoWorkspaceBanner(
                isShowingDemoData = isDemo,
                hasRealData = uiState.hasRealData,
                onToggleDemoPreview = onToggleDemoPreview,
                onAddFirstRealItem = { onOpenDialog(DashboardDialog.ADD_TASK) },
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        item {
            SectionHeaderRow(
                title = stringResource(R.string.dashboard_section_stats),
                isDemo = isDemo,
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        item {
            AcademicStatsGrid(
                metrics = uiState.metrics,
                calculatedGpa = uiState.calculatedGpa,
                isDemo = isDemo,
                onPendingClick = { onOpenTasksSection(TasksSubTab.ASSIGNMENTS) },
                onAssessmentsClick = { onOpenTasksSection(TasksSubTab.EXAMS) },
                onAttendanceClick = {
                    onOpenAcademicsSection?.invoke(AcademicsSubTab.ATTENDANCE)
                        ?: onNavigateToTab(DashboardTab.ACADEMICS)
                },
                onGpaClick = {
                    onOpenAcademicsSection?.invoke(AcademicsSubTab.GPA_CGPA)
                        ?: onOpenDialog(DashboardDialog.GPA_CALCULATOR)
                },
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        item {
            SectionHeaderRow(
                title = stringResource(R.string.dashboard_section_quick_actions),
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        item {
            QuickActionsGrid(
                onAddAssignment = onOpenAddAssignmentDialog,
                onCreateNote = {
                    onOpenCreateNoteDialog?.invoke() ?: onOpenDialog(DashboardDialog.ADD_NOTE)
                },
                onOpenTimetable = { onNavigateToTab(DashboardTab.PLANNER) },
                onCalculateGpa = {
                    onOpenAcademicsSection?.invoke(AcademicsSubTab.GPA_CGPA)
                        ?: onOpenDialog(DashboardDialog.GPA_CALCULATOR)
                },
                onAddReminder = { onOpenAddReminderDialog?.invoke() ?: onOpenDialog(DashboardDialog.ADD_REMINDER) },
                onOpenAiAssistant = { onNavigateToTab(DashboardTab.AI_ASSISTANT) },
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        item {
            SectionHeaderRow(
                title = stringResource(R.string.dashboard_section_todays_classes),
                isDemo = isDemo && todaysClasses.isNotEmpty(),
                actionLabel = "Timetable",
                onActionClick = { onNavigateToTab(DashboardTab.PLANNER) },
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        if (todaysClasses.isEmpty()) {
            item {
                EmptySectionCard(
                    message = "No classes scheduled for today.",
                    ctaLabel = "Add Class",
                    onCtaClick = onOpenAddClassDialog,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        } else {
            items(todaysClasses, key = { "today-${it.id}" }) { timetableClass ->
                ClassScheduleRow(
                    timetableClass = timetableClass,
                    isDemo = isDemo,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        }

        item {
            SectionHeaderRow(
                title = stringResource(R.string.dashboard_section_pending_assignments),
                isDemo = isDemo && realPendingAssignments.isEmpty() && pendingAssignments.isNotEmpty(),
                actionLabel = "View All",
                onActionClick = { onOpenTasksSection(TasksSubTab.ASSIGNMENTS) },
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        if (realPendingAssignments.isNotEmpty()) {
            items(realPendingAssignments.take(3), key = { "real-assign-${it.id}" }) { assignment ->
                HomeAssignmentPreviewCard(
                    assignment = assignment,
                    onToggleCompleted = { onToggleAssignmentCompleted(assignment) },
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        } else if (pendingAssignments.isNotEmpty()) {
            items(pendingAssignments.take(4), key = { "assign-${it.id}" }) { task ->
                TaskItemRow(
                    task = task,
                    isDemo = isDemo,
                    onToggleCompleted = { onToggleTaskCompleted(task) },
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        } else {
            item {
                EmptySectionCard(
                    message = "All caught up on assignments.",
                    ctaLabel = "Add Assignment",
                    onCtaClick = onOpenAddAssignmentDialog,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        }

        item {
            SectionHeaderRow(
                title = stringResource(R.string.dashboard_section_upcoming_assessments),
                isDemo = isDemo && realUpcomingQuizzes.isEmpty() && realUpcomingExams.isEmpty() && upcomingAssessments.isNotEmpty(),
                actionLabel = "View All",
                onActionClick = {
                    if (realUpcomingExams.isNotEmpty() && realUpcomingQuizzes.isEmpty()) {
                        onOpenTasksSection(TasksSubTab.EXAMS)
                    } else {
                        onOpenTasksSection(TasksSubTab.QUIZZES)
                    }
                },
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        if (realUpcomingExams.isNotEmpty() || realUpcomingQuizzes.isNotEmpty()) {
            items(realUpcomingExams.take(2), key = { "home-exam-${it.id}" }) { exam ->
                HomeExamPreviewCard(
                    exam = exam,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
            items(realUpcomingQuizzes.take(2), key = { "home-quiz-${it.id}" }) { quiz ->
                HomeQuizPreviewCard(
                    quiz = quiz,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        } else if (upcomingAssessments.isNotEmpty()) {
            items(upcomingAssessments.take(4), key = { "exam-${it.id}" }) { task ->
                TaskItemRow(
                    task = task,
                    isDemo = isDemo,
                    onToggleCompleted = { onToggleTaskCompleted(task) },
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        } else {
            item {
                EmptySectionCard(
                    message = "No upcoming quizzes or exams listed.",
                    ctaLabel = "Add Exam",
                    onCtaClick = onOpenAddExamDialog,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        }

        item {
            SectionHeaderRow(
                title = stringResource(R.string.dashboard_section_attendance),
                isDemo = isDemo && uiState.courses.isNotEmpty(),
                actionLabel = "Details",
                onActionClick = {
                    onOpenAcademicsSection?.invoke(AcademicsSubTab.ATTENDANCE)
                        ?: onNavigateToTab(DashboardTab.ACADEMICS)
                },
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        if (uiState.courses.isEmpty()) {
            item {
                EmptySectionCard(
                    message = "Add a course to monitor your attendance percentage.",
                    ctaLabel = "Add Course",
                    onCtaClick = onOpenAddCourseDialog,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        } else {
            items(uiState.courses.take(3), key = { "att-${it.id}" }) { course ->
                AttendanceProgressCard(
                    course = course,
                    isDemo = isDemo,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        }

        item {
            SectionHeaderRow(
                title = stringResource(R.string.dashboard_section_study_goals),
                isDemo = isDemo && uiState.goals.isNotEmpty(),
                actionLabel = if (onOpenStudyPlanner != null) "Study Planner" else "+ Goal",
                onActionClick = { onOpenStudyPlanner?.invoke() ?: onOpenDialog(DashboardDialog.ADD_GOAL) },
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        if (uiState.goals.isEmpty()) {
            item {
                EmptySectionCard(
                    message = "Set a weekly study milestone to stay consistent.",
                    ctaLabel = "Create Goal",
                    onCtaClick = { onOpenDialog(DashboardDialog.ADD_GOAL) },
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        } else {
            items(uiState.goals.take(3), key = { "goal-${it.id}" }) { goal ->
                StudyGoalCard(
                    goal = goal,
                    isDemo = isDemo,
                    onIncrementClick = { onIncrementGoalStep(goal) },
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        }

        item {
            AiPromoCard(
                onLaunchAiAssistant = { onNavigateToTab(DashboardTab.AI_ASSISTANT) },
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun HomeAssignmentPreviewCard(
    assignment: StudentAssignment,
    onToggleCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOverdue = assignment.isOverdue()
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isOverdue) UniMateCoralError.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onToggleCompleted,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = if (assignment.resolvedStatus() == AssignmentStatus.COMPLETED) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.RadioButtonUnchecked
                    },
                    contentDescription = "Toggle assignment completed",
                    tint = if (assignment.resolvedStatus() == AssignmentStatus.COMPLETED) {
                        UniMateEmerald
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assignment.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "${assignment.courseName} • ${assignment.deadlineRelativeLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverdue) UniMateCoralError else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeExamPreviewCard(
    exam: StudentExam,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${exam.resolvedExamType().label}: ${exam.title}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "${exam.courseName} • Room ${exam.room}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = UniMateBlueElectric.copy(alpha = 0.12f),
            ) {
                Text(
                    text = exam.countdownLabel(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = UniMateBlueElectric,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeQuizPreviewCard(
    quiz: StudentQuiz,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quiz: ${quiz.title}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "${quiz.courseName} • ${quiz.formattedDate()} • ${quiz.formattedTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = UniMatePurplePrimary.copy(alpha = 0.12f),
            ) {
                Text(
                    text = quiz.resolvedPreparationStatus().label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = UniMateAmber,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
