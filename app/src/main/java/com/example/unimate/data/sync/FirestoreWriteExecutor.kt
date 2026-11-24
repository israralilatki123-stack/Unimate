package com.example.unimate.data.sync

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class DuplicateWriteIgnoredException(message: String) : IllegalStateException(message)

object FirestoreWriteExecutor {

    private const val ONLINE_ACK_TIMEOUT_MS = 2_500L

    suspend fun executeSet(
        syncCoordinator: SyncCoordinator,
        uid: String,
        docRef: DocumentReference,
        collectionKey: String,
        displayTitle: String,
        payload: Map<String, Any>,
        merge: Boolean = false,
        isCreate: Boolean = !merge,
        dedupKey: String? = null,
    ) {
        if (!dedupKey.isNullOrBlank()) {
            if (syncCoordinator.isDuplicateInFlight(dedupKey)) {
                throw DuplicateWriteIgnoredException("Duplicate submission ignored")
            }
            syncCoordinator.markInFlight(dedupKey)
        }

        val docPath = docRef.path
        val known = syncCoordinator.getKnownDocSnapshot(docPath)
        val opId = UUID.randomUUID().toString()
        val sanitizedPayload = SyncPayloadCodec.encodeForStorage(payload)

        syncCoordinator.enqueuePendingOperation(
            PendingSyncOperation(
                operationId = opId,
                uid = uid,
                docPath = docPath,
                collectionKey = collectionKey,
                displayTitle = displayTitle,
                operationKind = if (isCreate) SyncOperationKind.CREATE else SyncOperationKind.UPDATE,
                baseUpdatedAtMillis = known?.first ?: 0L,
                localSummary = SyncPayloadCodec.summarizeFields(sanitizedPayload),
                replayPayload = sanitizedPayload,
                baseSnapshotData = known?.second.orEmpty(),
            ),
        )

        val task = if (merge) {
            docRef.set(payload, SetOptions.merge())
        } else {
            docRef.set(payload)
        }
        awaitTaskRespectingOffline(
            task = task,
            syncCoordinator = syncCoordinator,
            opId = opId,
            docPath = docPath,
            dedupKey = dedupKey,
        )
    }

    suspend fun executeUpdate(
        syncCoordinator: SyncCoordinator,
        uid: String,
        docRef: DocumentReference,
        collectionKey: String,
        displayTitle: String,
        payload: Map<String, Any>,
    ) {
        val docPath = docRef.path
        val known = syncCoordinator.getKnownDocSnapshot(docPath)
        val opId = UUID.randomUUID().toString()
        val sanitizedPayload = SyncPayloadCodec.encodeForStorage(payload)

        syncCoordinator.enqueuePendingOperation(
            PendingSyncOperation(
                operationId = opId,
                uid = uid,
                docPath = docPath,
                collectionKey = collectionKey,
                displayTitle = displayTitle,
                operationKind = SyncOperationKind.UPDATE,
                baseUpdatedAtMillis = known?.first ?: 0L,
                localSummary = SyncPayloadCodec.summarizeFields(sanitizedPayload),
                replayPayload = sanitizedPayload,
                baseSnapshotData = known?.second.orEmpty(),
            ),
        )

        val task = docRef.update(payload)
        awaitTaskRespectingOffline(
            task = task,
            syncCoordinator = syncCoordinator,
            opId = opId,
            docPath = docPath,
            dedupKey = null,
        )
    }

    suspend fun executeDelete(
        syncCoordinator: SyncCoordinator,
        uid: String,
        docRef: DocumentReference,
        collectionKey: String,
        displayTitle: String,
    ) {
        val docPath = docRef.path
        val known = syncCoordinator.getKnownDocSnapshot(docPath)
        val opId = UUID.randomUUID().toString()

        syncCoordinator.enqueuePendingOperation(
            PendingSyncOperation(
                operationId = opId,
                uid = uid,
                docPath = docPath,
                collectionKey = collectionKey,
                displayTitle = displayTitle,
                operationKind = SyncOperationKind.DELETE,
                baseUpdatedAtMillis = known?.first ?: 0L,
                localSummary = "Delete $displayTitle",
                replayPayload = emptyMap(),
                baseSnapshotData = known?.second.orEmpty(),
            ),
        )

        val task = docRef.delete()
        awaitTaskRespectingOffline(
            task = task,
            syncCoordinator = syncCoordinator,
            opId = opId,
            docPath = docPath,
            dedupKey = null,
        )
    }

    data class BatchTrackedWrite(
        val docPath: String,
        val collectionKey: String,
        val displayTitle: String,
        val operationKind: SyncOperationKind,
        val payload: Map<String, Any>,
    )

    suspend fun executeBatch(
        syncCoordinator: SyncCoordinator,
        uid: String,
        batch: WriteBatch,
        trackedWrites: List<BatchTrackedWrite>,
    ) {
        val opIds = trackedWrites.map { write ->
            val known = syncCoordinator.getKnownDocSnapshot(write.docPath)
            val opId = UUID.randomUUID().toString()
            val sanitizedPayload = SyncPayloadCodec.encodeForStorage(write.payload)
            syncCoordinator.enqueuePendingOperation(
                PendingSyncOperation(
                    operationId = opId,
                    uid = uid,
                    docPath = write.docPath,
                    collectionKey = write.collectionKey,
                    displayTitle = write.displayTitle,
                    operationKind = write.operationKind,
                    baseUpdatedAtMillis = known?.first ?: 0L,
                    localSummary = if (write.operationKind == SyncOperationKind.DELETE) {
                        "Delete ${write.displayTitle}"
                    } else {
                        SyncPayloadCodec.summarizeFields(sanitizedPayload)
                    },
                    replayPayload = sanitizedPayload,
                    baseSnapshotData = known?.second.orEmpty(),
                ),
            )
            opId to write.docPath
        }

        val task = batch.commit()
        task.addOnCompleteListener { completed ->
            if (completed.isSuccessful) {
                opIds.forEach { (opId, path) ->
                    syncCoordinator.markOperationSynced(opId, path)
                }
            } else {
                val err = completed.exception ?: return@addOnCompleteListener
                opIds.forEach { (opId, _) ->
                    syncCoordinator.markOperationFailed(opId, err)
                }
            }
        }

        if (!syncCoordinator.isOnline) {
            return
        }

        try {
            withTimeoutOrNull(ONLINE_ACK_TIMEOUT_MS) {
                task.await()
            }
        } catch (ce: CancellationException) {
            throw ce
        }
    }

    private suspend fun awaitTaskRespectingOffline(
        task: Task<Void>,
        syncCoordinator: SyncCoordinator,
        opId: String,
        docPath: String,
        dedupKey: String?,
    ) {
        task.addOnCompleteListener { completed ->
            if (!dedupKey.isNullOrBlank()) {
                syncCoordinator.clearInFlight(dedupKey)
            }
            if (completed.isSuccessful) {
                syncCoordinator.markOperationSynced(opId, docPath)
            } else {
                completed.exception?.let { err ->
                    syncCoordinator.markOperationFailed(opId, err)
                }
            }
        }

        if (!syncCoordinator.isOnline) {
            return
        }

        try {
            withTimeoutOrNull(ONLINE_ACK_TIMEOUT_MS) {
                task.await()
            }
        } catch (ce: CancellationException) {
            throw ce
        }
    }
}
