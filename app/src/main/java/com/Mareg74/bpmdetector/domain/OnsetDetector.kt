package com.Mareg74.bpmdetector.domain

class OnsetDetector(private var sensitivity: Double) {
    private var prevEnergy = 0f
    private var frameIndex = 0L

    fun updateSensitivity(s: Double) {
        sensitivity = s
    }

    fun push(
        samples: FloatArray,
        sampleRate: Int,
        baseTimeMs: Long = 0L,
    ): List<Long> {
        val hop = 512
        val out = mutableListOf<Long>()
        var i = 0
        var localHop = 0
        while (i + hop <= samples.size) {
            var e = 0f
            for (k in 0 until hop) {
                e += samples[i + k] * samples[i + k]
            }
            e /= hop
            val flux = (e - prevEnergy).coerceAtLeast(0f)
            prevEnergy = e
            val threshold = 0.02f + (1.0 - sensitivity).toFloat() * 0.05f
            if (flux > threshold) {
                val tMs = baseTimeMs + localHop * hop * 1000L / sampleRate
                if (out.isEmpty() || tMs - out.last() > 100) {
                    out += tMs
                }
            }
            localHop++
            frameIndex++
            i += hop
        }
        return out
    }

    fun reset() {
        prevEnergy = 0f
        frameIndex = 0
    }
}