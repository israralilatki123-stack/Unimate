package com.example.unimate.data.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import java.util.Date

internal object SnapshotSyncSupport {
    fun reportQuerySnapshot(
        syncCoordinator: SyncCoordinator,
        collectionKey: String,
        uid: String,
        snapshot: QuerySnapshot,
    ) {
        val docsMap = linkedMapOf<String, Map<String, Any?>>()
        val updatedAtMap = linkedMapOf<String, Long>()
        snapshot.documents.forEach { doc ->
            val path = doc.reference.path
            val raw = doc.data.orEmpty()
            docsMap[path] = raw
            updatedAtMap[path] = extractUpdatedAtMillis(doc, raw)
        }
        syncCoordinator.reportCollectionSnapshot(
            collectionKey = collectionKey,
            uid = uid,
            docs = docsMap,
            updatedAtMillisByPath = updatedAtMap,
            isFromCache = snapshot.metadata.isFromCache,
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
        )
    }

    fun reportDocumentSnapshot(
        syncCoordinator: SyncCoordinator,
        collectionKey: String,
        uid: String,
        snapshot: DocumentSnapshot,
    ) {
        val path = snapshot.reference.path
        val docsMap = linkedMapOf<String, Map<String, Any?>>()
        val updatedAtMap = linkedMapOf<String, Long>()
        if (snapshot.exists()) {
            val raw = snapshot.data.orEmpty()
            docsMap[path] = raw
            updatedAtMap[path] = extractUpdatedAtMillis(snapshot, raw)
        }
        syncCoordinator.reportCollectionSnapshot(
            collectionKey = collectionKey,
            uid = uid,
            docs = docsMap,
            updatedAtMillisByPath = updatedAtMap,
            isFromCache = snapshot.metadata.isFromCache,
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
        )
    }

    private fun extractUpdatedAtMillis(doc: DocumentSnapshot, raw: Map<String, Any?>): Long {
        val updated = raw["updatedAt"] ?: doc.get("updatedAt")
        val millis = toMillis(updated)
        if (millis > 0L) return millis
        val clientMillis = (raw["clientUpdatedAtMillis"] as? Number)?.toLong() ?: 0L
        if (clientMillis > 0L) return clientMillis
        return toMillis(raw["createdAt"] ?: doc.get("createdAt"))
    }

    private fun toMillis(value: Any?): Long {
        return when (value) {
            is Timestamp -> value.toDate().time
            is Date -> value.time
            is Number -> value.toLong()
            else -> 0L
        }
    }
}
