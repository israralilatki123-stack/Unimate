package com.example.unimate.ui.reminders

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
import com.example.unimate.data.model.ReminderCategory
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.showNativeDatePicker
import com.example.unimate.ui.components.showNativeTimePicker

private val leadPresetMinutes: List<Pair<Long, String>> = listOf(
    0L to "At time",
    15L to "15m before",
    30L to "30m before",
    60L to "1h before",
    1440L to "1 day before",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderEditorDialog(
    state: ReminderEditorState,
    onTitleChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onCategoryChanged: (ReminderCategory) -> Unit,
    onTriggerAtChanged: (Long) -> Unit,
    onLeadMinutesChanged: (Long) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = {
            Text(
                text = if (state.editingReminderId == null) "Schedule Reminder" else "Edit Reminder",
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
                    text = "Reminder Category",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ReminderCategory.entries.forEach { category ->
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
                            label = { Text(category.shortLabel) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reminder_editor_cat_${category.wireValue.lowercase()}"),
                        )
                    }
                }

                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChanged,
                    label = { Text("Reminder Title") },
                    placeholder = { Text("e.g. CS-301 Midterm Revision") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reminder_editor_title_input"),
                )

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Notes (Optional)") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reminder_editor_notes_input"),
                )

                Text(
                    text = "Target Date & Time",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            showNativeDatePicker(context, state.triggerAtMillis) { picked ->
                                onTriggerAtChanged(picked)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("reminder_editor_date_button"),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AcademicDateFormatters.formatDateOnly(state.triggerAtMillis))
                    }
                    OutlinedButton(
                        onClick = {
                            showNativeTimePicker(context, state.triggerAtMillis) { picked ->
                                onTriggerAtChanged(picked)
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reminder_editor_time_button"),
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AcademicDateFormatters.formatTimeOnly(state.triggerAtMillis))
                    }
                }

                Text(
                    text = "Alert Lead Time",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    leadPresetMinutes.forEach { (minutes, label) ->
                        FilterChip(
                            selected = state.leadMinutes == minutes,
                            onClick = { onLeadMinutesChanged(minutes) },
                            label = { Text(label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reminder_editor_lead_$minutes"),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Schedule Local Notification",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "Uses WorkManager background work",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = onEnabledChanged,
                        modifier = Modifier.testTag("reminder_editor_enabled_switch"),
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
                    .testTag("reminder_editor_save_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.editingReminderId == null) "Save Reminder" else "Update Reminder")
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
