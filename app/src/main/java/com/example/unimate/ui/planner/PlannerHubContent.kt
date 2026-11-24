package com.example.unimate.ui.planner

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.data.model.CalendarEvent
import com.example.unimate.data.model.CalendarEventCategory
import com.example.unimate.data.model.CalendarViewMode
import com.example.unimate.data.model.DayOfWeekOption
import com.example.unimate.data.model.PomodoroPhase
import com.example.unimate.data.model.ReminderCategory
import com.example.unimate.data.model.ScheduledReminder
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.StudentReminder
import com.example.unimate.data.model.StudySession
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.ui.calendar.CalendarSectionContent
import com.example.unimate.ui.calendar.CalendarUiState
import com.example.unimate.ui.components.UniMateLogoMark
import com.example.unimate.ui.dashboard.PlannerSubTab
import com.example.unimate.ui.reminders.RemindersSectionContent
import com.example.unimate.ui.reminders.RemindersUiState
import com.example.unimate.ui.studyplanner.PomodoroUiState
import com.example.unimate.ui.studyplanner.StudyPlannerSectionContent
import com.example.unimate.ui.studyplanner.StudyPlannerUiState
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.timetable.TimetableTabContent
import com.example.unimate.ui.timetable.TimetableUiState
import com.example.unimate.ui.timetable.TimetableViewMode

