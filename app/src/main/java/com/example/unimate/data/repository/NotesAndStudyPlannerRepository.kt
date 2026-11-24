package com.example.unimate.data.repository

import com.example.unimate.data.firebase.OperationType
import com.example.unimate.data.firebase.handleFirestoreError
import com.example.unimate.data.model.NotesAndPlannerMappers
import com.example.unimate.data.model.SaveNoteRequest
import com.example.unimate.data.model.SaveStudyPlannerSettingsRequest
import com.example.unimate.data.model.SaveStudySessionRequest
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.model.StudyPlannerCalculator
import com.example.unimate.data.model.StudyPlannerSettings
import com.example.unimate.data.model.StudySession
import com.example.unimate.data.sync.DuplicateWriteIgnoredException
import com.example.unimate.data.sync.FirestoreWriteExecutor
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.SnapshotSyncSupport
import com.example.unimate.data.sync.SyncCoordinator
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

interface NotesAndStudyPlannerRepository {
    fun observeNotes(uid: String): Flow<List<StudyNote>>
    suspend fun createNote(uid: String, request: SaveNoteRequest): Result<String>
    suspend fun createNoteWithId(
        uid: String,
        noteId: String,
        request: SaveNoteRequest,
    ): Result<String> = createNote(uid, request)

    suspend fun updateNote(uid: String, noteId: String, request: SaveNoteRequest): Result<Unit>
    suspend fun toggleNotePinned(uid: String, noteId: String, pinned: Boolean): Result<Unit>
    suspend fun toggleNoteFavorite(uid: String, noteId: String, favorite: Boolean): Result<Unit>
    suspend fun deleteNote(uid: String, noteId: String): Result<Unit>

    fun observeStudySessions(uid: String): Flow<List<StudySession>>
    suspend fun createStudySession(uid: String, request: SaveStudySessionRequest): Result<String>
    suspend fun createStudySessionWithId(
        uid: String,
        sessionId: String,
        request: SaveStudySessionRequest,
    ): Result<String> = createStudySession(uid, request)

    suspend fun updateStudySession(uid: String, sessionId: String, request: SaveStudySessionRequest): Result<Unit>
    suspend fun markStudySessionCompleted(
        uid: String,
        sessionId: String,
        completed: Boolean,
        completedAtMillis: Long = System.currentTimeMillis(),
    ): Result<Unit>

    suspend fun deleteStudySession(uid: String, sessionId: String): Result<Unit>

    fun observePlannerSettings(uid: String): Flow<StudyPlannerSettings>
    suspend fun savePlannerSettings(uid: String, request: SaveStudyPlannerSettingsRequest): Result<Unit>
}

class FirestoreNotesAndStudyPlannerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val localCache: StudyPlannerLocalCache? = null,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : NotesAndStudyPlannerRepository {

    private fun requireAuthorizedUid(expectedUid: String): String {
        val currentUid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
        require(expectedUid.isNotBlank() && currentUid == expectedUid) {
            "Cannot access or mutate another student's notes or study planner."
        }
        return currentUid
    }

