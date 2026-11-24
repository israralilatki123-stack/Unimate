package com.example.unimate.data.repository

import com.example.unimate.data.firebase.OperationType
import com.example.unimate.data.firebase.handleFirestoreError
import com.example.unimate.data.model.AppNotification
import com.example.unimate.data.model.CalendarEvent
import com.example.unimate.data.model.CalendarReminderMappers
import com.example.unimate.data.model.CreateAppNotificationRequest
import com.example.unimate.data.model.SaveCalendarEventRequest
import com.example.unimate.data.model.SaveScheduledReminderRequest
import com.example.unimate.data.model.ScheduledReminder
import com.example.unimate.data.sync.DuplicateWriteIgnoredException
import com.example.unimate.data.sync.FirestoreWriteExecutor
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.SnapshotSyncSupport
import com.example.unimate.data.sync.SyncCoordinator
import com.example.unimate.data.sync.SyncOperationKind
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

interface CalendarRemindersRepository {
    fun observeCalendarEvents(uid: String): Flow<List<CalendarEvent>>
    suspend fun addCalendarEvent(uid: String, request: SaveCalendarEventRequest): Result<Unit>
    suspend fun addCalendarEventWithId(
        uid: String,
        eventId: String,
        request: SaveCalendarEventRequest,
    ): Result<Unit> = addCalendarEvent(uid, request)

    suspend fun updateCalendarEvent(uid: String, eventId: String, request: SaveCalendarEventRequest): Result<Unit>
    suspend fun deleteCalendarEvent(uid: String, eventId: String): Result<Unit>

    fun observeScheduledReminders(uid: String): Flow<List<ScheduledReminder>>
    suspend fun addScheduledReminder(
        uid: String,
        reminderId: String = UUID.randomUUID().toString(),
        request: SaveScheduledReminderRequest,
    ): Result<String>

    suspend fun updateScheduledReminder(
        uid: String,
        reminderId: String,
        request: SaveScheduledReminderRequest,
    ): Result<Unit>

    suspend fun updateScheduledReminderState(
        uid: String,
        reminderId: String,
        enabled: Boolean,
        completed: Boolean,
        workRequestId: String,
    ): Result<Unit>

    suspend fun deleteScheduledReminder(uid: String, reminderId: String): Result<Unit>

    fun observeNotifications(uid: String): Flow<List<AppNotification>>
    suspend fun createNotification(uid: String, request: CreateAppNotificationRequest): Result<Unit>
    suspend fun createNotificationWithId(
        uid: String,
        notificationId: String,
        request: CreateAppNotificationRequest,
    ): Result<Unit> = createNotification(uid, request)

    suspend fun markNotificationRead(uid: String, notificationId: String, read: Boolean): Result<Unit>
    suspend fun markAllNotificationsRead(uid: String, unreadIds: List<String>): Result<Unit>
    suspend fun deleteNotification(uid: String, notificationId: String): Result<Unit>
    suspend fun clearNotifications(uid: String, notificationIds: List<String>): Result<Unit>
}

class FirestoreCalendarRemindersRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : CalendarRemindersRepository {

    private fun requireAuthorizedUid(expectedUid: String): String {
        val currentUid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
        require(expectedUid.isNotBlank() && currentUid == expectedUid) {
            "Cannot access or mutate another student's calendar or reminder records."
        }
        return currentUid
    }

