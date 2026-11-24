package com.example.unimate.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.StudyNote
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.AcademicModuleEmptyCard
import com.example.unimate.ui.components.AcademicModuleErrorCard
import com.example.unimate.ui.components.AcademicModuleLoadingCard
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMatePurplePrimary

@Composable
fun NotesSectionContent(
    state: NotesUiState,
    actionMessage: String?,
    onClearActionMessage: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSelectSubjectFilter: (String?) -> Unit,
    onSelectQuickFilter: (NotesQuickFilter) -> Unit,
    onCreateNoteClick: () -> Unit,
    onOpenNoteDetail: (StudyNote) -> Unit,
    onEditNoteClick: (StudyNote) -> Unit,
    onTogglePin: (StudyNote) -> Unit,
    onToggleFavorite: (StudyNote) -> Unit,
    onDeleteNote: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("notes_section_content"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NotesHeaderCard(
                state = state,
                onCreateNoteClick = onCreateNoteClick,
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        if (!actionMessage.isNullOrBlank()) {
            item {
                Column(modifier = Modifier.widthIn(max = 760.dp)) {
                    AuthStatusBanner(
                        message = actionMessage,
                        type = AuthBannerType.SUCCESS,
                        testTag = "notes_action_banner",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onClearActionMessage) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        when (state) {
            NotesUiState.Loading -> {
                item {
                    AcademicModuleLoadingCard(
                        message = "Loading your Cloud Firestore & cached notes…",
                        testTag = "notes_loading_card",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            NotesUiState.Empty -> {
                item {
                    AcademicModuleEmptyCard(
                        icon = Icons.AutoMirrored.Rounded.Notes,
                        title = "No Study Notes Yet",
                        subtitle = "Organize lecture summaries by subject, pin key formulas, star favorites, and attach PDFs or resource links.",
                        ctaLabel = "Create First Note",
                        onCtaClick = onCreateNoteClick,
                        testTag = "notes_empty_card",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is NotesUiState.Error -> {
                item {
                    AcademicModuleErrorCard(
                        message = state.message,
                        onRetry = onRetry,
                        testTag = "notes_error_card",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is NotesUiState.Success -> {
                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = { Text("Search title, subject, content, or attachment…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChanged("") },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .testTag("notes_search_input"),
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NotesQuickFilter.entries.forEach { filter ->
                            val countSuffix = when (filter) {
                                NotesQuickFilter.ALL -> " (${state.allNotes.size})"
                                NotesQuickFilter.PINNED -> " (${state.pinnedCount})"
                                NotesQuickFilter.FAVORITES -> " (${state.favoritesCount})"
                            }
                            FilterChip(
                                selected = state.quickFilter == filter,
                                onClick = { onSelectQuickFilter(filter) },
                                label = { Text("${filter.label}$countSuffix") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("notes_filter_${filter.name.lowercase()}"),
                            )
                        }
                    }
                }

                if (state.subjects.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = state.selectedSubject == null,
                                onClick = { onSelectSubjectFilter(null) },
                                label = { Text("All Subjects") },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            )
                            state.subjects.forEach { subject ->
                                FilterChip(
                                    selected = state.selectedSubject == subject,
                                    onClick = { onSelectSubjectFilter(subject) },
                                    label = { Text(subject) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                )
                            }
                        }
                    }
                }

                if (state.filteredNotes.isEmpty()) {
                    item {
                        AcademicModuleEmptyCard(
                            icon = Icons.Rounded.Search,
                            title = "No Matching Notes",
                            subtitle = "Try clearing your search or switching subject/pin filters.",
                            ctaLabel = "Create Note",
                            onCtaClick = onCreateNoteClick,
                            testTag = "notes_filtered_empty_card",
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                } else {
                    items(state.filteredNotes, key = { "rich-note-${it.id}" }) { note ->
                        StudyNoteCard(
                            note = note,
                            onClick = { onOpenNoteDetail(note) },
                            onEditClick = { onEditNoteClick(note) },
                            onTogglePin = { onTogglePin(note) },
                            onToggleFavorite = { onToggleFavorite(note) },
                            onDelete = { onDeleteNote(note.id) },
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesHeaderCard(
    state: NotesUiState,
    onCreateNoteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalCount = (state as? NotesUiState.Success)?.allNotes?.size ?: 0
    val pinnedCount = (state as? NotesUiState.Success)?.pinnedCount ?: 0

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Study Notes & Attachments",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = if (totalCount > 0) {
                        "$totalCount saved • $pinnedCount pinned • offline cache active"
                    } else {
                        "Organize notes by subject with PDFs & links"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onCreateNoteClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("notes_create_button"),
            ) {
                Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Note")
            }
        }
    }
}

@Composable
private fun StudyNoteCard(
    note: StudyNote,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.pinned) {
                UniMatePurplePrimary.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (note.pinned) {
                UniMatePurplePrimary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("note_card_${note.id}"),
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
                    Surface(
                        color = UniMateBlueElectric.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = note.courseName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = UniMateBlueElectric,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    if (note.pinned) {
                        Surface(
                            color = UniMatePurplePrimary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = "Pinned",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = UniMatePurplePrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("note_pin_${note.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = if (note.pinned) "Unpin note" else "Pin note",
                            tint = if (note.pinned) UniMatePurplePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("note_fav_${note.id}"),
                    ) {
                        Icon(
                            imageVector = if (note.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = if (note.favorite) "Remove favorite" else "Favorite note",
                            tint = if (note.favorite) UniMateAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saved ${note.formattedUpdatedTimestamp()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (note.attachments.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = null,
                                tint = UniMateBlueElectric,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "${note.attachments.size} attachment${if (note.attachments.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = UniMateBlueElectric,
                            )
                        }
                    }
                }
                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("note_edit_${note.id}"),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit note", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("note_delete_${note.id}"),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete note", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
