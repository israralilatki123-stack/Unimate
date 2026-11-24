package com.example.unimate.data.repository

import com.example.unimate.data.firebase.OperationType
import com.example.unimate.data.firebase.handleFirestoreError
import com.example.unimate.data.model.AcademicModulesMappers
import com.example.unimate.data.model.AssignmentStatus
import com.example.unimate.data.model.ExamPreparationStatus
import com.example.unimate.data.model.QuizPreparationStatus
import com.example.unimate.data.model.RevisionChecklistItem
import com.example.unimate.data.model.SaveAssignmentRequest
import com.example.unimate.data.model.SaveExamRequest
import com.example.unimate.data.model.SaveQuizRequest
import com.example.unimate.data.model.StudentAssignment
import com.example.unimate.data.model.StudentExam
import com.example.unimate.data.model.StudentQuiz
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

interface AcademicModulesRepository {
    fun observeAssignments(uid: String): Flow<List<StudentAssignment>>
    suspend fun addAssignment(uid: String, request: SaveAssignmentRequest): Result<Unit>
    suspend fun addAssignmentWithId(
        uid: String,
        assignmentId: String,
        request: SaveAssignmentRequest,
    ): Result<Unit> = addAssignment(uid, request)

    suspend fun updateAssignment(uid: String, assignmentId: String, request: SaveAssignmentRequest): Result<Unit>
    suspend fun updateAssignmentStatus(uid: String, assignmentId: String, status: AssignmentStatus): Result<Unit>
    suspend fun deleteAssignment(uid: String, assignmentId: String): Result<Unit>

    fun observeQuizzes(uid: String): Flow<List<StudentQuiz>>
    suspend fun addQuiz(uid: String, request: SaveQuizRequest): Result<Unit>
    suspend fun addQuizWithId(
        uid: String,
        quizId: String,
        request: SaveQuizRequest,
    ): Result<Unit> = addQuiz(uid, request)

    suspend fun updateQuiz(uid: String, quizId: String, request: SaveQuizRequest): Result<Unit>
    suspend fun updateQuizStatusAndScore(
        uid: String,
        quizId: String,
        preparationStatus: QuizPreparationStatus,
        scoreRecorded: Boolean,
        obtainedMarks: Double,
    ): Result<Unit>

    suspend fun deleteQuiz(uid: String, quizId: String): Result<Unit>

    fun observeExams(uid: String): Flow<List<StudentExam>>
    suspend fun addExam(uid: String, request: SaveExamRequest): Result<Unit>
    suspend fun addExamWithId(
        uid: String,
        examId: String,
        request: SaveExamRequest,
    ): Result<Unit> = addExam(uid, request)

    suspend fun updateExam(uid: String, examId: String, request: SaveExamRequest): Result<Unit>
    suspend fun updateExamChecklistAndStatus(
        uid: String,
        examId: String,
        checklist: List<RevisionChecklistItem>,
        preparationStatus: ExamPreparationStatus,
    ): Result<Unit>

    suspend fun deleteExam(uid: String, examId: String): Result<Unit>
}

class FirestoreAcademicModulesRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : AcademicModulesRepository {

