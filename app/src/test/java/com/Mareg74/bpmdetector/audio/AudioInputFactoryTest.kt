package com.Mareg74.bpmdetector.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioInputFactoryTest {
    @Test
    fun availability_mic_probe_and_stubs_usb_internal() {
        val factory =
            AudioInputFactory(
                context = object : android.content.ContextWrapper(null) {},
                micAvailable = { true },
            )
        val map = factory.availability()
        assertThat(map[AudioSourceKind.Mic]).isTrue()
        assertThat(map[AudioSourceKind.Usb]).isFalse()
        assertThat(map[AudioSourceKind.Internal]).isFalse()
    }

    @Test
    fun create_mic_returns_mic_input() {
        val factory =
            AudioInputFactory(
                context = object : android.content.ContextWrapper(null) {},
                micAvailable = { true },
            )
        assertThat(factory.create(AudioSourceKind.Mic)).isInstanceOf(MicAudioInput::class.java)
    }
}