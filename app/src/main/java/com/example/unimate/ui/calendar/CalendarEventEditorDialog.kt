package com.example.unimate.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.AcademicDateFormatters
import com.example.unimate.data.model.CalendarEventCategory
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.CourseQuickFillRow
import com.example.unimate.ui.components.showNativeDatePicker
import com.example.unimate.ui.components.showNativeTimePicker

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarEventEditorDialog(
    state: CalendarEventEditorState,
    enrolledCourses: List<StudentCourse>,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onCategoryChanged: (CalendarEventCategory) -> Unit,
    onLocationOrCourseChanged: (String) -> Unit,
    onAllDayChanged: (Boolean) -> Unit,
    onStartAtChanged: (Long) -> Unit,
    onEndAtChanged: (Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = {
            Text(
                text = if (state.editingEventId == null) "Add Calendar Event" else "Edit Calendar Event",
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
                    text = "Event Category",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CalendarEventCategory.entries.forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { onCategoryChanged(category) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(category.color),
                                )
                            },
                            label = { Text(category.label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_editor_cat_${category.wireValue.lowercase()}"),
                        )
                    }
                }

                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChanged,
                    label = { Text("Event Title") },
                    placeholder = { Text("e.g. Tech Symposium / Spring Break") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calendar_editor_title_input"),
                )

                CourseQuickFillRow(
                    enrolledCourses = enrolledCourses,
                    onCourseSelected = onLocationOrCourseChanged,
                )

                OutlinedTextField(
                    value = state.locationOrCourse,
                    onValueChange = onLocationOrCourseChanged,
                    label = { Text("Course or Location (Optional)") },
                    placeholder = { Text("e.g. Main Auditorium or CS-301") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calendar_editor_location_input"),
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChanged,
                    label = { Text("Notes / Details (Optional)") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calendar_editor_description_input"),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "All-day event",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "Spans the full selected date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.allDay,
                        onCheckedChange = onAllDayChanged,
                        modifier = Modifier.testTag("calendar_editor_allday_switch"),
                    )
                }

                Text(
                    text = "Start Date & Time",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            showNativeDatePicker(context, state.startAtMillis) { picked ->
                                onStartAtChanged(picked)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("calendar_editor_start_date"),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AcademicDateFormatters.formatDateOnly(state.startAtMillis))
                    }
                    if (!state.allDay) {
                        OutlinedButton(
                            onClick = {
                                showNativeTimePicker(context, state.startAtMillis) { picked ->
                                    onStartAtChanged(picked)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_editor_start_time"),
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AcademicDateFormatters.formatTimeOnly(state.startAtMillis))
                        }
                    }
                }

                Text(
                    text = "End Date & Time",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            showNativeDatePicker(context, state.endAtMillis) { picked ->
                                onEndAtChanged(picked)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("calendar_editor_end_date"),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AcademicDateFormatters.formatDateOnly(state.endAtMillis))
                    }
                    if (!state.allDay) {
                        OutlinedButton(
                            onClick = {
                                showNativeTimePicker(context, state.endAtMillis) { picked ->
                                    onEndAtChanged(picked)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_editor_end_time"),
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AcademicDateFormatters.formatTimeOnly(state.endAtMillis))
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
                    .testTag("calendar_editor_save_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.editingEventId == null) "Save Event" else "Update Event")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
            ) {
                Text("Cancel")
            }
        },
    )
}
