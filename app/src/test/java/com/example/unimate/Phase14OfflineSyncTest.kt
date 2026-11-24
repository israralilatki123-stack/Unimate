package com.example.unimate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.unimate.data.model.ProfileSetupRequest
import com.example.unimate.data.model.ProfileUpdateRequest
import com.example.unimate.data.model.UserProfile
import com.example.unimate.data.repository.AuthRepository
import com.example.unimate.data.repository.AuthenticatedUser
import com.example.unimate.data.repository.GoogleSignInResult
import com.example.unimate.data.repository.PhoneCodeSendResult
import com.example.unimate.data.repository.PhoneVerifyResult
import com.example.unimate.data.repository.UserProfileRepository
import com.example.unimate.data.sync.DataStoreSyncMetadataStore
import com.example.unimate.data.sync.FirestoreSyncBackend
import com.example.unimate.data.sync.InMemorySyncMetadataStore
import com.example.unimate.data.sync.MutableNetworkMonitor
import com.example.unimate.data.sync.NetworkState
import com.example.unimate.data.sync.OfflineSyncCoordinator
import com.example.unimate.data.sync.PendingSyncOperation
import com.example.unimate.data.sync.PersistedSyncMetadata
import com.example.unimate.data.sync.RemoteDocumentSnapshot
import com.example.unimate.data.sync.SyncConflict
import com.example.unimate.data.sync.SyncConflictReason
import com.example.unimate.data.sync.SyncOperationKind
import com.example.unimate.data.sync.SyncPayloadCodec
import com.example.unimate.ui.ai.AiAssistantViewModel
import com.example.unimate.ui.ai.AiStudyEngine
import com.example.unimate.ui.ai.AiUiState
import com.example.unimate.ui.dashboard.ProfileDashboardViewModel
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class Phase14OfflineSyncTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `offline operations persist and automatically synchronize when network returns`() = runTest(testDispatcher) {
        val networkMonitor = MutableNetworkMonitor(NetworkState.OFFLINE)
        val metadataStore = InMemorySyncMetadataStore()
        val backend = FakeFirestoreSyncBackend()
        var clock = 1_000L

        val firstScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val coordinator = OfflineSyncCoordinator(
            networkMonitor = networkMonitor,
            metadataStore = metadataStore,
            backend = backend,
            scope = firstScope,
            nowProvider = { clock },
        )
        coordinator.setActiveUser("uid-1")
        advanceUntilIdle()

        val docPath = "users/uid-1/tasks/task-offline-1"
        coordinator.enqueuePendingOperation(
            PendingSyncOperation(
                operationId = "op-1",
                uid = "uid-1",
                docPath = docPath,
                collectionKey = "tasks",
                displayTitle = "Submit Lab 4",
                operationKind = SyncOperationKind.CREATE,
                localSummary = "Submit Lab 4",
                replayPayload = mapOf("title" to "Submit Lab 4", "completed" to false),
                createdAtMillis = clock,
            ),
        )
        advanceUntilIdle()

        assertFalse(coordinator.syncState.value.isOnline)
        assertEquals(1, coordinator.syncState.value.pendingCount)
        assertEquals(1, metadataStore.load("uid-1").pendingOperations.size)
        assertFalse(backend.docs.containsKey(docPath))
        firstScope.cancel()

        // Simulate app restart while still offline -> queued operation is restored
        val restartedScope = CoroutineScope(SupervisorJob() + testDispatcher)
        try {
            val restartedCoordinator = OfflineSyncCoordinator(
                networkMonitor = networkMonitor,
                metadataStore = metadataStore,
                backend = backend,
                scope = restartedScope,
                nowProvider = { clock },
            )
            restartedCoordinator.setActiveUser("uid-1")
            advanceUntilIdle()
            assertEquals(1, restartedCoordinator.syncState.value.pendingCount)

            // Network reconnects -> automatic flush uploads queued record once
            clock = 5_000L
            networkMonitor.setOnline(true)
            advanceUntilIdle()

            assertTrue(restartedCoordinator.syncState.value.isOnline)
            assertEquals(0, restartedCoordinator.syncState.value.pendingCount)
            assertEquals(0, restartedCoordinator.syncState.value.conflictCount)
            assertNotNull(restartedCoordinator.syncState.value.lastSyncedAtMillis)
            assertTrue(backend.docs.containsKey(docPath))
            assertEquals("Submit Lab 4", backend.docs[docPath]?.data?.get("title"))
            assertEquals(1, backend.setCallsCount)
        } finally {
            restartedScope.cancel()
        }
    }

    @Test
    fun `datastore sync metadata store persists pending operations conflicts and last sync timestamp`() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DataStoreSyncMetadataStore(context)
        val uid = "sync-store-uid-14"
        val metadata = PersistedSyncMetadata(
            pendingOperations = listOf(
                PendingSyncOperation(
                    operationId = "op-ds-1",
                    uid = uid,
                    docPath = "users/$uid/notes/n1",
                    collectionKey = "notes",
                    displayTitle = "Distributed Systems",
                    operationKind = SyncOperationKind.UPDATE,
                    baseUpdatedAtMillis = 4_000L,
                    localSummary = "title: Distributed Systems",
                    replayPayload = mapOf("title" to "Distributed Systems", "pinned" to true),
                    createdAtMillis = 4_500L,
                    retryCount = 2,
                ),
            ),
            conflicts = listOf(
                SyncConflict(
                    conflictId = "conf-ds-1",
                    uid = uid,
                    docPath = "users/$uid/courses/c1",
                    collectionKey = "courses",
                    displayTitle = "CS-401",
                    reason = SyncConflictReason.CONCURRENT_REMOTE_MODIFICATION,
                    localSummary = "credits: 4.0",
                    serverSummary = "credits: 3.0",
                    localPayload = mapOf("creditHours" to 4.0),
                    serverData = mapOf("creditHours" to 3.0),
                    detectedAtMillis = 5_000L,
                    supportsKeepBoth = true,
                ),
            ),
            lastSyncedAtMillis = 9_999L,
        )

        store.save(uid, metadata)
        val loaded = store.load(uid)

        assertEquals(1, loaded.pendingOperations.size)
        assertEquals("op-ds-1", loaded.pendingOperations[0].operationId)
        assertEquals(2, loaded.pendingOperations[0].retryCount)
        assertEquals(1, loaded.conflicts.size)
        assertEquals("conf-ds-1", loaded.conflicts[0].conflictId)
        assertEquals(9_999L, loaded.lastSyncedAtMillis)
    }

    @Test
    fun `deduplication blocks rapid double taps and reconciles repeated identical creates cleanly`() = runTest(testDispatcher) {
        val networkMonitor = MutableNetworkMonitor(NetworkState.ONLINE)
        val metadataStore = InMemorySyncMetadataStore()
        val backend = FakeFirestoreSyncBackend()
        var clock = 10_000L

        val docPath = "users/uid-1/notes/note-1"
        backend.docs[docPath] = RemoteDocumentSnapshot(
            exists = true,
            data = mapOf("title" to "Graph Theory", "content" to "BFS and DFS"),
            updatedAtMillis = 9_000L,
        )

        val coordScope = CoroutineScope(SupervisorJob() + testDispatcher)
        try {
            val coordinator = OfflineSyncCoordinator(
                networkMonitor = networkMonitor,
                metadataStore = metadataStore,
                backend = backend,
                scope = coordScope,
                nowProvider = { clock },
            )
            coordinator.setActiveUser("uid-1")
            advanceUntilIdle()

            // In-flight dedup guard
            assertFalse(coordinator.isDuplicateInFlight("note:$docPath", windowMs = 10_000L))
            coordinator.markInFlight("note:$docPath")
            assertTrue(coordinator.isDuplicateInFlight("note:$docPath", windowMs = 10_000L))
            clock += 11_000L
            assertFalse(coordinator.isDuplicateInFlight("note:$docPath", windowMs = 10_000L))

            // Repeated identical CREATE (e.g. from retry after ambiguous timeout) should not duplicate or conflict
            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-dup-create",
                    uid = "uid-1",
                    docPath = docPath,
                    collectionKey = "notes",
                    displayTitle = "Graph Theory",
                    operationKind = SyncOperationKind.CREATE,
                    localSummary = "Graph Theory",
                    replayPayload = mapOf("title" to "Graph Theory", "content" to "BFS and DFS"),
                ),
            )
            advanceUntilIdle()

            coordinator.syncNowAwait(triggerBanner = true)
            assertEquals(0, coordinator.syncState.value.pendingCount)
            assertEquals(0, coordinator.syncState.value.conflictCount)
            assertEquals(0, backend.setCallsCount)
            assertEquals(0, backend.copyCallsCount)
        } finally {
            coordScope.cancel()
        }
    }

    @Test
    fun `create followed by delete offline cancels both operations with zero cloud writes`() = runTest(testDispatcher) {
        val networkMonitor = MutableNetworkMonitor(NetworkState.OFFLINE)
        val metadataStore = InMemorySyncMetadataStore()
        val backend = FakeFirestoreSyncBackend()

        val coordScope = CoroutineScope(SupervisorJob() + testDispatcher)
        try {
            val coordinator = OfflineSyncCoordinator(
                networkMonitor = networkMonitor,
                metadataStore = metadataStore,
                backend = backend,
                scope = coordScope,
            )
            coordinator.setActiveUser("uid-1")
            advanceUntilIdle()

            val docPath = "users/uid-1/reminders/rem-temp-1"
            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-create",
                    uid = "uid-1",
                    docPath = docPath,
                    collectionKey = "reminders",
                    displayTitle = "Temp Reminder",
                    operationKind = SyncOperationKind.CREATE,
                    replayPayload = mapOf("title" to "Temp Reminder"),
                ),
            )
            advanceUntilIdle()
            assertEquals(1, coordinator.syncState.value.pendingCount)

            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-delete",
                    uid = "uid-1",
                    docPath = docPath,
                    collectionKey = "reminders",
                    displayTitle = "Temp Reminder",
                    operationKind = SyncOperationKind.DELETE,
                    replayPayload = mapOf(OfflineSyncCoordinator.PENDING_DELETE_MARKER to true),
                ),
            )
            advanceUntilIdle()

            assertEquals(0, coordinator.syncState.value.pendingCount)
            assertEquals(0, metadataStore.load("uid-1").pendingOperations.size)

            networkMonitor.setOnline(true)
            advanceUntilIdle()
            assertEquals(0, backend.setCallsCount)
            assertEquals(0, backend.deleteCallsCount)
        } finally {
            coordScope.cancel()
        }
    }

    @Test
    fun `transient sync error retains pending operation with retry count and succeeds on retry`() = runTest(testDispatcher) {
        val networkMonitor = MutableNetworkMonitor(NetworkState.ONLINE)
        val metadataStore = InMemorySyncMetadataStore()
        val backend = FakeFirestoreSyncBackend().apply {
            throwOnFetch = FirebaseFirestoreException(
                "Temporarily unavailable",
                FirebaseFirestoreException.Code.UNAVAILABLE,
            )
        }

        val coordScope = CoroutineScope(SupervisorJob() + testDispatcher)
        try {
            val coordinator = OfflineSyncCoordinator(
                networkMonitor = networkMonitor,
                metadataStore = metadataStore,
                backend = backend,
                scope = coordScope,
            )
            coordinator.setActiveUser("uid-1")
            advanceUntilIdle()

            val docPath = "users/uid-1/courses/course-1"
            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-retry-1",
                    uid = "uid-1",
                    docPath = docPath,
                    collectionKey = "courses",
                    displayTitle = "Algorithms",
                    operationKind = SyncOperationKind.CREATE,
                    replayPayload = mapOf("courseCode" to "CS-301", "courseName" to "Algorithms"),
                ),
            )
            advanceUntilIdle()

            coordinator.syncNowAwait(triggerBanner = true)
            assertEquals(1, coordinator.syncState.value.pendingCount)
            assertEquals(1, coordinator.syncState.value.pendingOperations.first().retryCount)
            assertNotNull(coordinator.syncState.value.lastSyncError)

            // Retry after backend recovers
            backend.throwOnFetch = null
            coordinator.syncNowAwait(triggerBanner = true)
            assertEquals(0, coordinator.syncState.value.pendingCount)
            assertNull(coordinator.syncState.value.lastSyncError)
            assertTrue(backend.docs.containsKey(docPath))
        } finally {
            coordScope.cancel()
        }
    }

    @Test
    fun `concurrent modification conflict supports keep mine use cloud and keep both`() = runTest(testDispatcher) {
        val networkMonitor = MutableNetworkMonitor(NetworkState.OFFLINE)
        val metadataStore = InMemorySyncMetadataStore()
        val backend = FakeFirestoreSyncBackend()
        val docPath = "users/uid-1/notes/note-conflict-1"

        backend.docs[docPath] = RemoteDocumentSnapshot(
            exists = true,
            data = mapOf("title" to "Cloud Edited Title", "content" to "Cloud version"),
            updatedAtMillis = 5_000L,
        )

        val coordScope = CoroutineScope(SupervisorJob() + testDispatcher)
        try {
            val coordinator = OfflineSyncCoordinator(
                networkMonitor = networkMonitor,
                metadataStore = metadataStore,
                backend = backend,
                scope = coordScope,
            )
            coordinator.setActiveUser("uid-1")
            advanceUntilIdle()

            // Local user edited offline based on older timestamp 1_000L
            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-conflict-1",
                    uid = "uid-1",
                    docPath = docPath,
                    collectionKey = "notes",
                    displayTitle = "Offline Note Edit",
                    operationKind = SyncOperationKind.UPDATE,
                    baseUpdatedAtMillis = 1_000L,
                    localSummary = "title: Local Edited Title",
                    replayPayload = mapOf("title" to "Local Edited Title", "content" to "Local version"),
                    baseSnapshotData = mapOf("courseName" to "CS-301"),
                ),
            )
            advanceUntilIdle()

            // Reconnect triggers sync -> conflict detected
            networkMonitor.setOnline(true)
            advanceUntilIdle()

            assertEquals(0, coordinator.syncState.value.pendingCount)
            assertEquals(1, coordinator.syncState.value.conflictCount)
            val conflict = coordinator.syncState.value.conflicts.first()
            assertEquals(SyncConflictReason.CONCURRENT_REMOTE_MODIFICATION, conflict.reason)
            assertTrue(conflict.supportsKeepBoth)

            // Option A: Save Both -> creates copy doc, preserves cloud doc, clears conflict
            coordinator.resolveKeepBothAwait(conflict.conflictId)
            assertEquals(0, coordinator.syncState.value.conflictCount)
            assertEquals(1, backend.copyCallsCount)
            assertEquals("Cloud Edited Title", backend.docs[docPath]?.data?.get("title"))
            val copyDoc = backend.docs.entries.firstOrNull { it.key.contains("copy_") }
            assertNotNull(copyDoc)
            assertTrue((copyDoc?.value?.data?.get("title") as? String)?.endsWith("(Copy)") == true)

            // Option B: Keep Mine -> overwrites cloud doc
            backend.docs[docPath] = RemoteDocumentSnapshot(
                exists = true,
                data = mapOf("title" to "Remote V2"),
                updatedAtMillis = 8_000L,
            )
            networkMonitor.setOnline(false)
            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-conflict-2",
                    uid = "uid-1",
                    docPath = docPath,
                    collectionKey = "notes",
                    displayTitle = "Offline Note Edit 2",
                    operationKind = SyncOperationKind.UPDATE,
                    baseUpdatedAtMillis = 2_000L,
                    localSummary = "title: Keep Mine Title",
                    replayPayload = mapOf("title" to "Keep Mine Title"),
                ),
            )
            advanceUntilIdle()
            networkMonitor.setOnline(true)
            advanceUntilIdle()
            val conflict2 = coordinator.syncState.value.conflicts.first()
            coordinator.resolveKeepLocalAwait(conflict2.conflictId)
            assertEquals(0, coordinator.syncState.value.conflictCount)
            assertEquals("Keep Mine Title", backend.docs[docPath]?.data?.get("title"))

            // Option C: Use Cloud -> discards local change without mutating server
            backend.docs[docPath] = RemoteDocumentSnapshot(
                exists = true,
                data = mapOf("title" to "Remote V3"),
                updatedAtMillis = 12_000L,
            )
            networkMonitor.setOnline(false)
            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-conflict-3",
                    uid = "uid-1",
                    docPath = docPath,
                    collectionKey = "notes",
                    displayTitle = "Offline Note Edit 3",
                    operationKind = SyncOperationKind.UPDATE,
                    baseUpdatedAtMillis = 9_000L,
                    localSummary = "title: Discarded Local",
                    replayPayload = mapOf("title" to "Discarded Local"),
                ),
            )
            advanceUntilIdle()
            networkMonitor.setOnline(true)
            advanceUntilIdle()
            val conflict3 = coordinator.syncState.value.conflicts.first()
            coordinator.resolveUseCloudAwait(conflict3.conflictId)
            assertEquals(0, coordinator.syncState.value.conflictCount)
            assertEquals("Remote V3", backend.docs[docPath]?.data?.get("title"))
        } finally {
            coordScope.cancel()
        }
    }

    @Test
    fun `remote deletion while edited offline raises conflict and keep mine recreates document`() = runTest(testDispatcher) {
        val networkMonitor = MutableNetworkMonitor(NetworkState.OFFLINE)
        val metadataStore = InMemorySyncMetadataStore()
        val backend = FakeFirestoreSyncBackend()
        val docPath = "users/uid-1/assignments/assign-9"

        val coordScope = CoroutineScope(SupervisorJob() + testDispatcher)
        try {
            val coordinator = OfflineSyncCoordinator(
                networkMonitor = networkMonitor,
                metadataStore = metadataStore,
                backend = backend,
                scope = coordScope,
            )
            coordinator.setActiveUser("uid-1")
            advanceUntilIdle()

            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-del-edit",
                    uid = "uid-1",
                    docPath = docPath,
                    collectionKey = "assignments",
                    displayTitle = "OS Essay",
                    operationKind = SyncOperationKind.UPDATE,
                    baseUpdatedAtMillis = 1_000L,
                    localSummary = "completed: true",
                    replayPayload = mapOf("completed" to true),
                    baseSnapshotData = mapOf("title" to "OS Essay", "courseName" to "CS-320"),
                ),
            )
            advanceUntilIdle()

            networkMonitor.setOnline(true)
            advanceUntilIdle()

            val conflict = coordinator.syncState.value.conflicts.single()
            assertEquals(SyncConflictReason.REMOTE_DELETED_LOCAL_EDITED, conflict.reason)
            assertFalse(conflict.supportsKeepBoth)

            coordinator.resolveKeepLocalAwait(conflict.conflictId)
            assertEquals(0, coordinator.syncState.value.conflictCount)
            assertTrue(backend.docs.containsKey(docPath))
            assertEquals("OS Essay", backend.docs[docPath]?.data?.get("title"))
            assertEquals(true, backend.docs[docPath]?.data?.get("completed"))
        } finally {
            coordScope.cancel()
        }
    }

    @Test
    fun `ai assistant preserves prompt draft offline and recovers when online`() = runTest(testDispatcher) {
        val networkMonitor = MutableNetworkMonitor(NetworkState.OFFLINE)
        val engine = object : AiStudyEngine {
            override fun generateStream(prompt: String): Flow<String> = flowOf("Answer: use dynamic programming.")
        }
        val vm = AiAssistantViewModel(studyEngine = engine, networkMonitor = networkMonitor)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isOnline)
        vm.onInputChanged("Explain knapsack DP")
        vm.sendPrompt()
        advanceUntilIdle()

        // Draft preserved, no message sent, error shown
        assertEquals("Explain knapsack DP", vm.uiState.value.inputText)
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertTrue(vm.uiState.value.inferenceState is AiUiState.Error)

        // Online again -> sends cleanly
        networkMonitor.setOnline(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isOnline)

        vm.sendPrompt()
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.inputText)
        assertTrue(vm.uiState.value.inferenceState is AiUiState.Success)
        assertEquals(2, vm.uiState.value.messages.size)
    }

    @Test
    fun `sign out warns when unsynchronized changes exist and supports sync-and-sign-out or forced sign out`() = runTest(testDispatcher) {
        val networkMonitor = MutableNetworkMonitor(NetworkState.OFFLINE)
        val metadataStore = InMemorySyncMetadataStore()
        val backend = FakeFirestoreSyncBackend()
        val coordScope = CoroutineScope(SupervisorJob() + testDispatcher)
        try {
            val coordinator = OfflineSyncCoordinator(
                networkMonitor = networkMonitor,
                metadataStore = metadataStore,
                backend = backend,
                scope = coordScope,
            )
            coordinator.setActiveUser("uid-1")
            advanceUntilIdle()

            val authRepo = FakeSignOutAuthRepository()
            val profileRepo = FakeSimpleProfileRepository()
            val vm = ProfileDashboardViewModel(
                uid = "uid-1",
                authUser = AuthenticatedUser(
                    uid = "uid-1",
                    email = "student@unimate.edu",
                    displayName = "Alex Student",
                    photoUrl = null,
                ),
                userProfileRepository = profileRepo,
                authRepository = authRepo,
                syncCoordinator = coordinator,
            )
            advanceUntilIdle()

            // Enqueue offline pending write
            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-signout-1",
                    uid = "uid-1",
                    docPath = "users/uid-1/tasks/t1",
                    collectionKey = "tasks",
                    displayTitle = "Read Chapter 5",
                    operationKind = SyncOperationKind.CREATE,
                    replayPayload = mapOf("title" to "Read Chapter 5"),
                ),
            )
            advanceUntilIdle()

            // Requesting sign out opens warning instead of immediately signing out
            vm.requestSignOut()
            assertTrue(vm.formState.value.showUnsyncedSignOutWarning)
            assertEquals(1, vm.formState.value.unsyncedPendingCount)
            assertEquals(0, authRepo.signOutCalls)

            // Sync & sign out while offline is blocked with a clear error message
            vm.syncAndSignOut()
            assertNotNull(vm.formState.value.signOutSyncError)
            assertEquals(0, authRepo.signOutCalls)

            // Back online -> syncAndSignOut flushes queue and then signs out
            networkMonitor.setOnline(true)
            advanceUntilIdle()
            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-signout-2",
                    uid = "uid-1",
                    docPath = "users/uid-1/tasks/t2",
                    collectionKey = "tasks",
                    displayTitle = "Submit Homework",
                    operationKind = SyncOperationKind.CREATE,
                    replayPayload = mapOf("title" to "Submit Homework"),
                ),
            )
            advanceUntilIdle()
            vm.requestSignOut()
            assertTrue(vm.formState.value.showUnsyncedSignOutWarning)

            vm.syncAndSignOut()
            advanceUntilIdle()
            assertFalse(vm.formState.value.showUnsyncedSignOutWarning)
            assertEquals(1, authRepo.signOutCalls)
            assertEquals(0, coordinator.syncState.value.pendingCount)

            // Sign out anyway path in a fresh session
            val vm2 = ProfileDashboardViewModel(
                uid = "uid-1",
                authUser = AuthenticatedUser(
                    uid = "uid-1",
                    email = "student@unimate.edu",
                    displayName = "Alex Student",
                    photoUrl = null,
                ),
                userProfileRepository = profileRepo,
                authRepository = authRepo,
                syncCoordinator = coordinator,
            )
            advanceUntilIdle()

            networkMonitor.setOnline(false)
            coordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = "op-signout-3",
                    uid = "uid-1",
                    docPath = "users/uid-1/tasks/t3",
                    collectionKey = "tasks",
                    displayTitle = "Unsynced Item",
                    operationKind = SyncOperationKind.CREATE,
                    replayPayload = mapOf("title" to "Unsynced Item"),
                ),
            )
            advanceUntilIdle()
            vm2.requestSignOut()
            assertTrue(vm2.formState.value.showUnsyncedSignOutWarning)
            vm2.confirmSignOutAnyway()
            advanceUntilIdle()
            assertFalse(vm2.formState.value.showUnsyncedSignOutWarning)
            assertEquals(2, authRepo.signOutCalls)
        } finally {
            coordScope.cancel()
        }
    }
}

