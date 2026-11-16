package com.Mareg74.bpmdetector.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoveltyBpmEstimatorTest {
    @Test
    fun periodic_novelty_recovers_156() {
        val est = NoveltyBpmEstimator()
        val periodMs = 385L
        var t = 0L
        while (t < 12_000L) {
            val novelty = if (t % periodMs < 25) 2.5 else 0.05
            est.push(t, novelty)
            t += 20L
        }
        val result = est.estimate()!!
        assertThat(result.first).isWithin(4.0).of(156.0)
    }
}