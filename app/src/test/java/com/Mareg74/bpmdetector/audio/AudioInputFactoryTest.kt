package com.Mareg74.bpmdetector.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioInputFactoryTest {
    @Test
    fun availability_reflects_probes() {
        val factory =
            AudioInputFactory(
                context = object : android.content.ContextWrapper(null) {},
                micAvailable = { true },
                usbAvailable = { true },
                internalAvailable = { false },
            )
        val map = factory.availability()
        assertThat(map[AudioSourceKind.Mic]).isTrue()
        assertThat(map[AudioSourceKind.Usb]).isTrue()
        assertThat(map[AudioSourceKind.Internal]).isFalse()
    }

    @Test
    fun create_returns_expected_input_types() {
        val factory =
            AudioInputFactory(
                context = object : android.content.ContextWrapper(null) {},
                micAvailable = { true },
            )
        assertThat(factory.create(AudioSourceKind.Mic)).isInstanceOf(MicAudioInput::class.java)
        assertThat(factory.create(AudioSourceKind.Usb)).isInstanceOf(UsbAudioInput::class.java)
        assertThat(factory.create(AudioSourceKind.Internal))
            .isInstanceOf(InternalCaptureAudioInput::class.java)
    }
}