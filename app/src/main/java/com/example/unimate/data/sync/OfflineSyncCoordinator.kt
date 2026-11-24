package com.example.unimate.data.sync

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class RemoteDocumentSnapshot(
    val exists: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val updatedAtMillis: Long = 0L,
)

interface FirestoreSyncBackend {
    suspend fun fetchServerDocument(docPath: String): RemoteDocumentSnapshot
    suspend fun setDocument(docPath: String, payload: Map<String, Any>, merge: Boolean)
    suspend fun updateDocument(docPath: String, payload: Map<String, Any>)
    suspend fun deleteDocument(docPath: String)
    suspend fun createCopyDocument(collectionPath: String, newDocId: String, payload: Map<String, Any>)
    suspend fun waitForPendingWrites()
}

class FirebaseFirestoreSyncBackend(
    private val db: FirebaseFirestore,
) : FirestoreSyncBackend {

    override suspend fun fetchServerDocument(docPath: String): RemoteDocumentSnapshot {
        val snap = db.document(docPath).get(Source.SERVER).await()
        if (!snap.exists()) {
            return RemoteDocumentSnapshot(exists = false)
        }
        val rawData = snap.data.orEmpty()
        val sanitized = SyncPayloadCodec.encodeForStorage(rawData)
        val updatedMillis = extractTimestampMillis(rawData["updatedAt"])
            .takeIf { it > 0L }
            ?: (rawData["clientUpdatedAtMillis"] as? Number)?.toLong()
            ?: extractTimestampMillis(rawData["createdAt"])
        return RemoteDocumentSnapshot(
            exists = true,
            data = sanitized,
            updatedAtMillis = updatedMillis,
        )
    }

    override suspend fun setDocument(docPath: String, payload: Map<String, Any>, merge: Boolean) {
        val ref = db.document(docPath)
        if (merge) {
            ref.set(payload, SetOptions.merge()).await()
        } else {
            ref.set(payload).await()
        }
    }

    override suspend fun updateDocument(docPath: String, payload: Map<String, Any>) {
        db.document(docPath).update(payload).await()
    }

    override suspend fun deleteDocument(docPath: String) {
        db.document(docPath).delete().await()
    }

    override suspend fun createCopyDocument(
        collectionPath: String,
        newDocId: String,
        payload: Map<String, Any>,
    ) {
        db.collection(collectionPath).document(newDocId).set(payload).await()
    }

    override suspend fun waitForPendingWrites() {
        db.waitForPendingWrites().await()
    }

    private fun extractTimestampMillis(value: Any?): Long {
        return when (value) {
            is Timestamp -> value.toDate().time
            is Date -> value.time
            is Number -> value.toLong()
            else -> 0L
        }
    }
}

interface SyncCoordinator {
    val syncState: StateFlow<SyncStatusUiState>
    val isOnline: Boolean

    fun setActiveUser(uid: String?)

    fun reportCollectionSnapshot(
        collectionKey: String,
        uid: String,
        docs: Map<String, Map<String, Any?>>,
        updatedAtMillisByPath: Map<String, Long>,
        isFromCache: Boolean,
        hasPendingWrites: Boolean,
    )

    fun getKnownDocSnapshot(docPath: String): Pair<Long, Map<String, Any?>>?

    fun isDuplicateInFlight(dedupKey: String, windowMs: Long = 10_000L): Boolean
    fun markInFlight(dedupKey: String)
    fun clearInFlight(dedupKey: String)

    fun enqueuePendingOperation(op: PendingSyncOperation)
    fun markOperationSynced(operationId: String, docPath: String)
    fun markOperationFailed(operationId: String, error: Throwable)
    fun markOperationFailedByPath(docPath: String, error: Throwable)

    fun syncNow()
    fun resolveKeepLocal(conflictId: String)
    fun resolveUseCloud(conflictId: String)
    fun resolveKeepBoth(conflictId: String)
    fun dismissJustSyncedBanner()
    fun clearSyncError()

    fun formatActionMessage(
        onlineMessage: String,
        offlineMessage: String = "$onlineMessage (saved offline — will sync when online)",
    ): String
}

object NoOpSyncCoordinator : SyncCoordinator {
    private val _syncState = MutableStateFlow(SyncStatusUiState())
    override val syncState: StateFlow<SyncStatusUiState> = _syncState.asStateFlow()
    override val isOnline: Boolean = true

    override fun setActiveUser(uid: String?) = Unit

    override fun reportCollectionSnapshot(
        collectionKey: String,
        uid: String,
        docs: Map<String, Map<String, Any?>>,
        updatedAtMillisByPath: Map<String, Long>,
        isFromCache: Boolean,
        hasPendingWrites: Boolean,
    ) = Unit

    override fun getKnownDocSnapshot(docPath: String): Pair<Long, Map<String, Any?>>? = null
    override fun isDuplicateInFlight(dedupKey: String, windowMs: Long): Boolean = false
    override fun markInFlight(dedupKey: String) = Unit
    override fun clearInFlight(dedupKey: String) = Unit
    override fun enqueuePendingOperation(op: PendingSyncOperation) = Unit
    override fun markOperationSynced(operationId: String, docPath: String) = Unit
    override fun markOperationFailed(operationId: String, error: Throwable) = Unit
    override fun markOperationFailedByPath(docPath: String, error: Throwable) = Unit
    override fun syncNow() = Unit
    override fun resolveKeepLocal(conflictId: String) = Unit
    override fun resolveUseCloud(conflictId: String) = Unit
    override fun resolveKeepBoth(conflictId: String) = Unit
    override fun dismissJustSyncedBanner() = Unit
    override fun clearSyncError() = Unit
    override fun formatActionMessage(onlineMessage: String, offlineMessage: String): String = onlineMessage
}

