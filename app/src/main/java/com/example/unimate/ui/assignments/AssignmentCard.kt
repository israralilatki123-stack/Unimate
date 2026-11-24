package com.example.unimate.ui.assignments

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.AssignmentPriority
import com.example.unimate.data.model.AssignmentStatus
import com.example.unimate.data.model.StudentAssignment
import com.example.unimate.ui.components.openAttachmentSafely
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateCoralError
import com.example.unimate.ui.theme.UniMateEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssignmentCard(
    assignment: StudentAssignment,
    onToggleCompleted: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    val isCompleted = assignment.resolvedStatus() == AssignmentStatus.COMPLETED
    val isOverdue = assignment.isOverdue()
    val priority = assignment.resolvedPriority()
    val priorityColor = when (priority) {
        AssignmentPriority.HIGH -> UniMateCoralError
        AssignmentPriority.MEDIUM -> UniMateAmber
        AssignmentPriority.LOW -> UniMateBlueElectric
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isOverdue) 1.5.dp else 1.dp,
            color = if (isOverdue) UniMateCoralError.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("assignment_card_${assignment.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onToggleCompleted,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("assignment_toggle_${assignment.id}"),
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (isCompleted) "Mark pending" else "Mark completed",
                        tint = if (isCompleted) UniMateEmerald else MaterialTheme.colorScheme.primary,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assignment.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = assignment.courseName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("assignment_edit_${assignment.id}"),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit assignment")
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("assignment_delete_${assignment.id}"),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete assignment")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = priorityColor.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = "${priority.label} Priority",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = priorityColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isCompleted -> UniMateEmerald.copy(alpha = 0.14f)
                        isOverdue -> UniMateCoralError.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = when {
                                isCompleted -> UniMateEmerald
                                isOverdue -> UniMateCoralError
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = "${assignment.formattedDeadline()} • ${assignment.deadlineRelativeLabel()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isCompleted -> UniMateEmerald
                                isOverdue -> UniMateCoralError
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            if (assignment.description.isNotBlank()) {
                Text(
                    text = assignment.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (assignment.attachments.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    assignment.attachments.forEach { attachment ->
                        val isUrl = attachment.mimeType == "text/uri-list" ||
                            attachment.uri.startsWith("http://") ||
                            attachment.uri.startsWith("https://")
                        AssistChip(
                            onClick = {
                                val opened = openAttachmentSafely(context, attachment.uri, attachment.mimeType)
                                if (!opened) {
                                    Toast.makeText(
                                        context,
                                        "No compatible viewer found for ${attachment.name}.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isUrl) Icons.Rounded.Link else Icons.Rounded.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = attachment.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
            }
        }
    }
}
