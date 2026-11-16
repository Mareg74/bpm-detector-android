package com.Mareg74.bpmdetector.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Picks a musically plausible fundamental tempo when onsets also follow faster grids
 * (hi-hats, subdivisions). Biases toward slower, well-supported BPM when scores tie.
 */
object FundamentalTempo {
    private val HARMONIC_FACTORS =
        doubleArrayOf(
            1.0,
            0.5,
            2.0 / 3.0,
            3.0 / 4.0,
            4.0 / 5.0,
            5.0 / 6.0,
        )

    fun refine(
        onsets: List<Long>,
        rawBpm: Double,
        rawConf: Double,
        minBpm: Double,
        maxBpm: Double,
    ): Pair<Double, Double> {
        if (onsets.size < 4) return rawBpm to rawConf
        val candidates = LinkedHashSet<Double>()
        for (f in HARMONIC_FACTORS) {
            val bpm = rawBpm * f
            if (bpm in minBpm..maxBpm) candidates.add(bpm)
        }
        AutocorrelationTempo.medianIntervalBpm(onsets, minBpm, maxBpm)?.first?.let { median ->
            if (median in minBpm..maxBpm) candidates.add(median)
        }
        if (candidates.isEmpty()) return rawBpm to rawConf

        data class Scored(val bpm: Double, val score: Double)
        val scored =
            candidates
                .map { bpm ->
                    var score = intervalScore(onsets, bpm)
                    if (rawBpm >= 155.0 && bpm <= rawBpm * 0.78) {
                        score = (score * 1.18).coerceAtMost(1.0)
                    }
                    Scored(bpm, score)
                }
                .filter { it.score > 0.05 }
        if (scored.isEmpty()) return rawBpm to rawConf

        val bestScore = scored.maxOf { it.score }
        val floor = bestScore * 0.9
        val chosen =
            scored
                .filter { it.score >= floor }
                .minByOrNull { it.bpm }
                ?.bpm ?: rawBpm
        val chosenScore = scored.firstOrNull { abs(it.bpm - chosen) < 0.5 }?.score ?: bestScore
        val conf =
            (rawConf * 0.35 + chosenScore * 0.65).coerceIn(0.0, 1.0)
        return chosen to conf
    }

  private fun intervalScore(onsets: List<Long>, bpm: Double): Double {
        if (onsets.size < 3) return 0.0
        val period = 60_000.0 / bpm
        val deltas =
            (1 until onsets.size).map { (onsets[it] - onsets[it - 1]).toDouble() }
        if (deltas.isEmpty()) return 0.0
        var hits = 0
        for (d in deltas) {
            val ratio = (d / period).coerceAtLeast(0.5)
            val multiple = ratio.roundToInt().coerceAtLeast(1)
            val expected = multiple * period
            if (abs(d - expected) <= period * 0.14) hits++
        }
        return hits.toDouble() / deltas.size
    }
}