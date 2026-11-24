package com.example.unimate.ui.quizzes

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.example.unimate.data.model.QuizPreparationStatus
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.CourseQuickFillRow
import com.example.unimate.ui.components.showNativeDatePicker
import com.example.unimate.ui.components.showNativeTimePicker

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizEditorDialog(
    state: QuizEditorState,
    enrolledCourses: List<StudentCourse>,
    onTitleChanged: (String) -> Unit,
    onCourseChanged: (String) -> Unit,
    onScheduledAtChanged: (Long) -> Unit,
    onTotalMarksChanged: (String) -> Unit,
    onScoreRecordedChanged: (Boolean) -> Unit,
    onObtainedMarksChanged: (String) -> Unit,
    onPreparationStatusChanged: (QuizPreparationStatus) -> Unit,
    onNotesChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = {
            Text(
                text = if (state.editingQuizId == null) "Add Quiz" else "Edit Quiz",
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
                    label = { Text("Quiz Title *") },
                    placeholder = { Text("e.g. Quiz 2: Normalization & SQL") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quiz_title_input"),
                )

                CourseQuickFillRow(
                    enrolledCourses = enrolledCourses,
                    onCourseSelected = onCourseChanged,
                )

                OutlinedTextField(
                    value = state.courseName,
                    onValueChange = onCourseChanged,
                    label = { Text("Course *") },
                    placeholder = { Text("e.g. CS-310 • Database Systems") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quiz_course_input"),
                )

                Text(
                    text = "Quiz Date & Time",
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
                            .minimumInteractiveComponentSize()
                            .testTag("quiz_date_picker_button"),
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
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("quiz_time_picker_button"),
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AcademicDateFormatters.formatTimeOnly(state.scheduledAtMillis))
                    }
                }

                OutlinedTextField(
                    value = state.totalMarksText,
                    onValueChange = onTotalMarksChanged,
                    label = { Text("Total Marks *") },
                    placeholder = { Text("e.g. 20") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quiz_total_marks_input"),
                )

                Text(
                    text = "Preparation Status",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuizPreparationStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.preparationStatus == status,
                            onClick = { onPreparationStatusChanged(status) },
                            label = { Text(status.label) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Record Score (Moves to Quiz History)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "Log obtained marks once graded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.scoreRecorded,
                        onCheckedChange = onScoreRecordedChanged,
                        modifier = Modifier.testTag("quiz_score_switch"),
                    )
                }

                if (state.scoreRecorded) {
                    OutlinedTextField(
                        value = state.obtainedMarksText,
                        onValueChange = onObtainedMarksChanged,
                        label = { Text("Obtained Marks") },
                        placeholder = { Text("e.g. 18.5") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quiz_obtained_marks_input"),
                    )
                }

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Topics / Study Notes (optional)") },
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
                    .testTag("quiz_save_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.editingQuizId == null) "Save Quiz" else "Update")
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
