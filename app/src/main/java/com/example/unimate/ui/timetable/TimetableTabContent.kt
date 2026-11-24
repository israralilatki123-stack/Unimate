package com.example.unimate.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.DayOfWeekOption
import com.example.unimate.data.model.StudentReminder
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.dashboard.EmptySectionCard
import com.example.unimate.ui.dashboard.SectionHeaderRow
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary

@Composable
fun TimetableTabContent(
    timetableState: TimetableUiState,
    actionMessage: String?,
    reminders: List<StudentReminder>,
    isDemoReminders: Boolean,
    onSelectViewMode: (TimetableViewMode) -> Unit,
    onSelectDay: (DayOfWeekOption) -> Unit,
    onAddClassClick: (DayOfWeekOption) -> Unit,
    onEditClassClick: (TimetableClass) -> Unit,
    onDeleteClassClick: (String) -> Unit,
    onRetry: () -> Unit,
    onAddReminderClick: () -> Unit,
    onToggleReminderDone: (StudentReminder) -> Unit,
    onDeleteReminder: (StudentReminder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { DayOfWeekOption.today() }
    val activeDay = when (timetableState) {
        is TimetableUiState.Empty -> timetableState.selectedDay
        is TimetableUiState.Success -> timetableState.selectedDay
        else -> today
    }
    val activeViewMode = when (timetableState) {
        is TimetableUiState.Empty -> timetableState.viewMode
        is TimetableUiState.Success -> timetableState.viewMode
        else -> TimetableViewMode.DAILY
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("planner_tab_content"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Class Timetable & Planner",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = "Organize your Monday–Sunday classes with color-coded subjects and automatic conflict checks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onAddClassClick(activeDay) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("timetable_add_class_button"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Class")
                    }
                    OutlinedButton(
                        onClick = onAddReminderClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("planner_add_reminder_button"),
                    ) {
                        Icon(Icons.Rounded.AlarmAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Reminder")
                    }
                }
            }
        }

        if (!actionMessage.isNullOrBlank()) {
            item {
                AuthStatusBanner(
                    message = actionMessage,
                    type = AuthBannerType.INFO,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimetableViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = activeViewMode == mode,
                        onClick = { onSelectViewMode(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("timetable_mode_${mode.name.lowercase()}"),
                    )
                }
            }
        }

        when (timetableState) {
            TimetableUiState.Loading -> {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .testTag("timetable_loading_state"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                            Text(
                                text = "Syncing your timetable from Cloud Firestore…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            is TimetableUiState.Error -> {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .testTag("timetable_error_state"),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AuthStatusBanner(
                                message = timetableState.message,
                                type = AuthBannerType.ERROR,
                            )
                            OutlinedButton(
                                onClick = onRetry,
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Retry Sync")
                            }
                        }
                    }
                }
            }

            is TimetableUiState.Empty -> {
                item {
                    DayOfWeekStrip(
                        selectedDay = timetableState.selectedDay,
                        today = today,
                        classCountsByDay = emptyMap(),
                        onSelectDay = onSelectDay,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .testTag("timetable_empty_state"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = UniMatePurplePrimary,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = "Your timetable is empty",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "Add your classes for Monday through Sunday to see daily schedules, durations, and automatic overlap alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { onAddClassClick(timetableState.selectedDay) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Add Your First Class")
                            }
                        }
                    }
                }
            }

            is TimetableUiState.Success -> {
                val countsByDay = timetableState.allClasses.groupingBy { it.resolvedDay() }.eachCount()

                if (timetableState.totalConflictingClasses > 0) {
                    item {
                        Surface(
                            color = UniMateAmber.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, UniMateAmber.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp)
                                .testTag("timetable_conflict_banner"),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = UniMateAmber,
                                )
                                Text(
                                    text = "${timetableState.totalConflictingClasses} classes have overlapping schedules. Review highlighted cards below.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                if (timetableState.viewMode == TimetableViewMode.DAILY) {
                    item {
                        DayOfWeekStrip(
                            selectedDay = timetableState.selectedDay,
                            today = today,
                            classCountsByDay = countsByDay,
                            onSelectDay = onSelectDay,
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }

                    val dayClasses = timetableState.allClasses
                        .filter { it.resolvedDay() == timetableState.selectedDay }
                        .sortedBy { it.startTimeMinutes }

                    item {
                        SectionHeaderRow(
                            title = if (timetableState.selectedDay == today) {
                                "${timetableState.selectedDay.fullLabel} • Today's Classes (${dayClasses.size})"
                            } else {
                                "${timetableState.selectedDay.fullLabel} (${dayClasses.size})"
                            },
                            actionLabel = "+ Add",
                            onActionClick = { onAddClassClick(timetableState.selectedDay) },
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }

                    if (dayClasses.isEmpty()) {
                        item {
                            EmptySectionCard(
                                message = "No classes scheduled on ${timetableState.selectedDay.fullLabel}.",
                                ctaLabel = "Add Class",
                                onCtaClick = { onAddClassClick(timetableState.selectedDay) },
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                    } else {
                        items(dayClasses, key = { "daily-${it.id}" }) { item ->
                            TimetableClassCard(
                                item = item,
                                isToday = item.isToday(today),
                                conflictingPeers = timetableState.conflictMap[item.id].orEmpty(),
                                onEditClick = { onEditClassClick(item) },
                                onDeleteClick = { onDeleteClassClick(item.id) },
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                    }
                } else {
                    DayOfWeekOption.entries.forEach { day ->
                        val dayClasses = timetableState.allClasses
                            .filter { it.resolvedDay() == day }
                            .sortedBy { it.startTimeMinutes }
                        val isToday = day == today

                        item(key = "weekly-header-${day.wireValue}") {
                            WeeklyDayHeader(
                                day = day,
                                isToday = isToday,
                                classCount = dayClasses.size,
                                onAddForDay = { onAddClassClick(day) },
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }

                        if (dayClasses.isEmpty()) {
                            item(key = "weekly-empty-${day.wireValue}") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 760.dp),
                                ) {
                                    Text(
                                        text = "No classes scheduled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        } else {
                            items(dayClasses, key = { "weekly-${day.wireValue}-${it.id}" }) { item ->
                                TimetableClassCard(
                                    item = item,
                                    isToday = isToday,
                                    conflictingPeers = timetableState.conflictMap[item.id].orEmpty(),
                                    onEditClick = { onEditClassClick(item) },
                                    onDeleteClick = { onDeleteClassClick(item.id) },
                                    modifier = Modifier.widthIn(max = 760.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeaderRow(
                title = "Personal Reminders (${reminders.size})",
                actionLabel = "+ Add",
                onActionClick = onAddReminderClick,
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .padding(top = 8.dp),
            )
        }

        if (reminders.isEmpty()) {
            item {
                EmptySectionCard(
                    message = "No reminders added yet.",
                    ctaLabel = "Add Reminder",
                    onCtaClick = onAddReminderClick,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        } else {
            items(reminders, key = { "planner-rem-${it.id}" }) { reminder ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = { onToggleReminderDone(reminder) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = if (reminder.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (reminder.done) "Mark active" else "Mark done",
                                tint = if (reminder.done) UniMateEmerald else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reminder.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (reminder.done) TextDecoration.LineThrough else TextDecoration.None,
                                ),
                            )
                            Text(
                                text = reminder.remindAtText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!isDemoReminders) {
                            IconButton(
                                onClick = { onDeleteReminder(reminder) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete reminder",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
