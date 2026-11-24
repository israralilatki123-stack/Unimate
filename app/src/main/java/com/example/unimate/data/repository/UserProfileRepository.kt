package com.example.unimate.data.repository

import android.net.Uri
import android.util.Log
import com.example.unimate.data.firebase.OperationType
import com.example.unimate.data.firebase.handleFirestoreError
import com.example.unimate.data.model.ProfileSetupRequest
import com.example.unimate.data.model.ProfileUpdateRequest
import com.example.unimate.data.model.UserProfile
import com.example.unimate.data.model.UserProfileMapper
import com.example.unimate.data.sync.DuplicateWriteIgnoredException
import com.example.unimate.data.sync.FirestoreWriteExecutor
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.SnapshotSyncSupport
import com.example.unimate.data.sync.SyncCoordinator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
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

interface UserProfileRepository {
    fun observeUserProfile(uid: String): Flow<UserProfile?>
    suspend fun createUserProfile(request: ProfileSetupRequest): Result<Unit>
    suspend fun updateUserProfile(uid: String, request: ProfileUpdateRequest): Result<Unit>
    suspend fun updatePhoneNumber(uid: String, phoneNumber: String): Result<Unit>
}

class FirestoreUserProfileRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : UserProfileRepository {

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    override fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            emit(null)
            return@flow
        }
        val docPath = "users/$uid"
        emitAll(
            db.collection(COLLECTION_USERS).document(uid)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    SnapshotSyncSupport.reportDocumentSnapshot(
                        syncCoordinator = syncCoordinator,
                        collectionKey = COLLECTION_USERS,
                        uid = uid,
                        snapshot = snapshot,
                    )
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error

                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docPath)
                    }
                    throw error
                },
        )
    }

    override suspend fun createUserProfile(request: ProfileSetupRequest): Result<Unit> =
        withContext(Dispatchers.IO) {
            var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
            try {
                val uid = requireUserId()
                val user = auth.currentUser
                    ?: throw IllegalStateException("Authenticated user unavailable.")
                val email = user.email?.trim().orEmpty()
                    .ifEmpty { request.email?.trim().orEmpty() }
                require(email.length in 3..254) { "A valid email address is required." }

                val resolvedPhone = user.phoneNumber?.trim().orEmpty()
                    .ifEmpty { request.phoneNumber?.trim().orEmpty() }
                require(resolvedPhone.length <= 30) {
                    "Phone number must be 30 characters or fewer."
                }

                validateProfileFields(
                    fullName = request.fullName,
                    university = request.university,
                    department = request.department,
                    semester = request.semester,
                    profileImage = request.profileImage,
                )

                val docRef = db.collection(COLLECTION_USERS).document(uid)
                docPath = docRef.path
                val payload = UserProfileMapper.toCreatePayload(
                    uid = uid,
                    email = email,
                    request = request.copy(
                        email = email,
                        phoneNumber = resolvedPhone.ifEmpty { null },
                    ),
                )
                FirestoreWriteExecutor.executeSet(
                    syncCoordinator = syncCoordinator,
                    uid = uid,
                    docRef = docRef,
                    collectionKey = COLLECTION_USERS,
                    displayTitle = request.fullName.trim(),
                    payload = payload,
                    merge = false,
                    isCreate = true,
                    dedupKey = "create_profile:$uid",
                )

                syncFirebaseUserProfile(
                    fullName = request.fullName.trim(),
                    profileImage = request.profileImage?.trim(),
                )

                Result.success(Unit)
            } catch (_: DuplicateWriteIgnoredException) {
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docPath)
                Result.failure(e)
            }
        }

    override suspend fun updateUserProfile(
        uid: String,
        request: ProfileUpdateRequest,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var docPath = "users/$uid"
        try {
            val currentUid = requireUserId()
            require(uid.isNotBlank() && currentUid == uid) {
                "Cannot modify a profile that does not belong to the signed-in user."
            }

            validateProfileFields(
                fullName = request.fullName,
                university = request.university,
                department = request.department,
                semester = request.semester,
                profileImage = request.profileImage,
            )

            val cleanPhone = request.phoneNumber?.trim()
            if (cleanPhone != null) {
                require(cleanPhone.length <= 30) {
                    "Phone number must be 30 characters or fewer."
                }
            }

            val docRef = db.collection(COLLECTION_USERS).document(uid)
            docPath = docRef.path
            val updates = UserProfileMapper.toUpdatePayload(request)
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = currentUid,
                docRef = docRef,
                collectionKey = COLLECTION_USERS,
                displayTitle = request.fullName.trim(),
                payload = updates,
            )

            syncFirebaseUserProfile(
                fullName = request.fullName.trim(),
                profileImage = request.profileImage?.trim(),
            )

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updatePhoneNumber(
        uid: String,
        phoneNumber: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var docPath = "users/$uid"
        try {
            val currentUid = requireUserId()
            require(uid.isNotBlank() && currentUid == uid) {
                "Cannot modify a profile that does not belong to the signed-in user."
            }
            val cleanPhone = phoneNumber.trim()
            require(cleanPhone.length in 5..30) {
                "Verified phone number must be between 5 and 30 characters."
            }

            val docRef = db.collection(COLLECTION_USERS).document(uid)
            docPath = docRef.path
            FirestoreWriteExecutor.executeUpdate(
                syncCoordinator = syncCoordinator,
                uid = currentUid,
                docRef = docRef,
                collectionKey = COLLECTION_USERS,
                displayTitle = "Phone number",
                payload = UserProfileMapper.toPhoneUpdatePayload(cleanPhone),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun syncFirebaseUserProfile(
        fullName: String,
        profileImage: String?,
    ) {
        val user = auth.currentUser ?: return
        runCatching {
            val builder = UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
            val cleanImage = profileImage.orEmpty().trim()
            if (cleanImage.isNotEmpty()) {
                builder.setPhotoUri(Uri.parse(cleanImage))
            } else {
                builder.setPhotoUri(null)
            }
            user.updateProfile(builder.build()).await()
        }.onFailure { err ->
            Log.w(TAG, "FirebaseAuth.updateProfile sync warning: ${err.message}")
        }
    }

    private fun validateProfileFields(
        fullName: String,
        university: String,
        department: String,
        semester: String,
        profileImage: String?,
    ) {
        require(fullName.trim().length in 2..100) {
            "Full name must be between 2 and 100 characters."
        }
        require(university.trim().length in 2..150) {
            "University must be between 2 and 150 characters."
        }
        require(department.trim().length in 2..120) {
            "Department must be between 2 and 120 characters."
        }
        require(semester.trim().length in 1..60) {
            "Semester must be selected."
        }
        val cleanImage = profileImage?.trim().orEmpty()
        require(cleanImage.length <= 500) {
            "Profile image URL must be 500 characters or fewer."
        }
    }

    private companion object {
        const val TAG = "UserProfileRepository"
        const val COLLECTION_USERS = "users"
    }
}
