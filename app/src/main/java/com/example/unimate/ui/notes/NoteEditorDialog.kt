package com.example.unimate.ui.notes

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.data.model.NoteAttachment
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.StudyNote
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.CourseQuickFillRow
import com.example.unimate.ui.components.openAttachmentSafely
import com.example.unimate.ui.components.resolveDocumentDisplayName
import com.example.unimate.ui.components.tryTakePersistableReadPermission
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMatePurplePrimary

private val SUPPORTED_NOTE_DOCUMENT_MIMES = arrayOf(
    "application/pdf",
    "text/plain",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "image/*",
)

private fun resolveDocumentSizeBytes(context: Context, uri: Uri): Long {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) cursor.getLong(idx) else 0L
                } else {
                    0L
                }
            } ?: 0L
    }.getOrDefault(0L)
}

internal fun formatFileSizeLabel(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024L
    if (kb < 1024L) return "${kb.coerceAtLeast(1L)} KB"
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorDialog(
    state: NoteEditorState,
    enrolledCourses: List<StudentCourse>,
    onTitleChanged: (String) -> Unit,
    onSubjectChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onPinnedChanged: (Boolean) -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onLinkDraftUriChanged: (String) -> Unit,
    onLinkDraftNameChanged: (String) -> Unit,
    onAddPickedAttachment: (name: String, uri: String, mimeType: String, sizeBytes: Long) -> Unit,
    onAddLinkAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current

    // Zero-permission Storage Access Framework file picker (Play Policy compliant on all Android APIs)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                tryTakePersistableReadPermission(context, uri)
                val name = resolveDocumentDisplayName(context, uri)
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val sizeBytes = resolveDocumentSizeBytes(context, uri)
                onAddPickedAttachment(name, uri.toString(), mime, sizeBytes)
            }
        },
    )

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = {
            Text(
                text = if (state.editingNoteId == null) "Create Study Note" else "Edit Study Note",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuthStatusBanner(message = state.errorMessage, type = AuthBannerType.ERROR)

                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChanged,
                    label = { Text("Note Title *") },
                    placeholder = { Text("e.g. Dynamic Programming Patterns") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_editor_title_input"),
                )

                CourseQuickFillRow(
                    enrolledCourses = enrolledCourses,
                    onCourseSelected = onSubjectChanged,
                )

                OutlinedTextField(
                    value = state.courseName,
                    onValueChange = onSubjectChanged,
                    label = { Text("Subject / Course *") },
                    placeholder = { Text("e.g. CS-301 • Algorithms") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_editor_subject_input"),
                )

                OutlinedTextField(
                    value = state.content,
                    onValueChange = onContentChanged,
                    label = { Text("Study Notes *") },
                    placeholder = { Text("Key definitions, formulas, lecture summary, or exam pointers") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_editor_content_input"),
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.pinned,
                        onClick = { onPinnedChanged(!state.pinned) },
                        leadingIcon = {
                            Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(if (state.pinned) "Pinned" else "Pin Note") },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("note_editor_pin_chip"),
                    )
                    FilterChip(
                        selected = state.favorite,
                        onClick = { onFavoriteChanged(!state.favorite) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text(if (state.favorite) "Favorited" else "Favorite") },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("note_editor_favorite_chip"),
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "PDF & Document Attachments (${state.attachments.size}/10)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "Uses Android Storage Access Framework (no broad storage permission). Picked PDFs/docs stay linked locally on this device; remote cloud file upload requires Firebase Storage.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = { documentPickerLauncher.launch(SUPPORTED_NOTE_DOCUMENT_MIMES) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("note_editor_pick_document_button"),
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Attach PDF or Document")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.linkDraftUri,
                        onValueChange = onLinkDraftUriChanged,
                        label = { Text("Or paste PDF / resource URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("note_editor_link_input"),
                    )
                    IconButton(
                        onClick = onAddLinkAttachment,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("note_editor_add_link_button"),
                    ) {
                        Icon(Icons.Rounded.AddLink, contentDescription = "Add resource link")
                    }
                }
                if (state.linkDraftUri.isNotBlank()) {
                    OutlinedTextField(
                        value = state.linkDraftName,
                        onValueChange = onLinkDraftNameChanged,
                        label = { Text("Link Label (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.attachments.forEach { item ->
                    AttachmentPreviewRow(
                        item = item,
                        onRemove = { onRemoveAttachment(item.uri) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("note_editor_save_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.editingNoteId == null) "Save Note" else "Update Note")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.dashboard_cancel))
            }
        },
    )
}

@Composable
private fun AttachmentPreviewRow(
    item: NoteAttachment,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (item.isPdf()) Icons.Rounded.PictureAsPdf else Icons.Rounded.AttachFile,
                contentDescription = null,
                tint = if (item.isPdf()) UniMatePurplePrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sizeLabel = formatFileSizeLabel(item.sizeBytes)
                if (sizeLabel.isNotBlank()) {
                    Text(
                        text = sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove ${item.name}",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun NoteDetailDialog(
    note: StudyNote,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var openError by remember(note.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = UniMateBlueElectric.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = note.courseName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = UniMateBlueElectric,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Updated ${note.formattedUpdatedTimestamp()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = if (note.pinned) "Unpin note" else "Pin note",
                                tint = if (note.pinned) UniMatePurplePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = if (note.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = if (note.favorite) "Remove favorite" else "Mark favorite",
                                tint = if (note.favorite) UniMateAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                )

                AuthStatusBanner(message = openError, type = AuthBannerType.ERROR)

                if (note.attachments.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Attachments (${note.attachments.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    note.attachments.forEach { attachment ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val opened = openAttachmentSafely(context, attachment.uri, attachment.mimeType)
                                    openError = if (!opened) {
                                        "No app found or permission expired for \"${attachment.name}\"."
                                    } else {
                                        null
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (attachment.isPdf()) Icons.Rounded.PictureAsPdf else Icons.Rounded.AttachFile,
                                    contentDescription = null,
                                    tint = if (attachment.isPdf()) UniMatePurplePrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = attachment.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Open ${attachment.name}",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onEdit,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.dialog_close))
                }
            }
        },
    )
}
