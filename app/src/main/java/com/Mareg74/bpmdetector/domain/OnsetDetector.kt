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
        val hop = 512
        val out = mutableListOf<Long>()
        val energies = mutableListOf<Float>()
        var i = 0
        var localHop = 0
        val threshold = 0.02f + (1.0 - sensitivity).toFloat() * 0.05f
        while (i + hop <= samples.size) {
            var e = 0f
            for (k in 0 until hop) {
                e += samples[i + k] * samples[i + k]
            }
            e /= hop
            energies += e
            val flux = (e - prevEnergy).coerceAtLeast(0f)
            prevEnergy = e
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
        if (out.isNotEmpty()) return out
        if (energies.isEmpty()) return out

        val frameMean = energies.sum() / energies.size
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
        val median = energies.sorted()[energies.size / 2]
        val peakFloor =
            (median * (1.08f + (1.0 - sensitivity).toFloat() * 0.25f))
                .coerceAtLeast(1e-5f)
        val peakOnsets = mutableListOf<Long>()
        energies.forEachIndexed { idx, e ->
            val tMs = baseTimeMs + idx * hopMs
            val prevE = energies.getOrNull(idx - 1) ?: e
            val nextE = energies.getOrNull(idx + 1) ?: e
            val prominence = e - minOf(prevE, nextE)
            val peakHit =
                e >= prevE && e > nextE &&
                    e >= peakFloor &&
                    prominence >= peakFloor * relativeFactor * 0.5f
            if (peakHit && (peakOnsets.isEmpty() || tMs - peakOnsets.last() > 100)) {
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
}