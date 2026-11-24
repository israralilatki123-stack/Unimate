package com.example.unimate.ui.gpa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.GradeScaleItem
import com.example.unimate.data.model.SemesterEntryMode
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.theme.UniMatePurplePrimary
import java.util.Locale

@Composable
fun SemesterGpaEditorDialog(
    state: SemesterEditorState,
    enrolledCourses: List<StudentCourse>,
    gradingScale: List<GradeScaleItem>,
    onSemesterNameChanged: (String) -> Unit,
    onOrderChanged: (String) -> Unit,
    onActiveSemesterChanged: (Boolean) -> Unit,
    onEntryModeChanged: (SemesterEntryMode) -> Unit,
    onSummaryCreditsChanged: (String) -> Unit,
    onSummaryGpaChanged: (String) -> Unit,
    onImportAllEnrolled: () -> Unit,
    onAddEnrolledCourse: (StudentCourse) -> Unit,
    onDraftCourseCodeChanged: (String) -> Unit,
    onDraftCourseNameChanged: (String) -> Unit,
    onDraftCreditHoursChanged: (String) -> Unit,
    onDraftLetterGradeChanged: (String) -> Unit,
    onAddCustomCourse: () -> Unit,
    onUpdateDraftCourseGrade: (String, String) -> Unit,
    onRemoveDraftCourse: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (state.editingRecordId == null) "Add Semester Record" else "Edit Semester Record")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!state.errorMessage.isNullOrBlank()) {
                    AuthStatusBanner(message = state.errorMessage, type = AuthBannerType.ERROR)
                }

                OutlinedTextField(
                    value = state.semesterName,
                    onValueChange = onSemesterNameChanged,
                    label = { Text("Semester Name") },
                    placeholder = { Text("e.g. Fall 2026 (Sem 5)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gpa_semester_name_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.orderIndexInput,
                        onValueChange = onOrderChanged,
                        label = { Text("Order #") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1.6f),
                    ) {
                        Checkbox(
                            checked = state.activeSemester,
                            onCheckedChange = onActiveSemesterChanged,
                        )
                        Text("Current Semester", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SemesterEntryMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.entryMode == mode,
                            onClick = { onEntryModeChanged(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("gpa_entry_mode_${mode.wireValue.lowercase()}"),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = UniMatePurplePrimary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Preview: ${String.format(Locale.US, "%.1f", state.livePreviewCredits())} Cr",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = "GPA ${String.format(Locale.US, "%.2f", state.livePreviewGpa())}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = UniMatePurplePrimary,
                        )
                    }
                }

                if (state.entryMode == SemesterEntryMode.SUMMARY) {
                    OutlinedTextField(
                        value = state.summaryCreditsInput,
                        onValueChange = onSummaryCreditsChanged,
                        label = { Text("Completed Credit Hours") },
                        placeholder = { Text("e.g. 16.0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gpa_summary_credits_input"),
                    )
                    OutlinedTextField(
                        value = state.summaryGpaInput,
                        onValueChange = onSummaryGpaChanged,
                        label = { Text("Semester GPA") },
                        placeholder = { Text("e.g. 3.55") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gpa_summary_gpa_input"),
                    )
                } else {
                    if (enrolledCourses.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Quick Pick Enrolled",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                            TextButton(onClick = onImportAllEnrolled) {
                                Text("Import All (${enrolledCourses.size})")
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            enrolledCourses.forEach { course ->
                                OutlinedButton(
                                    onClick = { onAddEnrolledCourse(course) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Text("+ ${course.courseCode}")
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Text(
                        text = "Add Course Manually",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.draftCourseCode,
                            onValueChange = onDraftCourseCodeChanged,
                            label = { Text("Code") },
                            placeholder = { Text("CS-301") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.draftCreditHours,
                            onValueChange = onDraftCreditHoursChanged,
                            label = { Text("Credits") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gpa_draft_credits_input"),
                        )
                    }
                    OutlinedTextField(
                        value = state.draftCourseName,
                        onValueChange = onDraftCourseNameChanged,
                        label = { Text("Course Title") },
                        placeholder = { Text("Data Structures & Algorithms") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gpa_draft_course_name_input"),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        gradingScale.forEach { item ->
                            FilterChip(
                                selected = state.draftLetterGrade == item.letter,
                                onClick = { onDraftLetterGradeChanged(item.letter) },
                                label = {
                                    Text("${item.letter} (${String.format(Locale.US, "%.2f", item.points)})")
                                },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onAddCustomCourse,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("gpa_add_draft_course_button"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Add Course to Semester")
                    }

                    if (state.courses.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "Semester Courses (${state.courses.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                        state.courses.forEach { course ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (course.courseCode.isNotBlank()) {
                                                    "${course.courseCode} • ${course.courseName}"
                                                } else {
                                                    course.courseName
                                                },
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            )
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f", course.creditHours)} Cr • " +
                                                    "${course.letterGrade} (${String.format(Locale.US, "%.2f", course.gradePoints)})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            onClick = { onRemoveDraftCourse(course.id) },
                                            modifier = Modifier.minimumInteractiveComponentSize(),
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove course")
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        gradingScale.forEach { item ->
                                            FilterChip(
                                                selected = course.letterGrade == item.letter,
                                                onClick = { onUpdateDraftCourseGrade(course.id, item.letter) },
                                                label = { Text(item.letter) },
                                                modifier = Modifier.minimumInteractiveComponentSize(),
                                            )
                                        }
                                    }
                                }
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
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("gpa_save_semester_button"),
            ) {
                Text(if (state.isSaving) "Saving…" else "Save Semester")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun GradingScaleEditorDialog(
    state: GradingScaleEditorState,
    onScaleNameChanged: (String) -> Unit,
    onRowLetterChanged: (String, String) -> Unit,
    onRowPointsChanged: (String, String) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (String) -> Unit,
    onApplyPreset: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurable Grading Scale") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Customize grade letters and points to match your official university/department handbook.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.errorMessage.isNullOrBlank()) {
                    AuthStatusBanner(message = state.errorMessage, type = AuthBannerType.ERROR)
                }
                OutlinedTextField(
                    value = state.scaleName,
                    onValueChange = onScaleNameChanged,
                    label = { Text("Scale Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onApplyPreset(true) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                    ) { Text("4.0 (+/-)") }
                    OutlinedButton(
                        onClick = { onApplyPreset(false) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                    ) { Text("4.0 Plus-Only") }
                }

                state.rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = row.letter,
                            onValueChange = { onRowLetterChanged(row.id, it) },
                            label = { Text("Letter") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = row.pointsInput,
                            onValueChange = { onRowPointsChanged(row.id, it) },
                            label = { Text("Points") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.2f),
                        )
                        IconButton(
                            onClick = { onRemoveRow(row.id) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove grade row")
                        }
                    }
                }

                OutlinedButton(
                    onClick = onAddRow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Add Grade Row")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("gpa_scale_save_button"),
            ) {
                Text(if (state.isSaving) "Saving…" else "Save Scale")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
