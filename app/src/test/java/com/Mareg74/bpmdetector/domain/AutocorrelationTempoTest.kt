package com.Mareg74.bpmdetector.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutocorrelationTempoTest {
    @Test
    fun autocorrelation_recovers_120() {
        val periodMs = 500L // 120 BPM
        val onsets = (0L until 10L).map { it * periodMs }
        val (bpm, conf) = AutocorrelationTempo.estimateBpm(onsets, 60.0, 200.0)!!
        assertThat(bpm).isWithin(2.0).of(120.0)
        assertThat(conf).isGreaterThan(0.5)
    }

    @Test
    fun autocorrelation_recovers_156() {
        val periodMs = 385L
        val onsets = (0L until 14L).map { it * periodMs }
        val (bpm, conf) = AutocorrelationTempo.estimateBpm(onsets, 60.0, 200.0)!!
        assertThat(bpm).isWithin(5.0).of(156.0)
        assertThat(conf).isGreaterThan(0.3)
    }
}