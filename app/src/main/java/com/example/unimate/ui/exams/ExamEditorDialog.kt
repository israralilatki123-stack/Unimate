package com.example.unimate.ui.exams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.data.model.AcademicDateFormatters
import com.example.unimate.data.model.ExamPreparationStatus
import com.example.unimate.data.model.ExamType
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.CourseQuickFillRow
import com.example.unimate.ui.components.showNativeDatePicker
import com.example.unimate.ui.components.showNativeTimePicker

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamEditorDialog(
    state: ExamEditorState,
    enrolledCourses: List<StudentCourse>,
    onTitleChanged: (String) -> Unit,
    onExamTypeChanged: (ExamType) -> Unit,
    onCourseChanged: (String) -> Unit,
    onExamAtChanged: (Long) -> Unit,
    onRoomChanged: (String) -> Unit,
    onPreparationStatusChanged: (ExamPreparationStatus) -> Unit,
    onChecklistTopicDraftChanged: (String) -> Unit,
    onAddChecklistTopic: () -> Unit,
    onToggleChecklistTopic: (String) -> Unit,
    onRemoveChecklistTopic: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = {
            Text(
                text = if (state.editingExamId == null) "Add Exam" else "Edit Exam",
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

                Text(
                    text = "Exam Type",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExamType.entries.forEach { type ->
                        FilterChip(
                            selected = state.examType == type,
                            onClick = { onExamTypeChanged(type) },
                            label = { Text("${type.label} Exam") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("exam_type_${type.wireValue.lowercase()}"),
                        )
                    }
                }

                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChanged,
                    label = { Text("Exam Title *") },
                    placeholder = { Text("e.g. Operating Systems Midterm") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_title_input"),
                )

                CourseQuickFillRow(
                    enrolledCourses = enrolledCourses,
                    onCourseSelected = onCourseChanged,
                )

                OutlinedTextField(
                    value = state.courseName,
                    onValueChange = onCourseChanged,
                    label = { Text("Course *") },
                    placeholder = { Text("e.g. CS-320 • Operating Systems") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_course_input"),
                )

                Text(
                    text = "Exam Date & Time",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showNativeDatePicker(context, state.examAtMillis, onExamAtChanged) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("exam_date_picker_button"),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AcademicDateFormatters.formatDateOnly(state.examAtMillis),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = { showNativeTimePicker(context, state.examAtMillis, onExamAtChanged) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("exam_time_picker_button"),
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AcademicDateFormatters.formatTimeOnly(state.examAtMillis))
                    }
                }

                OutlinedTextField(
                    value = state.room,
                    onValueChange = onRoomChanged,
                    label = { Text("Exam Room / Hall *") },
                    placeholder = { Text("e.g. Main Auditorium Seat 42") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_room_input"),
                )

                Text(
                    text = "Preparation Status",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExamPreparationStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.preparationStatus == status,
                            onClick = { onPreparationStatusChanged(status) },
                            label = { Text(status.label) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }

                Text(
                    text = "Revision Checklist (${state.revisionChecklist.size}/30)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.checklistTopicDraft,
                        onValueChange = onChecklistTopicDraftChanged,
                        label = { Text("Add chapter or topic") },
                        placeholder = { Text("e.g. Ch 5: Deadlocks") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("exam_topic_input"),
                    )
                    IconButton(
                        onClick = onAddChecklistTopic,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("exam_add_topic_button"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add revision topic")
                    }
                }

                state.revisionChecklist.forEach { topic ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = topic.done,
                                onCheckedChange = { onToggleChecklistTopic(topic.id) },
                            )
                            Text(
                                text = topic.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onRemoveChecklistTopic(topic.id) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove ${topic.title}",
                                    modifier = Modifier.size(16.dp),
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
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("exam_save_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.editingExamId == null) "Save Exam" else "Update")
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
