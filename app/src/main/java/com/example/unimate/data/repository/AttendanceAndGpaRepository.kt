package com.example.unimate.data.repository

import com.example.unimate.data.firebase.OperationType
import com.example.unimate.data.firebase.handleFirestoreError
import com.example.unimate.data.model.AcademicSettings
import com.example.unimate.data.model.AttendanceGpaMappers
import com.example.unimate.data.model.AttendanceLog
import com.example.unimate.data.model.AttendanceStatus
import com.example.unimate.data.model.SaveAcademicSettingsRequest
import com.example.unimate.data.model.SaveAttendanceLogRequest
import com.example.unimate.data.model.SaveSemesterGpaRequest
import com.example.unimate.data.model.SemesterEntryMode
import com.example.unimate.data.model.SemesterGpaRecord
import com.example.unimate.data.model.WorkspaceMappers
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

interface AttendanceAndGpaRepository {
    fun observeAttendanceLogs(uid: String): Flow<List<AttendanceLog>>
    fun observeAcademicSettings(uid: String): Flow<AcademicSettings>
    fun observeSemesterGpaRecords(uid: String): Flow<List<SemesterGpaRecord>>

    suspend fun logAttendanceSession(uid: String, request: SaveAttendanceLogRequest): Result<Unit>
    suspend fun logAttendanceSessionWithId(
        uid: String,
        logId: String,
        request: SaveAttendanceLogRequest,
    ): Result<Unit> = logAttendanceSession(uid, request)

    suspend fun updateAttendanceLogStatus(
        uid: String,
        log: AttendanceLog,
        newStatus: AttendanceStatus,
        notes: String = log.notes,
    ): Result<Unit>

    suspend fun deleteAttendanceLog(uid: String, log: AttendanceLog): Result<Unit>

    suspend fun saveAcademicSettings(uid: String, request: SaveAcademicSettingsRequest): Result<Unit>

    suspend fun addSemesterGpaRecord(uid: String, request: SaveSemesterGpaRequest): Result<Unit>
    suspend fun addSemesterGpaRecordWithId(
        uid: String,
        recordId: String,
        request: SaveSemesterGpaRequest,
    ): Result<Unit> = addSemesterGpaRecord(uid, request)

    suspend fun updateSemesterGpaRecord(
        uid: String,
        recordId: String,
        request: SaveSemesterGpaRequest,
    ): Result<Unit>

    suspend fun deleteSemesterGpaRecord(uid: String, recordId: String): Result<Unit>
}

class FirestoreAttendanceAndGpaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : AttendanceAndGpaRepository {

    private fun requireAuthorizedUid(expectedUid: String): String {
        val currentUid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
        require(expectedUid.isNotBlank() && currentUid == expectedUid) {
            "Cannot access or mutate another student's academic records."
        }
        return currentUid
    }

