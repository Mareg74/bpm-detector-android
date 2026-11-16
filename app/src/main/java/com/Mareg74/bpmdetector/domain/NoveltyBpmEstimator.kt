package com.Mareg74.bpmdetector.domain

import android.util.Log
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Binned novelty autocorrelation with parabolic lag refinement and beat-interval fusion.
 */
class NoveltyBpmEstimator(
    private val minBpm: Double = 60.0,
    private val maxBpm: Double = 200.0,
    private val genre: NoveltyAnalyzer.NoveltyGenre = NoveltyAnalyzer.NoveltyGenre.Electro,
) {
    private val history = ArrayDeque<Pair<Long, Double>>()
    private val beatTimes = ArrayDeque<Long>()
    private var smoothedBpm: Double? = null
    private var lastPeakScanAnchor = 0L
    private var lastDebugLogMs = 0L

    private val noveltyWinMs = 8_000L
    private val TAG = "BpmNovelty"
    private val DEBUG_LOG = true
    private val binMs = 20L

    fun reset() {
        history.clear()
        beatTimes.clear()
        smoothedBpm = null
        lastPeakScanAnchor = 0L
    }

    fun isWarmingUp(): Boolean {
        if (history.size < 8) return false
        val span = history.last().first - history.first().first
        return span in 1 until 3_200
    }

    fun push(
        timeMs: Long,
        novelty: Double,
    ) {
        history.addLast(timeMs to novelty)
        val cutoff = timeMs - noveltyWinMs
        while (history.isNotEmpty() && history.first().first < cutoff) {
            history.removeFirst()
        }
        detectPeaks(timeMs)
    }

    fun estimate(): Pair<Double, Double>? {
        if (history.size < 40) return null
        val span = history.last().first - history.first().first
        if (span < 3200) return null

        val winMs = min(span, noveltyWinMs)
        val n = (winMs / binMs).toInt()
        val now = history.last().first
        val bins = FloatArray(n)
        for ((t, v) in history) {
            val age = now - t
            if (age > winMs) continue
            val idx = n - 1 - (age / binMs).toInt()
            val weight = exp(-age / 3200.0)
            if (idx in 0 until n) {
                bins[idx] = max(bins[idx], (v * weight).toFloat())
            }
        }

        val lagMin = (60_000.0 / 180.0 / binMs).toInt().coerceAtLeast(1)
        val lagMax = (60_000.0 / 60.0 / binMs).toInt()
        var bestLag = -1
        var bestCorr = Double.NEGATIVE_INFINITY
        val corrByLag = DoubleArray(lagMax + 1)
        for (lag in lagMin..lagMax) {
            val count = n - lag
            if (count <= 0) continue
            var corr = 0.0
            for (i in 0 until count) {
                corr += bins[i] * bins[i + lag]
            }
            corr /= count
            corrByLag[lag] = corr
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }
        if (bestLag < 0 || bestCorr < 1e-6) return null

        val slowBpm = 60_000.0 / (bestLag * binMs)
        val danceLagMin = (60_000.0 / 178.0 / binMs).toInt()
        val danceLagMax = (60_000.0 / 122.0 / binMs).toInt()
        if (slowBpm < 112.0 || genre == NoveltyAnalyzer.NoveltyGenre.Electro) {
            var danceLag = bestLag
            var danceCorr = bestCorr
            var danceBpm = slowBpm
            val floor =
                if (genre == NoveltyAnalyzer.NoveltyGenre.Electro) bestCorr * 0.48 else bestCorr * 0.58
            for (lag in danceLagMin..danceLagMax) {
                val c = corrByLag.getOrElse(lag) { 0.0 }
                if (c < floor) continue
                val lagBpm = 60_000.0 / (lag * binMs)
                val take =
                    c > danceCorr + 1e-9 ||
                        (c >= danceCorr * 0.94 && lagBpm > danceBpm + 0.8)
                if (take) {
                    danceCorr = c
                    danceLag = lag
                    danceBpm = lagBpm
                }
            }
            if (danceLag != bestLag && danceCorr >= floor) {
                bestLag = danceLag
                bestCorr = danceCorr
            }
        }

        val peakMedIv = recentBeatMedianIntervalMs()
        var refinedLag = parabolicRefinedLag(bestLag, corrByLag, lagMin, lagMax)
        refinedLag =
            resolveOneBinFasterDanceAlias(
                refinedLag,
                corrByLag,
                lagMin,
                lagMax,
                peakMedIv,
            )

        var bpm = 60_000.0 / (refinedLag * binMs)
        refinedLag =
            preferDanceBandLagIfPeaksSupport(
                refinedLag,
                bpm,
                bestCorr,
                corrByLag,
                lagMin,
                lagMax,
                peakMedIv,
            )
        bpm = 60_000.0 / (refinedLag * binMs)
        if (bpm in 125.0..153.5) {
            var fasterLag = refinedLag
            var fasterBpm = bpm
            val corrFloor = if (bpm < 150.0) bestCorr * 0.82 else bestCorr * 0.86
            for (lag in lagMin..lagMax) {
                val c = corrByLag.getOrElse(lag) { 0.0 }
                if (c < corrFloor) continue
                val lagRefined = parabolicRefinedLag(lag, corrByLag, lagMin, lagMax)
                val candidate = 60_000.0 / (lagRefined * binMs)
                if (candidate in 149.0..170.0 && candidate > fasterBpm && lagRefined < fasterLag) {
                    fasterLag = lagRefined
                    fasterBpm = candidate
                }
            }
                if (fasterBpm > bpm + 2.5) {
                refinedLag = parabolicRefinedLag(fasterLag.toInt(), corrByLag, lagMin, lagMax)
                bpm = 60_000.0 / (refinedLag * binMs)
            }
        }
        if (bpm < 105.0) {
            val halfLag = (refinedLag / 2.0).toInt()
            if (halfLag >= lagMin && halfLag <= lagMax) {
                val corrHalf = corrByLag[halfLag]
                if (corrHalf >= bestCorr * 0.52) {
                    refinedLag = parabolicRefinedLag(halfLag, corrByLag, lagMin, lagMax)
                    bpm = 60_000.0 / (refinedLag * binMs)
                }
            }
        }
        while (bpm > 180) bpm /= 2
        while (bpm < 60) bpm *= 2
        if (bpm < 60 || bpm > 180) return null

        fundamentalBpmFromPeakIntervals()?.let { fund ->
            when {
                bpm < 112.0 && fund in 128.0..175.0 -> bpm = fund
                fund > bpm + 2.5 && fund in 148.0..168.0 && bpm in 138.0..153.5 -> bpm = fund
            }
        }

        if (beatTimes.size >= 4) {
            val intervals = mutableListOf<Double>()
            val list = beatTimes.toList()
            for (i in 1 until list.size) {
                val iv = (list[i] - list[i - 1]).toDouble()
                if (iv in 200.0..1500.0) intervals.add(iv)
            }
            if (intervals.size >= 3) {
                val fastIv = intervals.filter { it in 378.0..398.0 }
                val medIv = median(intervals)
                val meanIv = intervals.sum() / intervals.size
                val std =
                    kotlin.math.sqrt(
                        intervals.sumOf { (it - meanIv) * (it - meanIv) } / intervals.size,
                    )
                val relStd = std / meanIv
                val bpmPeak = 60_000.0 / medIv
                val peaksStable = relStd < 0.14
                if (
                    fastIv.size >= 2 &&
                    medIv in 403.0..418.0 &&
                    bpm in 144.0..151.0
                ) {
                    val fastMed = median(fastIv)
                    val bpmFast = 60_000.0 / fastMed
                    if (bpmFast in 150.5..159.5) {
                        bpm = bpm * 0.12 + bpmFast * 0.88
                    }
                } else if (
                    peaksStable &&
                    medIv in 384.0..396.0 &&
                    bpmPeak in 151.5..156.5 &&
                    bpm in 143.0..151.0
                ) {
                    bpm = bpmPeak
                } else if (peaksStable && bpmPeak in 125.0..178.0 && bpm < bpmPeak * 0.72) {
                    bpm = bpmPeak
                } else if (
                    peaksStable &&
                    bpmPeak in 130.0..175.0 &&
                    bpm in 133.0..153.5 &&
                    bpmPeak - bpm in 2.5..16.0
                ) {
                    val towardPeak =
                        if (medIv in 368.0..402.0 && bpmPeak in 149.0..165.0) 0.82 else 0.68
                    bpm = bpm * (1.0 - towardPeak) + bpmPeak * towardPeak
                } else {
                    when {
                        abs(bpm - bpmPeak) < 15 -> bpm = (bpm + bpmPeak) / 2
                        abs(bpm * 2 - bpmPeak) < 15 -> bpm = (bpm * 2 + bpmPeak) / 2
                        abs(bpm - bpmPeak * 2) < 15 -> bpm = (bpm + bpmPeak * 2) / 2
                        bpm < 105 &&
                            bpmPeak in 130.0..178.0 &&
                            abs(bpm * 2 - bpmPeak) < 32 ->
                            bpm = (bpm * 2 + bpmPeak) / 2
                    }
                }
            }
        }

        var alpha = if (genre == NoveltyAnalyzer.NoveltyGenre.Acoustic) 0.35 else 0.28
        smoothedBpm =
            smoothedBpm?.let { prev ->
                when {
                    abs(bpm - prev) > 28 -> bpm
                    prev in 145.0..151.0 &&
                        bpm in 151.0..158.0 &&
                        bpm > prev + 4.0 -> bpm
                    else -> {
                        if (genre == NoveltyAnalyzer.NoveltyGenre.Electro &&
                            prev in 145.5..150.5 &&
                            bpm in 151.5..157.5 &&
                            bpm > prev
                        ) {
                            alpha = 0.5
                        }
                        (1 - alpha) * prev + alpha * bpm
                    }
                }
            } ?: bpm
        if (DEBUG_LOG) {
            val now = history.lastOrNull()?.first ?: 0L
            if (now - lastDebugLogMs >= 900L) {
                lastDebugLogMs = now
                runCatching {
                    Log.d(
                        TAG,
                        "acfLag=%.2f bpm=%.1f smooth=%.1f peakIv=%s bestLag=%d c19=%.3f c20=%.3f"
                            .format(
                                refinedLag,
                                bpm,
                                smoothedBpm,
                                peakMedIv?.let { "%.0f".format(it) } ?: "—",
                                bestLag,
                                corrByLag.getOrElse(19) { 0.0 },
                                corrByLag.getOrElse(20) { 0.0 },
                            ),
                    )
                }
            }
        }
        val confidence =
            ((bestCorr * 80).coerceIn(0.0, 1.0) + if (beatTimes.size >= 6) 0.25 else 0.0)
                .coerceIn(0.0, 1.0)
        return smoothedBpm!! to confidence
    }

    fun recentBeats(): List<Long> = beatTimes.toList()

    private fun detectPeaks(now: Long) {
        if (history.size < 15) return
        val refBpm = smoothedBpm ?: 128.0
        val minInterval =
            when (genre) {
                NoveltyAnalyzer.NoveltyGenre.Rock -> 240L
                NoveltyAnalyzer.NoveltyGenre.Acoustic -> 280L
                NoveltyAnalyzer.NoveltyGenre.Electro ->
                    (60_000.0 / refBpm / 2.12).toLong().coerceIn(168L, 380L)
            }
        val values = history.map { it.second }
        val mean = values.sum() / values.size
        val std =
            sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        val threshold =
            when (genre) {
                NoveltyAnalyzer.NoveltyGenre.Rock -> mean + 1.35 * std
                NoveltyAnalyzer.NoveltyGenre.Acoustic -> mean + 0.9 * std
                NoveltyAnalyzer.NoveltyGenre.Electro -> mean + 0.88 * std
            }
        val win =
            when (genre) {
                NoveltyAnalyzer.NoveltyGenre.Rock -> 5
                NoveltyAnalyzer.NoveltyGenre.Acoustic -> 6
                NoveltyAnalyzer.NoveltyGenre.Electro -> 4
            }
        val list = history.toList()
        for (i in win until list.size - win) {
            val curr = list[i]
            if (curr.first <= lastPeakScanAnchor) continue
            var isMax = true
            for (k in 1..win) {
                if (list[i - k].second >= curr.second + 1e-3 ||
                    list[i + k].second >= curr.second + 1e-3
                ) {
                    isMax = false
                    break
                }
            }
            if (isMax && curr.second > threshold) {
                val lastBeat = beatTimes.lastOrNull() ?: 0L
                if (curr.first - lastBeat > minInterval) {
                    beatTimes.addLast(curr.first)
                    if (beatTimes.size > 40) beatTimes.removeFirst()
                }
            }
        }
        if (list.size > win + 1) {
            lastPeakScanAnchor = list[list.size - win - 1].first
        }
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        return s[s.size / 2]
    }

    private fun parabolicRefinedLag(
        lag: Int,
        corrByLag: DoubleArray,
        lagMin: Int,
        lagMax: Int,
    ): Double {
        if (lag - 1 < lagMin || lag + 1 > lagMax) return lag.toDouble()
        val cL = corrByLag[lag - 1]
        val cC = corrByLag[lag]
        val cR = corrByLag[lag + 1]
        val denom = cL - 2 * cC + cR
        if (abs(denom) <= 1e-9) return lag.toDouble()
        val delta = 0.5 * (cL - cR) / denom
        return lag + max(-1.0, min(1.0, delta))
    }

    /**
     * Autocorr bins are 20 ms: 147 vs 154 is often lag N vs N−1 (~5 %). Prefer the faster
     * lag when the slower read sits ~147–151 and the adjacent lag lands ~152–157 (keeps ~146).
     */
    private fun resolveOneBinFasterDanceAlias(
        refinedLag: Double,
        corrByLag: DoubleArray,
        lagMin: Int,
        lagMax: Int,
        peakMedIv: Double?,
    ): Double {
        val slowLag = refinedLag.toInt().coerceIn(lagMin, lagMax)
        val fastLag = slowLag - 1
        if (fastLag < lagMin) return refinedLag
        val bpmSlow = 60_000.0 / (refinedLag * binMs)
        val fastRefined = parabolicRefinedLag(fastLag, corrByLag, lagMin, lagMax)
        val bpmFast = 60_000.0 / (fastRefined * binMs)
        if (bpmSlow !in 146.5..151.5 || bpmFast !in 150.5..158.5) return refinedLag
        val cSlow = corrByLag[slowLag]
        val cFast = corrByLag[fastLag]
        if (cFast < cSlow * 0.62) return refinedLag
        return fastRefined
    }

    private fun preferDanceBandLagIfPeaksSupport(
        refinedLag: Double,
        bpm: Double,
        bestCorr: Double,
        corrByLag: DoubleArray,
        lagMin: Int,
        lagMax: Int,
        peakMedIv: Double?,
    ): Double {
        if (bpm !in 146.5..151.5) return refinedLag
        val peaksSayFast =
            peakMedIv != null && peakMedIv < 400.0 && 60_000.0 / peakMedIv in 150.0..158.0
        if (peakMedIv != null && peakMedIv > 402.0 && !peaksSayFast) return refinedLag
        var pickLag = refinedLag
        var pickBpm = bpm
        var pickCorr = corrByLag[refinedLag.toInt().coerceIn(lagMin, lagMax)]
        for (lag in lagMin..lagMax) {
            val ref = parabolicRefinedLag(lag, corrByLag, lagMin, lagMax)
            val candBpm = 60_000.0 / (ref * binMs)
            if (candBpm !in 151.0..158.0) continue
            val c = corrByLag[lag]
            if (c < bestCorr * 0.55) continue
            if (c + 1e-9 < pickCorr && candBpm <= pickBpm + 0.5) continue
            if (candBpm >= pickBpm - 0.5 || c > pickCorr) {
                pickLag = ref
                pickBpm = candBpm
                pickCorr = c
            }
        }
        if (pickBpm > bpm + 3.0 && (peaksSayFast || cFastEnough(pickCorr, bestCorr))) {
            return pickLag
        }
        return refinedLag
    }

    private fun cFastEnough(pickCorr: Double, bestCorr: Double): Boolean = pickCorr >= bestCorr * 0.72

    private fun recentBeatMedianIntervalMs(): Double? {
        if (beatTimes.size < 4) return null
        val ivs = mutableListOf<Double>()
        val list = beatTimes.toList()
        for (i in 1 until list.size) {
            val iv = (list[i] - list[i - 1]).toDouble()
            if (iv in 280.0..520.0) ivs.add(iv)
        }
        if (ivs.size < 2) return null
        return median(ivs)
    }

    /** Recovers ~140–150 BPM when peak spacing looks like 1.5× or 2× the true beat period. */
    private fun fundamentalBpmFromPeakIntervals(): Double? {
        if (beatTimes.size < 5) return null
        val ivs = mutableListOf<Double>()
        val list = beatTimes.toList()
        for (i in 1 until list.size) {
            val iv = (list[i] - list[i - 1]).toDouble()
            if (iv in 180.0..1200.0) ivs.add(iv)
        }
        if (ivs.size < 3) return null
        val direct = ivs.filter { it in 330.0..480.0 }
        if (direct.size >= 2) return 60_000.0 / median(direct)
        val slow = ivs.filter { it in 580.0..780.0 }
        if (slow.size >= 2) {
            return 60_000.0 / (median(slow) * 2.0 / 3.0)
        }
        return null
    }
}