    override fun observeNotes(uid: String): Flow<List<StudyNote>> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(emptyList())
            return@flow
        }
        val cached = localCache?.loadCachedNotes(uid).orEmpty()
        if (cached.isNotEmpty()) {
            emit(cached)
        }
        val path = "users/$uid/$SUB_NOTES"
        emitAll(
            db.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUB_NOTES)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    SnapshotSyncSupport.reportQuerySnapshot(
                        syncCoordinator = syncCoordinator,
                        collectionKey = SUB_NOTES,
                        uid = uid,
                        snapshot = snapshot,
                    )
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(StudyNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                }
                .onEach { list ->
                    localCache?.cacheNotes(uid, list)
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

    override suspend fun createNote(uid: String, request: SaveNoteRequest): Result<String> =
        createNoteWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun createNoteWithId(
        uid: String,
        noteId: String,
        request: SaveNoteRequest,
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetId = noteId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_NOTES/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            validateNoteRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_NOTES)
                .document(targetId)
            path = docRef.path
            val existingCached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            if (existingCached != null && existingCached.exists()) {
                return@withContext Result.success(docRef.id)
            }
            FirestoreWriteExecutor.executeSet(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_NOTES,
                displayTitle = request.title.trim(),
                payload = NotesAndPlannerMappers.toCreateNotePayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_NOTES:$activeUid:$targetId",
            )
            Result.success(docRef.id)
        } catch (_: DuplicateWriteIgnoredException) {
            Result.success(targetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateNote(
        uid: String,
        noteId: String,
        request: SaveNoteRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_NOTES/$noteId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(noteId.isNotBlank()) { "Invalid note identifier." }
            validateNoteRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_NOTES)
                .document(noteId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_NOTES,
                displayTitle = request.title.trim(),
                payload = NotesAndPlannerMappers.toUpdateNotePayload(request),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun toggleNotePinned(
        uid: String,
        noteId: String,
        pinned: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_NOTES/$noteId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(noteId.isNotBlank()) { "Invalid note identifier." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_NOTES)
                .document(noteId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_NOTES,
                displayTitle = "Note pin",
                payload = NotesAndPlannerMappers.toToggleNotePinnedPayload(pinned),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun toggleNoteFavorite(
        uid: String,
        noteId: String,
        favorite: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_NOTES/$noteId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(noteId.isNotBlank()) { "Invalid note identifier." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_NOTES)
                .document(noteId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_NOTES,
                displayTitle = "Note favorite",
                payload = NotesAndPlannerMappers.toToggleNoteFavoritePayload(favorite),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteNote(uid: String, noteId: String): Result<Unit> =
        deleteDoc(uid, SUB_NOTES, noteId, "Note")

    override fun observeStudySessions(uid: String): Flow<List<StudySession>> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(emptyList())
            return@flow
        }
        val cached = localCache?.loadCachedSessions(uid).orEmpty()
        if (cached.isNotEmpty()) {
            emit(cached)
        }
        val path = "users/$uid/$SUB_STUDY_SESSIONS"
        emitAll(
            db.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUB_STUDY_SESSIONS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    SnapshotSyncSupport.reportQuerySnapshot(
                        syncCoordinator = syncCoordinator,
                        collectionKey = SUB_STUDY_SESSIONS,
                        uid = uid,
                        snapshot = snapshot,
                    )
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(StudySession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                }
                .onEach { list ->
                    localCache?.cacheSessions(uid, list)
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

    override suspend fun createStudySession(
        uid: String,
        request: SaveStudySessionRequest,
    ): Result<String> = createStudySessionWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun createStudySessionWithId(
        uid: String,
        sessionId: String,
        request: SaveStudySessionRequest,
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetId = sessionId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_STUDY_SESSIONS/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            validateStudySessionRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_STUDY_SESSIONS)
                .document(targetId)
            path = docRef.path
            val existingCached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            if (existingCached != null && existingCached.exists()) {
                return@withContext Result.success(docRef.id)
            }
            FirestoreWriteExecutor.executeSet(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_STUDY_SESSIONS,
                displayTitle = request.title.trim(),
                payload = NotesAndPlannerMappers.toCreateStudySessionPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_STUDY_SESSIONS:$activeUid:$targetId",
            )
            Result.success(docRef.id)
        } catch (_: DuplicateWriteIgnoredException) {
            Result.success(targetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateStudySession(
        uid: String,
        sessionId: String,
        request: SaveStudySessionRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_STUDY_SESSIONS/$sessionId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(sessionId.isNotBlank()) { "Invalid study session identifier." }
            validateStudySessionRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_STUDY_SESSIONS)
                .document(sessionId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_STUDY_SESSIONS,
                displayTitle = request.title.trim(),
                payload = NotesAndPlannerMappers.toUpdateStudySessionPayload(request),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun markStudySessionCompleted(
        uid: String,
        sessionId: String,
        completed: Boolean,
        completedAtMillis: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_STUDY_SESSIONS/$sessionId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(sessionId.isNotBlank()) { "Invalid study session identifier." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_STUDY_SESSIONS)
                .document(sessionId)
            path = docRef.path
            val dateKey = StudyPlannerCalculator.toDateKey(completedAtMillis)
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_STUDY_SESSIONS,
                displayTitle = "Study session",
                payload = NotesAndPlannerMappers.toMarkSessionCompletedPayload(
                    completed = completed,
                    completedAtMillis = completedAtMillis,
                    studyDateKey = dateKey,
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

    override suspend fun deleteStudySession(uid: String, sessionId: String): Result<Unit> =
        deleteDoc(uid, SUB_STUDY_SESSIONS, sessionId, "Study session")

    override fun observePlannerSettings(uid: String): Flow<StudyPlannerSettings> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(StudyPlannerSettings())
            return@flow
        }
        val cached = localCache?.loadCachedSettings(uid)
        if (cached != null) {
            emit(cached)
        }
        val docRef = db.collection(COLLECTION_USERS)
            .document(uid)
            .collection(SUB_PLANNER_SETTINGS)
            .document(DOC_GOALS)
        val path = docRef.path
        emitAll(
            docRef.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    SnapshotSyncSupport.reportDocumentSnapshot(
                        syncCoordinator = syncCoordinator,
                        collectionKey = SUB_PLANNER_SETTINGS,
                        uid = uid,
                        snapshot = snapshot,
                    )
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            StudyPlannerSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        ) ?: StudyPlannerSettings(uid = uid)
                    } else {
                        cached ?: StudyPlannerSettings(uid = uid)
                    }
                }
                .onEach { settings ->
                    localCache?.cacheSettings(uid, settings)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                },
        )
    }

    override suspend fun savePlannerSettings(
        uid: String,
        request: SaveStudyPlannerSettingsRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_PLANNER_SETTINGS/$DOC_GOALS"
        var operation = OperationType.WRITE
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(request.dailyGoalMinutes in 15L..720L) {
                "Daily study goal must be between 15 and 720 minutes."
            }
            require(request.weeklyGoalMinutes in 30L..5040L) {
                "Weekly study goal must be between 30 and 5040 minutes."
            }
            require(request.pomodoroFocusMinutes in 5L..120L) {
                "Pomodoro focus duration must be between 5 and 120 minutes."
            }
            require(request.pomodoroBreakMinutes in 1L..60L) {
                "Pomodoro break duration must be between 1 and 60 minutes."
            }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_PLANNER_SETTINGS)
                .document(DOC_GOALS)
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
                operation = OperationType.UPDATE
                FirestoreWriteExecutor.executeUpdate(
                    syncCoordinator = syncCoordinator,
                    uid = activeUid,
                    docRef = docRef,
                    collectionKey = SUB_PLANNER_SETTINGS,
                    displayTitle = "Study planner goals",
                    payload = NotesAndPlannerMappers.toUpdatePlannerSettingsPayload(request),
                )
            } else {
                operation = OperationType.CREATE
                FirestoreWriteExecutor.executeSet(
                    syncCoordinator = syncCoordinator,
                    uid = activeUid,
                    docRef = docRef,
                    collectionKey = SUB_PLANNER_SETTINGS,
                    displayTitle = "Study planner goals",
                    payload = NotesAndPlannerMappers.toCreatePlannerSettingsPayload(activeUid, request),
                    merge = false,
                    isCreate = true,
                    dedupKey = "$SUB_PLANNER_SETTINGS:$activeUid",
                )
            }
            localCache?.cacheSettings(
                activeUid,
                StudyPlannerSettings(
                    uid = activeUid,
                    dailyGoalMinutes = request.dailyGoalMinutes,
                    weeklyGoalMinutes = request.weeklyGoalMinutes,
                    pomodoroFocusMinutes = request.pomodoroFocusMinutes,
                    pomodoroBreakMinutes = request.pomodoroBreakMinutes,
                ),
            )
            Result.success(Unit)
        } catch (_: DuplicateWriteIgnoredException) {
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, operation, path)
            Result.failure(e)
        }
    }

    private fun validateNoteRequest(request: SaveNoteRequest) {
        require(request.title.trim().length in 1..140) {
            "Note title must be between 1 and 140 characters."
        }
        require(request.courseName.trim().length in 1..100) {
            "Subject/course tag must be between 1 and 100 characters."
        }
        require(request.content.trim().length in 1..10000) {
            "Note content must be between 1 and 10,000 characters."
        }
        require(request.attachments.size <= 10) {
            "A note can have at most 10 attached files or links."
        }
    }

    private fun validateStudySessionRequest(request: SaveStudySessionRequest) {
        require(request.title.trim().length in 1..160) {
            "Session title must be between 1 and 160 characters."
        }
        require(request.subject.trim().length in 1..100) {
            "Subject name must be between 1 and 100 characters."
        }
        require(request.durationMinutes in 1L..600L) {
            "Session duration must be between 1 and 600 minutes."
        }
        require(Regex("^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$").matches(request.studyDateKey.trim())) {
            "Invalid study date key format."
        }
        require(request.notes.trim().length <= 500) {
            "Session notes must be 500 characters or fewer."
        }
    }

    private suspend fun deleteDoc(
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
        const val SUB_NOTES = "notes"
        const val SUB_STUDY_SESSIONS = "studySessions"
        const val SUB_PLANNER_SETTINGS = "studyPlannerSettings"
        const val DOC_GOALS = "goals"
    }
}
