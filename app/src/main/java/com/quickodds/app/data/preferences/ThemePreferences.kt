package com.quickodds.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single DataStore instance for the app. */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quickodds_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class ThemePreferences(private val dataStore: DataStore<Preferences>) {

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HAS_ACCEPTED_DISCLAIMER = booleanPreferencesKey("has_accepted_disclaimer")
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        try {
            ThemeMode.valueOf(prefs[THEME_MODE] ?: ThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    val hasAcceptedDisclaimer: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HAS_ACCEPTED_DISCLAIMER] ?: false
    }

    suspend fun setDisclaimerAccepted() {
        dataStore.edit { it[HAS_ACCEPTED_DISCLAIMER] = true }
    }
}
