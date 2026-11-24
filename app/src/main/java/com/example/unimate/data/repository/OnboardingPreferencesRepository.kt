package com.example.unimate.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.example.unimate.data.local.unimateDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

interface OnboardingPreferencesRepository {
    val hasCompletedOnboardingFlow: Flow<Boolean>
    suspend fun hasCompletedOnboarding(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean)
}

class DataStoreOnboardingPreferencesRepository(
    private val context: Context,
) : OnboardingPreferencesRepository {

    override val hasCompletedOnboardingFlow: Flow<Boolean> =
        context.unimateDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_ONBOARDING_COMPLETED] ?: false
            }

    override suspend fun hasCompletedOnboarding(): Boolean =
        hasCompletedOnboardingFlow.first()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.unimateDataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("has_completed_onboarding")
    }
}
