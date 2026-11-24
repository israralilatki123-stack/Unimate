package com.example.unimate.ui.quizzes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.QuizPreparationStatus
import com.example.unimate.data.model.QuizSectionFilter
import com.example.unimate.data.model.StudentQuiz
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
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizzesSectionContent(
    state: QuizzesUiState,
    actionMessage: String?,
    onSelectSection: (QuizSectionFilter) -> Unit,
    onAddQuizClick: () -> Unit,
    onEditQuizClick: (StudentQuiz) -> Unit,
    onUpdatePreparationStatus: (StudentQuiz, QuizPreparationStatus) -> Unit,
    onDeleteQuiz: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<StudentQuiz?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quizzes_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quizzes",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = "Track upcoming quizzes, marks, preparation progress, and quiz history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAddQuizClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quizzes_add_button"),
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
                testTag = "quizzes_action_banner",
            )
        }

        when (state) {
            QuizzesUiState.Loading -> {
                AcademicModuleLoadingCard(
                    message = "Syncing quizzes from Cloud Firestore…",
                    testTag = "quizzes_loading_state",
                )
            }

            is QuizzesUiState.Error -> {
                AcademicModuleErrorCard(
                    message = state.message,
                    onRetry = onRetry,
                    testTag = "quizzes_error_state",
                )
            }

            QuizzesUiState.Empty -> {
                AcademicModuleEmptyCard(
                    icon = Icons.Rounded.Quiz,
                    title = "No quizzes recorded",
                    subtitle = "Add upcoming class quizzes or record past quiz scores to build your quiz history.",
                    ctaLabel = "Add First Quiz",
                    onCtaClick = onAddQuizClick,
                    testTag = "quizzes_empty_state",
                )
            }

            is QuizzesUiState.Success -> {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuizSectionFilter.entries.forEach { section ->
                        val count = when (section) {
                            QuizSectionFilter.UPCOMING -> state.upcomingQuizzes.size
                            QuizSectionFilter.HISTORY -> state.historyQuizzes.size
                            QuizSectionFilter.ALL -> state.allQuizzes.size
                        }
                        FilterChip(
                            selected = state.activeSection == section,
                            onClick = { onSelectSection(section) },
                            label = { Text("${section.label} ($count)") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("quizzes_section_${section.name.lowercase()}"),
                        )
                    }
                }

                val scoredHistory = state.historyQuizzes.filter { it.scoreRecorded && it.totalMarks > 0.0 }
                if (scoredHistory.isNotEmpty()) {
                    val avgPercent = scoredHistory.map { it.scorePercent() }.average().toInt()
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = UniMateEmerald.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, UniMateEmerald.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Quiz History Average (${scoredHistory.size} graded)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                text = "$avgPercent%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = UniMateEmerald,
                            )
                        }
                    }
                }

                if (state.visibleQuizzes.isEmpty()) {
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
                                text = if (state.activeSection == QuizSectionFilter.UPCOMING) {
                                    "No upcoming quizzes scheduled."
                                } else {
                                    "No quizzes in history yet."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = {
                                    onSelectSection(QuizSectionFilter.ALL)
                                },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Show All Quizzes")
                            }
                        }
                    }
                } else {
                    state.visibleQuizzes.forEach { quiz ->
                        QuizCard(
                            quiz = quiz,
                            onEditClick = { onEditQuizClick(quiz) },
                            onUpdateStatus = { nextStatus -> onUpdatePreparationStatus(quiz, nextStatus) },
                            onDeleteClick = { pendingDelete = quiz },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Quiz") },
            text = { Text("Remove \"${target.title}\" from Cloud Firestore?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteQuiz(target.id)
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
private fun QuizCard(
    quiz: StudentQuiz,
    onEditClick: () -> Unit,
    onUpdateStatus: (QuizPreparationStatus) -> Unit,
    onDeleteClick: () -> Unit,
) {
    val status = quiz.resolvedPreparationStatus()
    val statusColor = when (status) {
        QuizPreparationStatus.NOT_STARTED -> UniMateCoralError
        QuizPreparationStatus.IN_PROGRESS -> UniMateAmber
        QuizPreparationStatus.READY -> UniMateBlueElectric
        QuizPreparationStatus.COMPLETED -> UniMateEmerald
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quiz_card_${quiz.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quiz.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = quiz.courseName,
                        style = MaterialTheme.typography.labelMedium,
                        color = UniMatePurplePrimary,
                    )
                }
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quiz_edit_${quiz.id}"),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit quiz")
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quiz_delete_${quiz.id}"),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete quiz")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            text = "${quiz.formattedDate()} • ${quiz.formattedTime()}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = UniMatePurplePrimary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "Total: ${formatQuizMarks(quiz.totalMarks)} marks",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = UniMatePurplePrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                if (quiz.scoreRecorded) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = UniMateEmerald.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "Score: ${formatQuizMarks(quiz.obtainedMarks)}/${formatQuizMarks(quiz.totalMarks)} (${quiz.scorePercent()}%)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = UniMateEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (quiz.notes.isNotBlank()) {
                Text(
                    text = quiz.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QuizPreparationStatus.entries.forEach { option ->
                    FilterChip(
                        selected = status == option,
                        onClick = { if (status != option) onUpdateStatus(option) },
                        label = { Text(option.label) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                }
            }
        }
    }
}

private fun formatQuizMarks(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}