@Composable
fun PlannerHubContent(
    activeSubTab: PlannerSubTab,
    unreadNotificationCount: Int,
    onSelectSubTab: (PlannerSubTab) -> Unit,
    onOpenNotificationsClick: () -> Unit,
    // Timetable
    timetableState: TimetableUiState,
    timetableActionMessage: String?,
    legacyReminders: List<StudentReminder>,
    isDemoReminders: Boolean,
    onSelectTimetableViewMode: (TimetableViewMode) -> Unit,
    onSelectTimetableDay: (DayOfWeekOption) -> Unit,
    onAddClassClick: (DayOfWeekOption) -> Unit,
    onEditClassClick: (TimetableClass) -> Unit,
    onDeleteClassClick: (String) -> Unit,
    onRetryTimetable: () -> Unit,
    onToggleLegacyReminderDone: (StudentReminder) -> Unit,
    onDeleteLegacyReminder: (StudentReminder) -> Unit,
    // Calendar
    calendarState: CalendarUiState,
    calendarActionMessage: String?,
    onSelectCalendarViewMode: (CalendarViewMode) -> Unit,
    onSelectCalendarDate: (Long) -> Unit,
    onSelectCalendarCategory: (CalendarEventCategory?) -> Unit,
    onPreviousCalendarPeriod: () -> Unit,
    onNextCalendarPeriod: () -> Unit,
    onJumpCalendarToday: () -> Unit,
    onAddCalendarEventClick: (CalendarEventCategory) -> Unit,
    onEditCalendarEventClick: (CalendarEvent) -> Unit,
    onDeleteCalendarEvent: (String) -> Unit,
    onRetryCalendar: () -> Unit,
    // Reminders
    remindersState: RemindersUiState,
    remindersActionMessage: String?,
    onSelectReminderCategory: (ReminderCategory?) -> Unit,
    onAddReminderClick: (ReminderCategory) -> Unit,
    onEditReminderClick: (ScheduledReminder) -> Unit,
    onToggleReminderEnabled: (ScheduledReminder) -> Unit,
    onToggleReminderCompleted: (ScheduledReminder) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onRetryReminders: () -> Unit,
    // Study Planner
    studyPlannerState: StudyPlannerUiState,
    pomodoroState: PomodoroUiState,
    enrolledCourses: List<StudentCourse>,
    studyPlannerActionMessage: String?,
    onClearStudyPlannerMessage: () -> Unit,
    onSelectStudySubjectFilter: (String?) -> Unit,
    onOpenAddStudySession: (Boolean) -> Unit,
    onOpenEditStudySession: (StudySession) -> Unit,
    onToggleStudySessionCompleted: (StudySession) -> Unit,
    onDeleteStudySession: (String) -> Unit,
    onLaunchStudySessionPomodoro: (StudySession) -> Unit,
    onOpenStudyGoalsDialog: () -> Unit,
    onPomodoroSubjectSelected: (String) -> Unit,
    onPomodoroTitleChanged: (String) -> Unit,
    onPomodoroDurationSelected: (Long) -> Unit,
    onPomodoroStart: () -> Unit,
    onPomodoroPause: () -> Unit,
    onPomodoroReset: () -> Unit,
    onPomodoroSwitchPhase: (PomodoroPhase) -> Unit,
    onPomodoroCompleteNow: () -> Unit,
    onRetryStudyPlanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("planner_hub_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UniMateLogoMark(size = 34.dp)
                        Column {
                            Text(
                                text = "UniMate Planner",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            )
                            Text(
                                text = "Timetable • Calendar • Reminders • Study Planner",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenNotificationsClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("planner_notifications_button"),
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationCount > 0) {
                                    Badge(containerColor = UniMateAmber, contentColor = Color.Black) {
                                        Text(unreadNotificationCount.coerceAtMost(99).toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = stringResource(R.string.cd_notifications_bell),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlannerSubTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            PlannerSubTab.TIMETABLE -> Icons.Rounded.ViewWeek
                            PlannerSubTab.CALENDAR -> Icons.Rounded.CalendarMonth
                            PlannerSubTab.REMINDERS -> Icons.Rounded.Alarm
                            PlannerSubTab.STUDY_PLANNER -> Icons.Rounded.Timer
                        }
                        FilterChip(
                            selected = activeSubTab == tab,
                            onClick = { onSelectSubTab(tab) },
                            leadingIcon = { Icon(icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("planner_subtab_${tab.name.lowercase()}"),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (activeSubTab) {
                PlannerSubTab.TIMETABLE -> {
                    TimetableTabContent(
                        timetableState = timetableState,
                        actionMessage = timetableActionMessage,
                        reminders = legacyReminders,
                        isDemoReminders = isDemoReminders,
                        onSelectViewMode = onSelectTimetableViewMode,
                        onSelectDay = onSelectTimetableDay,
                        onAddClassClick = onAddClassClick,
                        onEditClassClick = onEditClassClick,
                        onDeleteClassClick = onDeleteClassClick,
                        onRetry = onRetryTimetable,
                        onAddReminderClick = { onAddReminderClick(ReminderCategory.CLASS) },
                        onToggleReminderDone = onToggleLegacyReminderDone,
                        onDeleteReminder = onDeleteLegacyReminder,
                    )
                }

                PlannerSubTab.CALENDAR -> {
                    CalendarSectionContent(
                        state = calendarState,
                        actionMessage = calendarActionMessage,
                        onSelectViewMode = onSelectCalendarViewMode,
                        onSelectDate = onSelectCalendarDate,
                        onSelectCategoryFilter = onSelectCalendarCategory,
                        onPreviousPeriod = onPreviousCalendarPeriod,
                        onNextPeriod = onNextCalendarPeriod,
                        onJumpToToday = onJumpCalendarToday,
                        onAddEventClick = onAddCalendarEventClick,
                        onEditEventClick = onEditCalendarEventClick,
                        onDeleteEvent = onDeleteCalendarEvent,
                        onRetry = onRetryCalendar,
                    )
                }

                PlannerSubTab.REMINDERS -> {
                    RemindersSectionContent(
                        state = remindersState,
                        actionMessage = remindersActionMessage,
                        unreadNotificationsCount = unreadNotificationCount,
                        onSelectCategoryFilter = onSelectReminderCategory,
                        onAddReminderClick = onAddReminderClick,
                        onEditReminderClick = onEditReminderClick,
                        onToggleEnabled = onToggleReminderEnabled,
                        onToggleCompleted = onToggleReminderCompleted,
                        onDeleteReminder = onDeleteReminder,
                        onOpenNotificationsClick = onOpenNotificationsClick,
                        onRetry = onRetryReminders,
                    )
                }

                PlannerSubTab.STUDY_PLANNER -> {
                    StudyPlannerSectionContent(
                        uiState = studyPlannerState,
                        pomodoroState = pomodoroState,
                        enrolledCourses = enrolledCourses,
                        actionMessage = studyPlannerActionMessage,
                        onClearActionMessage = onClearStudyPlannerMessage,
                        onSelectSubjectFilter = onSelectStudySubjectFilter,
                        onOpenAddSessionDialog = onOpenAddStudySession,
                        onOpenEditSessionDialog = onOpenEditStudySession,
                        onToggleSessionCompleted = onToggleStudySessionCompleted,
                        onDeleteSession = onDeleteStudySession,
                        onLaunchSessionInPomodoro = onLaunchStudySessionPomodoro,
                        onOpenGoalsDialog = onOpenStudyGoalsDialog,
                        onPomodoroSubjectSelected = onPomodoroSubjectSelected,
                        onPomodoroTitleChanged = onPomodoroTitleChanged,
                        onPomodoroDurationSelected = onPomodoroDurationSelected,
                        onPomodoroStart = onPomodoroStart,
                        onPomodoroPause = onPomodoroPause,
                        onPomodoroReset = onPomodoroReset,
                        onPomodoroSwitchPhase = onPomodoroSwitchPhase,
                        onPomodoroCompleteNow = onPomodoroCompleteNow,
                        onRetry = onRetryStudyPlanner,
                    )
                }
            }
        }
    }
}
