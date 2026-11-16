package com.Mareg74.bpmdetector.ui.measure

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.Mareg74.bpmdetector.audio.AudioInputFactory
import com.Mareg74.bpmdetector.data.SettingsStore
import com.Mareg74.bpmdetector.data.settingsDataStore
import com.Mareg74.bpmdetector.domain.BpmEngine
import com.Mareg74.bpmdetector.domain.GenreCatalog
import com.Mareg74.bpmdetector.domain.SessionStore

class MeasureViewModelFactory(
    private val appContext: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MeasureViewModel::class.java))
        val settingsStore = SettingsStore(appContext.settingsDataStore)
        val audioInputFactory = AudioInputFactory(appContext)
        val engine = BpmEngine(GenreCatalog.byId("auto"))
        val session = SessionStore()
        return MeasureViewModel(settingsStore, audioInputFactory, engine, session) as T
    }
}