    private fun requireAuthorizedUid(expectedUid: String): String {
        val currentUid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
        require(expectedUid.isNotBlank() && currentUid == expectedUid) {
            "Cannot access or mutate another student's academic records."
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

    override fun observeAssignments(uid: String): Flow<List<StudentAssignment>> =
        observeSubcollection(uid, SUB_ASSIGNMENTS, StudentAssignment::class.java)

    override suspend fun addAssignment(uid: String, request: SaveAssignmentRequest): Result<Unit> =
        addAssignmentWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addAssignmentWithId(
        uid: String,
        assignmentId: String,
        request: SaveAssignmentRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = assignmentId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_ASSIGNMENTS/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            validateAssignmentRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_ASSIGNMENTS)
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
                collectionKey = SUB_ASSIGNMENTS,
                displayTitle = request.title.trim(),
                payload = AcademicModulesMappers.toCreateAssignmentPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_ASSIGNMENTS:$activeUid:$targetId",
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

    override suspend fun updateAssignment(
        uid: String,
        assignmentId: String,
        request: SaveAssignmentRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_ASSIGNMENTS/$assignmentId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(assignmentId.isNotBlank()) { "Invalid assignment identifier." }
            validateAssignmentRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_ASSIGNMENTS)
                .document(assignmentId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_ASSIGNMENTS,
                displayTitle = request.title.trim(),
                payload = AcademicModulesMappers.toUpdateAssignmentPayload(request),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateAssignmentStatus(
        uid: String,
        assignmentId: String,
        status: AssignmentStatus,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_ASSIGNMENTS/$assignmentId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(assignmentId.isNotBlank()) { "Invalid assignment identifier." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_ASSIGNMENTS)
                .document(assignmentId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_ASSIGNMENTS,
                displayTitle = "Assignment status",
                payload = AcademicModulesMappers.toAssignmentStatusPayload(status),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteAssignment(uid: String, assignmentId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_ASSIGNMENTS, assignmentId, "Assignment")

    override fun observeQuizzes(uid: String): Flow<List<StudentQuiz>> =
        observeSubcollection(uid, SUB_QUIZZES, StudentQuiz::class.java)

    override suspend fun addQuiz(uid: String, request: SaveQuizRequest): Result<Unit> =
        addQuizWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addQuizWithId(
        uid: String,
        quizId: String,
        request: SaveQuizRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = quizId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_QUIZZES/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            validateQuizRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_QUIZZES)
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
                collectionKey = SUB_QUIZZES,
                displayTitle = request.title.trim(),
                payload = AcademicModulesMappers.toCreateQuizPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_QUIZZES:$activeUid:$targetId",
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

    override suspend fun updateQuiz(
        uid: String,
        quizId: String,
        request: SaveQuizRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_QUIZZES/$quizId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(quizId.isNotBlank()) { "Invalid quiz identifier." }
            validateQuizRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_QUIZZES)
                .document(quizId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_QUIZZES,
                displayTitle = request.title.trim(),
                payload = AcademicModulesMappers.toUpdateQuizPayload(request),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateQuizStatusAndScore(
        uid: String,
        quizId: String,
        preparationStatus: QuizPreparationStatus,
        scoreRecorded: Boolean,
        obtainedMarks: Double,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_QUIZZES/$quizId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(quizId.isNotBlank()) { "Invalid quiz identifier." }
            require(obtainedMarks in 0.0..1000.0) { "Obtained marks must be valid." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_QUIZZES)
                .document(quizId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_QUIZZES,
                displayTitle = "Quiz status",
                payload = AcademicModulesMappers.toQuizStatusPayload(
                    preparationStatus = preparationStatus,
                    scoreRecorded = scoreRecorded,
                    obtainedMarks = if (scoreRecorded) obtainedMarks else 0.0,
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

    override suspend fun deleteQuiz(uid: String, quizId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_QUIZZES, quizId, "Quiz")

    override fun observeExams(uid: String): Flow<List<StudentExam>> =
        observeSubcollection(uid, SUB_EXAMS, StudentExam::class.java)

    override suspend fun addExam(uid: String, request: SaveExamRequest): Result<Unit> =
        addExamWithId(uid, UUID.randomUUID().toString(), request)

    override suspend fun addExamWithId(
        uid: String,
        examId: String,
        request: SaveExamRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetId = examId.trim().ifEmpty { UUID.randomUUID().toString() }
        var path = "users/$uid/$SUB_EXAMS/$targetId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            validateExamRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_EXAMS)
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
                collectionKey = SUB_EXAMS,
                displayTitle = request.title.trim(),
                payload = AcademicModulesMappers.toCreateExamPayload(activeUid, request),
                merge = false,
                isCreate = true,
                dedupKey = "$SUB_EXAMS:$activeUid:$targetId",
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

    override suspend fun updateExam(
        uid: String,
        examId: String,
        request: SaveExamRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_EXAMS/$examId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(examId.isNotBlank()) { "Invalid exam identifier." }
            validateExamRequest(request)
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_EXAMS)
                .document(examId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_EXAMS,
                displayTitle = request.title.trim(),
                payload = AcademicModulesMappers.toUpdateExamPayload(request),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateExamChecklistAndStatus(
        uid: String,
        examId: String,
        checklist: List<RevisionChecklistItem>,
        preparationStatus: ExamPreparationStatus,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var path = "users/$uid/$SUB_EXAMS/$examId"
        try {
            val activeUid = requireAuthorizedUid(uid)
            require(examId.isNotBlank()) { "Invalid exam identifier." }
            require(checklist.size <= 30) { "Maximum 30 revision topics supported." }
            val docRef = db.collection(COLLECTION_USERS)
                .document(activeUid)
                .collection(SUB_EXAMS)
                .document(examId)
            path = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = activeUid,
                docRef = docRef,
                collectionKey = SUB_EXAMS,
                displayTitle = "Exam checklist",
                payload = AcademicModulesMappers.toExamChecklistAndStatusPayload(
                    checklist = checklist,
                    preparationStatus = preparationStatus,
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

    override suspend fun deleteExam(uid: String, examId: String): Result<Unit> =
        deleteSubcollectionDoc(uid, SUB_EXAMS, examId, "Exam")

    private fun validateAssignmentRequest(request: SaveAssignmentRequest) {
        require(request.title.trim().length in 1..160) {
            "Assignment title must be 1–160 characters."
        }
        require(request.description.trim().length <= 2000) {
            "Description must be 2000 characters or fewer."
        }
        require(request.courseName.trim().length in 1..100) {
            "Course is required (1–100 characters)."
        }
        require(request.deadlineMillis > 0L) {
            "Please select a valid deadline."
        }
        require(request.attachments.size <= 10) {
            "Maximum 10 attachments supported."
        }
    }

    private fun validateQuizRequest(request: SaveQuizRequest) {
        require(request.title.trim().length in 1..160) {
            "Quiz title must be 1–160 characters."
        }
        require(request.courseName.trim().length in 1..100) {
            "Course is required (1–100 characters)."
        }
        require(request.scheduledAtMillis > 0L) {
            "Please pick a valid quiz date and time."
        }
        require(request.totalMarks in 0.5..1000.0) {
            "Total marks must be between 0.5 and 1000."
        }
        if (request.scoreRecorded) {
            require(request.obtainedMarks in 0.0..request.totalMarks) {
                "Obtained marks cannot exceed total marks."
            }
        }
        require(request.notes.trim().length <= 500) {
            "Notes must be 500 characters or fewer."
        }
    }

    private fun validateExamRequest(request: SaveExamRequest) {
        require(request.title.trim().length in 1..160) {
            "Exam title must be 1–160 characters."
        }
        require(request.courseName.trim().length in 1..100) {
            "Course is required (1–100 characters)."
        }
        require(request.examAtMillis > 0L) {
            "Please pick a valid exam date and time."
        }
        require(request.room.trim().length in 1..80) {
            "Exam room is required (1–80 characters)."
        }
        require(request.revisionChecklist.size <= 30) {
            "Maximum 30 revision checklist items supported."
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
        const val SUB_ASSIGNMENTS = "assignments"
        const val SUB_QUIZZES = "quizzes"
        const val SUB_EXAMS = "exams"
    }
}
