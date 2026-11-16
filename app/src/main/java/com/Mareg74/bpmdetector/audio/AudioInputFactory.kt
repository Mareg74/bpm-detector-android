package com.Mareg74.bpmdetector.audio

import android.content.Context

class AudioInputFactory(
    private val context: Context,
    private val micAvailable: () -> Boolean = { MicAudioInput.isHardwareAvailable() },
    private val usbAvailable: () -> Boolean = { UsbAudioInput.hasDevice(context) },
    private val internalAvailable: () -> Boolean = { InternalCaptureAudioInput.isSupported() },
) {
    fun create(kind: AudioSourceKind): AudioInput =
        when (kind) {
            AudioSourceKind.Mic -> MicAudioInput(context)
            AudioSourceKind.Usb -> UsbAudioInput(context)
            AudioSourceKind.Internal -> InternalCaptureAudioInput(context)
        }

    fun availability(): Map<AudioSourceKind, Boolean> =
        mapOf(
            AudioSourceKind.Mic to micAvailable(),
            AudioSourceKind.Usb to usbAvailable(),
            AudioSourceKind.Internal to internalAvailable(),
        )
}