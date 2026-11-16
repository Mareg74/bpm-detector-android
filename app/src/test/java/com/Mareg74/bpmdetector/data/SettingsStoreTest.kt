package com.Mareg74.bpmdetector.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class SettingsStoreTest {
    @Test
    fun persists_round_trip() = runTest {
        val ds = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File.createTempFile("settings", ".preferences_pb")
        }
        val store = SettingsStore(ds)
        store.update { it.copy(showDecimal = true, genreId = "dnb", theme = ThemeMode.Night) }
        val v = store.settings.first()
        assertThat(v.showDecimal).isTrue()
        assertThat(v.genreId).isEqualTo("dnb")
        assertThat(v.theme).isEqualTo(ThemeMode.Night)
    }
}