package com.example.unimate.ui.studyplanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.StudyPlannerCalculator
import com.example.unimate.data.model.StudyProductivityStats
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary
import kotlin.math.max

@Composable
fun StudyStreakAndGoalsCard(
    stats: StudyProductivityStats,
    onEditGoalsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("study_goals_streak_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = UniMateAmber.copy(alpha = 0.16f),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = UniMateAmber,
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "${stats.currentStreakDays}-Day Study Streak",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        )
                        Text(
                            text = "Best streak: ${stats.longestStreakDays} day${if (stats.longestStreakDays == 1) "" else "s"} • ${stats.completedSessionsCount} completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onEditGoalsClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("study_edit_goals_button"),
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Goals", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (stats.weeklyBars.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    stats.weeklyBars.forEach { day ->
                        val active = day.minutes > 0L
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    active -> UniMateEmerald.copy(alpha = 0.18f)
                                    day.isToday -> UniMatePurplePrimary.copy(alpha = 0.12f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                border = if (day.isToday) BorderStroke(1.5.dp, UniMatePurplePrimary) else null,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (active) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "${day.dayLabel} completed",
                                            tint = UniMateEmerald,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = day.dayLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (day.isToday) UniMatePurplePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            GoalProgressMeter(
                title = "Daily Study Goal",
                currentLabel = "${StudyPlannerCalculator.formatMinutesCompact(stats.todayMinutes)} / ${StudyPlannerCalculator.formatMinutesCompact(stats.dailyGoalMinutes)}",
                percent = stats.dailyGoalPercent,
                fraction = stats.dailyGoalProgressFraction,
                barColor = UniMateBlueElectric,
            )

            GoalProgressMeter(
                title = "Weekly Study Goal",
                currentLabel = "${StudyPlannerCalculator.formatMinutesCompact(stats.weeklyMinutes)} / ${StudyPlannerCalculator.formatMinutesCompact(stats.weeklyGoalMinutes)}",
                percent = stats.weeklyGoalPercent,
                fraction = stats.weeklyGoalProgressFraction,
                barColor = UniMatePurplePrimary,
            )
        }
    }
}

@Composable
private fun GoalProgressMeter(
    title: String,
    currentLabel: String,
    percent: Int,
    fraction: Float,
    barColor: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$title ($percent%)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = barColor,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
        )
    }
}

@Composable
fun WeeklyStudyStatsCard(
    stats: StudyProductivityStats,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val barColor = UniMateBlueElectric
    val todayBarColor = UniMatePurplePrimary
    val targetLineColor = UniMateEmerald

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("study_productivity_stats_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.BarChart, contentDescription = null, tint = UniMatePurplePrimary)
                    Text(
                        text = "Productivity Statistics",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    text = "Avg ${stats.averageSessionMinutes}m/session",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChipTile(
                    label = "Total Focus",
                    value = StudyPlannerCalculator.formatMinutesCompact(stats.totalCompletedMinutes),
                    modifier = Modifier.weight(1f),
                )
                StatChipTile(
                    label = "Completed",
                    value = "${stats.completedSessionsCount}",
                    modifier = Modifier.weight(1f),
                )
                StatChipTile(
                    label = "Planned",
                    value = "${stats.plannedSessionsCount}",
                    modifier = Modifier.weight(1f),
                )
            }

            if (stats.weeklyBars.isNotEmpty()) {
                val maxVal = max(
                    stats.dailyGoalMinutes.toFloat(),
                    (stats.weeklyBars.maxOfOrNull { it.minutes } ?: 60L).toFloat(),
                ).coerceAtLeast(30f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .padding(top = 6.dp),
                ) {
                    val chartHeight = size.height - 8.dp.toPx()
                    val topOffset = 4.dp.toPx()
                    val slotWidth = size.width / stats.weeklyBars.size.toFloat()
                    val barWidth = (slotWidth * 0.48f).coerceAtMost(36.dp.toPx())

                    val targetRatio = (stats.dailyGoalMinutes.toFloat() / maxVal).coerceIn(0f, 1f)
                    val targetY = topOffset + chartHeight * (1f - targetRatio)
                    drawLine(
                        color = targetLineColor.copy(alpha = 0.7f),
                        start = Offset(0f, targetY),
                        end = Offset(size.width, targetY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, topOffset + chartHeight),
                        end = Offset(size.width, topOffset + chartHeight),
                        strokeWidth = 1.dp.toPx(),
                    )

                    stats.weeklyBars.forEachIndexed { index, bar ->
                        val ratio = (bar.minutes.toFloat() / maxVal).coerceIn(0f, 1f)
                        val barH = if (bar.minutes > 0L) (chartHeight * ratio).coerceAtLeast(6.dp.toPx()) else 3.dp.toPx()
                        val left = index * slotWidth + (slotWidth - barWidth) / 2f
                        val top = topOffset + chartHeight - barH
                        drawRoundRect(
                            color = when {
                                bar.minutes <= 0L -> gridColor
                                bar.isToday -> todayBarColor
                                else -> barColor
                            },
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    stats.weeklyBars.forEach { bar ->
                        Text(
                            text = "${bar.dayLabel}\n${bar.minutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bar.isToday) UniMatePurplePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (stats.subjectBreakdown.isNotEmpty()) {
                Text(
                    text = "Subject Breakdown",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                stats.subjectBreakdown.take(5).forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = item.subject,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${StudyPlannerCalculator.formatMinutesCompact(item.totalMinutes)} (${item.sessionCount})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { item.fraction },
                            color = UniMateBlueElectric,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChipTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = UniMatePurplePrimary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
