package com.example.unimate.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.DayOfWeekOption
import com.example.unimate.data.model.SubjectColorPalette
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary

@Composable
fun DayOfWeekStrip(
    selectedDay: DayOfWeekOption,
    today: DayOfWeekOption,
    classCountsByDay: Map<DayOfWeekOption, Int>,
    onSelectDay: (DayOfWeekOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayOfWeekOption.entries.forEach { day ->
            val count = classCountsByDay[day] ?: 0
            val isToday = day == today
            val label = buildString {
                append(day.shortLabel)
                if (isToday) append(" • Today")
                if (count > 0) append(" ($count)")
            }
            FilterChip(
                selected = selectedDay == day,
                onClick = { onSelectDay(day) },
                label = { Text(label) },
                leadingIcon = if (isToday) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Today,
                            contentDescription = "Today",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("timetable_day_chip_${day.wireValue.lowercase()}"),
            )
        }
    }
}

@Composable
fun WeeklyDayHeader(
    day: DayOfWeekOption,
    isToday: Boolean,
    classCount: Int,
    onAddForDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${day.fullLabel} ($classCount)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
            if (isToday) {
                Surface(
                    color = UniMatePurplePrimary,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        TextButton(
            onClick = onAddForDay,
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            Text("+ Add")
        }
    }
}

@Composable
fun TimetableClassCard(
    item: TimetableClass,
    isToday: Boolean,
    conflictingPeers: List<TimetableClass>,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectColor = SubjectColorPalette.colorForIndex(item.colorIndex)
    val hasConflict = conflictingPeers.isNotEmpty()
    val borderColor = when {
        hasConflict -> UniMateAmber
        isToday -> subjectColor
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) {
                subjectColor.copy(alpha = 0.06f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(if (isToday || hasConflict) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("timetable_class_card_${item.id}"),
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
                    .background(subjectColor),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        Surface(
                            color = subjectColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = item.courseCode,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = subjectColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        if (isToday) {
                            Surface(
                                color = UniMateEmerald.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = "TODAY",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = UniMateEmerald,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (hasConflict) {
                            Surface(
                                color = UniMateAmber.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = "CONFLICT",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = UniMateAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Row {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("tt_edit_${item.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit ${item.subjectName}",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("tt_delete_${item.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete ${item.subjectName}",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Text(
                    text = item.subjectName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = subjectColor,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "${item.formattedTimeRange()} (${item.formattedDuration()})",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = item.instructor,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = item.classroom,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (hasConflict) {
                    val peerSummary = conflictingPeers.joinToString(", ") { "${it.courseCode} (${it.formattedTimeRange()})" }
                    Text(
                        text = "Overlaps with $peerSummary",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = UniMateAmber,
                    )
                }
            }
        }
    }
}
