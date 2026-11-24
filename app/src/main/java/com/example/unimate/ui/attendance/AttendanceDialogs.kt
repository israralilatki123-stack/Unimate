package com.example.unimate.ui.attendance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.AttendanceCalculator
import com.example.unimate.data.model.AttendanceStatus
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogAttendanceDialog(
    state: AttendanceLogEditorState,
    courses: List<StudentCourse>,
    onCourseSelected: (String) -> Unit,
    onStatusSelected: (AttendanceStatus) -> Unit,
    onDateSelected: (Long) -> Unit,
    onNotesChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.sessionDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                        showDatePicker = false
                    },
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Class Attendance") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!state.errorMessage.isNullOrBlank()) {
                    AuthStatusBanner(message = state.errorMessage, type = AuthBannerType.ERROR)
                }

                Text("Course", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                if (courses.isEmpty()) {
                    Text(
                        text = "No courses available yet. Add a course first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        courses.forEach { course ->
                            FilterChip(
                                selected = state.selectedCourseId == course.id,
                                onClick = { onCourseSelected(course.id) },
                                label = { Text("${course.courseCode} • ${course.courseName}") },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            )
                        }
                    }
                }

                Text("Status", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttendanceStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.status == status,
                            onClick = { onStatusSelected(status) },
                            label = { Text(status.label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("attendance_dialog_status_${status.wireValue.lowercase()}"),
                        )
                    }
                }

                Text("Session Date", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    text = AttendanceCalculator.formatSessionDate(state.sessionDateMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onDateSelected(AttendanceCalculator.startOfDayOffset(0)) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) { Text("Today") }
                    OutlinedButton(
                        onClick = { onDateSelected(AttendanceCalculator.startOfDayOffset(-1)) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) { Text("Yesterday") }
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) { Text("Pick Date") }
                }

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g. Lab 5 or Medical leave") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !state.isSaving && courses.isNotEmpty(),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("attendance_dialog_save_button"),
            ) {
                Text(if (state.isSaving) "Saving…" else "Save Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun DirectTotalsEditorDialog(
    state: DirectTotalsEditorState,
    onAttendedChanged: (String) -> Unit,
    onTotalChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Course Attendance Totals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = state.courseLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                if (!state.errorMessage.isNullOrBlank()) {
                    AuthStatusBanner(message = state.errorMessage, type = AuthBannerType.ERROR)
                }
                OutlinedTextField(
                    value = state.attendedInput,
                    onValueChange = onAttendedChanged,
                    label = { Text("Attended Classes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("attendance_totals_attended_input"),
                )
                OutlinedTextField(
                    value = state.totalInput,
                    onValueChange = onTotalChanged,
                    label = { Text("Total Classes Held") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("attendance_totals_total_input"),
                )
                Text(
                    text = "Tip: Use this to match past totals from before you started logging individual dates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("attendance_totals_save_button"),
            ) {
                Text(if (state.isSaving) "Saving…" else "Update Totals")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ThresholdSettingsDialog(
    state: ThresholdEditorState,
    onThresholdChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Attendance Threshold") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!state.errorMessage.isNullOrBlank()) {
                    AuthStatusBanner(message = state.errorMessage, type = AuthBannerType.ERROR)
                }
                Text(
                    text = "Minimum required percentage: ${state.thresholdPercent}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Slider(
                    value = state.thresholdPercent.toFloat(),
                    onValueChange = { onThresholdChanged(it.roundToInt().coerceIn(50, 95)) },
                    valueRange = 50f..95f,
                    steps = 44,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(70, 75, 80, 85).forEach { preset ->
                        FilterChip(
                            selected = state.thresholdPercent == preset,
                            onClick = { onThresholdChanged(preset) },
                            label = { Text("$preset%") },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
                Text(
                    text = "Shortage alerts and remaining allowable absences update automatically across all courses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("attendance_threshold_save_button"),
            ) {
                Text(if (state.isSaving) "Saving…" else "Save Threshold")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
