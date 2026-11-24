package com.example.unimate.data.sync

enum class SyncOperationKind(val wireValue: String) {
    CREATE("CREATE"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    BATCH_WRITE("BATCH_WRITE"),
    ;

    companion object {
        fun fromWire(value: String?): SyncOperationKind {
            return entries.firstOrNull { it.wireValue == value } ?: UPDATE
        }
    }
}

enum class SyncConflictReason(val wireValue: String, val label: String) {
    REMOTE_DELETED_LOCAL_EDITED(
        "REMOTE_DELETED_LOCAL_EDITED",
        "Deleted on another device while edited offline",
    ),
    CONCURRENT_REMOTE_MODIFICATION(
        "CONCURRENT_REMOTE_MODIFICATION",
        "Modified both offline and in Cloud Firestore",
    ),
    PERMISSION_OR_VALIDATION_ERROR(
        "PERMISSION_OR_VALIDATION_ERROR",
        "Rejected by Cloud Firestore during sync",
    ),
    ;

    companion object {
        fun fromWire(value: String?): SyncConflictReason {
            return entries.firstOrNull { it.wireValue == value } ?: CONCURRENT_REMOTE_MODIFICATION
        }
    }
}

data class PendingSyncOperation(
    val operationId: String,
    val uid: String,
    val docPath: String,
    val collectionKey: String,
    val displayTitle: String,
    val operationKind: SyncOperationKind,
    val baseUpdatedAtMillis: Long = 0L,
    val localSummary: String = "",
    val replayPayload: Map<String, Any?> = emptyMap(),
    val baseSnapshotData: Map<String, Any?> = emptyMap(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
)

data class SyncConflict(
    val conflictId: String,
    val uid: String,
    val docPath: String,
    val collectionKey: String,
    val displayTitle: String,
    val reason: SyncConflictReason,
    val localSummary: String,
    val serverSummary: String,
    val localPayload: Map<String, Any?> = emptyMap(),
    val fullCreatePayload: Map<String, Any?> = emptyMap(),
    val serverData: Map<String, Any?> = emptyMap(),
    val detectedAtMillis: Long = System.currentTimeMillis(),
    val supportsKeepBoth: Boolean = true,
)

data class CollectionMetadataReport(
    val isFromCache: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val observedAtMillis: Long = 0L,
)

data class SyncStatusUiState(
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val isUsingCachedData: Boolean = false,
    val hasSdkPendingWrites: Boolean = false,
    val pendingOperations: List<PendingSyncOperation> = emptyList(),
    val conflicts: List<SyncConflict> = emptyList(),
    val lastSyncedAtMillis: Long? = null,
    val lastSyncError: String? = null,
    val showJustSyncedBanner: Boolean = false,
) {
    val pendingCount: Int
        get() = pendingOperations.size

    val conflictCount: Int
        get() = conflicts.size

    val hasUnsynchronizedData: Boolean
        get() = pendingOperations.isNotEmpty() || conflicts.isNotEmpty() || hasSdkPendingWrites
}