    override fun observeAttendanceLogs(uid: String): Flow<List<AttendanceLog>> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/$SUB_ATTENDANCE_LOGS"
        emitAll(
            db.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUB_ATTENDANCE_LOGS)
                .orderBy("sessionDateMillis", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    SnapshotSyncSupport.reportQuerySnapshot(
                        syncCoordinator = syncCoordinator,
                        collectionKey = SUB_ATTENDANCE_LOGS,
                        uid = uid,
                        snapshot = snapshot,
                    )
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            AttendanceLog::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        )
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                },
        )
    }

    override fun observeAcademicSettings(uid: String): Flow<AcademicSettings> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(AcademicSettings(uid = uid))
            return@flow
        }
        val path = "users/$uid/$SUB_ACADEMIC_SETTINGS/$DOC_PREFERENCES"
        emitAll(
            db.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUB_ACADEMIC_SETTINGS)
                .document(DOC_PREFERENCES)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    SnapshotSyncSupport.reportDocumentSnapshot(
                        syncCoordinator = syncCoordinator,
                        collectionKey = SUB_ACADEMIC_SETTINGS,
                        uid = uid,
                        snapshot = snapshot,
                    )
                    snapshot.toObject(
                        AcademicSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ) ?: AcademicSettings(uid = uid)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, uid)
                },
        )
    }

    override fun observeSemesterGpaRecords(uid: String): Flow<List<SemesterGpaRecord>> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/$SUB_SEMESTER_GPA"
        emitAll(
            db.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUB_SEMESTER_GPA)
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    SnapshotSyncSupport.reportQuerySnapshot(
                        syncCoordinator = syncCoordinator,
                        collectionKey = SUB_SEMESTER_GPA,
                        uid = uid,
                        snapshot = snapshot,
                    )
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            SemesterGpaRecord::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        )
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                },
        )
    }

    override suspend fun logAttendanceSession(
        uid: String,
        request: SaveAttendanceLogRequest,
    ): Result<Unit> = logAttendanceSessionWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun logAttendanceSessionWithId(
        uid: String,
        logId: String,
        request: SaveAttendanceLogRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = logId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_ATTENDANCE_LOGS/$targetId"
        val dedupKey = "$SUB_ATTENDANCE_LOGS:$uid:$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(request.courseId.trim().isNotBlank()) { "Please select a valid course." }
            require(request.courseCode.trim().length in 1..24) { "Invalid course code." }
            require(request.courseName.trim().length in 1..120) { "Invalid course name." }
            require(request.sessionDateMillis > 0L) { "Please select a valid session date." }
            require(request.notes.trim().length <= 400) { "Session notes must be 400 characters or fewer." }

            if (syncCoordinator.isDuplicateInFlight(dedupKey)) {
                return@withContext Result.success(Unit)
            }
            syncCoordinator.markInFlight(dedupKey)

            val userDoc = db.collection(COLLECTION_USERS).document(activeUid)
            val logRef = userDoc.collection(SUB_ATTENDANCE_LOGS).document(targetId)
            val courseRef = userDoc.collection(SUB_COURSES).document(request.courseId.trim())
            path = logRef.path

            val existingCached = runCatching { logRef.get(Source.CACHE).await() }.getOrNull()
            if (existingCached != null && existingCached.exists()) {
                syncCoordinator.clearInFlight(dedupKey)
                return@withContext Result.success(Unit)
            }

            val logPayload = AttendanceGpaMappers.toCreateAttendanceLogPayload(activeUid, request)
            var txSucceeded = false
            if (syncCoordinator.isOnline) {
                txSucceeded = try {
                    db.runTransaction { tx ->
                        val courseSnap = tx.get(courseRef)
                        if (courseSnap.exists()) {
                            val currentAttended = (courseSnap.get("attendedClasses") as? Number)?.toLong() ?: 0L
                            val currentTotal = (courseSnap.get("totalClasses") as? Number)?.toLong() ?: 0L
                            val nextTotal = (currentTotal + 1L).coerceAtMost(1000L)
                            val nextAttended = if (request.status == AttendanceStatus.PRESENT) {
                                (currentAttended + 1L).coerceAtMost(nextTotal)
                            } else {
                                currentAttended.coerceAtMost(nextTotal)
                            }
                            tx.update(
                                courseRef,
                                WorkspaceMappers.toUpdateAttendancePayload(nextAttended, nextTotal),
                            )
                        }
                        tx.set(logRef, logPayload)
                        null
                    }.await()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!isTransientOfflineException(e)) throw e
                    false
                }
            }

            if (!txSucceeded) {
                val cachedCourse = runCatching { courseRef.get(Source.CACHE).await() }.getOrNull()
                val batch = db.batch()
                val tracked = mutableListOf<FirestoreWriteExecutor.BatchTrackedWrite>()
                if (cachedCourse != null && cachedCourse.exists()) {
                    val currentAttended = (cachedCourse.get("attendedClasses") as? Number)?.toLong() ?: 0L
                    val currentTotal = (cachedCourse.get("totalClasses") as? Number)?.toLong() ?: 0L
                    val nextTotal = (currentTotal + 1L).coerceAtMost(1000L)
                    val nextAttended = if (request.status == AttendanceStatus.PRESENT) {
                        (currentAttended + 1L).coerceAtMost(nextTotal)
                    } else {
                        currentAttended.coerceAtMost(nextTotal)
                    }
                    val attendancePayload = WorkspaceMappers.toUpdateAttendancePayload(nextAttended, nextTotal)
                    batch.update(courseRef, attendancePayload)
                    tracked.add(
                        FirestoreWriteExecutor.BatchTrackedWrite(
                            docPath = courseRef.path,
                            collectionKey = SUB_COURSES,
                            displayTitle = request.courseName.trim(),
                            operationKind = SyncOperationKind.UPDATE,
                            payload = attendancePayload,
                        ),
                    )
                }
                batch.set(logRef, logPayload)
                tracked.add(
                    FirestoreWriteExecutor.BatchTrackedWrite(
                        docPath = logRef.path,
                        collectionKey = SUB_ATTENDANCE_LOGS,
                        displayTitle = "${request.courseCode.trim()} attendance",
                        operationKind = SyncOperationKind.CREATE,
                        payload = logPayload,
                    ),
                )
                FirestoreWriteExecutor.executeBatch(
                    syncCoordinator = syncCoordinator,
                    uid = activeUid,
                    batch = batch,
                    trackedWrites = tracked,
                )
            }

            syncCoordinator.clearInFlight(dedupKey)
            Result.success(Unit)
        } catch (e: CancellationException) {
            syncCoordinator.clearInFlight(dedupKey)
            throw e
        } catch (e: Exception) {
            syncCoordinator.clearInFlight(dedupKey)
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateAttendanceLogStatus(
        uid: String,
        log: AttendanceLog,
        newStatus: AttendanceStatus,
        notes: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_ATTENDANCE_LOGS/${log.id}"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(log.id.isNotBlank()) { "Invalid attendance log identifier." }
            require(notes.trim().length <= 400) { "Session notes must be 400 characters or fewer." }

            val userDoc = db.collection(COLLECTION_USERS).document(activeUid)
            val logRef = userDoc.collection(SUB_ATTENDANCE_LOGS).document(log.id)
            val courseRef = userDoc.collection(SUB_COURSES).document(log.courseId)
            path = logRef.path

            val oldStatus = log.resolvedStatus()
            val deltaAttended = when {
                oldStatus == newStatus -> 0L
                newStatus == AttendanceStatus.PRESENT -> 1L
                else -> -1L
            }
            val logUpdatePayload = AttendanceGpaMappers.toUpdateAttendanceLogPayload(
                status = newStatus,
                sessionDateMillis = log.sessionDateMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
                notes = notes,
            )

            var txSucceeded = false
            if (syncCoordinator.isOnline) {
                txSucceeded = try {
                    db.runTransaction { tx ->
                        val courseSnap = if (log.courseId.isNotBlank() && deltaAttended != 0L) tx.get(courseRef) else null
                        if (courseSnap != null && courseSnap.exists()) {
                            val currentAttended = (courseSnap.get("attendedClasses") as? Number)?.toLong() ?: 0L
                            val currentTotal = (courseSnap.get("totalClasses") as? Number)?.toLong() ?: 0L
                            val nextAttended = (currentAttended + deltaAttended).coerceIn(0L, currentTotal)
                            tx.update(
                                courseRef,
                                WorkspaceMappers.toUpdateAttendancePayload(nextAttended, currentTotal),
                            )
                        }
                        tx.update(logRef, logUpdatePayload)
                        null
                    }.await()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!isTransientOfflineException(e)) throw e
                    false
                }
            }

            if (!txSucceeded) {
                val batch = db.batch()
                val tracked = mutableListOf<FirestoreWriteExecutor.BatchTrackedWrite>()
                val cachedCourse = if (log.courseId.isNotBlank() && deltaAttended != 0L) {
                    runCatching { courseRef.get(Source.CACHE).await() }.getOrNull()
                } else {
                    null
                }
                if (cachedCourse != null && cachedCourse.exists()) {
                    val currentAttended = (cachedCourse.get("attendedClasses") as? Number)?.toLong() ?: 0L
                    val currentTotal = (cachedCourse.get("totalClasses") as? Number)?.toLong() ?: 0L
                    val nextAttended = (currentAttended + deltaAttended).coerceIn(0L, currentTotal)
                    val attendancePayload = WorkspaceMappers.toUpdateAttendancePayload(nextAttended, currentTotal)
                    batch.update(courseRef, attendancePayload)
                    tracked.add(
                        FirestoreWriteExecutor.BatchTrackedWrite(
                            docPath = courseRef.path,
                            collectionKey = SUB_COURSES,
                            displayTitle = log.courseName.ifBlank { "Course attendance" },
                            operationKind = SyncOperationKind.UPDATE,
                            payload = attendancePayload,
                        ),
                    )
                }
                batch.update(logRef, logUpdatePayload)
                tracked.add(
                    FirestoreWriteExecutor.BatchTrackedWrite(
                        docPath = logRef.path,
                        collectionKey = SUB_ATTENDANCE_LOGS,
                        displayTitle = "${log.courseCode} attendance",
                        operationKind = SyncOperationKind.UPDATE,
                        payload = logUpdatePayload,
                    ),
                )
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

    override suspend fun deleteAttendanceLog(uid: String, log: AttendanceLog): Result<Unit> =
        withContext(Dispatchers.IO) {
            var path = "users/$uid/$SUB_ATTENDANCE_LOGS/${log.id}"
            try {
                val activeUid = requireAuthorizedUid(uid)
                require(log.id.isNotBlank()) { "Invalid attendance log identifier." }

                val userDoc = db.collection(COLLECTION_USERS).document(activeUid)
                val logRef = userDoc.collection(SUB_ATTENDANCE_LOGS).document(log.id)
                val courseRef = userDoc.collection(SUB_COURSES).document(log.courseId)
                path = logRef.path

                var txSucceeded = false
                if (syncCoordinator.isOnline) {
                    txSucceeded = try {
                        db.runTransaction { tx ->
                            val courseSnap = if (log.courseId.isNotBlank()) tx.get(courseRef) else null
                            if (courseSnap != null && courseSnap.exists()) {
                                val currentAttended = (courseSnap.get("attendedClasses") as? Number)?.toLong() ?: 0L
                                val currentTotal = (courseSnap.get("totalClasses") as? Number)?.toLong() ?: 0L
                                val nextTotal = (currentTotal - 1L).coerceAtLeast(0L)
                                val nextAttended = if (log.resolvedStatus() == AttendanceStatus.PRESENT) {
                                    (currentAttended - 1L).coerceIn(0L, nextTotal)
                                } else {
                                    currentAttended.coerceIn(0L, nextTotal)
                                }
                                tx.update(
                                    courseRef,
                                    WorkspaceMappers.toUpdateAttendancePayload(nextAttended, nextTotal),
                                )
                            }
                            tx.delete(logRef)
                            null
                        }.await()
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (!isTransientOfflineException(e)) throw e
                        false
                    }
                }

                if (!txSucceeded) {
                    val batch = db.batch()
                    val tracked = mutableListOf<FirestoreWriteExecutor.BatchTrackedWrite>()
                    val cachedCourse = if (log.courseId.isNotBlank()) {
                        runCatching { courseRef.get(Source.CACHE).await() }.getOrNull()
                    } else {
                        null
                    }
                    if (cachedCourse != null && cachedCourse.exists()) {
                        val currentAttended = (cachedCourse.get("attendedClasses") as? Number)?.toLong() ?: 0L
                        val currentTotal = (cachedCourse.get("totalClasses") as? Number)?.toLong() ?: 0L
                        val nextTotal = (currentTotal - 1L).coerceAtLeast(0L)
                        val nextAttended = if (log.resolvedStatus() == AttendanceStatus.PRESENT) {
                            (currentAttended - 1L).coerceIn(0L, nextTotal)
                        } else {
                            currentAttended.coerceIn(0L, nextTotal)
                        }
                        val attendancePayload = WorkspaceMappers.toUpdateAttendancePayload(nextAttended, nextTotal)
                        batch.update(courseRef, attendancePayload)
                        tracked.add(
                            FirestoreWriteExecutor.BatchTrackedWrite(
                                docPath = courseRef.path,
                                collectionKey = SUB_COURSES,
                                displayTitle = log.courseName.ifBlank { "Course attendance" },
                                operationKind = SyncOperationKind.UPDATE,
                                payload = attendancePayload,
                            ),
                        )
                    }
                    batch.delete(logRef)
                    tracked.add(
                        FirestoreWriteExecutor.BatchTrackedWrite(
                            docPath = logRef.path,
                            collectionKey = SUB_ATTENDANCE_LOGS,
                            displayTitle = "${log.courseCode} attendance log",
                            operationKind = SyncOperationKind.DELETE,
                            payload = emptyMap(),
                        ),
                    )
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

    override suspend fun saveAcademicSettings(
        uid: String,
        request: SaveAcademicSettingsRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_ACADEMIC_SETTINGS/$DOC_PREFERENCES"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(request.attendanceThresholdPercent in 1..100) {
                "Attendance threshold must be between 1% and 100%."
            }
            require(request.scaleName.trim().length in 1..100) {
                "Grading scale name must be between 1 and 100 characters."
            }
            require(request.gradingScale.size in 1..20) {
                "Grading scale must contain between 1 and 20 grade rows."
            }
            val cleanLetters = request.gradingScale.map { it.letter.trim().uppercase() }
            require(cleanLetters.all { it.isNotEmpty() && it.length <= 6 }) {
                "Each grade letter must be 1–6 characters."
            }
            require(cleanLetters.distinct().size == cleanLetters.size) {
                "Duplicate grade letters are not allowed."
            }
            require(request.gradingScale.all { it.points.isFinite() && it.points in 0.0..10.0 }) {
                "Grade points must be finite values between 0.0 and 10.0."
            }

            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_ACADEMIC_SETTINGS)
                .document(DOC_PREFERENCES)
            path = docRef.path

            val cachedSnap = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            val existsLocally = cachedSnap?.exists() == true ||
                syncCoordinator.getKnownDocSnapshot(docRef.path) != null
            val existsRemotely = if (!existsLocally && syncCoordinator.isOnline) {
                withTimeoutOrNull(1_500L) {
                    runCatching { docRef.get(Source.SERVER).await().exists() }.getOrDefault(false)
                } ?: false
            } else {
                false
            }
            val shouldUpdate = existsLocally || existsRemotely

            if (shouldUpdate) {
                FirestoreWriteExecutor.executeUpdate(
                    syncCoordinator = syncCoordinator,
                    uid = activeUid,
                    docRef = docRef,
                    collectionKey = SUB_ACADEMIC_SETTINGS,
                    displayTitle = "Academic settings",
                    payload = AttendanceGpaMappers.toSaveAcademicSettingsPayload(
                        uid = activeUid,
                        request = request,
                        isCreate = false,
                    ),
                )
            } else {
                FirestoreWriteExecutor.executeSet(
                    syncCoordinator = syncCoordinator,
                    uid = activeUid,
                    docRef = docRef,
                    collectionKey = SUB_ACADEMIC_SETTINGS,
                    displayTitle = "Academic settings",
                    payload = AttendanceGpaMappers.toSaveAcademicSettingsPayload(
                        uid = activeUid,
                        request = request,
                        isCreate = true,
                    ),
                    merge = false,
                    isCreate = true,
                    dedupKey = "$SUB_ACADEMIC_SETTINGS:$activeUid",
                )
            }
            Result.success(Unit)
        } catch (_: DuplicateWriteIgnoredException) {
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun addSemesterGpaRecord(
        uid: String,
        request: SaveSemesterGpaRequest,
    ): Result<Unit> = addSemesterGpaRecordWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addSemesterGpaRecordWithId(
        uid: String,
        recordId: String,
        request: SaveSemesterGpaRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = recordId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_SEMESTER_GPA/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            validateSemesterRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_SEMESTER_GPA)
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
                collectionKey = SUB_SEMESTER_GPA,
                displayTitle = request.semesterName.trim(),
                payload = AttendanceGpaMappers.toCreateSemesterGpaPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_SEMESTER_GPA:$activeUid:$targetId",
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

    override suspend fun updateSemesterGpaRecord(
        uid: String,
        recordId: String,
        request: SaveSemesterGpaRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_SEMESTER_GPA/$recordId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(recordId.isNotBlank()) { "Invalid semester record identifier." }
            validateSemesterRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_SEMESTER_GPA)
                .document(recordId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_SEMESTER_GPA,
                displayTitle = request.semesterName.trim(),
                payload = AttendanceGpaMappers.toUpdateSemesterGpaPayload(request),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteSemesterGpaRecord(uid: String, recordId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            var path = "users/$uid/$SUB_SEMESTER_GPA/$recordId"
            try {
                val activeUid = requireAuthorizedUid(uid)
                require(recordId.isNotBlank()) { "Invalid semester record identifier." }
                val docRef = db.collection(COLLECTION_USERS)
                    .document(activeUid)
                    .collection(SUB_SEMESTER_GPA)
                    .document(recordId)
                path = docRef.path
                FirestoreWriteExecutor.executeDelete(
                    syncCoordinator = syncCoordinator,
                    uid = activeUid,
                    docRef = docRef,
                    collectionKey = SUB_SEMESTER_GPA,
                    displayTitle = "Semester GPA record",
                )
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, path)
                Result.failure(e)
            }
        }

    private fun isTransientOfflineException(error: Throwable): Boolean {
        val code = (error as? FirebaseFirestoreException)?.code
        return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
    }

    private fun validateSemesterRequest(request: SaveSemesterGpaRequest) {
        require(request.semesterName.trim().length in 1..80) {
            "Semester name must be between 1 and 80 characters."
        }
        require(request.orderIndex in 0..100) {
            "Semester order must be between 0 and 100."
        }
        require(request.courses.size <= 24) {
            "Maximum 24 courses allowed per semester record."
        }
        if (request.entryMode == SemesterEntryMode.SUMMARY) {
            require(request.summaryCredits.isFinite() && request.summaryCredits in 0.5..100.0) {
                "Previous semester credit hours must be between 0.5 and 100.0."
            }
            require(request.summaryGpa.isFinite() && request.summaryGpa in 0.0..10.0) {
                "Previous semester GPA must be between 0.00 and 10.00."
            }
        } else {
            request.courses.forEach { course ->
                require(course.courseName.trim().length in 1..120) {
                    "Each course must have a name (1–120 characters)."
                }
                require(course.creditHours.isFinite() && course.creditHours in 0.5..12.0) {
                    "Credit hours for ${course.courseName} must be between 0.5 and 12.0."
                }
                require(course.gradePoints.isFinite() && course.gradePoints in 0.0..10.0) {
                    "Grade points for ${course.courseName} must be between 0.0 and 10.0."
                }
            }
        }
    }

    private fun handleFlowError(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String,
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val SUB_COURSES = "courses"
        const val SUB_ATTENDANCE_LOGS = "attendanceLogs"
        const val SUB_ACADEMIC_SETTINGS = "academicSettings"
        const val SUB_SEMESTER_GPA = "semesterGpaRecords"
        const val DOC_PREFERENCES = "preferences"
    }
}
