package com.example.unimate.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.CalendarDateEngine
import com.example.unimate.data.model.CalendarDisplayItem
import com.example.unimate.data.model.MonthGridCell
import com.example.unimate.ui.theme.UniMatePurplePrimary

private val weekdayShortLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun CalendarMonthlyGridCard(
    monthCells: List<MonthGridCell>,
    monthItemsByDay: Map<Long, List<CalendarDisplayItem>>,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("calendar_monthly_grid"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayShortLabels.forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            monthCells.chunked(7).forEach { weekRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    weekRow.forEach { cell ->
                        val dayItems = monthItemsByDay[cell.dayStartMillis].orEmpty()
                        MonthDayCellView(
                            cell = cell,
                            items = dayItems,
                            onClick = { onSelectDay(cell.dayStartMillis) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCellView(
    cell: MonthGridCell,
    items: List<CalendarDisplayItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        cell.isSelected -> UniMatePurplePrimary
        cell.isToday -> UniMatePurplePrimary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val textColor = when {
        cell.isSelected -> Color.White
        cell.isToday -> UniMatePurplePrimary
        cell.isInCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val borderColor = when {
        cell.isSelected -> UniMatePurplePrimary
        cell.isToday -> UniMatePurplePrimary.copy(alpha = 0.45f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${CalendarDateEngine.formatDayFull(cell.dayStartMillis)}, ${items.size} events"
            }
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .testTag("calendar_day_cell_${cell.dayStartMillis}"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = cell.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (cell.isSelected || cell.isToday) FontWeight.ExtraBold else FontWeight.Medium,
                ),
                color = textColor,
            )

            if (items.isNotEmpty()) {
                val distinctColors = items.map { it.category.color }.distinct().take(3)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    distinctColors.forEach { dotColor ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (cell.isSelected) Color.White else dotColor),
                        )
                    }
                    if (items.size > 3) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (cell.isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarWeeklyStripCard(
    weekDays: List<Long>,
    selectedDateMillis: Long,
    todayMillis: Long,
    weekItemsByDay: Map<Long, List<CalendarDisplayItem>>,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("calendar_weekly_view"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        weekDays.forEach { dayMillis ->
            val items = weekItemsByDay[dayMillis].orEmpty()
            val isSelected = CalendarDateEngine.isSameDay(dayMillis, selectedDateMillis)
            val isToday = CalendarDateEngine.isSameDay(dayMillis, todayMillis)
            val (dow, dom) = CalendarDateEngine.formatDayChipShort(dayMillis)

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        UniMatePurplePrimary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                border = BorderStroke(
                    width = if (isSelected || isToday) 1.5.dp else 1.dp,
                    color = when {
                        isSelected -> UniMatePurplePrimary
                        isToday -> UniMatePurplePrimary.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable { onSelectDay(dayMillis) }
                    .testTag("calendar_week_day_$dayMillis"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isSelected -> UniMatePurplePrimary
                            isToday -> UniMatePurplePrimary.copy(alpha = 0.14f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.width(54.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = dow.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = dom,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isToday) "Today" else CalendarDateEngine.formatDayFull(dayMillis),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isToday) UniMatePurplePrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${items.size} item(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (items.isEmpty()) {
                            Text(
                                text = "No events scheduled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            items.take(3).forEach { event ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = event.category.color.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(event.category.color),
                                        )
                                        Text(
                                            text = event.title,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = event.category.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = event.category.color,
                                        )
                                    }
                                }
                            }
                            if (items.size > 3) {
                                Text(
                                    text = "+${items.size - 3} more — tap day to view all",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UniMatePurplePrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
