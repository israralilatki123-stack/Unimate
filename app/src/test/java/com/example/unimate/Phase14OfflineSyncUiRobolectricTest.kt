package com.example.unimate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.unimate.data.sync.PendingSyncOperation
import com.example.unimate.data.sync.SyncConflict
import com.example.unimate.data.sync.SyncConflictReason
import com.example.unimate.data.sync.SyncOperationKind
import com.example.unimate.data.sync.SyncStatusUiState
import com.example.unimate.ui.ai.AiAssistantTabContent
import com.example.unimate.ui.ai.AiAssistantUiState
import com.example.unimate.ui.sync.GlobalSyncStatusBanner
import com.example.unimate.ui.sync.SyncStatusDetailsDialog
import com.example.unimate.ui.sync.UnsyncedDataSignOutDialog
import com.example.unimate.ui.theme.UniMateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase14OfflineSyncUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `global sync status banner renders offline conflict syncing retry and just-synced states`() {
        var syncState by mutableStateOf(
            SyncStatusUiState(
                isOnline = false,
                pendingOperations = listOf(
                    PendingSyncOperation(
                        operationId = "op-1",
                        uid = "u1",
                        docPath = "users/u1/tasks/t1",
                        collectionKey = "tasks",
                        displayTitle = "Lab Report",
                        operationKind = SyncOperationKind.CREATE,
                    ),
                ),
            ),
        )
        var openedDetails = false
        var retriedSync = false
        var dismissedJustSynced = false

        composeRule.setContent {
            UniMateTheme {
                GlobalSyncStatusBanner(
                    syncState = syncState,
                    onOpenDetails = { openedDetails = true },
                    onSyncNow = { retriedSync = true },
                    onDismissJustSynced = { dismissedJustSynced = true },
                )
            }
        }

        // Offline banner
        composeRule.onNodeWithTag("sync_banner_offline").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_banner_details_button").performClick()
        assertTrue(openedDetails)

        // Online + pending/error -> Retry action
        composeRule.runOnIdle {
            syncState = SyncStatusUiState(
                isOnline = true,
                lastSyncError = "Temporary failure",
                pendingOperations = syncState.pendingOperations,
            )
        }
        composeRule.onNodeWithTag("sync_banner_pending").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_banner_retry_button").performClick()
        assertTrue(retriedSync)

        // Syncing state
        composeRule.runOnIdle {
            syncState = SyncStatusUiState(
                isOnline = true,
                isSyncing = true,
                pendingOperations = syncState.pendingOperations,
            )
        }
        composeRule.onNodeWithTag("sync_banner_syncing").assertIsDisplayed()

        // Conflict state
        composeRule.runOnIdle {
            syncState = SyncStatusUiState(
                isOnline = true,
                conflicts = listOf(
                    SyncConflict(
                        conflictId = "c-1",
                        uid = "u1",
                        docPath = "users/u1/notes/n1",
                        collectionKey = "notes",
                        displayTitle = "Algorithms Notes",
                        reason = SyncConflictReason.CONCURRENT_REMOTE_MODIFICATION,
                        localSummary = "title: Local Edit",
                        serverSummary = "title: Cloud Edit",
                    ),
                ),
            )
        }
        composeRule.onNodeWithTag("sync_banner_conflict").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_banner_resolve_button").assertIsDisplayed()

        // Just synced state + dismiss
        composeRule.runOnIdle {
            syncState = SyncStatusUiState(
                isOnline = true,
                showJustSyncedBanner = true,
            )
        }
        composeRule.onNodeWithTag("sync_banner_just_synced").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_banner_dismiss_button").performClick()
        assertTrue(dismissedJustSynced)
    }

    @Test
    fun `sync status details dialog exposes pending items and conflict resolution actions`() {
        var resolvedAction: String? = null
        var syncNowClicked = false

        val state = SyncStatusUiState(
            isOnline = true,
            pendingOperations = listOf(
                PendingSyncOperation(
                    operationId = "op-ui-1",
                    uid = "u1",
                    docPath = "users/u1/courses/c1",
                    collectionKey = "courses",
                    displayTitle = "CS-301",
                    operationKind = SyncOperationKind.UPDATE,
                    localSummary = "credits: 3.0",
                ),
            ),
            conflicts = listOf(
                SyncConflict(
                    conflictId = "conf-ui-1",
                    uid = "u1",
                    docPath = "users/u1/notes/n1",
                    collectionKey = "notes",
                    displayTitle = "Operating Systems",
                    reason = SyncConflictReason.CONCURRENT_REMOTE_MODIFICATION,
                    localSummary = "title: Local OS",
                    serverSummary = "title: Cloud OS",
                    supportsKeepBoth = true,
                ),
            ),
        )

        composeRule.setContent {
            UniMateTheme {
                SyncStatusDetailsDialog(
                    syncState = state,
                    onSyncNow = { syncNowClicked = true },
                    onResolveKeepLocal = { resolvedAction = "keep_local:$it" },
                    onResolveUseCloud = { resolvedAction = "use_cloud:$it" },
                    onResolveKeepBoth = { resolvedAction = "keep_both:$it" },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("sync_details_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_details_dialog")
            .performScrollToNode(hasTestTag("sync_conflict_card_conf-ui-1"))
        composeRule.onNodeWithTag("sync_conflict_card_conf-ui-1").assertIsDisplayed()

        composeRule.onNodeWithTag("conflict_keep_mine_conf-ui-1").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("keep_local:conf-ui-1", resolvedAction)

        composeRule.onNodeWithTag("conflict_use_cloud_conf-ui-1").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("use_cloud:conf-ui-1", resolvedAction)

        composeRule.onNodeWithTag("conflict_keep_both_conf-ui-1").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("keep_both:conf-ui-1", resolvedAction)

        composeRule.onNodeWithTag("sync_details_dialog")
            .performScrollToNode(hasTestTag("pending_sync_item_op-ui-1"))
        composeRule.onNodeWithTag("pending_sync_item_op-ui-1").assertIsDisplayed()

        composeRule.onNodeWithTag("sync_details_sync_now_button").performClick()
        composeRule.waitForIdle()
        assertTrue(syncNowClicked)
    }

    @Test
    fun `unsynced sign out warning dialog guards against silent data loss`() {
        var online by mutableStateOf(false)
        var syncClicked = false
        var stayClicked = false
        var signOutAnywayClicked = false

        composeRule.setContent {
            UniMateTheme {
                UnsyncedDataSignOutDialog(
                    pendingCount = 2,
                    conflictCount = 1,
                    isOnline = online,
                    isSyncing = false,
                    errorMessage = null,
                    onSyncAndSignOut = { syncClicked = true },
                    onStaySignedIn = { stayClicked = true },
                    onSignOutAnyway = { signOutAnywayClicked = true },
                )
            }
        }

        composeRule.onNodeWithTag("unsynced_signout_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("signout_warning_sync_button").assertIsNotEnabled()

        composeRule.runOnIdle { online = true }
        composeRule.onNodeWithTag("signout_warning_sync_button").assertIsEnabled()
        composeRule.onNodeWithTag("signout_warning_sync_button").performClick()
        assertTrue(syncClicked)

        composeRule.onNodeWithTag("signout_warning_stay_button").performClick()
        assertTrue(stayClicked)

        composeRule.onNodeWithTag("signout_warning_anyway_button").performClick()
        assertTrue(signOutAnywayClicked)
    }

    @Test
    fun `ai assistant tab displays offline banner when disconnected`() {
        var aiState by mutableStateOf(AiAssistantUiState(isOnline = false))

        composeRule.setContent {
            UniMateTheme {
                AiAssistantTabContent(
                    uiState = aiState,
                    studentContextSummary = "CS • 5th Semester",
                    onInputChanged = {},
                    onSendPrompt = {},
                    onClearChat = {},
                )
            }
        }

        composeRule.onNodeWithTag("ai_offline_banner").assertIsDisplayed()

        composeRule.runOnIdle {
            aiState = aiState.copy(isOnline = true)
        }
        composeRule.onNodeWithTag("ai_offline_banner").assertDoesNotExist()
    }
}
