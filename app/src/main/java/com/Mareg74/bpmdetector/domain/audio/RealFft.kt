package com.Mareg74.bpmdetector.domain.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** In-place radix-2 Cooley–Tukey FFT (power-of-two size). */
object RealFft {
    fun magnitudeSpectrum(samples: FloatArray): FloatArray {
        val n = samples.size
        require(n > 1 && (n and (n - 1)) == 0) { "FFT size must be a power of two" }
        val re = samples.copyOf()
        val im = FloatArray(n)
        bitReversePermute(re, im)
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var j = 0
                var wr = 1f
                var wi = 0f
                while (j < len / 2) {
                    val evenRe = re[i + j]
                    val evenIm = im[i + j]
                    val oddRe = re[i + j + len / 2]
                    val oddIm = im[i + j + len / 2]
                    val tRe = wr * oddRe - wi * oddIm
                    val tIm = wr * oddIm + wi * oddRe
                    re[i + j] = evenRe + tRe
                    im[i + j] = evenIm + tIm
                    re[i + j + len / 2] = evenRe - tRe
                    im[i + j + len / 2] = evenIm - tIm
                    val nWr = wr * wRe - wi * wIm
                    wi = wr * wIm + wi * wRe
                    wr = nWr
                    j++
                }
                i += len
            }
            len *= 2
        }
        val half = n / 2
        val mags = FloatArray(half)
        for (i in 0 until half) {
            mags[i] = sqrt(re[i] * re[i] + im[i] * im[i])
        }
        return mags
    }

    private fun bitReversePermute(
        re: FloatArray,
        im: FloatArray,
    ) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
    }
}