package com.example.unimate.ui.calendar

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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Today
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.CalendarDateEngine
import com.example.unimate.data.model.CalendarDisplayItem
import com.example.unimate.data.model.CalendarEvent
import com.example.unimate.data.model.CalendarEventCategory
import com.example.unimate.data.model.CalendarViewMode
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.AcademicModuleEmptyCard
import com.example.unimate.ui.components.AcademicModuleErrorCard
import com.example.unimate.ui.components.AcademicModuleLoadingCard
import com.example.unimate.ui.dashboard.SectionHeaderRow

@Composable
fun CalendarSectionContent(
    state: CalendarUiState,
    actionMessage: String?,
    onSelectViewMode: (CalendarViewMode) -> Unit,
    onSelectDate: (Long) -> Unit,
    onSelectCategoryFilter: (CalendarEventCategory?) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onJumpToToday: () -> Unit,
    onAddEventClick: (CalendarEventCategory) -> Unit,
    onEditEventClick: (CalendarEvent) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<CalendarEvent?>(null) }
    val todayMillis = remember { System.currentTimeMillis() }

    val activeViewMode = (state as? CalendarUiState.Success)?.viewMode ?: CalendarViewMode.MONTHLY
    val activeCategory = (state as? CalendarUiState.Success)?.selectedCategoryFilter

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("calendar_section"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Academic Calendar",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    )
                    Text(
                        text = "Monthly, weekly & daily view across classes, deadlines, exams, holidays & personal events.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onAddEventClick(activeCategory ?: CalendarEventCategory.UNIVERSITY_EVENTS) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("calendar_add_event_button"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Event")
                }
            }
        }

        if (!actionMessage.isNullOrBlank()) {
            item {
                AuthStatusBanner(
                    message = actionMessage,
                    type = AuthBannerType.INFO,
                    modifier = Modifier.widthIn(max = 760.dp),
                    testTag = "calendar_action_banner",
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
                CalendarViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = activeViewMode == mode,
                        onClick = { onSelectViewMode(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calendar_mode_${mode.name.lowercase()}"),
                    )
                }
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
                    label = { Text("All Categories") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("calendar_category_all"),
                )
                CalendarEventCategory.entries.forEach { category ->
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
                        label = { Text(category.label) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calendar_category_${category.wireValue.lowercase()}"),
                    )
                }
            }
        }

        when (state) {
            CalendarUiState.Loading -> {
                item {
                    AcademicModuleLoadingCard(
                        message = "Loading your academic calendar from Cloud Firestore…",
                        testTag = "calendar_loading_state",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is CalendarUiState.Error -> {
                item {
                    AcademicModuleErrorCard(
                        message = state.message,
                        onRetry = onRetry,
                        testTag = "calendar_error_state",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is CalendarUiState.Success -> {
                val periodHeaderTitle = when (state.viewMode) {
                    CalendarViewMode.MONTHLY -> CalendarDateEngine.formatMonthYear(state.selectedDateMillis)
                    CalendarViewMode.WEEKLY -> CalendarDateEngine.formatWeekRange(state.selectedDateMillis)
                    CalendarViewMode.DAILY -> CalendarDateEngine.formatDayFull(state.selectedDateMillis)
                }

                item {
                    CalendarDateNavigatorBar(
                        title = periodHeaderTitle,
                        itemCount = state.totalVisiblePeriodItemsCount,
                        onPrevious = onPreviousPeriod,
                        onToday = onJumpToToday,
                        onNext = onNextPeriod,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                when (state.viewMode) {
                    CalendarViewMode.MONTHLY -> {
                        item {
                            CalendarMonthlyGridCard(
                                monthCells = state.monthCells,
                                monthItemsByDay = state.monthItemsByDay,
                                onSelectDay = onSelectDate,
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                    }

                    CalendarViewMode.WEEKLY -> {
                        item {
                            CalendarWeeklyStripCard(
                                weekDays = state.weekDays,
                                selectedDateMillis = state.selectedDateMillis,
                                todayMillis = todayMillis,
                                weekItemsByDay = state.weekItemsByDay,
                                onSelectDay = onSelectDate,
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                    }

                    CalendarViewMode.DAILY -> Unit
                }

                item {
                    val isToday = CalendarDateEngine.isSameDay(state.selectedDateMillis, todayMillis)
                    val dateLabel = if (isToday) {
                        "Today • ${CalendarDateEngine.formatDayFull(state.selectedDateMillis)}"
                    } else {
                        CalendarDateEngine.formatDayFull(state.selectedDateMillis)
                    }
                    SectionHeaderRow(
                        title = "$dateLabel (${state.selectedDayItems.size})",
                        actionLabel = "+ Event",
                        onActionClick = {
                            onAddEventClick(state.selectedCategoryFilter ?: CalendarEventCategory.UNIVERSITY_EVENTS)
                        },
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                if (state.selectedDayItems.isEmpty()) {
                    item {
                        AcademicModuleEmptyCard(
                            icon = Icons.Rounded.CalendarMonth,
                            title = "No events on this date",
                            subtitle = if (state.totalUserEventsCount == 0) {
                                "Add university events, holidays, classes, or personal milestones to build your schedule."
                            } else {
                                "Tap Add Event above or pick another date to view your synced academic schedule."
                            },
                            ctaLabel = "Add Event on This Date",
                            onCtaClick = {
                                onAddEventClick(state.selectedCategoryFilter ?: CalendarEventCategory.UNIVERSITY_EVENTS)
                            },
                            testTag = "calendar_day_empty_state",
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                } else {
                    items(state.selectedDayItems, key = { it.key }) { item ->
                        CalendarAgendaItemCard(
                            item = item,
                            onEditClick = { item.editableEvent?.let(onEditEventClick) },
                            onDeleteClick = { pendingDelete = item.editableEvent },
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Calendar Event?") },
            text = { Text("Remove \"${event.title}\" from your synced UniMate calendar?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEvent(event.id)
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
private fun CalendarDateNavigatorBar(
    title: String,
    itemCount: Int,
    onPrevious: () -> Unit,
    onToday: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("calendar_prev_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous period",
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$itemCount scheduled item(s) in view",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = onToday,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("calendar_today_button"),
            ) {
                Icon(Icons.Rounded.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Today")
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("calendar_next_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Next period",
                )
            }
        }
    }
}

@Composable
private fun CalendarAgendaItemCard(
    item: CalendarDisplayItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, item.category.color.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("calendar_item_${item.key}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(item.category.color),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = item.category.color.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = item.category.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = item.category.color,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    if (!item.isEditableCalendarEvent) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "Synced",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val description = item.editableEvent?.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (item.isEditableCalendarEvent) {
                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calendar_edit_${item.key}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit calendar event",
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calendar_delete_${item.key}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete calendar event",
                        )
                    }
                }
            }
        }
    }
}
