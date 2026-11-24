package com.example.unimate.data.repository

import com.example.unimate.data.firebase.OperationType
import com.example.unimate.data.firebase.handleFirestoreError
import com.example.unimate.data.model.AcademicTask
import com.example.unimate.data.model.AddGoalRequest
import com.example.unimate.data.model.AddNoteRequest
import com.example.unimate.data.model.AddReminderRequest
import com.example.unimate.data.model.AddTaskRequest
import com.example.unimate.data.model.SaveCourseRequest
import com.example.unimate.data.model.SaveTimetableClassRequest
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.StudentReminder
import com.example.unimate.data.model.StudyGoal
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.data.model.WorkspaceMappers
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

interface WorkspaceRepository {
    fun observeTasks(uid: String): Flow<List<AcademicTask>>
    fun observeCourses(uid: String): Flow<List<StudentCourse>>
    fun observeTimetable(uid: String): Flow<List<TimetableClass>>
    fun observeNotes(uid: String): Flow<List<StudyNote>>
    fun observeReminders(uid: String): Flow<List<StudentReminder>>
    fun observeGoals(uid: String): Flow<List<StudyGoal>>

    suspend fun addTask(uid: String, request: AddTaskRequest): Result<Unit>
    suspend fun addTaskWithId(
        uid: String,
        taskId: String,
        request: AddTaskRequest,
    ): Result<Unit> = addTask(uid, request)

    suspend fun toggleTaskCompleted(uid: String, taskId: String, completed: Boolean): Result<Unit>
    suspend fun deleteTask(uid: String, taskId: String): Result<Unit>

    suspend fun addCourse(uid: String, request: SaveCourseRequest): Result<Unit>
    suspend fun addCourseWithId(
        uid: String,
        courseId: String,
        request: SaveCourseRequest,
    ): Result<Unit> = addCourse(uid, request)

    suspend fun updateCourse(uid: String, courseId: String, request: SaveCourseRequest): Result<Unit>
    suspend fun updateCourseAttendance(
        uid: String,
        courseId: String,
        attendedClasses: Long,
        totalClasses: Long,
    ): Result<Unit>

    suspend fun deleteCourse(uid: String, courseId: String): Result<Unit>

    suspend fun addTimetableClass(uid: String, request: SaveTimetableClassRequest): Result<Unit>
    suspend fun addTimetableClassWithId(
        uid: String,
        classId: String,
        request: SaveTimetableClassRequest,
    ): Result<Unit> = addTimetableClass(uid, request)

    suspend fun updateTimetableClass(
        uid: String,
        classId: String,
        request: SaveTimetableClassRequest,
    ): Result<Unit>

    suspend fun deleteTimetableClass(uid: String, classId: String): Result<Unit>

    suspend fun addNote(uid: String, request: AddNoteRequest): Result<Unit>
    suspend fun addNoteWithId(
        uid: String,
        noteId: String,
        request: AddNoteRequest,
    ): Result<Unit> = addNote(uid, request)

    suspend fun deleteNote(uid: String, noteId: String): Result<Unit>

    suspend fun addReminder(uid: String, request: AddReminderRequest): Result<Unit>
    suspend fun addReminderWithId(
        uid: String,
        reminderId: String,
        request: AddReminderRequest,
    ): Result<Unit> = addReminder(uid, request)

    suspend fun toggleReminderDone(uid: String, reminderId: String, done: Boolean): Result<Unit>
    suspend fun deleteReminder(uid: String, reminderId: String): Result<Unit>

    suspend fun addGoal(uid: String, request: AddGoalRequest): Result<Unit>
    suspend fun addGoalWithId(
        uid: String,
        goalId: String,
        request: AddGoalRequest,
    ): Result<Unit> = addGoal(uid, request)

    suspend fun updateGoalProgress(
        uid: String,
        goalId: String,
        completedSteps: Long,
        targetSteps: Long,
    ): Result<Unit>

    suspend fun deleteGoal(uid: String, goalId: String): Result<Unit>
}

class FirestoreWorkspaceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : WorkspaceRepository {

