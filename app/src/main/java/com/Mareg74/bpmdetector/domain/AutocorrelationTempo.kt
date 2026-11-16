package com.Mareg74.bpmdetector.domain

import kotlin.math.abs

object AutocorrelationTempo {
    fun medianIntervalBpm(
        onsetTimesMs: List<Long>,
        minBpm: Double,
        maxBpm: Double,
    ): Pair<Double, Double>? {
        if (onsetTimesMs.size < 3) return null
        val minGap = (60_000.0 / maxBpm).toInt()
        val maxGap = (60_000.0 / minBpm).toInt()
        val deltas =
            (1 until onsetTimesMs.size).map { (onsetTimesMs[it] - onsetTimesMs[it - 1]).toInt() }
                .filter { it in minGap..maxGap }
        if (deltas.isEmpty()) return null
        val beatLike = deltas.filter { it <= maxGap * 2 / 3 }
        val use = if (beatLike.size >= 2) beatLike else deltas
        val sorted = use.sorted()
        val med = sorted[sorted.size / 2]
        val bpm = 60_000.0 / med
        val conf = (use.count { abs(it - med) <= 35 }.toDouble() / use.size)
            .coerceIn(0.0, 1.0)
        return bpm to conf
    }
    fun estimateBpm(
        onsetTimesMs: List<Long>,
        minBpm: Double,
        maxBpm: Double,
        minOnsets: Int = 4,
    ): Pair<Double, Double>? {
        if (onsetTimesMs.size < minOnsets) return null
        val minLag = (60_000.0 / maxBpm).toInt()
        val maxLag = (60_000.0 / minBpm).toInt()
        val scores = IntArray(maxLag + 1)
        for (i in onsetTimesMs.indices) {
            for (j in i + 1 until onsetTimesMs.size) {
                val lag = (onsetTimesMs[j] - onsetTimesMs[i]).toInt()
                if (lag in minLag..maxLag) {
                    scores[lag]++
                }
            }
        }
        var bestLag = -1
        var best = 0
        for (lag in minLag..maxLag) {
            val s = scores[lag] +
                scores.getOrElse(lag - 1) { 0 } +
                scores.getOrElse(lag + 1) { 0 }
            if (s > best) {
                best = s
                bestLag = lag
            }
        }
        if (bestLag <= 0 || best == 0) return null
        val floor = (best * 0.9).toInt().coerceAtLeast(1)
        var chosen = bestLag
        for (lag in minLag..maxLag) {
            val s = scores[lag] +
                scores.getOrElse(lag - 1) { 0 } +
                scores.getOrElse(lag + 1) { 0 }
            if (s >= floor && lag < chosen) chosen = lag
        }
        bestLag = chosen
        var half = bestLag / 2
        while (half >= minLag) {
            val s = scores[half] +
                scores.getOrElse(half - 1) { 0 } +
                scores.getOrElse(half + 1) { 0 }
            if (s >= floor) {
                bestLag = half
                half /= 2
            } else {
                break
            }
        }
        val bpm = 60_000.0 / bestLag
        val conf = (best.toDouble() / onsetTimesMs.size).coerceIn(0.0, 1.0)
        return bpm to conf
    }
}