package com.example.unimate.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.unimateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "unimate_preferences"
)
