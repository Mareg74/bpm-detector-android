package com.Mareg74.bpmdetector.data

import com.Mareg74.bpmdetector.audio.AudioSourceKind

enum class ThemeMode {
    Day,
    Night,
}

data class UserSettings(
    val source: AudioSourceKind,
    val genreId: String,
    val showDecimal: Boolean,
    val theme: ThemeMode,
)