    private fun requireAuthorizedUid(expectedUid: String): String {
        val currentUid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
        require(expectedUid.isNotBlank() && currentUid == expectedUid) {
            "Cannot access or mutate another student's workspace."
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

    override fun observeTasks(uid: String): Flow<List<AcademicTask>> =
        observeSubcollection(uid, SUB_TASKS, AcademicTask::class.java)

    override fun observeCourses(uid: String): Flow<List<StudentCourse>> =
        observeSubcollection(uid, SUB_COURSES, StudentCourse::class.java)

    override fun observeTimetable(uid: String): Flow<List<TimetableClass>> =
        observeSubcollection(uid, SUB_TIMETABLE, TimetableClass::class.java)

    override fun observeNotes(uid: String): Flow<List<StudyNote>> =
        observeSubcollection(uid, SUB_NOTES, StudyNote::class.java)

    override fun observeReminders(uid: String): Flow<List<StudentReminder>> =
        observeSubcollection(uid, SUB_REMINDERS, StudentReminder::class.java)

    override fun observeGoals(uid: String): Flow<List<StudyGoal>> =
        observeSubcollection(uid, SUB_GOALS, StudyGoal::class.java)

    override suspend fun addTask(uid: String, request: AddTaskRequest): Result<Unit> =
        addTaskWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addTaskWithId(
        uid: String,
        taskId: String,
        request: AddTaskRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = taskId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_TASKS/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(request.title.trim().length in 1..160) {
                "Task title must be between 1 and 160 characters."
            }
            require(request.courseName.trim().length in 1..100) {
                "Course name must be between 1 and 100 characters."
            }
            require(request.dueDateText.trim().length in 1..60) {
                "Due date label must be between 1 and 60 characters."
            }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_TASKS)
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
                collectionKey = SUB_TASKS,
                displayTitle = request.title.trim(),
                payload = WorkspaceMappers.toCreateTaskPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_TASKS:$activeUid:$targetId",
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

    override suspend fun toggleTaskCompleted(
        uid: String,
        taskId: String,
        completed: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_TASKS/$taskId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(taskId.isNotBlank()) { "Invalid task identifier." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_TASKS)
                .document(taskId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_TASKS,
                displayTitle = "Task",
                payload = WorkspaceMappers.toToggleTaskPayload(completed),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteTask(uid: String, taskId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_TASKS, taskId, "Task")

    override suspend fun addCourse(uid: String, request: SaveCourseRequest): Result<Unit> =
        addCourseWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addCourseWithId(
        uid: String,
        courseId: String,
        request: SaveCourseRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = courseId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_COURSES/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            validateCourseRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_COURSES)
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
                collectionKey = SUB_COURSES,
                displayTitle = request.courseName.trim(),
                payload = WorkspaceMappers.toCreateCoursePayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_COURSES:$activeUid:$targetId",
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

    override suspend fun updateCourse(
        uid: String,
        courseId: String,
        request: SaveCourseRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_COURSES/$courseId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(courseId.isNotBlank()) { "Invalid course identifier." }
            validateCourseRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_COURSES)
                .document(courseId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_COURSES,
                displayTitle = request.courseName.trim(),
                payload = WorkspaceMappers.toUpdateCoursePayload(request),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateCourseAttendance(
        uid: String,
        courseId: String,
        attendedClasses: Long,
        totalClasses: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_COURSES/$courseId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(courseId.isNotBlank()) { "Invalid course identifier." }
            require(attendedClasses in 0L..1000L && totalClasses in 0L..1000L) {
                "Attendance counts must be between 0 and 1000."
            }
            require(attendedClasses <= totalClasses) {
                "Attended classes cannot exceed total classes."
            }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_COURSES)
                .document(courseId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_COURSES,
                displayTitle = "Course attendance",
                payload = WorkspaceMappers.toUpdateAttendancePayload(attendedClasses, totalClasses),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteCourse(uid: String, courseId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_COURSES, courseId, "Course")

    override suspend fun addTimetableClass(
        uid: String,
        request: SaveTimetableClassRequest,
    ): Result<Unit> = addTimetableClassWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addTimetableClassWithId(
        uid: String,
        classId: String,
        request: SaveTimetableClassRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = classId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_TIMETABLE/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            validateTimetableRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_TIMETABLE)
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
                collectionKey = SUB_TIMETABLE,
                displayTitle = request.subjectName.trim(),
                payload = WorkspaceMappers.toCreateTimetableClassPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_TIMETABLE:$activeUid:$targetId",
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

    override suspend fun updateTimetableClass(
        uid: String,
        classId: String,
        request: SaveTimetableClassRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_TIMETABLE/$classId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(classId.isNotBlank()) { "Invalid timetable class identifier." }
            validateTimetableRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_TIMETABLE)
                .document(classId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_TIMETABLE,
                displayTitle = request.subjectName.trim(),
                payload = WorkspaceMappers.toUpdateTimetableClassPayload(request),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteTimetableClass(uid: String, classId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_TIMETABLE, classId, "Timetable class")

    override suspend fun addNote(uid: String, request: AddNoteRequest): Result<Unit> =
        addNoteWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addNoteWithId(
        uid: String,
        noteId: String,
        request: AddNoteRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = noteId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_NOTES/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(request.title.trim().length in 1..140) {
                "Note title must be between 1 and 140 characters."
            }
            require(request.courseName.trim().length in 1..100) {
                "Course tag must be between 1 and 100 characters."
            }
            require(request.content.trim().length in 1..4000) {
                "Note content must be between 1 and 4000 characters."
            }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_NOTES)
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
                collectionKey = SUB_NOTES,
                displayTitle = request.title.trim(),
                payload = WorkspaceMappers.toCreateNotePayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_NOTES:$activeUid:$targetId",
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

    override suspend fun deleteNote(uid: String, noteId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_NOTES, noteId, "Note")

    override suspend fun addReminder(uid: String, request: AddReminderRequest): Result<Unit> =
        addReminderWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addReminderWithId(
        uid: String,
        reminderId: String,
        request: AddReminderRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = reminderId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_REMINDERS/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(request.title.trim().length in 1..160) {
                "Reminder title must be between 1 and 160 characters."
            }
            require(request.remindAtText.trim().length in 1..80) {
                "Reminder time must be between 1 and 80 characters."
            }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_REMINDERS)
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
                collectionKey = SUB_REMINDERS,
                displayTitle = request.title.trim(),
                payload = WorkspaceMappers.toCreateReminderPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_REMINDERS:$activeUid:$targetId",
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

    override suspend fun toggleReminderDone(
        uid: String,
        reminderId: String,
        done: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_REMINDERS/$reminderId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(reminderId.isNotBlank()) { "Invalid reminder identifier." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_REMINDERS)
                .document(reminderId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_REMINDERS,
                displayTitle = "Reminder",
                payload = WorkspaceMappers.toToggleReminderPayload(done),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteReminder(uid: String, reminderId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_REMINDERS, reminderId, "Reminder")

    override suspend fun addGoal(uid: String, request: AddGoalRequest): Result<Unit> =
        addGoalWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addGoalWithId(
        uid: String,
        goalId: String,
        request: AddGoalRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = goalId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_GOALS/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(request.title.trim().length in 1..140) {
                "Goal title must be between 1 and 140 characters."
            }
            require(request.targetSteps in 1L..500L) {
                "Target steps must be between 1 and 500."
            }
            require(request.completedSteps in 0L..request.targetSteps) {
                "Completed steps must be between 0 and target steps."
            }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_GOALS)
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
                collectionKey = SUB_GOALS,
                displayTitle = request.title.trim(),
                payload = WorkspaceMappers.toCreateGoalPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_GOALS:$activeUid:$targetId",
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

    override suspend fun updateGoalProgress(
        uid: String,
        goalId: String,
        completedSteps: Long,
        targetSteps: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_GOALS/$goalId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(goalId.isNotBlank()) { "Invalid goal identifier." }
            require(targetSteps in 1L..500L && completedSteps in 0L..targetSteps) {
                "Completed steps must stay between 0 and target steps."
            }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_GOALS)
                .document(goalId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_GOALS,
                displayTitle = "Study goal",
                payload = WorkspaceMappers.toUpdateGoalProgressPayload(completedSteps),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteGoal(uid: String, goalId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_GOALS, goalId, "Study goal")

    private fun validateCourseRequest(request: SaveCourseRequest) {
        require(request.courseCode.trim().length in 1..24) {
            "Course code must be between 1 and 24 characters."
        }
        require(request.courseName.trim().length in 1..120) {
            "Course title must be between 1 and 120 characters."
        }
        require(request.creditHours in 0.5..12.0) {
            "Credit hours must be between 0.5 and 12.0."
        }
        require(request.instructor.trim().length in 1..100) {
            "Instructor name must be between 1 and 100 characters."
        }
        require(request.semester.trim().length in 1..60) {
            "Semester must be between 1 and 60 characters."
        }
        require(request.description.trim().length <= 500) {
            "Description must be 500 characters or fewer."
        }
        require(request.classroom.trim().length in 1..60) {
            "Classroom must be between 1 and 60 characters."
        }
        require(request.schedule.trim().length in 1..120) {
            "Schedule must be between 1 and 120 characters."
        }
        require(request.attendedClasses in 0L..1000L && request.totalClasses in 0L..1000L) {
            "Attendance counts must be between 0 and 1000."
        }
        require(request.attendedClasses <= request.totalClasses) {
            "Attended classes cannot exceed total classes."
        }
        require(request.colorIndex in 0L..7L) {
            "Invalid course color index."
        }
    }

    private fun validateTimetableRequest(request: SaveTimetableClassRequest) {
        require(request.subjectName.trim().length in 1..120) {
            "Subject name must be between 1 and 120 characters."
        }
        require(request.courseCode.trim().length in 1..24) {
            "Course code must be between 1 and 24 characters."
        }
        require(request.instructor.trim().length in 1..100) {
            "Instructor name must be between 1 and 100 characters."
        }
        require(request.classroom.trim().length in 1..60) {
            "Classroom must be between 1 and 60 characters."
        }
        require(request.startTimeMinutes in 0L..1438L && request.endTimeMinutes in 1L..1439L) {
            "Class times must be within a single day."
        }
        require(request.endTimeMinutes > request.startTimeMinutes) {
            "End time must be after start time."
        }
        require(request.colorIndex in 0L..7L) {
            "Invalid subject color index."
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
        const val SUB_TASKS = "tasks"
        const val SUB_COURSES = "courses"
        const val SUB_TIMETABLE = "timetable"
        const val SUB_NOTES = "notes"
        const val SUB_REMINDERS = "reminders"
        const val SUB_GOALS = "goals"
    }
}