    private fun <T : Any> observeSubcollection(
        uid: String,
        subcollection: String,
        clazz: Class<T>,
    ): Flow<List<T>> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/$subcollection"
        emitAll(
            db.collection(COLLECTION_USERS)
                .document(uid)
                .collection(subcollection)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    SnapshotSyncSupport.reportQuerySnapshot(
                        syncCoordinator = syncCoordinator,
                        collectionKey = subcollection,
                        uid = uid,
                        snapshot = snapshot,
                    )
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error

                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                },
        )
    }

    override fun observeCalendarEvents(uid: String): Flow<List<CalendarEvent>> =
        observeSubcollection(uid, SUB_CALENDAR_EVENTS, CalendarEvent::class.java)

    override suspend fun addCalendarEvent(uid: String, request: SaveCalendarEventRequest): Result<Unit> =
        addCalendarEventWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addCalendarEventWithId(
        uid: String,
        eventId: String,
        request: SaveCalendarEventRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = eventId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_CALENDAR_EVENTS/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            validateCalendarEventRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_CALENDAR_EVENTS)
                .document(targetId)
            path = docRef.path
            val existingCached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            if (existingCached != null && existingCached.exists()) {
                return@withContext Result.success(Unit)
            }
            FirestoreWriteExecutor.executeSet(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_CALENDAR_EVENTS,
                displayTitle = request.title.trim(),
                payload = CalendarReminderMappers.toCreateCalendarEventPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_CALENDAR_EVENTS:$activeUid:$targetId",
            )
            Result.success(Unit)
        } catch (_: DuplicateWriteIgnoredException) {
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateCalendarEvent(
        uid: String,
        eventId: String,
        request: SaveCalendarEventRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_CALENDAR_EVENTS/$eventId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(eventId.isNotBlank()) { "Invalid calendar event identifier." }
            validateCalendarEventRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_CALENDAR_EVENTS)
                .document(eventId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_CALENDAR_EVENTS,
                displayTitle = request.title.trim(),
                payload = CalendarReminderMappers.toUpdateCalendarEventPayload(request),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteCalendarEvent(uid: String, eventId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_CALENDAR_EVENTS, eventId, "Calendar event")

    override fun observeScheduledReminders(uid: String): Flow<List<ScheduledReminder>> =
        observeSubcollection(uid, SUB_SCHEDULED_REMINDERS, ScheduledReminder::class.java)

    override suspend fun addScheduledReminder(
        uid: String,
        reminderId: String,
        request: SaveScheduledReminderRequest,
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetId = reminderId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_SCHEDULED_REMINDERS/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            validateReminderRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_SCHEDULED_REMINDERS)
                .document(targetId)
            path = docRef.path
            val existingCached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            if (existingCached != null && existingCached.exists()) {
                return@withContext Result.success(targetId)
            }
            FirestoreWriteExecutor.executeSet(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_SCHEDULED_REMINDERS,
                displayTitle = request.title.trim(),
                payload = CalendarReminderMappers.toCreateReminderPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_SCHEDULED_REMINDERS:$activeUid:$targetId",
            )
            Result.success(targetId)
        } catch (_: DuplicateWriteIgnoredException) {
            Result.success(targetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateScheduledReminder(
        uid: String,
        reminderId: String,
        request: SaveScheduledReminderRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_SCHEDULED_REMINDERS/$reminderId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(reminderId.isNotBlank()) { "Invalid reminder identifier." }
            validateReminderRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_SCHEDULED_REMINDERS)
                .document(reminderId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_SCHEDULED_REMINDERS,
                displayTitle = request.title.trim(),
                payload = CalendarReminderMappers.toUpdateReminderPayload(request),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateScheduledReminderState(
        uid: String,
        reminderId: String,
        enabled: Boolean,
        completed: Boolean,
        workRequestId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_SCHEDULED_REMINDERS/$reminderId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(reminderId.isNotBlank()) { "Invalid reminder identifier." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_SCHEDULED_REMINDERS)
                .document(reminderId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_SCHEDULED_REMINDERS,
                displayTitle = "Reminder state",
                payload = CalendarReminderMappers.toReminderStatePayload(
                    enabled = enabled,
                    completed = completed,
                    workRequestId = workRequestId,
                ),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteScheduledReminder(uid: String, reminderId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_SCHEDULED_REMINDERS, reminderId, "Scheduled reminder")

    override fun observeNotifications(uid: String): Flow<List<AppNotification>> =
        observeSubcollection(uid, SUB_NOTIFICATIONS, AppNotification::class.java)

    override suspend fun createNotification(
        uid: String,
        request: CreateAppNotificationRequest,
    ): Result<Unit> = createNotificationWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun createNotificationWithId(
        uid: String,
        notificationId: String,
        request: CreateAppNotificationRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = notificationId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_NOTIFICATIONS/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(request.title.trim().length in 1..160) {
                "Notification title must be 1–160 characters."
            }
            require(request.body.trim().length in 1..500) {
                "Notification body must be 1–500 characters."
            }
            require(request.deliveredAtMillis > 0L) {
                "Notification timestamp must be valid."
            }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_NOTIFICATIONS)
                .document(targetId)
            path = docRef.path
            val existingCached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            if (existingCached != null && existingCached.exists()) {
                return@withContext Result.success(Unit)
            }
            FirestoreWriteExecutor.executeSet(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_NOTIFICATIONS,
                displayTitle = request.title.trim(),
                payload = CalendarReminderMappers.toCreateNotificationPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_NOTIFICATIONS:$activeUid:$targetId",
            )
            Result.success(Unit)
        } catch (_: DuplicateWriteIgnoredException) {
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun markNotificationRead(
        uid: String,
        notificationId: String,
        read: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_NOTIFICATIONS/$notificationId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(notificationId.isNotBlank()) { "Invalid notification identifier." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_NOTIFICATIONS)
                .document(notificationId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_NOTIFICATIONS,
                displayTitle = "Notification",
                payload = CalendarReminderMappers.toMarkNotificationReadPayload(read),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun markAllNotificationsRead(
        uid: String,
        unreadIds: List<String>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanIds = unreadIds.filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return@withContext Result.success(Unit)
        val path = "users/$uid/$SUB_NOTIFICATIONS"
        try {
            val activeUid = requireAuthorizedUid(uid)
            val collection = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_NOTIFICATIONS)
            cleanIds.chunked(400).forEach { chunk ->
                val batch = db.batch()
                val payload = CalendarReminderMappers.toMarkNotificationReadPayload(true)
                val tracked = chunk.map { id ->
                    val ref = collection.document(id)
                    batch.update(ref, payload)
                    FirestoreWriteExecutor.BatchTrackedWrite(
                        docPath = ref.path,
                        collectionKey = SUB_NOTIFICATIONS,
                        displayTitle = "Notification",
                        operationKind = SyncOperationKind.UPDATE,
                        payload = payload,
                    )
                }
                FirestoreWriteExecutor.executeBatch(
                    syncCoordinator = syncCoordinator,
                    uid = activeUid,
                    batch = batch,
                    trackedWrites = tracked,
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteNotification(uid: String, notificationId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_NOTIFICATIONS, notificationId, "Notification")

    override suspend fun clearNotifications(
        uid: String,
        notificationIds: List<String>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanIds = notificationIds.filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return@withContext Result.success(Unit)
        val path = "users/$uid/$SUB_NOTIFICATIONS"
        try {
            val activeUid = requireAuthorizedUid(uid)
            val collection = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_NOTIFICATIONS)
            cleanIds.chunked(400).forEach { chunk ->
                val batch = db.batch()
                val tracked = chunk.map { id ->
                    val ref = collection.document(id)
                    batch.delete(ref)
                    FirestoreWriteExecutor.BatchTrackedWrite(
                        docPath = ref.path,
                        collectionKey = SUB_NOTIFICATIONS,
                        displayTitle = "Notification",
                        operationKind = SyncOperationKind.DELETE,
                        payload = emptyMap(),
                    )
                }
                FirestoreWriteExecutor.executeBatch(
                    syncCoordinator = syncCoordinator,
                    uid = activeUid,
                    batch = batch,
                    trackedWrites = tracked,
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun validateCalendarEventRequest(request: SaveCalendarEventRequest) {
        require(request.title.trim().length in 1..160) {
            "Event title must be 1–160 characters."
        }
        require(request.description.trim().length <= 1000) {
            "Description must be 1000 characters or fewer."
        }
        require(request.locationOrCourse.trim().length <= 120) {
            "Location or course must be 120 characters or fewer."
        }
        require(request.startAtMillis > 0L && request.endAtMillis >= request.startAtMillis) {
            "Event end time must not be earlier than start time."
        }
    }

    private fun validateReminderRequest(request: SaveScheduledReminderRequest) {
        require(request.title.trim().length in 1..160) {
            "Reminder title must be 1–160 characters."
        }
        require(request.notes.trim().length <= 500) {
            "Notes must be 500 characters or fewer."
        }
        require(request.triggerAtMillis > 0L) {
            "Please select a valid reminder date and time."
        }
        require(request.leadMinutes in 0L..10080L) {
            "Lead time must be between 0 minutes and 7 days."
        }
    }

    private suspend fun deleteSubcollectionDoc(
        uid: String,
        subcollection: String,
        docId: String,
        displayTitle: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$subcollection/$docId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(docId.isNotBlank()) { "Invalid document identifier." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(subcollection)
                .document(docId)
            path = docRef.path
            FirestoreWriteExecutor.executeDelete(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = subcollection,
                displayTitle = displayTitle,
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val SUB_CALENDAR_EVENTS = "calendarEvents"
        const val SUB_SCHEDULED_REMINDERS = "scheduledReminders"
        const val SUB_NOTIFICATIONS = "notifications"
    }
}
