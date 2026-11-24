package com.example.unimate.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.unimate.data.local.unimateDataStore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class PersistedSyncMetadata(
    val pendingOperations: List<PendingSyncOperation> = emptyList(),
    val conflicts: List<SyncConflict> = emptyList(),
    val lastSyncedAtMillis: Long? = null,
)

interface SyncMetadataStore {
    suspend fun load(uid: String): PersistedSyncMetadata
    suspend fun save(uid: String, metadata: PersistedSyncMetadata)
}

class InMemorySyncMetadataStore : SyncMetadataStore {
    private val mutex = Mutex()
    private val byUid = mutableMapOf<String, PersistedSyncMetadata>()

    override suspend fun load(uid: String): PersistedSyncMetadata = mutex.withLock {
        byUid[uid] ?: PersistedSyncMetadata()
    }

    override suspend fun save(uid: String, metadata: PersistedSyncMetadata) {
        mutex.withLock {
            byUid[uid] = metadata
        }
    }
}

class DataStoreSyncMetadataStore(
    context: Context,
) : SyncMetadataStore {

    private val appContext = context.applicationContext

    override suspend fun load(uid: String): PersistedSyncMetadata {
        if (uid.isBlank()) return PersistedSyncMetadata()
        val prefs = runCatching { appContext.unimateDataStore.data.first() }.getOrNull()
            ?: return PersistedSyncMetadata()
        val opsJson = prefs[pendingOpsKey(uid)]
        val conflictsJson = prefs[conflictsKey(uid)]
        val lastSynced = prefs[lastSyncedKey(uid)]
        return PersistedSyncMetadata(
            pendingOperations = opsJson?.let { SyncPayloadCodec.decodePendingOps(it) }.orEmpty(),
            conflicts = conflictsJson?.let { SyncPayloadCodec.decodeConflicts(it) }.orEmpty(),
            lastSyncedAtMillis = lastSynced?.takeIf { it > 0L },
        )
    }

    override suspend fun save(uid: String, metadata: PersistedSyncMetadata) {
        if (uid.isBlank()) return
        runCatching {
            appContext.unimateDataStore.edit { prefs ->
                prefs[pendingOpsKey(uid)] = SyncPayloadCodec.encodePendingOps(metadata.pendingOperations)
                prefs[conflictsKey(uid)] = SyncPayloadCodec.encodeConflicts(metadata.conflicts)
                val syncedAt = metadata.lastSyncedAtMillis
                if (syncedAt != null && syncedAt > 0L) {
                    prefs[lastSyncedKey(uid)] = syncedAt
                }
            }
        }
    }

    private fun pendingOpsKey(uid: String) = stringPreferencesKey("sync_pending_ops_$uid")
    private fun conflictsKey(uid: String) = stringPreferencesKey("sync_conflicts_$uid")
    private fun lastSyncedKey(uid: String) = longPreferencesKey("sync_last_synced_at_$uid")
}

object SyncPayloadCodec {
    const val SERVER_TIMESTAMP_SENTINEL = "__UNIMATE_SERVER_TIMESTAMP__"
    const val FIELD_DELETE_SENTINEL = "__UNIMATE_FIELD_DELETE__"

    private val serverTimestampClassName = FieldValue.serverTimestamp().javaClass.name
    private val fieldDeleteClassName = FieldValue.delete().javaClass.name