class OfflineSyncCoordinator(
    private val networkMonitor: NetworkMonitor,
    private val metadataStore: SyncMetadataStore,
    private val backend: FirestoreSyncBackend,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : SyncCoordinator {

    private val tag = "OfflineSyncCoordinator"
    private val stateMutex = Mutex()
    private val syncExecutionMutex = Mutex()

    private val _syncState = MutableStateFlow(
        SyncStatusUiState(isOnline = networkMonitor.isOnline),
    )
    override val syncState: StateFlow<SyncStatusUiState> = _syncState.asStateFlow()

    override val isOnline: Boolean
        get() = networkMonitor.isOnline

    private var activeUid: String? = null
    private val lastKnownServerDocs = ConcurrentHashMap<String, Pair<Long, Map<String, Any?>>>()
    private val collectionMetadata = ConcurrentHashMap<String, CollectionMetadataReport>()
    private val inFlightDedupKeys = ConcurrentHashMap<String, Long>()
    private var previousNetworkState: NetworkState = networkMonitor.networkState.value

    init {
        scope.launch {
            networkMonitor.networkState.collectLatest { state ->
                val online = state == NetworkState.ONLINE
                val transitionedToOnline = previousNetworkState == NetworkState.OFFLINE && online
                previousNetworkState = state
                _syncState.update { it.copy(isOnline = online) }
                if (transitionedToOnline) {
                    val hasPending = _syncState.value.pendingOperations.isNotEmpty() ||
                        _syncState.value.hasSdkPendingWrites
                    if (hasPending) {
                        syncNowInternal(triggerBanner = true)
                    }
                }
            }
        }
    }

    override fun setActiveUser(uid: String?) {
        if (activeUid == uid) return
        activeUid = uid
        lastKnownServerDocs.clear()
        collectionMetadata.clear()
        inFlightDedupKeys.clear()
        if (uid.isNullOrBlank()) {
            _syncState.value = SyncStatusUiState(isOnline = isOnline)
            return
        }
        scope.launch {
            val loaded = stateMutex.withLock {
                metadataStore.load(uid)
            }
            if (activeUid == uid) {
                _syncState.update {
                    it.copy(
                        isOnline = isOnline,
                        pendingOperations = loaded.pendingOperations,
                        conflicts = loaded.conflicts,
                        lastSyncedAtMillis = loaded.lastSyncedAtMillis,
                        lastSyncError = null,
                        showJustSyncedBanner = false,
                    )
                }
                if (isOnline && loaded.pendingOperations.isNotEmpty()) {
                    syncNowInternal(triggerBanner = false)
                }
            }
        }
    }

    override fun reportCollectionSnapshot(
        collectionKey: String,
        uid: String,
        docs: Map<String, Map<String, Any?>>,
        updatedAtMillisByPath: Map<String, Long>,
        isFromCache: Boolean,
        hasPendingWrites: Boolean,
    ) {
        if (activeUid != uid) {
            setActiveUser(uid)
        }
        collectionMetadata[collectionKey] = CollectionMetadataReport(
            isFromCache = isFromCache,
            hasPendingWrites = hasPendingWrites,
            observedAtMillis = nowProvider(),
        )
        if (!isFromCache && !hasPendingWrites) {
            docs.forEach { (path, data) ->
                val updatedAt = updatedAtMillisByPath[path] ?: 0L
                lastKnownServerDocs[path] = updatedAt to SyncPayloadCodec.encodeForStorage(data)
            }
        }

        scope.launch {
            reconcileWithSnapshot(
                collectionKey = collectionKey,
                uid = uid,
                docs = docs,
                updatedAtMillisByPath = updatedAtMillisByPath,
                isFromCache = isFromCache,
                hasPendingWrites = hasPendingWrites,
            )
        }
    }

    private suspend fun reconcileWithSnapshot(
        collectionKey: String,
        uid: String,
        docs: Map<String, Map<String, Any?>>,
        updatedAtMillisByPath: Map<String, Long>,
        isFromCache: Boolean,
        hasPendingWrites: Boolean,
    ) {
        var didSyncAny = false
        stateMutex.withLock {
            if (activeUid != uid) return
            val currentOps = _syncState.value.pendingOperations.toMutableList()
            val currentConflicts = _syncState.value.conflicts.toMutableList()
            var opsModified = false
            var conflictsModified = false

            if (!isFromCache && !hasPendingWrites) {
                val iterator = currentOps.iterator()
                while (iterator.hasNext()) {
                    val op = iterator.next()
                    if (op.collectionKey != collectionKey || op.uid != uid) continue
                    val serverData = docs[op.docPath]
                    val serverUpdatedAt = updatedAtMillisByPath[op.docPath] ?: 0L

                    when (op.operationKind) {
                        SyncOperationKind.DELETE -> {
                            if (serverData == null) {
                                iterator.remove()
                                opsModified = true
                                didSyncAny = true
                            } else if (op.baseUpdatedAtMillis > 0L && serverUpdatedAt > op.baseUpdatedAtMillis) {
                                val conflict = SyncConflict(
                                    conflictId = UUID.randomUUID().toString(),
                                    uid = uid,
                                    docPath = op.docPath,
                                    collectionKey = op.collectionKey,
                                    displayTitle = op.displayTitle,
                                    reason = SyncConflictReason.CONCURRENT_REMOTE_MODIFICATION,
                                    localSummary = "Deleted offline",
                                    serverSummary = SyncPayloadCodec.summarizeFields(serverData),
                                    localPayload = mapOf(PENDING_DELETE_MARKER to true),
                                    fullCreatePayload = op.baseSnapshotData,
                                    serverData = SyncPayloadCodec.encodeForStorage(serverData),
                                    detectedAtMillis = nowProvider(),
                                    supportsKeepBoth = false,
                                )
                                addOrReplaceConflict(currentConflicts, conflict)
                                iterator.remove()
                                opsModified = true
                                conflictsModified = true
                            }
                        }

                        SyncOperationKind.CREATE -> {
                            if (serverData != null) {
                                val sanitizedServer = SyncPayloadCodec.encodeForStorage(serverData)
                                if (!SyncPayloadCodec.payloadConflictsWithServer(op.replayPayload, sanitizedServer)) {
                                    iterator.remove()
                                    opsModified = true
                                    didSyncAny = true
                                } else if (op.baseUpdatedAtMillis > 0L && serverUpdatedAt > op.baseUpdatedAtMillis) {
                                    val conflict = SyncConflict(
                                        conflictId = UUID.randomUUID().toString(),
                                        uid = uid,
                                        docPath = op.docPath,
                                        collectionKey = op.collectionKey,
                                        displayTitle = op.displayTitle,
                                        reason = SyncConflictReason.CONCURRENT_REMOTE_MODIFICATION,
                                        localSummary = op.localSummary,
                                        serverSummary = SyncPayloadCodec.summarizeFields(sanitizedServer),
                                        localPayload = op.replayPayload,
                                        fullCreatePayload = op.replayPayload,
                                        serverData = sanitizedServer,
                                        detectedAtMillis = nowProvider(),
                                        supportsKeepBoth = !isSingletonPath(op.docPath, uid),
                                    )
                                    addOrReplaceConflict(currentConflicts, conflict)
                                    iterator.remove()
                                    opsModified = true
                                    conflictsModified = true
                                }
                            }
                        }

                        SyncOperationKind.UPDATE, SyncOperationKind.BATCH_WRITE -> {
                            if (serverData == null) {
                                if (!isSingletonPath(op.docPath, uid)) {
                                    val conflict = SyncConflict(
                                        conflictId = UUID.randomUUID().toString(),
                                        uid = uid,
                                        docPath = op.docPath,
                                        collectionKey = op.collectionKey,
                                        displayTitle = op.displayTitle,
                                        reason = SyncConflictReason.REMOTE_DELETED_LOCAL_EDITED,
                                        localSummary = op.localSummary,
                                        serverSummary = "Deleted in Cloud",
                                        localPayload = op.replayPayload,
                                        fullCreatePayload = op.baseSnapshotData + op.replayPayload,
                                        serverData = emptyMap(),
                                        detectedAtMillis = nowProvider(),
                                        supportsKeepBoth = false,
                                    )
                                    addOrReplaceConflict(currentConflicts, conflict)
                                    iterator.remove()
                                    opsModified = true
                                    conflictsModified = true
                                }
                            } else {
                                val sanitizedServer = SyncPayloadCodec.encodeForStorage(serverData)
                                if (!SyncPayloadCodec.payloadConflictsWithServer(op.replayPayload, sanitizedServer)) {
                                    iterator.remove()
                                    opsModified = true
                                    didSyncAny = true
                                } else if (op.baseUpdatedAtMillis > 0L && serverUpdatedAt > op.baseUpdatedAtMillis) {
                                    val conflict = SyncConflict(
                                        conflictId = UUID.randomUUID().toString(),
                                        uid = uid,
                                        docPath = op.docPath,
                                        collectionKey = op.collectionKey,
                                        displayTitle = op.displayTitle,
                                        reason = SyncConflictReason.CONCURRENT_REMOTE_MODIFICATION,
                                        localSummary = op.localSummary,
                                        serverSummary = SyncPayloadCodec.summarizeFields(sanitizedServer),
                                        localPayload = op.replayPayload,
                                        fullCreatePayload = op.baseSnapshotData + op.replayPayload,
                                        serverData = sanitizedServer,
                                        detectedAtMillis = nowProvider(),
                                        supportsKeepBoth = !isSingletonPath(op.docPath, uid),
                                    )
                                    addOrReplaceConflict(currentConflicts, conflict)
                                    iterator.remove()
                                    opsModified = true
                                    conflictsModified = true
                                }
                            }
                        }
                    }
                }
            }

            val anyCache = collectionMetadata.values.any { it.isFromCache }
            val anyPending = collectionMetadata.values.any { it.hasPendingWrites }
            val updatedLastSynced = if (!isFromCache && !hasPendingWrites) {
                nowProvider()
            } else {
                _syncState.value.lastSyncedAtMillis
            }

            if (opsModified || conflictsModified) {
                persistMetadata(
                    uid = uid,
                    ops = currentOps,
                    conflicts = currentConflicts,
                    lastSyncedAtMillis = updatedLastSynced,
                )
            }

            _syncState.update {
                it.copy(
                    isOnline = isOnline,
                    isUsingCachedData = anyCache,
                    hasSdkPendingWrites = anyPending,
                    pendingOperations = currentOps,
                    conflicts = currentConflicts,
                    lastSyncedAtMillis = updatedLastSynced,
                )
            }
        }
        if (didSyncAny && _syncState.value.pendingOperations.isEmpty() && _syncState.value.conflicts.isEmpty()) {
            triggerTemporaryJustSyncedBanner()
        }
    }

    override fun getKnownDocSnapshot(docPath: String): Pair<Long, Map<String, Any?>>? {
        return lastKnownServerDocs[docPath]
    }

    override fun isDuplicateInFlight(dedupKey: String, windowMs: Long): Boolean {
        val now = nowProvider()
        val timestamp = inFlightDedupKeys[dedupKey] ?: return false
        if (now - timestamp <= windowMs) {
            return true
        }
        inFlightDedupKeys.remove(dedupKey, timestamp)
        return false
    }

    override fun markInFlight(dedupKey: String) {
        inFlightDedupKeys[dedupKey] = nowProvider()
    }

    override fun clearInFlight(dedupKey: String) {
        inFlightDedupKeys.remove(dedupKey)
    }

    override fun enqueuePendingOperation(op: PendingSyncOperation) {
        if (op.uid.isBlank()) return
        if (activeUid != op.uid) {
            activeUid = op.uid
        }
        scope.launch {
            stateMutex.withLock {
                val currentOps = _syncState.value.pendingOperations.toMutableList()
                val existingIndex = currentOps.indexOfFirst { it.docPath == op.docPath }
                if (existingIndex >= 0) {
                    val prev = currentOps[existingIndex]
                    val mergedPayload = if (op.operationKind == SyncOperationKind.DELETE) {
                        op.replayPayload
                    } else {
                        prev.replayPayload + op.replayPayload
                    }
                    val mergedKind = when {
                        op.operationKind == SyncOperationKind.DELETE &&
                            prev.operationKind == SyncOperationKind.CREATE -> {
                            // Created offline and deleted offline before ever syncing -> cancel both!
                            currentOps.removeAt(existingIndex)
                            persistMetadata(
                                uid = op.uid,
                                ops = currentOps,
                                conflicts = _syncState.value.conflicts,
                                lastSyncedAtMillis = _syncState.value.lastSyncedAtMillis,
                            )
                            _syncState.update {
                                it.copy(
                                    pendingOperations = currentOps,
                                    showJustSyncedBanner = false,
                                )
                            }
                            return@withLock
                        }
                        op.operationKind == SyncOperationKind.DELETE -> SyncOperationKind.DELETE
                        prev.operationKind == SyncOperationKind.CREATE -> SyncOperationKind.CREATE
                        else -> op.operationKind
                    }
                    currentOps[existingIndex] = op.copy(
                        operationKind = mergedKind,
                        baseUpdatedAtMillis = prev.baseUpdatedAtMillis.takeIf { it > 0L } ?: op.baseUpdatedAtMillis,
                        baseSnapshotData = prev.baseSnapshotData.ifEmpty { op.baseSnapshotData },
                        replayPayload = mergedPayload,
                    )
                } else {
                    currentOps.add(op)
                }

                persistMetadata(
                    uid = op.uid,
                    ops = currentOps,
                    conflicts = _syncState.value.conflicts,
                    lastSyncedAtMillis = _syncState.value.lastSyncedAtMillis,
                )
                _syncState.update {
                    it.copy(
                        isOnline = isOnline,
                        pendingOperations = currentOps,
                        showJustSyncedBanner = false,
                    )
                }
            }
        }
    }

    override fun markOperationSynced(operationId: String, docPath: String) {
        val uid = activeUid ?: return
        scope.launch {
            var remainingEmpty = false
            var removedAny = false
            stateMutex.withLock {
                val currentOps = _syncState.value.pendingOperations
                val filtered = currentOps.filterNot {
                    it.operationId == operationId || (operationId.isBlank() && it.docPath == docPath)
                }
                removedAny = filtered.size != currentOps.size
                if (removedAny) {
                    val syncedAt = nowProvider()
                    persistMetadata(
                        uid = uid,
                        ops = filtered,
                        conflicts = _syncState.value.conflicts,
                        lastSyncedAtMillis = syncedAt,
                    )
                    remainingEmpty = filtered.isEmpty() && _syncState.value.conflicts.isEmpty()
                    _syncState.update {
                        it.copy(
                            pendingOperations = filtered,
                            lastSyncedAtMillis = syncedAt,
                            lastSyncError = null,
                        )
                    }
                }
            }
            if (removedAny && remainingEmpty) {
                triggerTemporaryJustSyncedBanner()
            }
        }
    }

    override fun markOperationFailed(operationId: String, error: Throwable) {
        val uid = activeUid ?: return
        scope.launch {
            handleTaskFailure(
                uid = uid,
                predicate = { it.operationId == operationId },
                error = error,
            )
        }
    }

    override fun markOperationFailedByPath(docPath: String, error: Throwable) {
        val uid = activeUid ?: return
        scope.launch {
            handleTaskFailure(
                uid = uid,
                predicate = { it.docPath == docPath },
                error = error,
            )
        }
    }

    private suspend fun handleTaskFailure(
        uid: String,
        predicate: (PendingSyncOperation) -> Boolean,
        error: Throwable,
    ) {
        val code = (error as? FirebaseFirestoreException)?.code
        if (code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
        ) {
            // Keep pending for retry when internet stabilizes.
            return
        }

        stateMutex.withLock {
            val currentOps = _syncState.value.pendingOperations.toMutableList()
            val index = currentOps.indexOfFirst(predicate)
            if (index < 0) return@withLock
            val op = currentOps[index]
            val currentConflicts = _syncState.value.conflicts.toMutableList()

            when (code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> {
                    currentOps.removeAt(index)
                    if (op.operationKind != SyncOperationKind.DELETE) {
                        val conflict = SyncConflict(
                            conflictId = UUID.randomUUID().toString(),
                            uid = uid,
                            docPath = op.docPath,
                            collectionKey = op.collectionKey,
                            displayTitle = op.displayTitle,
                            reason = SyncConflictReason.REMOTE_DELETED_LOCAL_EDITED,
                            localSummary = op.localSummary,
                            serverSummary = "Deleted in Cloud",
                            localPayload = op.replayPayload,
                            fullCreatePayload = op.baseSnapshotData + op.replayPayload,
                            serverData = emptyMap(),
                            detectedAtMillis = nowProvider(),
                            supportsKeepBoth = false,
                        )
                        addOrReplaceConflict(currentConflicts, conflict)
                    }
                }

                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                FirebaseFirestoreException.Code.INVALID_ARGUMENT,
                FirebaseFirestoreException.Code.FAILED_PRECONDITION,
                -> {
                    currentOps.removeAt(index)
                    val conflict = SyncConflict(
                        conflictId = UUID.randomUUID().toString(),
                        uid = uid,
                        docPath = op.docPath,
                        collectionKey = op.collectionKey,
                        displayTitle = op.displayTitle,
                        reason = SyncConflictReason.PERMISSION_OR_VALIDATION_ERROR,
                        localSummary = op.localSummary,
                        serverSummary = error.localizedMessage ?: "Validation rejected",
                        localPayload = op.replayPayload,
                        fullCreatePayload = op.baseSnapshotData + op.replayPayload,
                        serverData = emptyMap(),
                        detectedAtMillis = nowProvider(),
                        supportsKeepBoth = false,
                    )
                    addOrReplaceConflict(currentConflicts, conflict)
                }

                else -> {
                    _syncState.update {
                        it.copy(lastSyncError = error.localizedMessage ?: "Sync error occurred")
                    }
                    return@withLock
                }
            }

            persistMetadata(
                uid = uid,
                ops = currentOps,
                conflicts = currentConflicts,
                lastSyncedAtMillis = _syncState.value.lastSyncedAtMillis,
            )
            _syncState.update {
                it.copy(
                    pendingOperations = currentOps,
                    conflicts = currentConflicts,
                    lastSyncError = error.localizedMessage,
                )
            }
        }
    }

    override fun syncNow() {
        scope.launch {
            syncNowInternal(triggerBanner = true)
        }
    }

    suspend fun syncNowAwait(triggerBanner: Boolean = true) {
        syncNowInternal(triggerBanner = triggerBanner)
    }

    private suspend fun syncNowInternal(triggerBanner: Boolean) {
        val uid = activeUid ?: return
        if (!isOnline) {
            _syncState.update {
                it.copy(
                    isOnline = false,
                    lastSyncError = "Offline — pending changes will sync automatically when internet returns.",
                )
            }
            return
        }

        syncExecutionMutex.withLock {
            _syncState.update { it.copy(isSyncing = true, lastSyncError = null) }
            val opsToProcess = stateMutex.withLock {
                _syncState.value.pendingOperations.filter { it.uid == uid }
            }
            var syncedCount = 0
            var transientError: String? = null

            for (op in opsToProcess) {
                try {
                    val outcome = replaySingleOperation(op)
                    stateMutex.withLock {
                        val currentOps = _syncState.value.pendingOperations.toMutableList()
                        val currentConflicts = _syncState.value.conflicts.toMutableList()
                        when (outcome) {
                            is ReplayOutcome.Synced -> {
                                currentOps.removeAll { it.operationId == op.operationId }
                                syncedCount++
                            }

                            is ReplayOutcome.Conflict -> {
                                currentOps.removeAll { it.operationId == op.operationId }
                                addOrReplaceConflict(currentConflicts, outcome.conflict)
                            }

                            is ReplayOutcome.RetryLater -> {
                                val idx = currentOps.indexOfFirst { it.operationId == op.operationId }
                                if (idx >= 0) {
                                    currentOps[idx] = currentOps[idx].copy(
                                        retryCount = currentOps[idx].retryCount + 1,
                                    )
                                }
                                transientError = outcome.message
                            }
                        }
                        persistMetadata(
                            uid = uid,
                            ops = currentOps,
                            conflicts = currentConflicts,
                            lastSyncedAtMillis = _syncState.value.lastSyncedAtMillis,
                        )
                        _syncState.update {
                            it.copy(
                                pendingOperations = currentOps,
                                conflicts = currentConflicts,
                            )
                        }
                    }
                    if (outcome is ReplayOutcome.RetryLater) {
                        break
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Log.w(tag, "Sync failed for ${op.docPath}: ${e.message}")
                    transientError = e.localizedMessage ?: "Failed to sync changes"
                    break
                }
            }

            if (transientError == null) {
                runCatching { backend.waitForPendingWrites() }
                    .onFailure { err ->
                        if (err is CancellationException) throw err
                        Log.w(tag, "waitForPendingWrites warning: ${err.message}")
                    }
            }

            val remainingPending = _syncState.value.pendingOperations
            val remainingConflicts = _syncState.value.conflicts
            val newSyncedAt = if (transientError == null && remainingPending.isEmpty()) {
                nowProvider()
            } else {
                _syncState.value.lastSyncedAtMillis
            }

            stateMutex.withLock {
                persistMetadata(
                    uid = uid,
                    ops = remainingPending,
                    conflicts = remainingConflicts,
                    lastSyncedAtMillis = newSyncedAt,
                )
            }

            val showSynced = triggerBanner &&
                transientError == null &&
                remainingPending.isEmpty() &&
                remainingConflicts.isEmpty() &&
                syncedCount > 0

            _syncState.update {
                it.copy(
                    isOnline = isOnline,
                    isSyncing = false,
                    hasSdkPendingWrites = if (transientError == null && remainingPending.isEmpty()) {
                        false
                    } else {
                        it.hasSdkPendingWrites
                    },
                    lastSyncedAtMillis = newSyncedAt,
                    lastSyncError = transientError,
                    showJustSyncedBanner = showSynced,
                )
            }
            if (showSynced) {
                scheduleBannerAutoDismiss()
            }
        }
    }

    private suspend fun replaySingleOperation(op: PendingSyncOperation): ReplayOutcome {
        val remote = try {
            backend.fetchServerDocument(op.docPath)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (isTransientConnectivityError(e)) {
                return ReplayOutcome.RetryLater(
                    e.localizedMessage ?: "Connection unavailable while syncing",
                )
            }
            return ReplayOutcome.Conflict(
                SyncConflict(
                    conflictId = UUID.randomUUID().toString(),
                    uid = op.uid,
                    docPath = op.docPath,
                    collectionKey = op.collectionKey,
                    displayTitle = op.displayTitle,
                    reason = SyncConflictReason.PERMISSION_OR_VALIDATION_ERROR,
                    localSummary = op.localSummary,
                    serverSummary = e.localizedMessage ?: "Sync error",
                    localPayload = op.replayPayload,
                    fullCreatePayload = op.baseSnapshotData + op.replayPayload,
                    serverData = emptyMap(),
                    detectedAtMillis = nowProvider(),
                    supportsKeepBoth = false,
                ),
            )
        }

        val isSingleton = isSingletonPath(op.docPath, op.uid)

        return try {
            when (op.operationKind) {
                SyncOperationKind.DELETE -> {
                    if (!remote.exists) {
                        ReplayOutcome.Synced
                    } else if (op.baseUpdatedAtMillis > 0L && remote.updatedAtMillis > op.baseUpdatedAtMillis) {
                        ReplayOutcome.Conflict(
                            SyncConflict(
                                conflictId = UUID.randomUUID().toString(),
                                uid = op.uid,
                                docPath = op.docPath,
                                collectionKey = op.collectionKey,
                                displayTitle = op.displayTitle,
                                reason = SyncConflictReason.CONCURRENT_REMOTE_MODIFICATION,
                                localSummary = "Deleted offline",
                                serverSummary = SyncPayloadCodec.summarizeFields(remote.data),
                                localPayload = mapOf(PENDING_DELETE_MARKER to true),
                                fullCreatePayload = op.baseSnapshotData,
                                serverData = remote.data,
                                detectedAtMillis = nowProvider(),
                                supportsKeepBoth = false,
                            ),
                        )
                    } else {
                        backend.deleteDocument(op.docPath)
                        ReplayOutcome.Synced
                    }
                }

                SyncOperationKind.CREATE -> {
                    if (remote.exists) {
                        if (!SyncPayloadCodec.payloadConflictsWithServer(op.replayPayload, remote.data)) {
                            // Deduplication: identical record already exists in Cloud.
                            ReplayOutcome.Synced
                        } else {
                            ReplayOutcome.Conflict(
                                SyncConflict(
                                    conflictId = UUID.randomUUID().toString(),
                                    uid = op.uid,
                                    docPath = op.docPath,
                                    collectionKey = op.collectionKey,
                                    displayTitle = op.displayTitle,
                                    reason = SyncConflictReason.CONCURRENT_REMOTE_MODIFICATION,
                                    localSummary = op.localSummary,
                                    serverSummary = SyncPayloadCodec.summarizeFields(remote.data),
                                    localPayload = op.replayPayload,
                                    fullCreatePayload = op.replayPayload,
                                    serverData = remote.data,
                                    detectedAtMillis = nowProvider(),
                                    supportsKeepBoth = !isSingleton,
                                ),
                            )
                        }
                    } else {
                        val payload = SyncPayloadCodec.toFirestorePayload(
                            stored = op.replayPayload,
                            forCreate = true,
                            uid = op.uid,
                        )
                        backend.setDocument(op.docPath, payload, merge = false)
                        ReplayOutcome.Synced
                    }
                }

                SyncOperationKind.UPDATE, SyncOperationKind.BATCH_WRITE -> {
                    if (!remote.exists) {
                        if (isSingleton) {
                            val merged = op.baseSnapshotData + op.replayPayload
                            val payload = SyncPayloadCodec.toFirestorePayload(
                                stored = merged,
                                forCreate = true,
                                uid = op.uid,
                            )
                            backend.setDocument(op.docPath, payload, merge = false)
                            ReplayOutcome.Synced
                        } else {
                            ReplayOutcome.Conflict(
                                SyncConflict(
                                    conflictId = UUID.randomUUID().toString(),
                                    uid = op.uid,
                                    docPath = op.docPath,
                                    collectionKey = op.collectionKey,
                                    displayTitle = op.displayTitle,
                                    reason = SyncConflictReason.REMOTE_DELETED_LOCAL_EDITED,
                                    localSummary = op.localSummary,
                                    serverSummary = "Deleted in Cloud",
                                    localPayload = op.replayPayload,
                                    fullCreatePayload = op.baseSnapshotData + op.replayPayload,
                                    serverData = emptyMap(),
                                    detectedAtMillis = nowProvider(),
                                    supportsKeepBoth = false,
                                ),
                            )
                        }
                    } else if (op.baseUpdatedAtMillis > 0L &&
                        remote.updatedAtMillis > op.baseUpdatedAtMillis &&
                        SyncPayloadCodec.payloadConflictsWithServer(op.replayPayload, remote.data)
                    ) {
                        ReplayOutcome.Conflict(
                            SyncConflict(
                                conflictId = UUID.randomUUID().toString(),
                                uid = op.uid,
                                docPath = op.docPath,
                                collectionKey = op.collectionKey,
                                displayTitle = op.displayTitle,
                                reason = SyncConflictReason.CONCURRENT_REMOTE_MODIFICATION,
                                localSummary = op.localSummary,
                                serverSummary = SyncPayloadCodec.summarizeFields(remote.data),
                                localPayload = op.replayPayload,
                                fullCreatePayload = op.baseSnapshotData + op.replayPayload,
                                serverData = remote.data,
                                detectedAtMillis = nowProvider(),
                                supportsKeepBoth = !isSingleton,
                            ),
                        )
                    } else {
                        val payload = SyncPayloadCodec.toFirestorePayload(
                            stored = op.replayPayload,
                            forCreate = false,
                            uid = op.uid,
                        )
                        backend.updateDocument(op.docPath, payload)
                        ReplayOutcome.Synced
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (isTransientConnectivityError(e)) {
                ReplayOutcome.RetryLater(e.localizedMessage ?: "Network unavailable during sync")
            } else {
                ReplayOutcome.Conflict(
                    SyncConflict(
                        conflictId = UUID.randomUUID().toString(),
                        uid = op.uid,
                        docPath = op.docPath,
                        collectionKey = op.collectionKey,
                        displayTitle = op.displayTitle,
                        reason = SyncConflictReason.PERMISSION_OR_VALIDATION_ERROR,
                        localSummary = op.localSummary,
                        serverSummary = e.localizedMessage ?: "Rejected by Cloud Firestore",
                        localPayload = op.replayPayload,
                        fullCreatePayload = op.baseSnapshotData + op.replayPayload,
                        serverData = remote.data,
                        detectedAtMillis = nowProvider(),
                        supportsKeepBoth = false,
                    ),
                )
            }
        }
    }

    override fun resolveKeepLocal(conflictId: String) {
        scope.launch { resolveKeepLocalAwait(conflictId) }
    }

    suspend fun resolveKeepLocalAwait(conflictId: String) {
        val conflict = _syncState.value.conflicts.firstOrNull { it.conflictId == conflictId } ?: return
        if (!isOnline) {
            _syncState.update {
                it.copy(lastSyncError = "Connect to the internet to push your local version.")
            }
            return
        }
        _syncState.update { it.copy(isSyncing = true, lastSyncError = null) }
        try {
            if (conflict.localPayload[PENDING_DELETE_MARKER] == true) {
                backend.deleteDocument(conflict.docPath)
            } else if (conflict.reason == SyncConflictReason.REMOTE_DELETED_LOCAL_EDITED ||
                conflict.serverData.isEmpty()
            ) {
                val merged = conflict.fullCreatePayload + conflict.localPayload
                val payload = SyncPayloadCodec.toFirestorePayload(
                    stored = merged,
                    forCreate = true,
                    uid = conflict.uid,
                )
                backend.setDocument(conflict.docPath, payload, merge = false)
            } else {
                val payload = SyncPayloadCodec.toFirestorePayload(
                    stored = conflict.localPayload,
                    forCreate = false,
                    uid = conflict.uid,
                )
                backend.updateDocument(conflict.docPath, payload)
            }
            removeConflictAndFinish(conflict)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            _syncState.update {
                it.copy(
                    isSyncing = false,
                    lastSyncError = e.localizedMessage ?: "Unable to apply local changes.",
                )
            }
        }
    }

    override fun resolveUseCloud(conflictId: String) {
        scope.launch { resolveUseCloudAwait(conflictId) }
    }

    suspend fun resolveUseCloudAwait(conflictId: String) {
        val conflict = _syncState.value.conflicts.firstOrNull { it.conflictId == conflictId } ?: return
        removeConflictAndFinish(conflict)
    }

    override fun resolveKeepBoth(conflictId: String) {
        scope.launch { resolveKeepBothAwait(conflictId) }
    }

    suspend fun resolveKeepBothAwait(conflictId: String) {
        val conflict = _syncState.value.conflicts.firstOrNull { it.conflictId == conflictId } ?: return
        if (!conflict.supportsKeepBoth) {
            resolveKeepLocalAwait(conflictId)
            return
        }
        if (!isOnline) {
            _syncState.update {
                it.copy(lastSyncError = "Connect to the internet to save both copies.")
            }
            return
        }
        _syncState.update { it.copy(isSyncing = true, lastSyncError = null) }
        try {
            val collectionPath = conflict.docPath.substringBeforeLast('/')
            val copyDocId = "copy_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val merged = buildMap<String, Any?> {
                putAll(conflict.serverData)
                putAll(conflict.fullCreatePayload)
                putAll(conflict.localPayload)
            }.toMutableMap()
            applyCopyLabel(merged)
            val payload = SyncPayloadCodec.toFirestorePayload(
                stored = merged,
                forCreate = true,
                uid = conflict.uid,
            )
            backend.createCopyDocument(collectionPath, copyDocId, payload)
            removeConflictAndFinish(conflict)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            _syncState.update {
                it.copy(
                    isSyncing = false,
                    lastSyncError = e.localizedMessage ?: "Unable to create duplicate copy.",
                )
            }
        }
    }

    private suspend fun removeConflictAndFinish(conflict: SyncConflict) {
        val uid = activeUid ?: conflict.uid
        var allClear = false
        stateMutex.withLock {
            val remaining = _syncState.value.conflicts.filterNot { it.conflictId == conflict.conflictId }
            val syncedAt = nowProvider()
            persistMetadata(
                uid = uid,
                ops = _syncState.value.pendingOperations,
                conflicts = remaining,
                lastSyncedAtMillis = syncedAt,
            )
            allClear = remaining.isEmpty() && _syncState.value.pendingOperations.isEmpty()
            _syncState.update {
                it.copy(
                    isSyncing = false,
                    conflicts = remaining,
                    lastSyncedAtMillis = syncedAt,
                    lastSyncError = null,
                    showJustSyncedBanner = allClear,
                )
            }
        }
        if (allClear) {
            scheduleBannerAutoDismiss()
        }
    }

    override fun dismissJustSyncedBanner() {
        _syncState.update { it.copy(showJustSyncedBanner = false) }
    }

    override fun clearSyncError() {
        _syncState.update { it.copy(lastSyncError = null) }
    }

    override fun formatActionMessage(onlineMessage: String, offlineMessage: String): String {
        return if (isOnline) onlineMessage else offlineMessage
    }

    private fun triggerTemporaryJustSyncedBanner() {
        _syncState.update { it.copy(showJustSyncedBanner = true) }
        scheduleBannerAutoDismiss()
    }

    private fun scheduleBannerAutoDismiss() {
        scope.launch {
            delay(4_000L)
            _syncState.update { it.copy(showJustSyncedBanner = false) }
        }
    }

    private suspend fun persistMetadata(
        uid: String,
        ops: List<PendingSyncOperation>,
        conflicts: List<SyncConflict>,
        lastSyncedAtMillis: Long?,
    ) {
        metadataStore.save(
            uid = uid,
            metadata = PersistedSyncMetadata(
                pendingOperations = ops,
                conflicts = conflicts,
                lastSyncedAtMillis = lastSyncedAtMillis,
            ),
        )
    }

    private fun addOrReplaceConflict(list: MutableList<SyncConflict>, conflict: SyncConflict) {
        val idx = list.indexOfFirst { it.docPath == conflict.docPath }
        if (idx >= 0) {
            list[idx] = conflict
        } else {
            list.add(conflict)
        }
    }

    private fun isSingletonPath(docPath: String, uid: String): Boolean {
        return docPath == "users/$uid" ||
            docPath.endsWith("/academicSettings/preferences") ||
            docPath.endsWith("/studyPlannerSettings/goals")
    }

    private fun isTransientConnectivityError(error: Throwable): Boolean {
        val code = (error as? FirebaseFirestoreException)?.code
        return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
    }

    private fun applyCopyLabel(fields: MutableMap<String, Any?>) {
        val titleKeys = listOf("title", "courseName", "name", "semesterName", "goalTitle", "courseTitle")
        for (key in titleKeys) {
            val current = fields[key] as? String
            if (!current.isNullOrBlank()) {
                if (!current.endsWith(" (Copy)")) {
                    fields[key] = "$current (Copy)".take(160)
                }
                return
            }
        }
    }

    private sealed interface ReplayOutcome {
        data object Synced : ReplayOutcome
        data class Conflict(val conflict: SyncConflict) : ReplayOutcome
        data class RetryLater(val message: String) : ReplayOutcome
    }

    companion object {
        const val PENDING_DELETE_MARKER = "__UNIMATE_PENDING_DELETE__"
    }
}