private class FakeFirestoreSyncBackend : FirestoreSyncBackend {
    val docs = linkedMapOf<String, RemoteDocumentSnapshot>()
    var setCallsCount = 0
    var updateCallsCount = 0
    var deleteCallsCount = 0
    var copyCallsCount = 0
    var throwOnFetch: Throwable? = null

    override suspend fun fetchServerDocument(docPath: String): RemoteDocumentSnapshot {
        throwOnFetch?.let { throw it }
        return docs[docPath] ?: RemoteDocumentSnapshot(exists = false)
    }

    override suspend fun setDocument(docPath: String, payload: Map<String, Any>, merge: Boolean) {
        setCallsCount++
        val sanitized = SyncPayloadCodec.encodeForStorage(payload)
        val merged = if (merge && docs[docPath]?.exists == true) {
            docs[docPath]!!.data + sanitized
        } else {
            sanitized
        }
        docs[docPath] = RemoteDocumentSnapshot(
            exists = true,
            data = merged,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun updateDocument(docPath: String, payload: Map<String, Any>) {
        updateCallsCount++
        val existing = docs[docPath] ?: throw FirebaseFirestoreException(
            "Not found",
            FirebaseFirestoreException.Code.NOT_FOUND,
        )
        val sanitized = SyncPayloadCodec.encodeForStorage(payload)
        docs[docPath] = existing.copy(
            data = existing.data + sanitized,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun deleteDocument(docPath: String) {
        deleteCallsCount++
        docs.remove(docPath)
    }

    override suspend fun createCopyDocument(collectionPath: String, newDocId: String, payload: Map<String, Any>) {
        copyCallsCount++
        val sanitized = SyncPayloadCodec.encodeForStorage(payload)
        docs["$collectionPath/$newDocId"] = RemoteDocumentSnapshot(
            exists = true,
            data = sanitized,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun waitForPendingWrites() = Unit
}

private class FakeSignOutAuthRepository : AuthRepository {
    var signOutCalls = 0
    override val authStateFlow: Flow<AuthenticatedUser?> = flowOf(null)
    override fun currentAuthenticatedUser(): AuthenticatedUser? = null
    override suspend fun attemptAutoSignIn(): GoogleSignInResult = GoogleSignInResult.Cancelled
    override suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult =
        GoogleSignInResult.Cancelled
    override suspend fun sendPhoneVerificationCode(
        activityContext: Context,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
        linkToCurrentUser: Boolean,
    ): PhoneCodeSendResult = PhoneCodeSendResult.Error("Unused")
    override suspend fun verifyPhoneOtp(
        verificationId: String,
        smsCode: String,
        linkToCurrentUser: Boolean,
    ): PhoneVerifyResult = PhoneVerifyResult.Error("Unused")

    override suspend fun signOut() {
        signOutCalls++
    }
}

private class FakeSimpleProfileRepository : UserProfileRepository {
    private val profileFlow = MutableStateFlow<UserProfile?>(
        UserProfile(
            uid = "uid-1",
            fullName = "Alex Student",
            email = "student@unimate.edu",
            university = "UniMate University",
            department = "Computer Science",
            semester = "5th Semester",
        ),
    )

    override fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        profileFlow.collect { emit(it) }
    }

    override suspend fun createUserProfile(request: ProfileSetupRequest): Result<Unit> = Result.success(Unit)
    override suspend fun updateUserProfile(uid: String, request: ProfileUpdateRequest): Result<Unit> = Result.success(Unit)
    override suspend fun updatePhoneNumber(uid: String, phoneNumber: String): Result<Unit> = Result.success(Unit)
}
