package com.example.unimate.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.unimate.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class AuthenticatedUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val phoneNumber: String? = null,
    val providerIds: List<String> = emptyList(),
)

sealed interface GoogleSignInResult {
    data class Success(val user: AuthenticatedUser) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data object NoSilentCredential : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
}

sealed interface PhoneCodeSendResult {
    data class CodeSent(
        val verificationId: String,
        val resendToken: PhoneAuthProvider.ForceResendingToken?,
    ) : PhoneCodeSendResult

    data class AutoVerified(val user: AuthenticatedUser) : PhoneCodeSendResult
    data class Error(val message: String) : PhoneCodeSendResult
}

sealed interface PhoneVerifyResult {
    data class Success(val user: AuthenticatedUser) : PhoneVerifyResult
    data class Error(val message: String) : PhoneVerifyResult
}

interface AuthRepository {
    val authStateFlow: Flow<AuthenticatedUser?>
    fun currentAuthenticatedUser(): AuthenticatedUser?
    suspend fun attemptAutoSignIn(): GoogleSignInResult
    suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult
    suspend fun sendPhoneVerificationCode(
        activityContext: Context,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        linkToCurrentUser: Boolean = false,
    ): PhoneCodeSendResult

    suspend fun verifyPhoneOtp(
        verificationId: String,
        smsCode: String,
        linkToCurrentUser: Boolean = false,
    ): PhoneVerifyResult

    suspend fun signOut()
}

