package com.example.unimate.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.unimate.R
import com.example.unimate.data.model.AcademicTask
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.StudyGoal
import com.example.unimate.data.model.SubjectColorPalette
import com.example.unimate.data.model.TaskCategory
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.data.model.UserProfile
import com.example.unimate.ui.components.UniMateLogoMark
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMateIndigoDeep
import com.example.unimate.ui.theme.UniMatePurplePrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardTopHeaderCard(
    profile: UserProfile,
    unreadNotificationCount: Int,
    onNotificationsClick: () -> Unit,
    onProfileAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstName = remember(profile.fullName) {
        profile.fullName.trim().substringBefore(" ").ifEmpty { "Student" }
    }
    val greetingPrefix = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val currentDateText = remember {
        SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date())
    }

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            UniMateIndigoDeep,
            UniMatePurplePrimary,
            UniMateBlueElectric,
        ),
    )

    Card(
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UniMateLogoMark(
                            size = 44.dp,
                            borderColor = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.testTag("dashboard_brand_logo"),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                            )
                            Text(
                                text = stringResource(R.string.app_tagline),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.84f),
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(
                            onClick = onNotificationsClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dashboard_notifications_button"),
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
                                    tint = Color.White,
                                )
                            }
                        }

                        StudentAvatarButton(
                            fullName = profile.fullName,
                            profileImage = profile.profileImage,
                            onClick = onProfileAvatarClick,
                            modifier = Modifier.testTag("dashboard_profile_avatar_button"),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = currentDateText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.testTag("dashboard_current_date"),
                    )
                    Text(
                        text = "$greetingPrefix, $firstName",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("dashboard_greeting"),
                    )
                    Text(
                        text = "${profile.university} • ${profile.department} • ${profile.semester}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun StudentAvatarButton(
    fullName: String,
    profileImage: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initials = remember(fullName) {
        fullName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "U" }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(
                onClickLabel = stringResource(R.string.cd_open_profile_menu),
                onClick = onClick,
            ),
    ) {
        if (profileImage.isNotBlank()) {
            AsyncImage(
                model = profileImage,
                contentDescription = stringResource(R.string.cd_profile_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        } else {
            Surface(
                color = Color.White.copy(alpha = 0.24f),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.65f)),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
fun DemoBadgePill(modifier: Modifier = Modifier) {
    Surface(
        color = UniMateAmber.copy(alpha = 0.18f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, UniMateAmber.copy(alpha = 0.55f)),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.dashboard_demo_badge),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = UniMateAmber,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun DemoWorkspaceBanner(
    isShowingDemoData: Boolean,
    hasRealData: Boolean,
    onToggleDemoPreview: (Boolean) -> Unit,
    onAddFirstRealItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hasRealData) return

    if (isShowingDemoData) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = UniMateAmber.copy(alpha = 0.12f),
            ),
            border = BorderStroke(1.dp, UniMateAmber.copy(alpha = 0.45f)),
            modifier = modifier
                .fillMaxWidth()
                .testTag("dashboard_demo_banner"),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = UniMateAmber,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.dashboard_demo_banner_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    DemoBadgePill()
                }
                Text(
                    text = stringResource(R.string.dashboard_demo_banner_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onAddFirstRealItem,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("demo_banner_add_real_button"),
                    ) {
                        Text("Add Real Assignment")
                    }
                    OutlinedButton(
                        onClick = { onToggleDemoPreview(false) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("demo_banner_hide_button"),
                    ) {
                        Text(stringResource(R.string.dashboard_demo_hide))
                    }
                }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Clean workspace — add courses, tasks, or notes anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onToggleDemoPreview(true) },
                    modifier = Modifier.testTag("demo_banner_show_button"),
                ) {
                    Text(stringResource(R.string.dashboard_demo_show))
                }
            }
        }
    }
}

@Composable
fun SectionHeaderRow(
    title: String,
    isDemo: Boolean = false,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            if (isDemo) {
                DemoBadgePill()
            }
        }
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun AcademicStatsGrid(
    metrics: DashboardMetrics,
    calculatedGpa: Double,
    isDemo: Boolean,
    onPendingClick: () -> Unit,
    onAssessmentsClick: () -> Unit,
    onAttendanceClick: () -> Unit,
    onGpaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTileCard(
                title = "Pending Tasks",
                value = metrics.pendingTasksCount.toString(),
                subtitle = "Active items",
                icon = Icons.Rounded.TaskAlt,
                accentColor = UniMatePurplePrimary,
                isDemo = isDemo,
                onClick = onPendingClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_pending_tasks"),
            )
            StatTileCard(
                title = "Quizzes & Exams",
                value = metrics.upcomingAssessmentsCount.toString(),
                subtitle = "Upcoming",
                icon = Icons.Rounded.Quiz,
                accentColor = UniMateAmber,
                isDemo = isDemo,
                onClick = onAssessmentsClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_upcoming_exams"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTileCard(
                title = "Attendance",
                value = "${metrics.attendancePercent}%",
                subtitle = "${metrics.coursesCount} courses",
                icon = Icons.Rounded.School,
                accentColor = UniMateEmerald,
                isDemo = isDemo,
                onClick = onAttendanceClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_attendance"),
            )
            StatTileCard(
                title = "Est. GPA",
                value = String.format(Locale.US, "%.2f", calculatedGpa),
                subtitle = "Tap calculator",
                icon = Icons.Rounded.Calculate,
                accentColor = UniMateBlueElectric,
                isDemo = false,
                onClick = onGpaClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_gpa"),
            )
        }
    }
}

