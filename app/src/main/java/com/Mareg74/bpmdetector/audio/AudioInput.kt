package com.Mareg74.bpmdetector.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

sealed class AudioError {
    data object PermissionDenied : AudioError()

    data class SourceUnavailable(
        val kind: AudioSourceKind,
        val message: String,
    ) : AudioError()

    data class Disconnected(
        val kind: AudioSourceKind,
    ) : AudioError()
}

interface AudioInput {
    val frames: Flow<AudioFrame>
    val errors: SharedFlow<AudioError>

    suspend fun start()

    suspend fun stop()

    fun isAvailable(): Boolean
}