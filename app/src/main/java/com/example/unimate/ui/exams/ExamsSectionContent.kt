package com.example.unimate.ui.exams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.ExamPreparationStatus
import com.example.unimate.data.model.ExamType
import com.example.unimate.data.model.ExamTypeFilter
import com.example.unimate.data.model.StudentExam
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.AcademicModuleEmptyCard
import com.example.unimate.ui.components.AcademicModuleErrorCard
import com.example.unimate.ui.components.AcademicModuleLoadingCard
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateCoralError
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamsSectionContent(
    state: ExamsUiState,
    actionMessage: String?,
    onSelectTypeFilter: (ExamTypeFilter) -> Unit,
    onAddExamClick: () -> Unit,
    onEditExamClick: (StudentExam) -> Unit,
    onToggleChecklistTopic: (StudentExam, String) -> Unit,
    onUpdatePreparationStatus: (StudentExam, ExamPreparationStatus) -> Unit,
    onDeleteExam: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<StudentExam?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("exams_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Exams",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = "Midterms & finals with live countdowns, hall info, and revision checklists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAddExamClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("exams_add_button"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add")
            }
        }

        if (!actionMessage.isNullOrBlank()) {
            AuthStatusBanner(
                message = actionMessage,
                type = AuthBannerType.INFO,
                testTag = "exams_action_banner",
            )
        }

        when (state) {
            ExamsUiState.Loading -> {
                AcademicModuleLoadingCard(
                    message = "Syncing exams from Cloud Firestore…",
                    testTag = "exams_loading_state",
                )
            }

            is ExamsUiState.Error -> {
                AcademicModuleErrorCard(
                    message = state.message,
                    onRetry = onRetry,
                    testTag = "exams_error_state",
                )
            }

            ExamsUiState.Empty -> {
                AcademicModuleEmptyCard(
                    icon = Icons.AutoMirrored.Rounded.EventNote,
                    title = "No exams scheduled",
                    subtitle = "Add upcoming midterms or finals with hall locations and topic-by-topic revision checklists.",
                    ctaLabel = "Add First Exam",
                    onCtaClick = onAddExamClick,
                    testTag = "exams_empty_state",
                )
            }

            is ExamsUiState.Success -> {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExamTypeFilter.entries.forEach { filter ->
                        val count = when (filter) {
                            ExamTypeFilter.ALL -> state.allExams.size
                            ExamTypeFilter.MIDTERM -> state.midtermCount
                            ExamTypeFilter.FINAL -> state.finalCount
                        }
                        FilterChip(
                            selected = state.typeFilter == filter,
                            onClick = { onSelectTypeFilter(filter) },
                            label = { Text("${filter.label} ($count)") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("exams_filter_${filter.name.lowercase()}"),
                        )
                    }
                }

                if (state.filteredExams.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "No ${state.typeFilter.label.lowercase()} scheduled yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { onSelectTypeFilter(ExamTypeFilter.ALL) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Show All Exams")
                            }
                        }
                    }
                } else {
                    state.filteredExams.forEach { exam ->
                        ExamCard(
                            exam = exam,
                            onEditClick = { onEditExamClick(exam) },
                            onToggleChecklistTopic = { topicId -> onToggleChecklistTopic(exam, topicId) },
                            onUpdateStatus = { nextStatus -> onUpdatePreparationStatus(exam, nextStatus) },
                            onDeleteClick = { pendingDelete = exam },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Exam") },
            text = { Text("Remove \"${target.title}\" permanently from Cloud Firestore?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteExam(target.id)
                        pendingDelete = null
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Delete", color = UniMateCoralError)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamCard(
    exam: StudentExam,
    onEditClick: () -> Unit,
    onToggleChecklistTopic: (String) -> Unit,
    onUpdateStatus: (ExamPreparationStatus) -> Unit,
    onDeleteClick: () -> Unit,
) {
    val examType = exam.resolvedExamType()
    val prepStatus = exam.resolvedPreparationStatus()
    val isPast = exam.isPast()
    val typeAccent = if (examType == ExamType.FINAL) UniMateCoralError else UniMatePurplePrimary
    val countdownAccent = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else UniMateBlueElectric

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_card_${exam.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = typeAccent.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = examType.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = typeAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = countdownAccent.copy(alpha = 0.12f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = countdownAccent,
                                )
                                Text(
                                    text = exam.countdownLabel(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = countdownAccent,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = exam.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = exam.courseName,
                        style = MaterialTheme.typography.labelMedium,
                        color = UniMatePurplePrimary,
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("exam_edit_${exam.id}"),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit exam")
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("exam_delete_${exam.id}"),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete exam")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(13.dp))
                        Text(
                            text = exam.formattedDateTime(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.MeetingRoom, contentDescription = null, modifier = Modifier.size(13.dp))
                        Text(
                            text = exam.room,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Preparation Status",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExamPreparationStatus.entries.forEach { option ->
                        FilterChip(
                            selected = prepStatus == option,
                            onClick = { if (prepStatus != option) onUpdateStatus(option) },
                            label = { Text(option.label) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Revision Checklist",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        if (exam.revisionChecklist.isNotEmpty()) {
                            Text(
                                text = "${exam.revisedCount()}/${exam.revisionChecklist.size} revised",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (exam.revisionProgressFraction() >= 1f) UniMateEmerald else UniMateAmber,
                            )
                        }
                    }

                    if (exam.revisionChecklist.isEmpty()) {
                        Text(
                            text = "No revision topics added yet. Tap Edit to build your study checklist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { exam.revisionProgressFraction() },
                            color = if (exam.revisionProgressFraction() >= 1f) UniMateEmerald else UniMatePurplePrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        exam.revisionChecklist.forEach { topic ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleChecklistTopic(topic.id) }
                                    .testTag("exam_topic_${exam.id}_${topic.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = topic.done,
                                    onCheckedChange = { onToggleChecklistTopic(topic.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                )
                                Text(
                                    text = topic.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        textDecoration = if (topic.done) TextDecoration.LineThrough else TextDecoration.None,
                                    ),
                                    color = if (topic.done) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
