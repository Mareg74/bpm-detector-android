package com.Mareg74.bpmdetector.domain

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

    private val noveltyWinMs = 8_000L
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
            val weight = exp(-age / 4000.0)
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
        if (slowBpm < 112.0) {
            val danceLagMin = (60_000.0 / 178.0 / binMs).toInt()
            val danceLagMax = (60_000.0 / 128.0 / binMs).toInt()
            var danceLag = bestLag
            var danceCorr = bestCorr
            for (lag in danceLagMin..danceLagMax) {
                val c = corrByLag.getOrElse(lag) { 0.0 }
                if (c >= bestCorr * 0.58 && c >= danceCorr) {
                    danceCorr = c
                    danceLag = lag
                }
            }
            if (danceLag != bestLag && danceCorr >= bestCorr * 0.58) {
                bestLag = danceLag
                bestCorr = danceCorr
            }
        }

        var refinedLag = bestLag.toDouble()
        if (bestLag - 1 >= lagMin && bestLag + 1 <= lagMax) {
            val cL = corrByLag[bestLag - 1]
            val cC = corrByLag[bestLag]
            val cR = corrByLag[bestLag + 1]
            val denom = cL - 2 * cC + cR
            if (abs(denom) > 1e-9) {
                val delta = 0.5 * (cL - cR) / denom
                refinedLag = bestLag + max(-1.0, min(1.0, delta))
            }
        }

        var bpm = 60_000.0 / (refinedLag * binMs)
        if (bpm < 105.0) {
            val halfLag = (refinedLag / 2.0).toInt()
            if (halfLag >= lagMin && halfLag <= lagMax) {
                val corrHalf = corrByLag[halfLag]
                if (corrHalf >= bestCorr * 0.52) {
                    refinedLag = halfLag.toDouble()
                    if (halfLag - 1 >= lagMin && halfLag + 1 <= lagMax) {
                        val cL = corrByLag[halfLag - 1]
                        val cC = corrByLag[halfLag]
                        val cR = corrByLag[halfLag + 1]
                        val denom = cL - 2 * cC + cR
                        if (abs(denom) > 1e-9) {
                            val delta = 0.5 * (cL - cR) / denom
                            refinedLag = halfLag + max(-1.0, min(1.0, delta))
                        }
                    }
                    bpm = 60_000.0 / (refinedLag * binMs)
                }
            }
        }
        while (bpm > 180) bpm /= 2
        while (bpm < 60) bpm *= 2
        if (bpm < 60 || bpm > 180) return null

        if (beatTimes.size >= 4) {
            val intervals = mutableListOf<Double>()
            val list = beatTimes.toList()
            for (i in 1 until list.size) {
                val iv = (list[i] - list[i - 1]).toDouble()
                if (iv in 200.0..1500.0) intervals.add(iv)
            }
            if (intervals.size >= 3) {
                val medIv = median(intervals)
                val meanIv = intervals.sum() / intervals.size
                val std =
                    kotlin.math.sqrt(
                        intervals.sumOf { (it - meanIv) * (it - meanIv) } / intervals.size,
                    )
                val relStd = std / meanIv
                val bpmPeak = 60_000.0 / medIv
                val peaksStable = relStd < 0.14
                if (peaksStable && bpmPeak in 125.0..178.0 && bpm < bpmPeak * 0.72) {
                    bpm = bpmPeak
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

        val alpha = if (genre == NoveltyAnalyzer.NoveltyGenre.Acoustic) 0.35 else 0.20
        smoothedBpm =
            smoothedBpm?.let { prev ->
                if (abs(bpm - prev) > 28) bpm else (1 - alpha) * prev + alpha * bpm
            } ?: bpm
        val confidence =
            ((bestCorr * 80).coerceIn(0.0, 1.0) + if (beatTimes.size >= 6) 0.25 else 0.0)
                .coerceIn(0.0, 1.0)
        return smoothedBpm!! to confidence
    }

    fun recentBeats(): List<Long> = beatTimes.toList()

    private fun detectPeaks(now: Long) {
        if (history.size < 15) return
        val minInterval =
            when (genre) {
                NoveltyAnalyzer.NoveltyGenre.Rock -> 240L
                NoveltyAnalyzer.NoveltyGenre.Acoustic -> 280L
                NoveltyAnalyzer.NoveltyGenre.Electro -> 200L
            }
        val values = history.map { it.second }
        val mean = values.sum() / values.size
        val std =
            sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        val threshold =
            when (genre) {
                NoveltyAnalyzer.NoveltyGenre.Rock -> mean + 1.35 * std
                NoveltyAnalyzer.NoveltyGenre.Acoustic -> mean + 0.9 * std
                NoveltyAnalyzer.NoveltyGenre.Electro -> mean + 1.1 * std
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
}