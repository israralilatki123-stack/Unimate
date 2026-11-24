package com.example.unimate.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.unimate.data.local.unimateDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class SessionPreferences(
    val rememberSession: Boolean = true,
    val lastEmail: String = "",
)

interface SessionPreferencesRepository {
    val sessionPreferencesFlow: Flow<SessionPreferences>
    suspend fun getSessionPreferences(): SessionPreferences
    suspend fun saveRememberSession(remember: Boolean, email: String)
}

class DataStoreSessionPreferencesRepository(
    private val context: Context,
) : SessionPreferencesRepository {

    override val sessionPreferencesFlow: Flow<SessionPreferences> =
        context.unimateDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs ->
                SessionPreferences(
                    rememberSession = prefs[KEY_REMEMBER_SESSION] ?: true,
                    lastEmail = prefs[KEY_LAST_EMAIL].orEmpty(),
                )
            }

    override suspend fun getSessionPreferences(): SessionPreferences =
        sessionPreferencesFlow.first()

    override suspend fun saveRememberSession(remember: Boolean, email: String) {
        context.unimateDataStore.edit { prefs ->
            prefs[KEY_REMEMBER_SESSION] = remember
            if (remember && email.isNotBlank()) {
                prefs[KEY_LAST_EMAIL] = email.trim()
            } else if (!remember) {
                prefs.remove(KEY_LAST_EMAIL)
            }
        }
    }

    private companion object {
        val KEY_REMEMBER_SESSION = booleanPreferencesKey("remember_session")
        val KEY_LAST_EMAIL = stringPreferencesKey("last_login_email")
    }
}
