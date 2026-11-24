package com.example.unimate.ui.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.data.sync.PendingSyncOperation
import com.example.unimate.data.sync.SyncConflict
import com.example.unimate.data.sync.SyncStatusUiState
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateCoralError
import com.example.unimate.ui.theme.UniMateEmerald
import java.text.DateFormat
import java.util.Date

private enum class SyncBannerKind {
    HIDDEN,
    CONFLICT,
    OFFLINE,
    SYNCING,
    ERROR_OR_PENDING,
    JUST_SYNCED,
}

@Composable
fun GlobalSyncStatusBanner(
    syncState: SyncStatusUiState,
    onOpenDetails: () -> Unit,
    onSyncNow: () -> Unit,
    onDismissJustSynced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = when {
        syncState.conflictCount > 0 -> SyncBannerKind.CONFLICT
        !syncState.isOnline -> SyncBannerKind.OFFLINE
        syncState.isSyncing -> SyncBannerKind.SYNCING
        syncState.lastSyncError != null || syncState.pendingCount > 0 -> SyncBannerKind.ERROR_OR_PENDING
        syncState.showJustSyncedBanner -> SyncBannerKind.JUST_SYNCED
        else -> SyncBannerKind.HIDDEN
    }

    AnimatedVisibility(
        visible = kind != SyncBannerKind.HIDDEN,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        val accentColor: Color
        val containerColor: Color
        val titleText: String
        val subtitleText: String
        val bannerTag: String

        when (kind) {
            SyncBannerKind.CONFLICT -> {
                accentColor = UniMateCoralError
                containerColor = UniMateCoralError.copy(alpha = 0.12f)
                titleText = stringResource(R.string.sync_banner_conflict_title, syncState.conflictCount)
                subtitleText = syncState.conflicts.firstOrNull()?.reason?.label
                    ?: "Review local vs Cloud Firestore versions."
                bannerTag = "sync_banner_conflict"
            }

            SyncBannerKind.OFFLINE -> {
                accentColor = UniMateAmber
                containerColor = UniMateAmber.copy(alpha = 0.14f)
                titleText = stringResource(R.string.sync_banner_offline_title)
                subtitleText = if (syncState.pendingCount > 0) {
                    stringResource(R.string.sync_banner_offline_pending, syncState.pendingCount)
                } else {
                    stringResource(R.string.sync_banner_offline_cached)
                }
                bannerTag = "sync_banner_offline"
            }

            SyncBannerKind.SYNCING -> {
                accentColor = UniMateBlueElectric
                containerColor = UniMateBlueElectric.copy(alpha = 0.12f)
                titleText = stringResource(R.string.sync_banner_syncing_title)
                subtitleText = if (syncState.pendingCount > 0) {
                    "${syncState.pendingCount} pending change(s) syncing"
                } else {
                    "Verifying Cloud Firestore state"
                }
                bannerTag = "sync_banner_syncing"
            }

            SyncBannerKind.ERROR_OR_PENDING -> {
                accentColor = UniMateAmber
                containerColor = UniMateAmber.copy(alpha = 0.14f)
                titleText = if (syncState.pendingCount > 0) {
                    stringResource(R.string.sync_banner_pending_online, syncState.pendingCount)
                } else {
                    stringResource(R.string.sync_banner_error_title)
                }
                subtitleText = syncState.lastSyncError
                    ?: "Tap Sync Now to upload queued offline changes."
                bannerTag = "sync_banner_pending"
            }

            SyncBannerKind.JUST_SYNCED -> {
                accentColor = UniMateEmerald
                containerColor = UniMateEmerald.copy(alpha = 0.14f)
                titleText = stringResource(R.string.sync_banner_just_synced)
                subtitleText = "Offline changes are up to date."
                bannerTag = "sync_banner_just_synced"
            }

            SyncBannerKind.HIDDEN -> {
                accentColor = UniMateEmerald
                containerColor = MaterialTheme.colorScheme.surface
                titleText = ""
                subtitleText = ""
                bannerTag = "sync_banner_hidden"
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenDetails)
                .testTag(bannerTag),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (kind) {
                    SyncBannerKind.CONFLICT -> Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = stringResource(R.string.cd_sync_status_icon),
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    SyncBannerKind.OFFLINE -> Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = stringResource(R.string.cd_sync_status_icon),
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    SyncBannerKind.SYNCING -> CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    SyncBannerKind.ERROR_OR_PENDING -> Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = stringResource(R.string.cd_sync_status_icon),
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    SyncBannerKind.JUST_SYNCED, SyncBannerKind.HIDDEN -> Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = stringResource(R.string.cd_sync_status_icon),
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                when (kind) {
                    SyncBannerKind.CONFLICT -> TextButton(
                        onClick = onOpenDetails,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sync_banner_resolve_button"),
                    ) {
                        Text(
                            text = stringResource(R.string.sync_action_resolve),
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    SyncBannerKind.OFFLINE -> TextButton(
                        onClick = onOpenDetails,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sync_banner_details_button"),
                    ) {
                        Text(
                            text = stringResource(R.string.sync_action_details),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    SyncBannerKind.ERROR_OR_PENDING -> TextButton(
                        onClick = onSyncNow,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sync_banner_retry_button"),
                    ) {
                        Text(
                            text = stringResource(R.string.sync_action_retry),
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    SyncBannerKind.JUST_SYNCED -> IconButton(
                        onClick = onDismissJustSynced,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sync_banner_dismiss_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_dismiss_sync_banner),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SyncBannerKind.SYNCING, SyncBannerKind.HIDDEN -> Unit
                }
            }
        }
    }
}

@Composable
fun SyncStatusDetailsDialog(
    syncState: SyncStatusUiState,
    onSyncNow: () -> Unit,
    onResolveKeepLocal: (String) -> Unit,
    onResolveUseCloud: (String) -> Unit,
    onResolveKeepBoth: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val lastSyncedFormatted = syncState.lastSyncedAtMillis?.let { millis ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    } ?: stringResource(R.string.sync_never_synced)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (syncState.isOnline) Icons.Rounded.CloudQueue else Icons.Rounded.CloudOff,
                    contentDescription = stringResource(R.string.cd_sync_status_icon),
                    tint = if (syncState.isOnline) UniMateEmerald else UniMateAmber,
                )
                Text(
                    text = stringResource(R.string.sync_details_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .testTag("sync_details_dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SyncInfoRow(
                                label = "Connection",
                                value = if (syncState.isOnline) {
                                    stringResource(R.string.sync_status_online)
                                } else {
                                    stringResource(R.string.sync_status_offline)
                                },
                                valueColor = if (syncState.isOnline) UniMateEmerald else UniMateAmber,
                            )
                            SyncInfoRow(
                                label = stringResource(R.string.sync_cache_status_label),
                                value = if (syncState.isUsingCachedData) {
                                    "Serving local cache"
                                } else {
                                    stringResource(R.string.sync_cache_status_active)
                                },
                                valueColor = MaterialTheme.colorScheme.onSurface,
                            )
                            SyncInfoRow(
                                label = stringResource(R.string.sync_last_synced_label),
                                value = lastSyncedFormatted,
                                valueColor = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                if (syncState.lastSyncError != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = UniMateCoralError.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, UniMateCoralError.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = syncState.lastSyncError,
                                style = MaterialTheme.typography.bodySmall,
                                color = UniMateCoralError,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }

                if (syncState.conflicts.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.sync_conflicts_header, syncState.conflicts.size),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = UniMateCoralError,
                        )
                    }
                    items(syncState.conflicts, key = { it.conflictId }) { conflict ->
                        SyncConflictCard(
                            conflict = conflict,
                            isOnline = syncState.isOnline,
                            isSyncing = syncState.isSyncing,
                            onKeepLocal = { onResolveKeepLocal(conflict.conflictId) },
                            onUseCloud = { onResolveUseCloud(conflict.conflictId) },
                            onKeepBoth = { onResolveKeepBoth(conflict.conflictId) },
                        )
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.sync_pending_queue_header, syncState.pendingOperations.size),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (syncState.pendingOperations.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.sync_pending_queue_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(syncState.pendingOperations, key = { it.operationId }) { op ->
                        PendingOperationRow(op = op)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSyncNow,
                enabled = syncState.isOnline && !syncState.isSyncing,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("sync_details_sync_now_button"),
            ) {
                if (syncState.isSyncing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(stringResource(R.string.sync_action_sync_now))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("sync_details_close_button"),
            ) {
                Text(stringResource(R.string.dialog_close))
            }
        },
    )
}

@Composable
private fun SyncInfoRow(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
        )
    }
}

@Composable
private fun PendingOperationRow(op: PendingSyncOperation) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_sync_item_${op.operationId}"),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = op.displayTitle.ifBlank { op.collectionKey },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = UniMateAmber.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = op.operationKind.wireValue,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = UniMateAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (op.localSummary.isNotBlank()) {
                Text(
                    text = op.localSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (op.retryCount > 0) {
                Text(
                    text = "Retry attempts: ${op.retryCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncConflictCard(
    conflict: SyncConflict,
    isOnline: Boolean,
    isSyncing: Boolean,
    onKeepLocal: () -> Unit,
    onUseCloud: () -> Unit,
    onKeepBoth: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = UniMateCoralError.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, UniMateCoralError.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_conflict_card_${conflict.conflictId}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = conflict.displayTitle,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = conflict.reason.label,
                style = MaterialTheme.typography.labelSmall,
                color = UniMateCoralError,
            )
            Text(
                text = "Your device: ${conflict.localSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Cloud Firestore: ${conflict.serverSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = onKeepLocal,
                    enabled = isOnline && !isSyncing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("conflict_keep_mine_${conflict.conflictId}"),
                ) {
                    Text(stringResource(R.string.sync_conflict_keep_mine))
                }
                OutlinedButton(
                    onClick = onUseCloud,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("conflict_use_cloud_${conflict.conflictId}"),
                ) {
                    Text(stringResource(R.string.sync_conflict_use_cloud))
                }
                if (conflict.supportsKeepBoth) {
                    OutlinedButton(
                        onClick = onKeepBoth,
                        enabled = isOnline && !isSyncing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("conflict_keep_both_${conflict.conflictId}"),
                    ) {
                        Text(stringResource(R.string.sync_conflict_keep_both))
                    }
                }
            }
        }
    }
}

@Composable
fun UnsyncedDataSignOutDialog(
    pendingCount: Int,
    conflictCount: Int,
    isOnline: Boolean,
    isSyncing: Boolean,
    errorMessage: String?,
    onSyncAndSignOut: () -> Unit,
    onStaySignedIn: () -> Unit,
    onSignOutAnyway: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSyncing) onStaySignedIn() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = stringResource(R.string.cd_sync_status_icon),
                tint = UniMateAmber,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.sync_signout_warning_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier.testTag("unsynced_signout_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.sync_signout_warning_body, pendingCount, conflictCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = UniMateCoralError.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = UniMateCoralError,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSyncAndSignOut,
                        enabled = isOnline && !isSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("signout_warning_sync_button"),
                    ) {
                        Text(
                            text = if (isSyncing) {
                                stringResource(R.string.sync_signout_syncing)
                            } else {
                                stringResource(R.string.sync_signout_sync_and_exit)
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = onStaySignedIn,
                        enabled = !isSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("signout_warning_stay_button"),
                    ) {
                        Text(stringResource(R.string.sync_signout_stay))
                    }
                    TextButton(
                        onClick = onSignOutAnyway,
                        enabled = !isSyncing,
                        colors = ButtonDefaults.textButtonColors(contentColor = UniMateCoralError),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("signout_warning_anyway_button"),
                    ) {
                        Text(stringResource(R.string.sync_signout_anyway))
                    }
                }
            }
        },
        confirmButton = {},
    )
}
