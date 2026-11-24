package com.example.unimate.ui.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.AcademicDateFormatters
import com.example.unimate.data.model.ReminderCategory
import com.example.unimate.data.model.ScheduledReminder
import com.example.unimate.notifications.ReminderNotificationHelper
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.AcademicModuleEmptyCard
import com.example.unimate.ui.components.AcademicModuleErrorCard
import com.example.unimate.ui.components.AcademicModuleLoadingCard
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary

@Composable
fun RemindersSectionContent(
    state: RemindersUiState,
    actionMessage: String?,
    unreadNotificationsCount: Int,
    onSelectCategoryFilter: (ReminderCategory?) -> Unit,
    onAddReminderClick: (ReminderCategory) -> Unit,
    onEditReminderClick: (ScheduledReminder) -> Unit,
    onToggleEnabled: (ScheduledReminder) -> Unit,
    onToggleCompleted: (ScheduledReminder) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onOpenNotificationsClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(ReminderNotificationHelper.hasNotificationPermission(context))
    }
    val exactAlarmsAllowed = remember {
        ReminderNotificationHelper.canScheduleExactAlarmsSafely(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasNotificationPermission = granted },
    )

    var pendingDelete by remember { mutableStateOf<ScheduledReminder?>(null) }
    val activeCategory = (state as? RemindersUiState.Success)?.selectedCategory

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("reminders_section"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Smart Reminders",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = "Schedule WorkManager alerts for classes, assignments, quizzes, exams, study blocks, and custom tasks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { onAddReminderClick(activeCategory ?: ReminderCategory.CLASS) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reminders_add_button"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Reminder")
                    }
                    OutlinedButton(
                        onClick = onOpenNotificationsClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reminders_open_inbox_button"),
                    ) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (unreadNotificationsCount > 0) "Inbox ($unreadNotificationsCount)"
                            else "Notification Inbox",
                        )
                    }
                }
            }
        }

        item {
            ReminderPermissionStatusCard(
                hasNotificationPermission = hasNotificationPermission,
                exactAlarmsAllowed = exactAlarmsAllowed,
                onRequestPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        if (!actionMessage.isNullOrBlank()) {
            item {
                AuthStatusBanner(
                    message = actionMessage,
                    type = AuthBannerType.INFO,
                    modifier = Modifier.widthIn(max = 760.dp),
                    testTag = "reminders_action_banner",
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = activeCategory == null,
                    onClick = { onSelectCategoryFilter(null) },
                    label = { Text("All Reminders") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reminders_category_all"),
                )
                ReminderCategory.entries.forEach { category ->
                    FilterChip(
                        selected = activeCategory == category,
                        onClick = {
                            onSelectCategoryFilter(if (activeCategory == category) null else category)
                        },
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
                            .testTag("reminders_category_${category.wireValue.lowercase()}"),
                    )
                }
            }
        }

        when (state) {
            RemindersUiState.Loading -> {
                item {
                    AcademicModuleLoadingCard(
                        message = "Syncing scheduled reminders from Cloud Firestore…",
                        testTag = "reminders_loading_state",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is RemindersUiState.Error -> {
                item {
                    AcademicModuleErrorCard(
                        message = state.message,
                        onRetry = onRetry,
                        testTag = "reminders_error_state",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            RemindersUiState.Empty -> {
                item {
                    AcademicModuleEmptyCard(
                        icon = Icons.Rounded.Alarm,
                        title = "No reminders scheduled yet",
                        subtitle = "Create class, assignment, quiz, exam, study, or custom reminders backed by WorkManager and Firestore.",
                        ctaLabel = "Schedule First Reminder",
                        onCtaClick = { onAddReminderClick(ReminderCategory.CLASS) },
                        testTag = "reminders_empty_state",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is RemindersUiState.Success -> {
                if (state.filteredReminders.isEmpty()) {
                    item {
                        AcademicModuleEmptyCard(
                            icon = Icons.Rounded.Alarm,
                            title = "No reminders in this category",
                            subtitle = "Add a ${state.selectedCategory?.label ?: "new reminder"} or switch back to All Reminders.",
                            ctaLabel = "Add ${state.selectedCategory?.shortLabel ?: "Reminder"}",
                            onCtaClick = { onAddReminderClick(state.selectedCategory ?: ReminderCategory.CUSTOM) },
                            testTag = "reminders_filtered_empty_state",
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                } else {
                    items(state.filteredReminders, key = { it.id }) { reminder ->
                        ScheduledReminderCard(
                            reminder = reminder,
                            onEditClick = { onEditReminderClick(reminder) },
                            onToggleEnabled = { onToggleEnabled(reminder) },
                            onToggleCompleted = { onToggleCompleted(reminder) },
                            onDeleteClick = { pendingDelete = reminder },
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Reminder?") },
            text = { Text("Cancel local notification scheduling and delete \"${item.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteReminder(item.id)
                        pendingDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ReminderPermissionStatusCard(
    hasNotificationPermission: Boolean,
    exactAlarmsAllowed: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (hasNotificationPermission) UniMateEmerald.copy(alpha = 0.08f) else UniMateAmber.copy(alpha = 0.12f)
    val borderColor = if (hasNotificationPermission) UniMateEmerald.copy(alpha = 0.35f) else UniMateAmber.copy(alpha = 0.55f)
    val iconColor = if (hasNotificationPermission) UniMateEmerald else UniMateAmber

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reminders_permission_card"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (hasNotificationPermission) {
                        Icons.Rounded.NotificationsActive
                    } else {
                        Icons.Rounded.NotificationsOff
                    },
                    contentDescription = null,
                    tint = iconColor,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasNotificationPermission) {
                            "Local Notifications Enabled"
                        } else {
                            "Notification Permission Required (Android 13+)"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = if (exactAlarmsAllowed) {
                            "Scheduled locally via WorkManager. Respects battery & exact-alarm rules."
                        } else {
                            "Scheduled via WorkManager (system restricts exact alarms on Android 14+; alerts fire inexact/best-effort)."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reminders_request_permission_button"),
                ) {
                    Text("Allow Notifications")
                }
            }
        }
    }
}

@Composable
private fun ScheduledReminderCard(
    reminder: ScheduledReminder,
    onEditClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleCompleted: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = reminder.resolvedCategory()
    val effectiveMillis = reminder.effectiveScheduleMillis()
    val leadText = when (reminder.leadMinutes) {
        0L -> "At scheduled time"
        60L -> "1 hour before"
        1440L -> "1 day before"
        else -> "${reminder.leadMinutes}m before"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (reminder.completed) MaterialTheme.colorScheme.outlineVariant else category.color.copy(alpha = 0.4f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reminder_card_${reminder.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onToggleCompleted,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reminder_complete_${reminder.id}"),
                ) {
                    Icon(
                        imageVector = if (reminder.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (reminder.completed) "Mark active" else "Mark completed",
                        tint = if (reminder.completed) UniMateEmerald else category.color,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = category.color.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = category.color,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        if (reminder.enabled && !reminder.completed) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = UniMatePurplePrimary.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    text = leadText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UniMatePurplePrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (reminder.completed) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "Event: ${AcademicDateFormatters.formatDateTime(reminder.triggerAtMillis)} • Alert: ${
                            AcademicDateFormatters.formatDateTime(effectiveMillis)
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (reminder.notes.isNotBlank()) {
                        Text(
                            text = reminder.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(
                        checked = reminder.enabled && !reminder.completed,
                        enabled = !reminder.completed,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.testTag("reminder_switch_${reminder.id}"),
                    )
                    Text(
                        text = when {
                            reminder.completed -> "Completed"
                            reminder.enabled -> "WorkManager Scheduled"
                            else -> "Paused"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reminder_edit_${reminder.id}"),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit reminder")
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reminder_delete_${reminder.id}"),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete reminder")
                    }
                }
            }
        }
    }
}