@Composable
private fun StatTileCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isDemo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
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
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(7.dp),
                    )
                }
                if (isDemo) {
                    DemoBadgePill()
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickActionsGrid(
    onAddAssignment: () -> Unit,
    onCreateNote: () -> Unit,
    onOpenTimetable: () -> Unit,
    onCalculateGpa: () -> Unit,
    onAddReminder: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        Triple(stringResource(R.string.qa_add_assignment), Icons.Rounded.AddTask, "quick_action_add_assignment" to onAddAssignment),
        Triple(stringResource(R.string.qa_create_note), Icons.AutoMirrored.Rounded.NoteAdd, "quick_action_create_note" to onCreateNote),
        Triple(stringResource(R.string.qa_open_timetable), Icons.Rounded.CalendarMonth, "quick_action_open_timetable" to onOpenTimetable),
        Triple(stringResource(R.string.qa_calculate_gpa), Icons.Rounded.Calculate, "quick_action_calculate_gpa" to onCalculateGpa),
        Triple(stringResource(R.string.qa_add_reminder), Icons.Rounded.AlarmAdd, "quick_action_add_reminder" to onAddReminder),
        Triple(stringResource(R.string.qa_open_ai), Icons.Rounded.AutoAwesome, "quick_action_open_ai" to onOpenAiAssistant),
    )

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 3,
    ) {
        actions.forEach { (label, icon, tagAndAction) ->
            val (tag, action) = tagAndAction
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = action)
                    .testTag(tag),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun ClassScheduleRow(
    timetableClass: TimetableClass,
    isDemo: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = SubjectColorPalette.colorForIndex(timetableClass.colorIndex)
    val isToday = timetableClass.isToday()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isToday) 1.5.dp else 1.dp,
            color = if (isToday) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = timetableClass.courseCode,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                        )
                        if (isToday) {
                            Surface(
                                color = UniMateEmerald.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = "TODAY",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = UniMateEmerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                        if (isDemo) DemoBadgePill()
                    }
                    Text(
                        text = timetableClass.subjectName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "${timetableClass.formattedTimeRange()} (${timetableClass.formattedDuration()}) • ${timetableClass.classroom}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskItemRow(
    task: AcademicTask,
    isDemo: Boolean,
    onToggleCompleted: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val category = task.resolvedCategory()
    val badgeColor = when (category) {
        TaskCategory.ASSIGNMENT -> UniMatePurplePrimary
        TaskCategory.QUIZ -> UniMateAmber
        TaskCategory.EXAM -> MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onToggleCompleted,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (task.completed) "Mark incomplete" else "Mark completed",
                    tint = if (task.completed) UniMateEmerald else MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = task.courseName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isDemo) DemoBadgePill()
                }
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task.dueDateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDeleteClick != null && !isDemo) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun AttendanceProgressCard(
    course: StudentCourse,
    isDemo: Boolean,
    modifier: Modifier = Modifier,
) {
    val pct = course.attendancePercentage()
    val progressColor = when {
        pct >= 80 -> UniMateEmerald
        pct >= 70 -> UniMateAmber
        else -> MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "${course.courseCode} • ${course.courseName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isDemo) DemoBadgePill()
                }
                Text(
                    text = "$pct% (${course.attendedClasses}/${course.totalClasses})",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = progressColor,
                )
            }
            LinearProgressIndicator(
                progress = { pct / 100f },
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.16f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
fun StudyGoalCard(
    goal: StudyGoal,
    isDemo: Boolean,
    onIncrementClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = goal.progressFraction()
    val percent = (fraction * 100f).toInt()
    val isDone = goal.completedSteps >= goal.targetSteps

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = if (isDone) UniMateEmerald else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isDemo) DemoBadgePill()
                }
                TextButton(
                    onClick = onIncrementClick,
                    enabled = !isDone,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(if (isDone) "Done" else "+1 Step")
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                color = if (isDone) UniMateEmerald else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
            )
            Text(
                text = "${goal.completedSteps} of ${goal.targetSteps} steps completed ($percent%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AiPromoCard(
    onLaunchAiAssistant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aiBrush = Brush.linearGradient(
        colors = listOf(
            UniMateIndigoDeep,
            UniMatePurplePrimary,
            UniMateBlueElectric,
        ),
    )
    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_ai_promo_card"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(aiBrush)
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.ai_promo_badge),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.ai_promo_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.ai_promo_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Button(
                    onClick = onLaunchAiAssistant,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = UniMateIndigoDeep,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("ai_promo_launch_button"),
                ) {
                    Text(
                        text = stringResource(R.string.ai_promo_cta),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySectionCard(
    message: String,
    ctaLabel: String,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onCtaClick,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(ctaLabel)
            }
        }
    }
}
