package com.example.unimate.ui.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.AttendanceCalculator
import com.example.unimate.data.model.AttendanceLog
import com.example.unimate.data.model.AttendanceStatus
import com.example.unimate.data.model.CourseAttendanceSummary
import com.example.unimate.data.model.MonthlyAttendanceStat
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.SubjectColorPalette
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateCoralError
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary
import java.util.Locale

@Composable
fun AttendanceSectionContent(
    uiState: AttendanceUiState,
    actionMessage: String?,
    onClearActionMessage: () -> Unit,
    onOpenLogAttendance: (StudentCourse?, AttendanceStatus) -> Unit,
    onOpenEditTotals: (StudentCourse) -> Unit,
    onOpenThresholdDialog: () -> Unit,
    onSelectCourseFilter: (String?) -> Unit,
    onSelectMonthFilter: (String?) -> Unit,
    onToggleLogStatus: (AttendanceLog) -> Unit,
    onDeleteLog: (AttendanceLog) -> Unit,
    onAddCourseShortcut: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("attendance_section_content"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AttendanceDisclaimerCard(modifier = Modifier.widthIn(max = 760.dp))
        }

        if (!actionMessage.isNullOrBlank()) {
            item {
                Column(modifier = Modifier.widthIn(max = 760.dp)) {
                    AuthStatusBanner(message = actionMessage, type = AuthBannerType.INFO)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onClearActionMessage) { Text("Dismiss") }
                    }
                }
            }
        }

        when (uiState) {
            AttendanceUiState.Loading -> {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .testTag("attendance_loading_state"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator()
                            Text("Loading attendance records…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            is AttendanceUiState.Error -> {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .testTag("attendance_error_state"),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AuthStatusBanner(message = uiState.message, type = AuthBannerType.ERROR)
                            OutlinedButton(
                                onClick = onRetry,
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Retry Loading Attendance")
                            }
                        }
                    }
                }
            }

            is AttendanceUiState.Empty -> {
                item {
                    AttendanceEmptyCard(
                        thresholdPercent = uiState.settings.attendanceThresholdPercent,
                        onOpenThresholdDialog = onOpenThresholdDialog,
                        onAddCourseShortcut = onAddCourseShortcut,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is AttendanceUiState.Success -> {
                item {
                    OverallAttendanceHeroCard(
                        uiState = uiState,
                        onOpenLogAttendance = { onOpenLogAttendance(null, AttendanceStatus.PRESENT) },
                        onOpenThresholdDialog = onOpenThresholdDialog,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                if (uiState.overall.monthlyStats.isNotEmpty()) {
                    item {
                        MonthlyStatisticsCard(
                            stats = uiState.overall.monthlyStats,
                            thresholdPercent = uiState.settings.attendanceThresholdPercent,
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }

                item {
                    Text(
                        text = "Course Attendance (${uiState.courses.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                    )
                }

                items(uiState.courses, key = { "att-course-${it.course.id}" }) { summary ->
                    CourseAttendanceDetailCard(
                        summary = summary,
                        onLogPresent = { onOpenLogAttendance(summary.course, AttendanceStatus.PRESENT) },
                        onLogAbsent = { onOpenLogAttendance(summary.course, AttendanceStatus.ABSENT) },
                        onEditTotals = { onOpenEditTotals(summary.course) },
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                item {
                    AttendanceHistoryHeader(
                        courses = uiState.courses.map { it.course },
                        selectedCourseId = uiState.selectedCourseFilterId,
                        availableMonths = uiState.availableMonthKeys,
                        selectedMonthKey = uiState.selectedMonthFilter,
                        onSelectCourseFilter = onSelectCourseFilter,
                        onSelectMonthFilter = onSelectMonthFilter,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                if (uiState.filteredLogs.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp)
                                .testTag("attendance_history_empty"),
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "No dated session logs match this filter yet.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                )
                                Text(
                                    text = "Use \"Log Attendance\" to record dated Present/Absent sessions for monthly statistics.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.filteredLogs, key = { "att-log-${it.id}" }) { log ->
                        AttendanceLogRowCard(
                            log = log,
                            onToggleStatus = { onToggleLogStatus(log) },
                            onDelete = { onDeleteLog(log) },
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceDisclaimerCard(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = UniMateBlueElectric,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Personal attendance tracker only — not linked to an official university attendance portal.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttendanceEmptyCard(
    thresholdPercent: Int,
    onOpenThresholdDialog: () -> Unit,
    onAddCourseShortcut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("attendance_empty_state"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.EventAvailable,
                contentDescription = null,
                tint = UniMatePurplePrimary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = "No courses tracked yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Add your enrolled courses first to track attended/absent classes, monthly stats, and ${thresholdPercent}% threshold warnings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAddCourseShortcut,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Add Course")
                }
                OutlinedButton(
                    onClick = onOpenThresholdDialog,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Threshold ($thresholdPercent%)")
                }
            }
        }
    }
}

@Composable
private fun OverallAttendanceHeroCard(
    uiState: AttendanceUiState.Success,
    onOpenLogAttendance: () -> Unit,
    onOpenThresholdDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overall = uiState.overall
    val formattedPct = if (overall.totalClasses == 0L) {
        "0.0%"
    } else {
        String.format(Locale.US, "%.1f%%", overall.overallPercentage)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("attendance_overall_card"),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Overall Attendance",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "${overall.attendedClasses} attended • ${overall.absentClasses} absent • ${overall.totalClasses} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formattedPct,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (overall.totalClasses > 0L && overall.overallPercentage < overall.thresholdPercent) {
                        UniMateCoralError
                    } else {
                        UniMateEmerald
                    },
                )
            }

            if (overall.coursesBelowThresholdCount > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = UniMateCoralError.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, UniMateCoralError.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = UniMateCoralError,
                        )
                        Text(
                            text = "${overall.coursesBelowThresholdCount} course(s) below your ${overall.thresholdPercent}% minimum threshold.",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = UniMateCoralError,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onOpenLogAttendance,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("attendance_log_session_button"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log Attendance")
                }
                OutlinedButton(
                    onClick = onOpenThresholdDialog,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("attendance_threshold_button"),
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${overall.thresholdPercent}% Min")
                }
            }
        }
    }
}

@Composable
private fun MonthlyStatisticsCard(
    stats: List<MonthlyAttendanceStat>,
    thresholdPercent: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("attendance_monthly_stats_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = UniMatePurplePrimary)
                Text(
                    text = "Monthly Attendance Statistics",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
            stats.take(6).forEach { month ->
                val color = when {
                    month.percentage >= thresholdPercent -> UniMateEmerald
                    month.percentage >= thresholdPercent - 10 -> UniMateAmber
                    else -> UniMateCoralError
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = month.monthLabel,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "${month.attendedClasses}/${month.totalClasses} (${String.format(Locale.US, "%.1f%%", month.percentage)})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = color,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (month.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = color,
                        trackColor = color.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(50)),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseAttendanceDetailCard(
    summary: CourseAttendanceSummary,
    onLogPresent: () -> Unit,
    onLogAbsent: () -> Unit,
    onEditTotals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = SubjectColorPalette.colorForIndex(summary.course.colorIndex)
    val statusColor = when {
        !summary.hasSessions -> MaterialTheme.colorScheme.onSurfaceVariant
        summary.shortageWarning -> UniMateCoralError
        summary.nearThresholdWarning -> UniMateAmber
        else -> UniMateEmerald
    }
    val pctLabel = if (!summary.hasSessions) {
        "0.0%"
    } else {
        String.format(Locale.US, "%.1f%%", summary.percentage)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (summary.shortageWarning) UniMateCoralError.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("attendance_course_card_${summary.course.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.course.courseCode,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                        )
                        Text(
                            text = summary.course.courseName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pctLabel,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = statusColor,
                        )
                        IconButton(
                            onClick = onEditTotals,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("attendance_edit_totals_${summary.course.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit ${summary.course.courseCode} totals",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatBadgePill(label = "Total: ${summary.totalClasses}", tint = UniMateBlueElectric)
                    StatBadgePill(label = "Attended: ${summary.attendedClasses}", tint = UniMateEmerald)
                    StatBadgePill(label = "Absent: ${summary.absentClasses}", tint = UniMateCoralError)
                }

                LinearProgressIndicator(
                    progress = {
                        if (!summary.hasSessions) 0f else (summary.percentage / 100.0).toFloat().coerceIn(0f, 1f)
                    },
                    color = if (!summary.hasSessions) MaterialTheme.colorScheme.outline else statusColor,
                    trackColor = statusColor.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                )

                val guidanceText = when {
                    !summary.hasSessions -> "No classes recorded yet. Log a Present or Absent session to start tracking."
                    summary.shortageWarning -> {
                        val recovery = summary.classesNeededToRecover
                        if (recovery == null) {
                            "Shortage warning: below ${summary.thresholdPercent}% (cannot reach 100% after an absence)."
                        } else {
                            "Shortage warning: below ${summary.thresholdPercent}%. Attend the next $recovery consecutive class(es) to recover."
                        }
                    }
                    summary.nearThresholdWarning -> "On the ${summary.thresholdPercent}% edge — 0 allowable absences remaining."
                    else -> "Safe zone: ${summary.allowableAbsencesRemaining} allowable absence(s) remaining while staying ≥ ${summary.thresholdPercent}%."
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = if (summary.hasSessions) 0.10f else 0.08f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (summary.shortageWarning || summary.nearThresholdWarning) {
                                Icons.Rounded.WarningAmber
                            } else {
                                Icons.Rounded.CheckCircle
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = guidanceText,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor,
                        )
                    }
                }

                if (summary.monthlyStats.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        summary.monthlyStats.take(4).forEach { m ->
                            StatBadgePill(
                                label = "${m.monthLabel}: ${m.attendedClasses}/${m.totalClasses} (${m.percentage.toInt()}%)",
                                tint = UniMatePurplePrimary,
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onLogPresent,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("attendance_quick_present_${summary.course.id}"),
                    ) {
                        Text("+ Present")
                    }
                    OutlinedButton(
                        onClick = onLogAbsent,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("attendance_quick_absent_${summary.course.id}"),
                    ) {
                        Text("+ Absent")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadgePill(
    label: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.12f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AttendanceHistoryHeader(
    courses: List<StudentCourse>,
    selectedCourseId: String?,
    availableMonths: List<String>,
    selectedMonthKey: String?,
    onSelectCourseFilter: (String?) -> Unit,
    onSelectMonthFilter: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Attendance History",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedCourseId == null,
                onClick = { onSelectCourseFilter(null) },
                label = { Text("All Courses") },
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
            courses.forEach { course ->
                FilterChip(
                    selected = selectedCourseId == course.id,
                    onClick = { onSelectCourseFilter(course.id) },
                    label = { Text(course.courseCode) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                )
            }
        }
        if (availableMonths.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedMonthKey == null,
                    onClick = { onSelectMonthFilter(null) },
                    label = { Text("All Months") },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                )
                availableMonths.forEach { key ->
                    FilterChip(
                        selected = selectedMonthKey == key,
                        onClick = { onSelectMonthFilter(key) },
                        label = { Text(AttendanceCalculator.formatMonthLabel(key)) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceLogRowCard(
    log: AttendanceLog,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPresent = log.resolvedStatus() == AttendanceStatus.PRESENT
    val badgeColor = if (isPresent) UniMateEmerald else UniMateCoralError

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("attendance_log_card_${log.id}"),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = log.resolvedStatus().label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = "${log.courseCode} • ${log.courseName}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = AttendanceCalculator.formatSessionDate(log.sessionDateMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (log.notes.isNotBlank()) {
                    Text(
                        text = log.notes,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            IconButton(
                onClick = onToggleStatus,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = "Toggle present or absent",
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete attendance entry",
                )
            }
        }
    }
}
