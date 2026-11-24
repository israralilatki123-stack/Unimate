package com.example.unimate.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.AcademicTask
import com.example.unimate.data.model.AssignmentFilter
import com.example.unimate.data.model.AssignmentSortOrder
import com.example.unimate.data.model.ExamPreparationStatus
import com.example.unimate.data.model.ExamTypeFilter
import com.example.unimate.data.model.QuizPreparationStatus
import com.example.unimate.data.model.QuizSectionFilter
import com.example.unimate.data.model.StudentAssignment
import com.example.unimate.data.model.StudentExam
import com.example.unimate.data.model.StudentQuiz
import com.example.unimate.data.model.StudyGoal
import com.example.unimate.data.model.TaskCategory
import com.example.unimate.ui.assignments.AssignmentsSectionContent
import com.example.unimate.ui.assignments.AssignmentsUiState
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.exams.ExamsSectionContent
import com.example.unimate.ui.exams.ExamsUiState
import com.example.unimate.ui.quizzes.QuizzesSectionContent
import com.example.unimate.ui.quizzes.QuizzesUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksTabContent(
    uiState: MainDashboardUiState,
    assignmentsState: AssignmentsUiState,
    assignmentActionMessage: String?,
    quizzesState: QuizzesUiState,
    quizActionMessage: String?,
    examsState: ExamsUiState,
    examActionMessage: String?,
    onSelectSubTab: (TasksSubTab) -> Unit,
    onSelectAssignmentFilter: (AssignmentFilter) -> Unit,
    onSelectAssignmentSortOrder: (AssignmentSortOrder) -> Unit,
    onSearchAssignmentsChanged: (String) -> Unit,
    onAddAssignmentClick: () -> Unit,
    onEditAssignmentClick: (StudentAssignment) -> Unit,
    onToggleAssignmentCompleted: (StudentAssignment) -> Unit,
    onDeleteAssignment: (String) -> Unit,
    onRetryAssignments: () -> Unit,
    onSelectQuizSection: (QuizSectionFilter) -> Unit,
    onAddQuizClick: () -> Unit,
    onEditQuizClick: (StudentQuiz) -> Unit,
    onUpdateQuizPreparationStatus: (StudentQuiz, QuizPreparationStatus) -> Unit,
    onDeleteQuiz: (String) -> Unit,
    onRetryQuizzes: () -> Unit,
    onSelectExamTypeFilter: (ExamTypeFilter) -> Unit,
    onAddExamClick: () -> Unit,
    onEditExamClick: (StudentExam) -> Unit,
    onToggleExamChecklistTopic: (StudentExam, String) -> Unit,
    onUpdateExamPreparationStatus: (StudentExam, ExamPreparationStatus) -> Unit,
    onDeleteExam: (String) -> Unit,
    onRetryExams: () -> Unit,
    onSelectFilter: (TaskFilter) -> Unit,
    onToggleDemoPreview: (Boolean) -> Unit,
    onAddTaskClick: () -> Unit,
    onAddGoalClick: () -> Unit,
    onToggleTaskCompleted: (AcademicTask) -> Unit,
    onDeleteTask: (AcademicTask) -> Unit,
    onIncrementGoalStep: (StudyGoal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDemo = uiState.isShowingDemoData
    val filteredTasks = remember(uiState.tasks, uiState.taskFilter) {
        when (uiState.taskFilter) {
            TaskFilter.ALL -> uiState.tasks
            TaskFilter.ASSIGNMENT -> uiState.tasks.filter { it.resolvedCategory() == TaskCategory.ASSIGNMENT }
            TaskFilter.QUIZ -> uiState.tasks.filter { it.resolvedCategory() == TaskCategory.QUIZ }
            TaskFilter.EXAM -> uiState.tasks.filter { it.resolvedCategory() == TaskCategory.EXAM }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("tasks_tab_content"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Assignments, Quizzes & Exams",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = "Manage coursework deadlines, graded quizzes, exam countdowns, and study milestones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TasksSubTab.entries.forEach { subTab ->
                    FilterChip(
                        selected = uiState.tasksSubTab == subTab,
                        onClick = { onSelectSubTab(subTab) },
                        label = { Text(subTab.label) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tasks_subtab_${subTab.name.lowercase()}"),
                    )
                }
            }
        }

        when (uiState.tasksSubTab) {
            TasksSubTab.ASSIGNMENTS -> {
                item {
                    AssignmentsSectionContent(
                        state = assignmentsState,
                        actionMessage = assignmentActionMessage,
                        onSelectFilter = onSelectAssignmentFilter,
                        onSelectSortOrder = onSelectAssignmentSortOrder,
                        onSearchQueryChanged = onSearchAssignmentsChanged,
                        onAddAssignmentClick = onAddAssignmentClick,
                        onEditAssignmentClick = onEditAssignmentClick,
                        onToggleCompleted = onToggleAssignmentCompleted,
                        onDeleteAssignment = onDeleteAssignment,
                        onRetry = onRetryAssignments,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            TasksSubTab.QUIZZES -> {
                item {
                    QuizzesSectionContent(
                        state = quizzesState,
                        actionMessage = quizActionMessage,
                        onSelectSection = onSelectQuizSection,
                        onAddQuizClick = onAddQuizClick,
                        onEditQuizClick = onEditQuizClick,
                        onUpdatePreparationStatus = onUpdateQuizPreparationStatus,
                        onDeleteQuiz = onDeleteQuiz,
                        onRetry = onRetryQuizzes,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            TasksSubTab.EXAMS -> {
                item {
                    ExamsSectionContent(
                        state = examsState,
                        actionMessage = examActionMessage,
                        onSelectTypeFilter = onSelectExamTypeFilter,
                        onAddExamClick = onAddExamClick,
                        onEditExamClick = onEditExamClick,
                        onToggleChecklistTopic = onToggleExamChecklistTopic,
                        onUpdatePreparationStatus = onUpdateExamPreparationStatus,
                        onDeleteExam = onDeleteExam,
                        onRetry = onRetryExams,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            TasksSubTab.GOALS -> {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onAddGoalClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("tasks_add_goal_button"),
                        ) {
                            Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Study Goal")
                        }
                        OutlinedButton(
                            onClick = onAddTaskClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("tasks_add_task_button"),
                        ) {
                            Icon(Icons.Rounded.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quick Task")
                        }
                    }
                }

                if (!uiState.feedbackMessage.isNullOrBlank()) {
                    item {
                        AuthStatusBanner(
                            message = uiState.feedbackMessage,
                            type = AuthBannerType.INFO,
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }

                item {
                    DemoWorkspaceBanner(
                        isShowingDemoData = isDemo,
                        hasRealData = uiState.hasRealData,
                        onToggleDemoPreview = onToggleDemoPreview,
                        onAddFirstRealItem = onAddGoalClick,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                item {
                    SectionHeaderRow(
                        title = "Study Goals (${uiState.goals.size})",
                        isDemo = isDemo && uiState.goals.isNotEmpty(),
                        actionLabel = "+ Goal",
                        onActionClick = onAddGoalClick,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                if (uiState.goals.isEmpty()) {
                    item {
                        EmptySectionCard(
                            message = "No study goals yet.",
                            ctaLabel = "Create Goal",
                            onCtaClick = onAddGoalClick,
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                } else {
                    items(uiState.goals, key = { "tasks-goal-${it.id}" }) { goal ->
                        StudyGoalCard(
                            goal = goal,
                            isDemo = isDemo,
                            onIncrementClick = { onIncrementGoalStep(goal) },
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }

                item {
                    SectionHeaderRow(
                        title = "Quick Tasks (${filteredTasks.size})",
                        isDemo = isDemo && filteredTasks.isNotEmpty(),
                        actionLabel = "+ Task",
                        onActionClick = onAddTaskClick,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                item {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TaskFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = uiState.taskFilter == filter,
                                onClick = { onSelectFilter(filter) },
                                label = { Text(filter.label) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            )
                        }
                    }
                }

                if (filteredTasks.isEmpty()) {
                    item {
                        EmptySectionCard(
                            message = "No ${uiState.taskFilter.label.lowercase()} found.",
                            ctaLabel = "Add Task",
                            onCtaClick = onAddTaskClick,
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                } else {
                    items(filteredTasks, key = { "tasks-item-${it.id}" }) { task ->
                        TaskItemRow(
                            task = task,
                            isDemo = isDemo,
                            onToggleCompleted = { onToggleTaskCompleted(task) },
                            onDeleteClick = { onDeleteTask(task) },
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }
            }
        }
    }
}
