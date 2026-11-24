package com.example.unimate.ui.assignments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.AssignmentFilter
import com.example.unimate.data.model.AssignmentSortOrder
import com.example.unimate.data.model.StudentAssignment
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.components.AcademicModuleEmptyCard
import com.example.unimate.ui.components.AcademicModuleErrorCard
import com.example.unimate.ui.components.AcademicModuleLoadingCard
import com.example.unimate.ui.theme.UniMateCoralError
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssignmentsSectionContent(
    state: AssignmentsUiState,
    actionMessage: String?,
    onSelectFilter: (AssignmentFilter) -> Unit,
    onSelectSortOrder: (AssignmentSortOrder) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onAddAssignmentClick: () -> Unit,
    onEditAssignmentClick: (StudentAssignment) -> Unit,
    onToggleCompleted: (StudentAssignment) -> Unit,
    onDeleteAssignment: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<StudentAssignment?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("assignments_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Assignments",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = "Deadlines, priority levels, filters, and attached briefs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAddAssignmentClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("assignments_add_button"),
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
                testTag = "assignments_action_banner",
            )
        }

        when (state) {
            AssignmentsUiState.Loading -> {
                AcademicModuleLoadingCard(
                    message = "Syncing assignments from Cloud Firestore…",
                    testTag = "assignments_loading_state",
                )
            }

            is AssignmentsUiState.Error -> {
                AcademicModuleErrorCard(
                    message = state.message,
                    onRetry = onRetry,
                    testTag = "assignments_error_state",
                )
            }

            AssignmentsUiState.Empty -> {
                AcademicModuleEmptyCard(
                    icon = Icons.AutoMirrored.Rounded.Assignment,
                    title = "No assignments yet",
                    subtitle = "Add coursework deadlines, priority levels, and file or URL attachments.",
                    ctaLabel = "Add First Assignment",
                    onCtaClick = onAddAssignmentClick,
                    testTag = "assignments_empty_state",
                )
            }

            is AssignmentsUiState.Success -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssignmentSummaryPill(
                        label = "Pending",
                        count = state.pendingCount,
                        accent = UniMatePurplePrimary,
                        modifier = Modifier.weight(1f),
                    )
                    AssignmentSummaryPill(
                        label = "Completed",
                        count = state.completedCount,
                        accent = UniMateEmerald,
                        modifier = Modifier.weight(1f),
                    )
                    AssignmentSummaryPill(
                        label = "Overdue",
                        count = state.overdueCount,
                        accent = UniMateCoralError,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Search title, course, or notes…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChanged("") },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assignments_search_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssignmentFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = state.activeFilter == filter,
                                onClick = { onSelectFilter(filter) },
                                label = { Text(filter.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("assignments_filter_${filter.name.lowercase()}"),
                            )
                        }
                    }

                    Box {
                        OutlinedButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("assignments_sort_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sort")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            AssignmentSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (state.sortOrder == order) "✓ ${order.label}" else order.label,
                                            fontWeight = if (state.sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        sortMenuExpanded = false
                                        onSelectSortOrder(order)
                                    },
                                    modifier = Modifier.testTag("assignments_sort_${order.name.lowercase()}"),
                                )
                            }
                        }
                    }
                }

                if (state.filteredAssignments.isEmpty()) {
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
                                text = "No assignments match your current filter or search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = {
                                    onSelectFilter(AssignmentFilter.ALL)
                                    onSearchQueryChanged("")
                                },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Reset Filters")
                            }
                        }
                    }
                } else {
                    state.filteredAssignments.forEach { assignment ->
                        AssignmentCard(
                            assignment = assignment,
                            onToggleCompleted = { onToggleCompleted(assignment) },
                            onEditClick = { onEditAssignmentClick(assignment) },
                            onDeleteClick = { pendingDelete = assignment },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Assignment") },
            text = { Text("Remove \"${target.title}\" permanently from Cloud Firestore?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAssignment(target.id)
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

@Composable
private fun AssignmentSummaryPill(
    label: String,
    count: Int,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = accent,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