    fun encodeForStorage(raw: Map<String, Any?>): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        raw.forEach { (key, value) ->
            result[key] = sanitizeValue(value)
        }
        return result
    }

    private fun sanitizeValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Boolean, is String -> value
            is Int -> value.toLong()
            is Long, is Double -> value
            is Float -> value.toDouble()
            is Number -> value.toDouble()
            is Timestamp -> value.toDate().time
            is FieldValue -> {
                val cls = value.javaClass.name
                when {
                    cls == fieldDeleteClassName || cls.contains("Delete", ignoreCase = true) ->
                        FIELD_DELETE_SENTINEL
                    else -> SERVER_TIMESTAMP_SENTINEL
                }
            }
            is Map<*, *> -> {
                val map = linkedMapOf<String, Any?>()
                value.forEach { (k, v) ->
                    if (k != null) {
                        map[k.toString()] = sanitizeValue(v)
                    }
                }
                map
            }
            is Iterable<*> -> value.map { sanitizeValue(it) }
            else -> value.toString()
        }
    }

    fun toFirestorePayload(
        stored: Map<String, Any?>,
        forCreate: Boolean,
        uid: String,
    ): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        stored.forEach { (key, value) ->
            if (!forCreate && (key == "uid" || key == "createdAt")) {
                return@forEach
            }
            when (val restored = restoreValue(value)) {
                null -> Unit
                else -> result[key] = restored
            }
        }
        if (forCreate) {
            if (uid.isNotBlank()) {
                result["uid"] = uid
            }
            result["createdAt"] = FieldValue.serverTimestamp()
        }
        result["updatedAt"] = FieldValue.serverTimestamp()
        return result
    }

    private fun restoreValue(value: Any?): Any? {
        return when (value) {
            null -> null
            SERVER_TIMESTAMP_SENTINEL -> FieldValue.serverTimestamp()
            FIELD_DELETE_SENTINEL -> FieldValue.delete()
            is Map<*, *> -> {
                val map = linkedMapOf<String, Any>()
                value.forEach { (k, v) ->
                    val restored = restoreValue(v)
                    if (k != null && restored != null) {
                        map[k.toString()] = restored
                    }
                }
                map
            }
            is List<*> -> value.mapNotNull { restoreValue(it) }
            else -> value
        }
    }

    fun summarizeFields(data: Map<String, Any?>): String {
        val ignoredKeys = setOf("uid", "createdAt", "updatedAt", "clientUpdatedAtMillis", "workRequestId")
        val meaningful = data.entries
            .filter { (key, value) ->
                key !in ignoredKeys &&
                    value != null &&
                    value != SERVER_TIMESTAMP_SENTINEL &&
                    value != FIELD_DELETE_SENTINEL
            }
            .take(3)
            .map { (key, value) ->
                val formattedValue = when (value) {
                    is List<*> -> "${value.size} item(s)"
                    is Map<*, *> -> "${value.size} field(s)"
                    else -> value.toString().take(48)
                }
                "$key: $formattedValue"
            }
        return if (meaningful.isEmpty()) "Updated fields" else meaningful.joinToString(" • ")
    }

    fun payloadConflictsWithServer(
        localPayload: Map<String, Any?>,
        serverData: Map<String, Any?>,
    ): Boolean {
        val ignoredKeys = setOf("uid", "createdAt", "updatedAt", "clientUpdatedAtMillis")
        for ((key, localVal) in localPayload) {
            if (key in ignoredKeys) continue
            if (localVal == SERVER_TIMESTAMP_SENTINEL) continue
            if (localVal == FIELD_DELETE_SENTINEL) {
                if (serverData.containsKey(key) && serverData[key] != null) return true
                continue
            }
            val serverVal = serverData[key]
            if (!valuesEquivalent(localVal, serverVal)) {
                return true
            }
        }
        return false
    }

    private fun valuesEquivalent(a: Any?, b: Any?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        if (a is Number && b is Number) {
            return kotlin.math.abs(a.toDouble() - b.toDouble()) < 0.0001
        }
        if (a is List<*> && b is List<*>) {
            if (a.size != b.size) return false
            return a.indices.all { idx -> valuesEquivalent(a[idx], b[idx]) }
        }
        if (a is Map<*, *> && b is Map<*, *>) {
            if (a.size != b.size) return false
            return a.keys.all { key -> valuesEquivalent(a[key], b[key]) }
        }
        return a == b
    }

    fun encodePendingOps(ops: List<PendingSyncOperation>): String {
        val arr = JSONArray()
        ops.forEach { op ->
            arr.put(
                JSONObject().apply {
                    put("operationId", op.operationId)
                    put("uid", op.uid)
                    put("docPath", op.docPath)
                    put("collectionKey", op.collectionKey)
                    put("displayTitle", op.displayTitle)
                    put("operationKind", op.operationKind.wireValue)
                    put("baseUpdatedAtMillis", op.baseUpdatedAtMillis)
                    put("localSummary", op.localSummary)
                    put("replayPayload", mapToJsonObject(op.replayPayload))
                    put("baseSnapshotData", mapToJsonObject(op.baseSnapshotData))
                    put("createdAtMillis", op.createdAtMillis)
                    put("retryCount", op.retryCount)
                },
            )
        }
        return arr.toString()
    }

    fun decodePendingOps(json: String): List<PendingSyncOperation> {
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        PendingSyncOperation(
                            operationId = obj.optString("operationId"),
                            uid = obj.optString("uid"),
                            docPath = obj.optString("docPath"),
                            collectionKey = obj.optString("collectionKey"),
                            displayTitle = obj.optString("displayTitle"),
                            operationKind = SyncOperationKind.fromWire(obj.optString("operationKind")),
                            baseUpdatedAtMillis = obj.optLong("baseUpdatedAtMillis", 0L),
                            localSummary = obj.optString("localSummary"),
                            replayPayload = jsonObjectToMap(obj.optJSONObject("replayPayload")),
                            baseSnapshotData = jsonObjectToMap(obj.optJSONObject("baseSnapshotData")),
                            createdAtMillis = obj.optLong("createdAtMillis", 0L),
                            retryCount = obj.optInt("retryCount", 0),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeConflicts(conflicts: List<SyncConflict>): String {
        val arr = JSONArray()
        conflicts.forEach { conflict ->
            arr.put(
                JSONObject().apply {
                    put("conflictId", conflict.conflictId)
                    put("uid", conflict.uid)
                    put("docPath", conflict.docPath)
                    put("collectionKey", conflict.collectionKey)
                    put("displayTitle", conflict.displayTitle)
                    put("reason", conflict.reason.wireValue)
                    put("localSummary", conflict.localSummary)
                    put("serverSummary", conflict.serverSummary)
                    put("localPayload", mapToJsonObject(conflict.localPayload))
                    put("fullCreatePayload", mapToJsonObject(conflict.fullCreatePayload))
                    put("serverData", mapToJsonObject(conflict.serverData))
                    put("detectedAtMillis", conflict.detectedAtMillis)
                    put("supportsKeepBoth", conflict.supportsKeepBoth)
                },
            )
        }
        return arr.toString()
    }

    fun decodeConflicts(json: String): List<SyncConflict> {
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        SyncConflict(
                            conflictId = obj.optString("conflictId"),
                            uid = obj.optString("uid"),
                            docPath = obj.optString("docPath"),
                            collectionKey = obj.optString("collectionKey"),
                            displayTitle = obj.optString("displayTitle"),
                            reason = SyncConflictReason.fromWire(obj.optString("reason")),
                            localSummary = obj.optString("localSummary"),
                            serverSummary = obj.optString("serverSummary"),
                            localPayload = jsonObjectToMap(obj.optJSONObject("localPayload")),
                            fullCreatePayload = jsonObjectToMap(obj.optJSONObject("fullCreatePayload")),
                            serverData = jsonObjectToMap(obj.optJSONObject("serverData")),
                            detectedAtMillis = obj.optLong("detectedAtMillis", 0L),
                            supportsKeepBoth = obj.optBoolean("supportsKeepBoth", true),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (key, value) ->
            obj.put(key, anyToJsonValue(value))
        }
        return obj
    }

    private fun anyToJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val obj = JSONObject()
                value.forEach { (k, v) ->
                    if (k != null) obj.put(k.toString(), anyToJsonValue(v))
                }
                obj
            }
            is Iterable<*> -> {
                val arr = JSONArray()
                value.forEach { arr.put(anyToJsonValue(it)) }
                arr
            }
            else -> value
        }
    }

    private fun jsonObjectToMap(obj: JSONObject?): Map<String, Any?> {
        if (obj == null) return emptyMap()
        val result = linkedMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = jsonValueToAny(obj.opt(key))
        }
        return result
    }

    private fun jsonValueToAny(value: Any?): Any? {
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> jsonObjectToMap(value)
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    add(jsonValueToAny(value.opt(i)))
                }
            }
            is Int -> value.toLong()
            else -> value
        }
    }
}
