package com.example.unimate.ui.assignments

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.example.unimate.data.model.AssignmentPriority
import com.example.unimate.data.model.AssignmentStatus
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.CourseQuickFillRow
import com.example.unimate.ui.components.resolveDocumentDisplayName
import com.example.unimate.ui.components.showNativeDatePicker
import com.example.unimate.ui.components.showNativeTimePicker
import com.example.unimate.ui.components.tryTakePersistableReadPermission

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssignmentEditorDialog(
    state: AssignmentEditorState,
    enrolledCourses: List<StudentCourse>,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onCourseChanged: (String) -> Unit,
    onDeadlineChanged: (Long) -> Unit,
    onPriorityChanged: (AssignmentPriority) -> Unit,
    onStatusChanged: (AssignmentStatus) -> Unit,
    onLinkDraftNameChanged: (String) -> Unit,
    onLinkDraftUriChanged: (String) -> Unit,
    onAddPickedAttachment: (name: String, uri: String, mimeType: String) -> Unit,
    onAddLinkAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current

    // Zero-permission Storage Access Framework file picker (Play Policy compliant)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                tryTakePersistableReadPermission(context, uri)
                val name = resolveDocumentDisplayName(context, uri)
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                onAddPickedAttachment(name, uri.toString(), mime)
            }
        },
    )

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = {
            Text(
                text = if (state.editingAssignmentId == null) "Add Assignment" else "Edit Assignment",
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
                    label = { Text("Assignment Title *") },
                    placeholder = { Text("e.g. Compiler Parser Implementation") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assignment_title_input"),
                )

                CourseQuickFillRow(
                    enrolledCourses = enrolledCourses,
                    onCourseSelected = onCourseChanged,
                )

                OutlinedTextField(
                    value = state.courseName,
                    onValueChange = onCourseChanged,
                    label = { Text("Course *") },
                    placeholder = { Text("e.g. CS-312 • Compiler Construction") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assignment_course_input"),
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChanged,
                    label = { Text("Description & Requirements") },
                    placeholder = { Text("Deliverables, rubric notes, or submission details") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assignment_description_input"),
                )

                Text(
                    text = "Deadline",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showNativeDatePicker(context, state.deadlineMillis, onDeadlineChanged) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("assignment_date_picker_button"),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AcademicDateFormatters.formatDateOnly(state.deadlineMillis),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = { showNativeTimePicker(context, state.deadlineMillis, onDeadlineChanged) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("assignment_time_picker_button"),
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AcademicDateFormatters.formatTimeOnly(state.deadlineMillis))
                    }
                }

                Text(
                    text = "Priority",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssignmentPriority.entries.forEach { priority ->
                        FilterChip(
                            selected = state.priority == priority,
                            onClick = { onPriorityChanged(priority) },
                            label = { Text(priority.label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("assignment_priority_${priority.wireValue.lowercase()}"),
                        )
                    }
                }

                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssignmentStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.status == status,
                            onClick = { onStatusChanged(status) },
                            label = { Text(status.label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("assignment_status_${status.wireValue.lowercase()}"),
                        )
                    }
                }

                Text(
                    text = "Attachments (${state.attachments.size}/10)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                OutlinedButton(
                    onClick = { documentPickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("assignment_pick_file_button"),
                ) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Attach File / Document / Image")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.linkDraftUri,
                        onValueChange = onLinkDraftUriChanged,
                        label = { Text("Or paste resource URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onAddLinkAttachment,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("assignment_add_link_button"),
                    ) {
                        Icon(Icons.Rounded.AddLink, contentDescription = "Add resource link")
                    }
                }
                if (state.linkDraftUri.isNotBlank()) {
                    OutlinedTextField(
                        value = state.linkDraftName,
                        onValueChange = onLinkDraftNameChanged,
                        label = { Text("Link Label (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.attachments.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onRemoveAttachment(item.uri) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove ${item.name}",
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
                    .testTag("assignment_save_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.editingAssignmentId == null) "Save Assignment" else "Update")
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
