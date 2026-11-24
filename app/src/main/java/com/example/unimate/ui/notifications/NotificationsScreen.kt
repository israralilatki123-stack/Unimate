package com.example.unimate.ui.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.data.model.AcademicDateFormatters
import com.example.unimate.data.model.AppNotification
import com.example.unimate.data.model.NotificationFilter
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.AcademicModuleEmptyCard
import com.example.unimate.ui.components.AcademicModuleErrorCard
import com.example.unimate.ui.components.AcademicModuleLoadingCard
import com.example.unimate.ui.components.UniMateLogoMark
import com.example.unimate.ui.dashboard.SectionHeaderRow
import com.example.unimate.ui.theme.UniMatePurplePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    uiState: NotificationsUiState,
    actionMessage: String?,
    isMutating: Boolean,
    onSelectFilter: (NotificationFilter) -> Unit,
    onMarkAsRead: (String) -> Unit,
    onMarkAllAsRead: () -> Unit,
    onDeleteNotification: (String) -> Unit,
    onClearReadNotifications: () -> Unit,
    onClearAllNotifications: () -> Unit,
    onCreateReminderClick: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    var showClearAllConfirm by remember { mutableStateOf(false) }

    val unreadCount = (uiState as? NotificationsUiState.Success)?.unreadCount ?: 0
    val readCount = (uiState as? NotificationsUiState.Success)?.readCount ?: 0
    val selectedFilter = (uiState as? NotificationsUiState.Success)?.selectedFilter ?: NotificationFilter.ALL

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("notifications_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("notifications_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UniMateLogoMark(size = 34.dp)
                        Column {
                            Text(
                                text = "Notifications",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = if (unreadCount > 0) "$unreadCount unread alert(s)" else "Inbox up to date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        IconButton(
                            onClick = onMarkAllAsRead,
                            enabled = !isMutating,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("notifications_mark_all_read_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = "Mark all notifications as read",
                            )
                        }
                    }
                    if (unreadCount + readCount > 0) {
                        IconButton(
                            onClick = { showClearAllConfirm = true },
                            enabled = !isMutating,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("notifications_clear_all_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear notifications",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AuthStatusBanner(
                    message = "Local WorkManager reminder alerts appear here when triggered. Remote push (FCM) is omitted because FCM credentials are not configured.",
                    type = AuthBannerType.INFO,
                    modifier = Modifier.widthIn(max = 760.dp),
                    testTag = "notifications_fcm_status_banner",
                )
            }

            if (!actionMessage.isNullOrBlank()) {
                item {
                    AuthStatusBanner(
                        message = actionMessage,
                        type = AuthBannerType.SUCCESS,
                        modifier = Modifier.widthIn(max = 760.dp),
                        testTag = "notifications_action_banner",
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
                    NotificationFilter.entries.forEach { filter ->
                        val label = when (filter) {
                            NotificationFilter.ALL -> "All (${unreadCount + readCount})"
                            NotificationFilter.UNREAD -> "Unread ($unreadCount)"
                            NotificationFilter.READ -> "Read ($readCount)"
                        }
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { onSelectFilter(filter) },
                            label = { Text(label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("notifications_filter_${filter.name.lowercase()}"),
                        )
                    }
                }
            }

            if (uiState is NotificationsUiState.Success) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (uiState.unreadCount > 0) {
                            OutlinedButton(
                                onClick = onMarkAllAsRead,
                                enabled = !isMutating,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("notifications_mark_all_chip"),
                            ) {
                                Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mark All Read")
                            }
                        }
                        if (uiState.readCount > 0) {
                            OutlinedButton(
                                onClick = onClearReadNotifications,
                                enabled = !isMutating,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("notifications_clear_read_button"),
                            ) {
                                Text("Clear Read (${uiState.readCount})")
                            }
                        }
                        Button(
                            onClick = onCreateReminderClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("notifications_add_reminder_button"),
                        ) {
                            Icon(Icons.Rounded.AlarmAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Reminder")
                        }
                    }
                }
            }

            when (uiState) {
                NotificationsUiState.Loading -> {
                    item {
                        AcademicModuleLoadingCard(
                            message = "Loading your notification inbox from Cloud Firestore…",
                            testTag = "notifications_loading_state",
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }

                is NotificationsUiState.Error -> {
                    item {
                        AcademicModuleErrorCard(
                            message = uiState.message,
                            onRetry = onRetry,
                            testTag = "notifications_error_state",
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }

                NotificationsUiState.Empty -> {
                    item {
                        AcademicModuleEmptyCard(
                            icon = Icons.Rounded.NotificationsNone,
                            title = "Your notification inbox is empty",
                            subtitle = "Schedule a local reminder for classes, assignments, quizzes, or exams and delivered alerts will be logged here.",
                            ctaLabel = "Schedule Reminder",
                            onCtaClick = onCreateReminderClick,
                            testTag = "notifications_empty_state",
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }

                is NotificationsUiState.Success -> {
                    val showUnread = uiState.selectedFilter != NotificationFilter.READ
                    val showRead = uiState.selectedFilter != NotificationFilter.UNREAD

                    if (showUnread && uiState.unreadNotifications.isNotEmpty()) {
                        item {
                            SectionHeaderRow(
                                title = "Unread Notifications (${uiState.unreadCount})",
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                        items(uiState.unreadNotifications, key = { "unread-${it.id}" }) { item ->
                            NotificationInboxCard(
                                notification = item,
                                isMutating = isMutating,
                                onMarkAsRead = { onMarkAsRead(item.id) },
                                onDelete = { onDeleteNotification(item.id) },
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                    }

                    if (showRead && uiState.readNotifications.isNotEmpty()) {
                        item {
                            SectionHeaderRow(
                                title = "Read Notifications (${uiState.readCount})",
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                        items(uiState.readNotifications, key = { "read-${it.id}" }) { item ->
                            NotificationInboxCard(
                                notification = item,
                                isMutating = isMutating,
                                onMarkAsRead = {},
                                onDelete = { onDeleteNotification(item.id) },
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                    }

                    val visibleCount = (if (showUnread) uiState.unreadNotifications.size else 0) +
                        (if (showRead) uiState.readNotifications.size else 0)
                    if (visibleCount == 0) {
                        item {
                            AcademicModuleEmptyCard(
                                icon = Icons.Rounded.NotificationsNone,
                                title = "No ${uiState.selectedFilter.label.lowercase()} notifications",
                                subtitle = "Switch filter tabs above to inspect your other notifications.",
                                ctaLabel = "Schedule Reminder",
                                onCtaClick = onCreateReminderClick,
                                testTag = "notifications_filter_empty_state",
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear All Notifications?") },
            text = { Text("Remove all unread and read notifications from your Cloud Firestore inbox?") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllNotifications()
                        showClearAllConfirm = false
                    },
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun NotificationInboxCard(
    notification: AppNotification,
    isMutating: Boolean,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = notification.resolvedCategory()
    val isUnread = !notification.read

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) {
                UniMatePurplePrimary.copy(alpha = 0.06f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = if (isUnread) 1.5.dp else 1.dp,
            color = if (isUnread) category.color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("notification_card_${notification.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(UniMatePurplePrimary),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = category.color.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = category.color,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = if (isUnread) "Unread" else "Read",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUnread) UniMatePurplePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onDelete,
                    enabled = !isMutating,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("notification_delete_${notification.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete notification",
                    )
                }
            }

            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            if (notification.body.isNotBlank()) {
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = AcademicDateFormatters.formatDateTime(notification.deliveredAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isUnread) {
                    TextButton(
                        onClick = onMarkAsRead,
                        enabled = !isMutating,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("notification_mark_read_${notification.id}"),
                    ) {
                        Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark as Read")
                    }
                }
            }
        }
    }
}
