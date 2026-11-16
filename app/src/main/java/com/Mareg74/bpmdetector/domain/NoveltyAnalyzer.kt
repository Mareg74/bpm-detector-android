package com.Mareg74.bpmdetector.domain

import com.Mareg74.bpmdetector.domain.audio.RealFft
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max

/**
 * Multi-band spectral-flux novelty (inspired by public real-time BPM analyzers using
 * Web Audio FFT + kick-weighted novelty). Feeds [NoveltyBpmEstimator].
 */
class NoveltyAnalyzer(
    private val fftSize: Int = 4096,
    genre: NoveltyGenre = NoveltyGenre.Electro,
) {
    enum class NoveltyGenre { Electro, Rock, Acoustic }

    private data class Band(
        val lowHz: Double,
        val highHz: Double,
        val baseWeight: Double,
    )

    private val bands =
        arrayOf(
            Band(30.0, 220.0, 1.7),
            Band(220.0, 650.0, 1.0),
            Band(650.0, 2800.0, 1.3),
            Band(2800.0, 9000.0, 1.6),
        )

    private var genre = genre
    private val ring = FloatArray(fftSize)
    private var ringFill = 0
    private var prevBandMags: Array<FloatArray>? = null
    private var lastSampleMs = 0L

    fun setGenre(g: NoveltyGenre) {
        genre = g
    }

    fun push(
        samples: FloatArray,
        sampleRate: Int,
        timeMs: Long,
    ): Double? {
        for (s in samples) {
            ring[ringFill] = s
            ringFill = (ringFill + 1) % fftSize
        }
        if (timeMs - lastSampleMs < 20) return null
        lastSampleMs = timeMs
        val ordered = FloatArray(fftSize)
        var j = ringFill
        for (i in 0 until fftSize) {
            ordered[i] = ring[j]
            j = (j + 1) % fftSize
        }
        applyHann(ordered)
        val mags = RealFft.magnitudeSpectrum(ordered)
        val binHz = sampleRate / 2.0 / mags.size
        if (prevBandMags == null || prevBandMags!![0].size != mags.size) {
            prevBandMags = Array(bands.size) { FloatArray(mags.size) }
        }
        val weights = genreWeights()
        val bandFluxes = DoubleArray(bands.size)
        for (b in bands.indices) {
            val band = bands[b]
            var flux = 0.0
            var count = 0
            for (i in mags.indices) {
                val hz = i * binHz
                if (hz < band.lowHz || hz > band.highHz) continue
                val linear = (mags[i] / fftSize).coerceAtLeast(1e-9f)
                val db = 20f * log10(linear)
                val mag = max(0f, (db + 100f) / 100f)
                val prev = prevBandMags!![b][i]
                val d = mag - prev
                prevBandMags!![b][i] = mag
                if (d > 0) {
                    flux += d
                    count++
                }
            }
            bandFluxes[b] =
                if (count > 0) {
                    (flux / count) * band.baseWeight * weights[b]
                } else {
                    0.0
                }
        }
        return noveltyFromBands(bandFluxes)
    }

    fun reset() {
        ringFill = 0
        ring.fill(0f)
        prevBandMags = null
        lastSampleMs = 0L
    }

    private fun genreWeights(): DoubleArray {
        return when (genre) {
            NoveltyGenre.Electro -> doubleArrayOf(1.0, 1.0, 1.0, 1.0)
            NoveltyGenre.Rock ->
                doubleArrayOf(
                    2.5 / 1.7,
                    1.4 / 1.0,
                    0.75 / 1.3,
                    0.25 / 1.6,
                )
            NoveltyGenre.Acoustic ->
                doubleArrayOf(
                    0.6 / 1.7,
                    0.8 / 1.0,
                    2.2 / 1.3,
                    2.8 / 1.6,
                )
        }
    }

    private fun noveltyFromBands(f: DoubleArray): Double {
        val kick = f[0]
        val low = f[1]
        val mid = f[2]
        val high = f[3]
        return when (genre) {
            NoveltyGenre.Acoustic -> {
                var n = mid * 2.5 + high * 2.2 + low * 0.9 + kick * 0.4
                val ratio = (mid + high) / (kick + low + 1e-3)
                if (ratio > 1.5) n *= 1.2
                n
            }
            NoveltyGenre.Rock -> {
                var n = kick * 2.8 + low * 1.6 + mid * 0.9 + high * 0.35
                val kickRatio = kick / (low + mid + high + 1e-3)
                if (kickRatio > 0.35) n *= 1.25
                n
            }
            NoveltyGenre.Electro -> {
                var n = kick * 3.4 + low * 1.5 + mid * 0.75 + high * 0.85
                val kickRatio = kick / (low + mid + high + 1e-3)
                if (kickRatio > 0.28) n *= 1.28
                n
            }
        }
    }

    private fun applyHann(buf: FloatArray) {
        val n = buf.size
        for (i in buf.indices) {
            val w = 0.5 - 0.5 * kotlin.math.cos(2.0 * PI * i / (n - 1))
            buf[i] = (buf[i] * w).toFloat()
        }
    }
}