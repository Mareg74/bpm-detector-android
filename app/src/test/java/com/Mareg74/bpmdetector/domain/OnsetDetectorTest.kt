package com.Mareg74.bpmdetector.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnsetDetectorTest {
    @Test
    fun detects_regular_onsets_near_120bpm() {
        val sr = 44100
        val bpm = 120.0
        val period = (60.0 / bpm * sr).toInt()
        val samples = FloatArray(sr * 4) // 4s
        for (i in samples.indices step period) {
            for (j in 0 until 32) {
                if (i + j < samples.size) samples[i + j] = 1f
            }
        }
        val det = OnsetDetector(0.5)
        val onsets = det.push(samples, sr)
        assertThat(onsets.size).isAtLeast(6)
    }
}