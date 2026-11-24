package com.example.unimate.ui.studyplanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.PomodoroPhase
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.StudySession
import com.example.unimate.data.model.StudySessionMode
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.AcademicModuleEmptyCard
import com.example.unimate.ui.components.AcademicModuleErrorCard
import com.example.unimate.ui.components.AcademicModuleLoadingCard
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary

@Composable
fun StudyPlannerSectionContent(
    uiState: StudyPlannerUiState,
    pomodoroState: PomodoroUiState,
    enrolledCourses: List<StudentCourse>,
    actionMessage: String?,
    onClearActionMessage: () -> Unit,
    onSelectSubjectFilter: (String?) -> Unit,
    onOpenAddSessionDialog: (Boolean) -> Unit,
    onOpenEditSessionDialog: (StudySession) -> Unit,
    onToggleSessionCompleted: (StudySession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onLaunchSessionInPomodoro: (StudySession) -> Unit,
    onOpenGoalsDialog: () -> Unit,
    onPomodoroSubjectSelected: (String) -> Unit,
    onPomodoroTitleChanged: (String) -> Unit,
    onPomodoroDurationSelected: (Long) -> Unit,
    onPomodoroStart: () -> Unit,
    onPomodoroPause: () -> Unit,
    onPomodoroReset: () -> Unit,
    onPomodoroSwitchPhase: (PomodoroPhase) -> Unit,
    onPomodoroCompleteNow: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("study_planner_section_content"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            StudyPlannerHeaderCard(
                onAddSessionClick = { onOpenAddSessionDialog(false) },
                onLogCompletedClick = { onOpenAddSessionDialog(true) },
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        if (!actionMessage.isNullOrBlank()) {
            item {
                Column(modifier = Modifier.widthIn(max = 760.dp)) {
                    AuthStatusBanner(
                        message = actionMessage,
                        type = AuthBannerType.SUCCESS,
                        testTag = "study_planner_action_banner",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onClearActionMessage) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        item {
            PomodoroTimerCard(
                state = pomodoroState,
                enrolledCourses = enrolledCourses,
                onSelectSubject = onPomodoroSubjectSelected,
                onTitleChanged = onPomodoroTitleChanged,
                onSelectDuration = onPomodoroDurationSelected,
                onStart = onPomodoroStart,
                onPause = onPomodoroPause,
                onReset = onPomodoroReset,
                onSwitchPhase = onPomodoroSwitchPhase,
                onCompleteNow = onPomodoroCompleteNow,
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        when (uiState) {
            StudyPlannerUiState.Loading -> {
                item {
                    AcademicModuleLoadingCard(
                        message = "Syncing study goals, sessions, and streak history…",
                        testTag = "study_planner_loading_card",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is StudyPlannerUiState.Error -> {
                item {
                    AcademicModuleErrorCard(
                        message = uiState.message,
                        onRetry = onRetry,
                        testTag = "study_planner_error_card",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is StudyPlannerUiState.Empty -> {
                item {
                    StudyStreakAndGoalsCard(
                        stats = uiState.stats,
                        onEditGoalsClick = onOpenGoalsDialog,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
                item {
                    AcademicModuleEmptyCard(
                        icon = Icons.Rounded.Timer,
                        title = "No Study Sessions Yet",
                        subtitle = "Schedule subject-wise study blocks or complete a Pomodoro focus cycle to track streaks and productivity stats.",
                        ctaLabel = "Plan Study Session",
                        onCtaClick = { onOpenAddSessionDialog(false) },
                        testTag = "study_planner_empty_card",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is StudyPlannerUiState.Success -> {
                item {
                    StudyStreakAndGoalsCard(
                        stats = uiState.stats,
                        onEditGoalsClick = onOpenGoalsDialog,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                item {
                    WeeklyStudyStatsCard(
                        stats = uiState.stats,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                if (uiState.subjects.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = uiState.selectedSubjectFilter == null,
                                onClick = { onSelectSubjectFilter(null) },
                                label = { Text("All Subjects (${uiState.allSessions.size})") },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            )
                            uiState.subjects.forEach { subject ->
                                FilterChip(
                                    selected = uiState.selectedSubjectFilter == subject,
                                    onClick = { onSelectSubjectFilter(subject) },
                                    label = { Text(subject) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Planned Sessions (${uiState.plannedSessions.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        TextButton(onClick = { onOpenAddSessionDialog(false) }) {
                            Text("+ Plan")
                        }
                    }
                }

                if (uiState.plannedSessions.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp),
                        ) {
                            Text(
                                text = "No upcoming planned sessions. Tap \"Plan Session\" or start the Pomodoro timer above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                } else {
                    items(uiState.plannedSessions, key = { "planned-session-${it.id}" }) { session ->
                        StudySessionRowCard(
                            session = session,
                            onToggleCompleted = { onToggleSessionCompleted(session) },
                            onLaunchPomodoro = { onLaunchSessionInPomodoro(session) },
                            onEdit = { onOpenEditSessionDialog(session) },
                            onDelete = { onDeleteSession(session.id) },
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Completed Sessions (${uiState.completedSessions.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        TextButton(onClick = { onOpenAddSessionDialog(true) }) {
                            Text("+ Log")
                        }
                    }
                }

                if (uiState.completedSessions.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp),
                        ) {
                            Text(
                                text = "Completed Pomodoro and study sessions will appear here and build your daily streak.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                } else {
                    items(uiState.completedSessions, key = { "completed-session-${it.id}" }) { session ->
                        StudySessionRowCard(
                            session = session,
                            onToggleCompleted = { onToggleSessionCompleted(session) },
                            onLaunchPomodoro = null,
                            onEdit = { onOpenEditSessionDialog(session) },
                            onDelete = { onDeleteSession(session.id) },
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyPlannerHeaderCard(
    onAddSessionClick: () -> Unit,
    onLogCompletedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Study Planner & Pomodoro",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            )
            Text(
                text = "Set daily & weekly study goals, run Pomodoro focus sessions, and track streaks offline & in Cloud Firestore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAddSessionClick,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("study_plan_session_button"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Plan Session")
                }
                OutlinedButton(
                    onClick = onLogCompletedClick,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("study_log_session_button"),
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log Completed")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PomodoroTimerCard(
    state: PomodoroUiState,
    enrolledCourses: List<StudentCourse>,
    onSelectSubject: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onSelectDuration: (Long) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSwitchPhase: (PomodoroPhase) -> Unit,
    onCompleteNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeAccent = if (state.phase == PomodoroPhase.FOCUS) UniMatePurplePrimary else UniMateEmerald
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, activeAccent.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pomodoro_timer_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PomodoroPhase.entries.forEach { phase ->
                    FilterChip(
                        selected = state.phase == phase,
                        onClick = { onSwitchPhase(phase) },
                        label = { Text(phase.label) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("pomodoro_phase_${phase.wireValue.lowercase()}"),
                    )
                }
            }

            Box(
                modifier = Modifier.size(164.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(156.dp)) {
                    val strokeWidth = 12.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = activeAccent,
                        startAngle = -90f,
                        sweepAngle = 360f * state.progressFraction(),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.formattedClock(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = activeAccent,
                        modifier = Modifier.testTag("pomodoro_clock_text"),
                    )
                    Text(
                        text = state.selectedSubject,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (enrolledCourses.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AssistChip(
                        onClick = { onSelectSubject("General Study") },
                        label = { Text("General") },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                    enrolledCourses.forEach { course ->
                        val label = "${course.courseCode} • ${course.courseName}"
                        AssistChip(
                            onClick = { onSelectSubject(label) },
                            label = { Text(course.courseCode) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.sessionTitle,
                onValueChange = onTitleChanged,
                label = { Text("Focus Topic (optional)") },
                placeholder = { Text("e.g. Chapter 5 Exercises") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!state.running && state.phase == PomodoroPhase.FOCUS) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(15L, 25L, 45L, 50L).forEach { mins ->
                        FilterChip(
                            selected = state.focusMinutes == mins,
                            onClick = { onSelectDuration(mins) },
                            label = { Text("${mins}m") },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = if (state.running) onPause else onStart,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("pomodoro_primary_button"),
                ) {
                    Icon(
                        imageVector = if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.running) "Pause" else "Start")
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pomodoro_reset_button"),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset timer", modifier = Modifier.size(18.dp))
                }
                OutlinedButton(
                    onClick = onCompleteNow,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pomodoro_complete_button"),
                ) {
                    Text(if (state.phase == PomodoroPhase.FOCUS) "Complete" else "Done")
                }
            }
        }
    }
}

@Composable
private fun StudySessionRowCard(
    session: StudySession,
    onToggleCompleted: () -> Unit,
    onLaunchPomodoro: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = session.resolvedMode()
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (session.completed) UniMateEmerald.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("study_session_card_${session.id}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onToggleCompleted,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = if (session.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (session.completed) "Mark session planned" else "Mark session completed",
                        tint = if (session.completed) UniMateEmerald else MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = UniMateBlueElectric.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = session.subject,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = UniMateBlueElectric,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = (if (mode == StudySessionMode.POMODORO) UniMatePurplePrimary else UniMateEmerald).copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = "${session.durationMinutes}m • ${mode.label}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (mode == StudySessionMode.POMODORO) UniMatePurplePrimary else UniMateEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = session.formattedDateTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (session.notes.isNotBlank()) {
                Text(
                    text = session.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!session.completed && onLaunchPomodoro != null) {
                    TextButton(
                        onClick = onLaunchPomodoro,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Focus Now")
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit study session", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete study session", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