class FirebaseAuthRepository(
    private val context: Context,
) : AuthRepository {

    private val appContext = context.applicationContext
    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(appContext)
    }

    private fun firebaseAuthOrNull(): FirebaseAuth? =
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            runCatching { FirebaseAuth.getInstance() }.getOrNull()
        } else {
            null
        }

    private fun FirebaseUser.toAuthenticatedUser(): AuthenticatedUser = AuthenticatedUser(
        uid = uid,
        email = email.orEmpty(),
        displayName = displayName.orEmpty(),
        photoUrl = photoUrl?.toString(),
        phoneNumber = phoneNumber,
        providerIds = providerData.map { it.providerId }.filter { it != "firebase" },
    )

    override val authStateFlow: Flow<AuthenticatedUser?> = callbackFlow {
        val auth = firebaseAuthOrNull()
        if (auth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toAuthenticatedUser())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun currentAuthenticatedUser(): AuthenticatedUser? =
        firebaseAuthOrNull()?.currentUser?.toAuthenticatedUser()

    override suspend fun attemptAutoSignIn(): GoogleSignInResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuthOrNull()
            ?: return@withContext GoogleSignInResult.Error(FIREBASE_NOT_CONFIGURED_MESSAGE)

        currentAuthenticatedUser()?.let { existing ->
            return@withContext GoogleSignInResult.Success(existing)
        }

        val clientId = try {
            appContext.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return@withContext GoogleSignInResult.NoSilentCredential
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val credential = credentialManager.getCredential(appContext, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                val signedInUser = currentAuthenticatedUser()
                    ?: return@withContext GoogleSignInResult.NoSilentCredential
                GoogleSignInResult.Success(signedInUser)
            } else {
                GoogleSignInResult.NoSilentCredential
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            GoogleSignInResult.NoSilentCredential
        } catch (_: Exception) {
            GoogleSignInResult.NoSilentCredential
        }
    }

    override suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult {
        val auth = firebaseAuthOrNull()
            ?: return GoogleSignInResult.Error(FIREBASE_NOT_CONFIGURED_MESSAGE)

        val clientId = try {
            appContext.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return GoogleSignInResult.Error("Google Sign-In configuration missing: default_web_client_id not found.")
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val credentialHost: Context = (activityContext as? Activity) ?: activityContext
            val credential = credentialManager.getCredential(credentialHost, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                withContext(Dispatchers.IO) {
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                }
                val signedInUser = currentAuthenticatedUser()
                    ?: return GoogleSignInResult.Error("Signed in with Google, but Firebase user session failed to load.")
                GoogleSignInResult.Success(signedInUser)
            } else {
                GoogleSignInResult.Error("Unexpected credential type returned by Google Sign-In.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google credential available on device: ${e.message}", e)
            GoogleSignInResult.Error(
                "No Google account is configured on this device. Add a Google account or use Phone OTP.",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleSignInResult.Error(e.localizedMessage ?: "Google Sign-In failed. Please try again.")
        }
    }

    private sealed interface PhoneSendOutcome {
        data class CodeSent(
            val verificationId: String,
            val resendToken: PhoneAuthProvider.ForceResendingToken,
        ) : PhoneSendOutcome

        data class AutoCredential(val credential: PhoneAuthCredential) : PhoneSendOutcome
        data class Failure(val message: String) : PhoneSendOutcome
    }

    override suspend fun sendPhoneVerificationCode(
        activityContext: Context,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
        linkToCurrentUser: Boolean,
    ): PhoneCodeSendResult {
        val auth = firebaseAuthOrNull()
            ?: return PhoneCodeSendResult.Error(FIREBASE_NOT_CONFIGURED_MESSAGE)
        val activity = activityContext as? Activity
            ?: return PhoneCodeSendResult.Error("Phone verification requires an active Activity window.")

        val normalizedPhone = phoneNumber.trim()

        val outcome = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<PhoneSendOutcome> { continuation ->
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        if (continuation.isActive) {
                            continuation.resume(PhoneSendOutcome.AutoCredential(credential))
                        }
                    }

                    override fun onVerificationFailed(exception: FirebaseException) {
                        Log.e(TAG, "Phone verification failed: ${exception.message}", exception)
                        if (continuation.isActive) {
                            continuation.resume(
                                PhoneSendOutcome.Failure(
                                    exception.localizedMessage
                                        ?: "Phone verification failed. Check the number format and try again.",
                                ),
                            )
                        }
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(PhoneSendOutcome.CodeSent(verificationId, token))
                        }
                    }
                }

                val builder = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(normalizedPhone)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)

                if (resendToken != null) {
                    builder.setForceResendingToken(resendToken)
                }

                PhoneAuthProvider.verifyPhoneNumber(builder.build())
            }
        }

        return when (outcome) {
            is PhoneSendOutcome.CodeSent -> PhoneCodeSendResult.CodeSent(
                verificationId = outcome.verificationId,
                resendToken = outcome.resendToken,
            )

            is PhoneSendOutcome.Failure -> PhoneCodeSendResult.Error(outcome.message)

            is PhoneSendOutcome.AutoCredential -> {
                when (
                    val applyResult = applyPhoneCredential(
                        auth = auth,
                        credential = outcome.credential,
                        linkToCurrentUser = linkToCurrentUser,
                    )
                ) {
                    is PhoneVerifyResult.Success -> PhoneCodeSendResult.AutoVerified(applyResult.user)
                    is PhoneVerifyResult.Error -> PhoneCodeSendResult.Error(applyResult.message)
                }
            }
        }
    }

    override suspend fun verifyPhoneOtp(
        verificationId: String,
        smsCode: String,
        linkToCurrentUser: Boolean,
    ): PhoneVerifyResult {
        val auth = firebaseAuthOrNull()
            ?: return PhoneVerifyResult.Error(FIREBASE_NOT_CONFIGURED_MESSAGE)

        val credential = PhoneAuthProvider.getCredential(verificationId, smsCode.trim())
        return applyPhoneCredential(
            auth = auth,
            credential = credential,
            linkToCurrentUser = linkToCurrentUser,
        )
    }

    private suspend fun applyPhoneCredential(
        auth: FirebaseAuth,
        credential: PhoneAuthCredential,
        linkToCurrentUser: Boolean,
    ): PhoneVerifyResult = withContext(Dispatchers.IO) {
        try {
            if (linkToCurrentUser) {
                val user = auth.currentUser
                    ?: return@withContext PhoneVerifyResult.Error("No signed-in user to link phone number.")
                user.linkWithCredential(credential).await()
                user.reload().await()
            } else {
                auth.signInWithCredential(credential).await()
            }

            val updatedUser = currentAuthenticatedUser()
                ?: return@withContext PhoneVerifyResult.Error("Phone verified, but user session could not be loaded.")
            PhoneVerifyResult.Success(updatedUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.w(TAG, "Phone number already linked to another account", e)
            PhoneVerifyResult.Error("This phone number is already linked to another UniMate account.")
        } catch (e: Exception) {
            Log.e(TAG, "OTP verification failed", e)
            PhoneVerifyResult.Error(e.localizedMessage ?: "Invalid verification code. Please try again.")
        }
    }

    override suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                firebaseAuthOrNull()?.signOut()
            }
        }
    }

    private companion object {
        const val TAG = "Auth"
        const val FIREBASE_NOT_CONFIGURED_MESSAGE =
            "Firebase Authentication is not configured yet. Please verify google-services.json."
    }
}
