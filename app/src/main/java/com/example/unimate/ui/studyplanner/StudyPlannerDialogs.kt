package com.example.unimate.ui.studyplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.data.model.AcademicDateFormatters
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.CourseQuickFillRow
import com.example.unimate.ui.components.showNativeDatePicker
import com.example.unimate.ui.components.showNativeTimePicker

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudySessionEditorDialog(
    state: StudySessionEditorState,
    enrolledCourses: List<StudentCourse>,
    onTitleChanged: (String) -> Unit,
    onSubjectChanged: (String) -> Unit,
    onDurationChanged: (String) -> Unit,
    onCompletedChanged: (Boolean) -> Unit,
    onScheduledAtChanged: (Long) -> Unit,
    onNotesChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = {
            Text(
                text = if (state.editingSessionId == null) "Study Session" else "Edit Study Session",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuthStatusBanner(message = state.errorMessage, type = AuthBannerType.ERROR)

                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChanged,
                    label = { Text("Session Topic *") },
                    placeholder = { Text("e.g. Revise Graph Algorithms & BFS") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("study_session_title_input"),
                )

                CourseQuickFillRow(
                    enrolledCourses = enrolledCourses,
                    onCourseSelected = onSubjectChanged,
                )

                OutlinedTextField(
                    value = state.subject,
                    onValueChange = onSubjectChanged,
                    label = { Text("Subject / Course *") },
                    placeholder = { Text("e.g. CS-301 • Algorithms") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("study_session_subject_input"),
                )

                Text(
                    text = "Session Duration (Minutes)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 25, 45, 60, 90).forEach { mins ->
                        FilterChip(
                            selected = state.durationMinutesText == mins.toString(),
                            onClick = { onDurationChanged(mins.toString()) },
                            label = { Text("${mins}m") },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
                OutlinedTextField(
                    value = state.durationMinutesText,
                    onValueChange = onDurationChanged,
                    label = { Text("Custom Duration (1–600 mins) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("study_session_duration_input"),
                )

                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.completed,
                        onClick = { onCompletedChanged(false) },
                        label = { Text("Planned Session") },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                    FilterChip(
                        selected = state.completed,
                        onClick = { onCompletedChanged(true) },
                        label = { Text("Log as Completed") },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                }

                Text(
                    text = if (state.completed) "Completed Date & Time" else "Scheduled Date & Time",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showNativeDatePicker(context, state.scheduledAtMillis, onScheduledAtChanged) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AcademicDateFormatters.formatDateOnly(state.scheduledAtMillis),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = { showNativeTimePicker(context, state.scheduledAtMillis, onScheduledAtChanged) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AcademicDateFormatters.formatTimeOnly(state.scheduledAtMillis))
                    }
                }

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Study Notes (optional)") },
                    placeholder = { Text("Chapters covered or target problems") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("study_session_save_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.editingSessionId == null) "Save Session" else "Update")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.dashboard_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerGoalsSettingsDialog(
    state: PlannerGoalsEditorState,
    onDailyChanged: (String) -> Unit,
    onWeeklyChanged: (String) -> Unit,
    onFocusMinutesChanged: (String) -> Unit,
    onBreakMinutesChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text("Study Goals & Pomodoro Defaults") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuthStatusBanner(message = state.errorMessage, type = AuthBannerType.ERROR)

                Text(
                    text = "Daily Study Goal (Minutes)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 120, 180, 240).forEach { mins ->
                        FilterChip(
                            selected = state.dailyGoalMinutesText == mins.toString(),
                            onClick = { onDailyChanged(mins.toString()) },
                            label = { Text("${mins / 60}h") },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
                OutlinedTextField(
                    value = state.dailyGoalMinutesText,
                    onValueChange = onDailyChanged,
                    label = { Text("Daily Target (15–720 min)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("planner_daily_goal_input"),
                )

                Text(
                    text = "Weekly Study Goal (Minutes)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(300, 600, 840, 1200).forEach { mins ->
                        FilterChip(
                            selected = state.weeklyGoalMinutesText == mins.toString(),
                            onClick = { onWeeklyChanged(mins.toString()) },
                            label = { Text("${mins / 60}h") },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
                OutlinedTextField(
                    value = state.weeklyGoalMinutesText,
                    onValueChange = onWeeklyChanged,
                    label = { Text("Weekly Target (30–5040 min)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("planner_weekly_goal_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.pomodoroFocusMinutesText,
                        onValueChange = onFocusMinutesChanged,
                        label = { Text("Focus (min)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.pomodoroBreakMinutesText,
                        onValueChange = onBreakMinutesChanged,
                        label = { Text("Break (min)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("planner_goals_save_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Goals")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.dashboard_cancel))
            }
        },
    )
}
