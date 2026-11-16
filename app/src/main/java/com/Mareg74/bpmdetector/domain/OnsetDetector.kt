package com.Mareg74.bpmdetector.domain

class OnsetDetector(private var sensitivity: Double) {
    private var prevEnergy = 0f
    private var lastFrameMean = 0f
    private var frameIndex = 0L

    fun updateSensitivity(s: Double) {
        sensitivity = s
    }

    fun push(
        samples: FloatArray,
        sampleRate: Int,
        baseTimeMs: Long = 0L,
    ): List<Long> {
        val smoothed = lowPass(samples, (sampleRate / 220).coerceIn(48, 256))
        val hop = 512
        val out = mutableListOf<Long>()
        val lowEnergies = mutableListOf<Float>()
        var i = 0
        var localHop = 0
        val threshold = 0.02f + (1.0 - sensitivity).toFloat() * 0.05f
        val minOnsetGapMs = 120L
        while (i + hop <= samples.size) {
            var e = 0f
            for (k in 0 until hop) {
                e += samples[i + k] * samples[i + k]
            }
            e /= hop
            var eLow = 0f
            for (k in 0 until hop) {
                eLow += smoothed[i + k] * smoothed[i + k]
            }
            lowEnergies += eLow / hop
            val flux = (e - prevEnergy).coerceAtLeast(0f)
            prevEnergy = e
            if (flux > threshold) {
                val tMs = baseTimeMs + localHop * hop * 1000L / sampleRate
                if (out.isEmpty() || tMs - out.last() >= minOnsetGapMs) {
                    out += tMs
                }
            }
            localHop++
            frameIndex++
            i += hop
        }
        if (out.isNotEmpty()) return out
        if (lowEnergies.isEmpty()) return out

        val frameMean = lowEnergies.sum() / lowEnergies.size
        val frameRiseOnsets = mutableListOf<Long>()
        if (lastFrameMean > 1e-4f) {
            val rise = (frameMean - lastFrameMean).coerceAtLeast(0f)
            val relativeRise = rise / lastFrameMean
            val riseThreshold = 0.025f + (1.0 - sensitivity).toFloat() * 0.05f
            if (relativeRise > riseThreshold) {
                frameRiseOnsets += baseTimeMs
            }
        }
        lastFrameMean = frameMean * 0.65f + lastFrameMean * 0.35f

        val hopMs = hop * 1000L / sampleRate
        val relativeFactor = 0.12f + sensitivity.toFloat() * 0.18f
        val median = lowEnergies.sorted()[lowEnergies.size / 2]
        val peakFloor =
            (median * (1.08f + (1.0 - sensitivity).toFloat() * 0.25f))
                .coerceAtLeast(1e-5f)
        val peakOnsets = mutableListOf<Long>()
        lowEnergies.forEachIndexed { idx, e ->
            val tMs = baseTimeMs + idx * hopMs
            val prevE = lowEnergies.getOrNull(idx - 1) ?: e
            val nextE = lowEnergies.getOrNull(idx + 1) ?: e
            val prominence = e - minOf(prevE, nextE)
            val peakHit =
                e >= prevE && e > nextE &&
                    e >= peakFloor &&
                    prominence >= peakFloor * relativeFactor * 0.5f
            if (peakHit && (peakOnsets.isEmpty() || tMs - peakOnsets.last() >= minOnsetGapMs)) {
                peakOnsets += tMs
            }
        }
        return when {
            peakOnsets.isNotEmpty() -> peakOnsets
            frameRiseOnsets.isNotEmpty() -> frameRiseOnsets
            else -> emptyList()
        }
    }

    fun reset() {
        prevEnergy = 0f
        lastFrameMean = 0f
        frameIndex = 0
    }

    private fun lowPass(samples: FloatArray, width: Int): FloatArray {
        if (width <= 1 || samples.isEmpty()) return samples
        val out = FloatArray(samples.size)
        var sum = 0f
        for (i in samples.indices) {
            sum += samples[i]
            if (i >= width) sum -= samples[i - width]
            val denom = minOf(i + 1, width).toFloat()
            out[i] = sum / denom
        }
        return out
    }
}