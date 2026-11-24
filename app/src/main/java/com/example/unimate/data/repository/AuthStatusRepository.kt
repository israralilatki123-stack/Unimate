package com.example.unimate.data.repository

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AuthSessionStatus {
    data object Unauthenticated : AuthSessionStatus
    data class AuthenticatedAndVerified(val uid: String, val email: String?) : AuthSessionStatus
}

interface AuthStatusRepository {
    suspend fun checkAuthStatus(): AuthSessionStatus
}

class FirebaseAuthStatusRepository(
    private val context: Context,
) : AuthStatusRepository {

    override suspend fun checkAuthStatus(): AuthSessionStatus = withContext(Dispatchers.IO) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            return@withContext AuthSessionStatus.Unauthenticated
        }

        val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
            ?: return@withContext AuthSessionStatus.Unauthenticated

        AuthSessionStatus.AuthenticatedAndVerified(
            uid = currentUser.uid,
            email = currentUser.email,
        )
    }
}
