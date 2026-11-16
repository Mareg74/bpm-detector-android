package com.Mareg74.bpmdetector.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FundamentalTempoTest {
    @Test
    fun pulls_fast_estimate_toward_kick_grid() {
        val bpm = 140.0
        val kickPeriod = (60_000.0 / bpm).toLong()
        val onsets = mutableListOf<Long>()
        var t = 0L
        repeat(32) {
            onsets += t
            t += kickPeriod
        }
        val median = AutocorrelationTempo.medianIntervalBpm(onsets, 60.0, 200.0)!!
        assertThat(median.first).isWithin(6.0).of(140.0)
        val refined =
            FundamentalTempo.refine(
                onsets,
                rawBpm = 186.0,
                rawConf = 0.75,
                minBpm = 60.0,
                maxBpm = 200.0,
            )
        assertThat(refined.first).isAtMost(150.0)
    }
}