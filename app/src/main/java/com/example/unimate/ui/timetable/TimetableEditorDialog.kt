package com.example.unimate.ui.timetable

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.data.model.DayOfWeekOption
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.SubjectColorPalette
import com.example.unimate.data.model.TimeFormatUtils
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.theme.UniMateAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimetableEditorDialog(
    state: TimetableEditorState,
    enrolledCourses: List<StudentCourse>,
    onSelectCourseTemplate: (StudentCourse) -> Unit,
    onDayChanged: (DayOfWeekOption) -> Unit,
    onSubjectChanged: (String) -> Unit,
    onCourseCodeChanged: (String) -> Unit,
    onInstructorChanged: (String) -> Unit,
    onClassroomChanged: (String) -> Unit,
    onStartTimeChanged: (Long) -> Unit,
    onEndTimeChanged: (Long) -> Unit,
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val today = DayOfWeekOption.today()
    val durationLabel = TimeFormatUtils.formatDuration(state.startTimeMinutes, state.endTimeMinutes)

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = {
            Text(
                text = if (state.editingClassId == null) "Add Timetable Class" else "Edit Timetable Class",
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

                if (state.liveConflicts.isNotEmpty()) {
                    Surface(
                        color = UniMateAmber.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, UniMateAmber.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("timetable_editor_conflict_warning"),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = UniMateAmber,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Schedule Conflict Detected",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = UniMateAmber,
                                )
                                state.liveConflicts.forEach { conflict ->
                                    Text(
                                        text = "Overlaps ${conflict.courseCode} (${conflict.subjectName}) • ${conflict.formattedTimeRange()}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }

                if (enrolledCourses.isNotEmpty() && state.editingClassId == null) {
                    Text(
                        text = "Quick fill from your courses",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        enrolledCourses.forEach { course ->
                            AssistChip(
                                onClick = { onSelectCourseTemplate(course) },
                                label = { Text("${course.courseCode} • ${course.courseName}") },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            )
                        }
                    }
                }

                Text(
                    text = "Day of Week (Monday – Sunday)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DayOfWeekOption.entries.forEach { day ->
                        FilterChip(
                            selected = state.dayOfWeek == day,
                            onClick = { onDayChanged(day) },
                            label = {
                                Text(if (day == today) "${day.shortLabel} (Today)" else day.shortLabel)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }

                OutlinedTextField(
                    value = state.subjectName,
                    onValueChange = onSubjectChanged,
                    label = { Text("Subject Name") },
                    placeholder = { Text("e.g. Data Structures") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tt_subject_input"),
                )
                OutlinedTextField(
                    value = state.courseCode,
                    onValueChange = onCourseCodeChanged,
                    label = { Text("Course Code") },
                    placeholder = { Text("e.g. CS-301") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tt_code_input"),
                )
                OutlinedTextField(
                    value = state.instructor,
                    onValueChange = onInstructorChanged,
                    label = { Text("Instructor") },
                    placeholder = { Text("e.g. Dr. Tariq Mehmood") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tt_instructor_input"),
                )
                OutlinedTextField(
                    value = state.classroom,
                    onValueChange = onClassroomChanged,
                    label = { Text("Classroom") },
                    placeholder = { Text("e.g. AB-2 Room 204") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tt_classroom_input"),
                )

                Text(
                    text = "Time & Duration ($durationLabel)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val initHour = (state.startTimeMinutes / 60L).toInt()
                            val initMin = (state.startTimeMinutes % 60L).toInt()
                            TimePickerDialog(
                                context,
                                { _, h, m -> onStartTimeChanged(h * 60L + m) },
                                initHour,
                                initMin,
                                false,
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("tt_start_time_button"),
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start: ${TimeFormatUtils.formatMinutes(state.startTimeMinutes)}")
                    }
                    OutlinedButton(
                        onClick = {
                            val initHour = (state.endTimeMinutes / 60L).toInt()
                            val initMin = (state.endTimeMinutes % 60L).toInt()
                            TimePickerDialog(
                                context,
                                { _, h, m -> onEndTimeChanged(h * 60L + m) },
                                initHour,
                                initMin,
                                false,
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("tt_end_time_button"),
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("End: ${TimeFormatUtils.formatMinutes(state.endTimeMinutes)}")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(45L to "45m", 60L to "1h", 90L to "1h 30m", 120L to "2h").forEach { (mins, label) ->
                        AssistChip(
                            onClick = {
                                val nextEnd = (state.startTimeMinutes + mins).coerceAtMost(1439L)
                                onEndTimeChanged(nextEnd)
                            },
                            label = { Text(label) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }

                Text(
                    text = "Subject Color",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SubjectColorPalette.options.forEach { option ->
                        val selected = state.colorIndex == option.index
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(option.color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.6f),
                                    shape = CircleShape,
                                )
                                .clickable { onColorSelected(option.index) },
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected ${option.name}",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.testTag("tt_save_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.editingClassId == null) "Add Class" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                Text(stringResource(R.string.dashboard_cancel))
            }
        },
    )
}
