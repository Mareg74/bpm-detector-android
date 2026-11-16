package com.Mareg74.bpmdetector.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.Mareg74.bpmdetector.audio.AudioSourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<UserSettings> = dataStore.data.map { prefs -> prefs.toUserSettings() }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toUserSettings())
            prefs[SOURCE_KEY] = updated.source.name
            prefs[GENRE_ID_KEY] = updated.genreId
            prefs[SHOW_DECIMAL_KEY] = updated.showDecimal
            prefs[THEME_KEY] = updated.theme.name
        }
    }

    private fun Preferences.toUserSettings(): UserSettings =
        UserSettings(
            source = this[SOURCE_KEY]?.let { AudioSourceKind.valueOf(it) } ?: AudioSourceKind.Mic,
            genreId = this[GENRE_ID_KEY] ?: DEFAULT_GENRE_ID,
            showDecimal = this[SHOW_DECIMAL_KEY] ?: false,
            theme = this[THEME_KEY]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.Day,
        )

    companion object {
        private const val DEFAULT_GENRE_ID = "auto"

        private val SOURCE_KEY = stringPreferencesKey("source")
        private val GENRE_ID_KEY = stringPreferencesKey("genreId")
        private val SHOW_DECIMAL_KEY = booleanPreferencesKey("showDecimal")
        private val THEME_KEY = stringPreferencesKey("theme")
    }
}