package com.Mareg74.bpmdetector.audio

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow

class AudioInputFactory(
    private val context: Context,
    private val micAvailable: () -> Boolean = { MicAudioInput.isHardwareAvailable() },
) {
    fun create(kind: AudioSourceKind): AudioInput =
        when (kind) {
            AudioSourceKind.Mic -> MicAudioInput(context)
            AudioSourceKind.Usb,
            AudioSourceKind.Internal,
            -> StubUnavailableAudioInput(kind)
        }

    fun availability(): Map<AudioSourceKind, Boolean> =
        mapOf(
            AudioSourceKind.Mic to micAvailable(),
            AudioSourceKind.Usb to false,
            AudioSourceKind.Internal to false,
        )
}

private class StubUnavailableAudioInput(
    private val kind: AudioSourceKind,
) : AudioInput {
    private val _errors = MutableSharedFlow<AudioError>(extraBufferCapacity = 2)

    override val frames: Flow<AudioFrame> = emptyFlow()
    override val errors: SharedFlow<AudioError> = _errors.asSharedFlow()

    override fun isAvailable(): Boolean = false

    override suspend fun start() {
        _errors.emit(
            AudioError.SourceUnavailable(
                kind,
                "Source not yet implemented",
            ),
        )
    }

    override suspend fun stop() {}
}