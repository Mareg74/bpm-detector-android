package com.Mareg74.bpmdetector.domain

object AutocorrelationTempo {
    fun estimateBpm(
        onsetTimesMs: List<Long>,
        minBpm: Double,
        maxBpm: Double,
    ): Pair<Double, Double>? {
        if (onsetTimesMs.size < 4) return null
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
        val bpm = 60_000.0 / bestLag
        val conf = (best.toDouble() / onsetTimesMs.size).coerceIn(0.0, 1.0)
        return bpm to conf
    }
}