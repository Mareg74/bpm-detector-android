package com.Mareg74.bpmdetector.domain

import kotlin.math.abs
import kotlin.math.sqrt

class BpmEngine(private var profile: GenreProfile) {
    private fun onsetWindowForEstimate(onsets: List<Long>, minUnaligned: Int = 4): List<Long> {
        val unaligned = onsets.filter { it % 1000L != 0L }
        return if (unaligned.size >= minUnaligned) unaligned else onsets
    }
    private val noveltyGenre = noveltyGenreFor(profile)
    private val noveltyAnalyzer = NoveltyAnalyzer(genre = noveltyGenre)
    private val noveltyEstimator =
        NoveltyBpmEstimator(
            minBpm = 60.0,
            maxBpm = 200.0,
            genre = noveltyGenre,
        )
    private var noveltyCandidateBpm: Double? = null
    private var noveltyCandidateSinceMs: Long? = null
    private var noveltyConfirmedBpm: Double? = null

    private val onsetDetector = OnsetDetector(profile.onsetSensitivity)
    private val onsetTimes = ArrayDeque<Long>()
    private val rmsEnvelope = ArrayDeque<Pair<Long, Float>>()
    private var state = MeasureState.Starting
    private var lockedBpm: Double? = null
    private var candidateSinceMs: Long? = null
    private var resyncSinceMs: Long? = null
    private var autoNarrowDone = profile.id != "auto"

    fun setProfile(p: GenreProfile) {
        profile = p
        onsetDetector.updateSensitivity(p.onsetSensitivity)
        val g = noveltyGenreFor(p)
        noveltyAnalyzer.setGenre(g)
        noveltyEstimator.reset()
        noveltyCandidateBpm = null
        noveltyCandidateSinceMs = null
        noveltyConfirmedBpm = null
        if (p.id != "auto") autoNarrowDone = true
    }

    fun reset() {
        onsetDetector.reset()
        noveltyAnalyzer.reset()
        noveltyEstimator.reset()
        noveltyCandidateBpm = null
        noveltyCandidateSinceMs = null
        noveltyConfirmedBpm = null
        onsetTimes.clear()
        rmsEnvelope.clear()
        state = MeasureState.Starting
        lockedBpm = null
        candidateSinceMs = null
        resyncSinceMs = null
        autoNarrowDone = profile.id != "auto"
    }

    fun push(samples: FloatArray, sampleRate: Int, nowMs: Long): BpmReading {
        val newOnsets = onsetDetector.push(samples, sampleRate, nowMs)
        for (o in newOnsets) {
            onsetTimes.addLast(o)
        }
        appendFrameRmsOnsets(samples, nowMs)
        val windowMs = (profile.lockWindowMs + 1500).coerceAtMost(5000)
        while (onsetTimes.isNotEmpty() && onsetTimes.first() < nowMs - windowMs) {
            onsetTimes.removeFirst()
        }
        while (onsetTimes.size > 64) onsetTimes.removeFirst()

        noveltyAnalyzer.push(samples, sampleRate, nowMs)?.let { novelty ->
            noveltyEstimator.push(nowMs, novelty)
        }
        val noveltyEstimate = noveltyEstimator.estimate()

        var beatPulse = newOnsets.isNotEmpty()
        if (noveltyEstimate == null) {
            if (noveltyEstimator.isWarmingUp()) {
                return BpmReading(
                    bpm =
                        when (state) {
                            MeasureState.Tracking -> lockedBpm
                            MeasureState.Resyncing -> lockedBpm
                            else -> null
                        },
                    confidence = 0.0,
                    state = state,
                    beatPulse = beatPulse,
                    timestampMs = nowMs,
                )
            }
            return pushOnsetFallback(newOnsets, nowMs, beatPulse)
        }
        gateNoveltyBpm(noveltyEstimate.first, nowMs)
        val displayNovelty =
            noveltyConfirmedBpm
                ?: noveltyCandidateBpm?.takeIf {
                    nowMs - (noveltyCandidateSinceMs ?: 0L) >= 250L
                }
                ?: noveltyEstimate.first
        var bpm = profile.clamp(displayNovelty)
        var conf = noveltyEstimate.second.coerceIn(0.0, 1.0)
        if (!autoNarrowDone && noveltyConfirmedBpm != null && conf > 0.55) {
            profile = GenreCatalog.autoNarrow(bpm)
            onsetDetector.updateSensitivity(profile.onsetSensitivity)
            autoNarrowDone = true
        }
        when (state) {
            MeasureState.Starting, MeasureState.Resyncing -> {
                val lockConf = if (state == MeasureState.Resyncing) 0.38 else 0.20
                val canLock = conf >= lockConf && noveltyConfirmedBpm != null
                if (canLock) {
                    if (candidateSinceMs == null) candidateSinceMs = nowMs
                    val activeLockWindow =
                        if (state == MeasureState.Resyncing) {
                            profile.lockWindowMs / 2
                        } else {
                            650L
                        }
                    if (nowMs - (candidateSinceMs ?: nowMs) >= activeLockWindow) {
                        lockedBpm = bpm
                        state = MeasureState.Tracking
                        candidateSinceMs = null
                        resyncSinceMs = null
                        beatPulse = true
                    }
                } else {
                    candidateSinceMs = null
                }
            }
            MeasureState.Tracking -> {
                val locked = lockedBpm!!
                val probeBpm = bpm
                val probeConf = conf
                if (abs(probeBpm - locked) > profile.resyncDeltaBpm && probeConf >= 0.5) {
                    if (resyncSinceMs == null) resyncSinceMs = nowMs
                    if (nowMs - (resyncSinceMs ?: nowMs) >= profile.lockWindowMs * 2 / 5) {
                        state = MeasureState.Resyncing
                        candidateSinceMs = null
                        onsetTimes.clear()
                        onsetTimes.addAll(newOnsets)
                    }
                } else {
                    resyncSinceMs = null
                    val followAlpha =
                        when {
                            probeBpm > locked && probeBpm - locked in 4.0..12.0 -> 0.34
                            else -> 0.22
                        }
                    if (abs(probeBpm - locked) <= profile.resyncDeltaBpm / 2) {
                        lockedBpm = locked * (1.0 - followAlpha) + probeBpm * followAlpha
                    } else if (
                        probeBpm > locked &&
                        probeBpm - locked in 4.0..12.0 &&
                        probeConf >= 0.28
                    ) {
                        lockedBpm = locked * 0.62 + probeBpm * 0.38
                    }
                    beatPulse = newOnsets.isNotEmpty()
                }
            }
            MeasureState.Idle -> Unit
        }
        val displayBpm =
            when (state) {
                MeasureState.Starting ->
                    if (conf >= 0.18) profile.clamp(bpm) else null
                MeasureState.Tracking -> lockedBpm
                MeasureState.Resyncing -> lockedBpm
                MeasureState.Idle -> null
            }
        return BpmReading(displayBpm, conf, state, beatPulse, nowMs)
    }

    private fun pushOnsetFallback(
        newOnsets: List<Long>,
        nowMs: Long,
        beatPulseIn: Boolean,
    ): BpmReading {
        var beatPulse = beatPulseIn
        val estimate =
            AutocorrelationTempo.estimateBpm(
                onsetTimes.toList(),
                profile.minBpm,
                profile.maxBpm,
            )
            ?: return BpmReading(
                bpm =
                    when (state) {
                        MeasureState.Tracking -> lockedBpm
                        MeasureState.Resyncing -> lockedBpm
                        else -> null
                    },
                confidence = 0.0,
                state = state,
                beatPulse = beatPulse,
                timestampMs = nowMs,
            )
        var (bpm, conf) = estimate
        val refined = refineIfFastGrid(onsetTimes.toList(), bpm, conf)
        bpm = profile.clamp(refined.first)
        conf = refined.second
        val recentOnsets = onsetTimes.filter { it >= nowMs - profile.lockWindowMs }
        val recentMinOnsets = if (state == MeasureState.Resyncing) 3 else 4
        val recentTempo =
            AutocorrelationTempo.medianIntervalBpm(recentOnsets, profile.minBpm, profile.maxBpm)
                ?: AutocorrelationTempo.estimateBpm(
                    onsetWindowForEstimate(
                        recentOnsets,
                        minUnaligned = if (state == MeasureState.Resyncing) 3 else 4,
                    ),
                    profile.minBpm,
                    profile.maxBpm,
                    minOnsets = recentMinOnsets,
                )
        var recentBpm = recentTempo?.first?.let { profile.clamp(it) } ?: bpm
        if (recentTempo != null) {
            recentBpm = profile.clamp(refineIfFastGrid(recentOnsets, recentBpm, recentTempo.second).first)
        }
        if (!autoNarrowDone && conf > 0.55) {
            profile = GenreCatalog.autoNarrow(bpm)
            onsetDetector.updateSensitivity(profile.onsetSensitivity)
            autoNarrowDone = true
        }
        when (state) {
            MeasureState.Starting, MeasureState.Resyncing -> {
                val lockConf = if (state == MeasureState.Resyncing) 0.42 else 0.30
                val canLock =
                    if (state == MeasureState.Resyncing) {
                        recentTempo != null && recentTempo.second >= lockConf
                    } else {
                        conf >= lockConf
                    }
                if (canLock) {
                    if (candidateSinceMs == null) candidateSinceMs = nowMs
                    val activeLockWindow =
                        if (state == MeasureState.Resyncing) {
                            profile.lockWindowMs / 2
                        } else {
                            (profile.lockWindowMs * 0.55).toLong().coerceAtLeast(1200L)
                        }
                    if (nowMs - (candidateSinceMs ?: nowMs) >= activeLockWindow) {
                        lockedBpm = if (state == MeasureState.Resyncing) recentBpm else bpm
                        state = MeasureState.Tracking
                        candidateSinceMs = null
                        resyncSinceMs = null
                        beatPulse = true
                    }
                } else {
                    candidateSinceMs = null
                }
            }
            MeasureState.Tracking -> {
                val locked = lockedBpm!!
                if (abs(recentBpm - locked) > profile.resyncDeltaBpm && (recentTempo?.second ?: conf) >= 0.5) {
                    if (resyncSinceMs == null) resyncSinceMs = nowMs
                    if (nowMs - (resyncSinceMs ?: nowMs) >= profile.lockWindowMs / 2) {
                        state = MeasureState.Resyncing
                        candidateSinceMs = null
                        onsetTimes.clear()
                        onsetTimes.addAll(newOnsets)
                    }
                } else {
                    resyncSinceMs = null
                    if (abs(recentBpm - locked) <= profile.resyncDeltaBpm / 2) {
                        lockedBpm = locked * 0.8 + recentBpm * 0.2
                    }
                    beatPulse = newOnsets.isNotEmpty()
                }
            }
            MeasureState.Idle -> Unit
        }
        val displayBpm =
            when (state) {
                MeasureState.Starting -> if (conf >= 0.22) profile.clamp(bpm) else null
                MeasureState.Tracking -> lockedBpm
                MeasureState.Resyncing -> lockedBpm
                MeasureState.Idle -> null
            }
        return BpmReading(displayBpm, conf, state, beatPulse, nowMs)
    }

    private fun refineIfFastGrid(
        onsets: List<Long>,
        bpm: Double,
        conf: Double,
    ): Pair<Double, Double> {
        val median =
            AutocorrelationTempo.medianIntervalBpm(
                onsets,
                profile.minBpm,
                profile.maxBpm,
            )
        if (median != null && median.second >= 0.32 && bpm >= 168.0) {
            val merged = (conf * 0.4 + median.second * 0.6).coerceIn(0.0, 1.0)
            if (median.first < bpm * 0.88) {
                return profile.clamp(median.first) to merged
            }
        }
        if (bpm < 168.0) {
            if (bpm in 76.0..88.0) {
                val doubled = bpm * 2.0
                if (doubled in 130.0..175.0 && conf < 0.62) {
                    val harmonic =
                        FundamentalTempo.refine(
                            onsets,
                            doubled,
                            conf,
                            profile.minBpm,
                            profile.maxBpm,
                        )
                    if (harmonic.first >= doubled * 0.88 && harmonic.second >= conf * 0.45) {
                        return profile.clamp(harmonic.first) to harmonic.second
                    }
                }
            }
            return bpm to conf
        }
        val refined =
            FundamentalTempo.refine(
                onsets,
                bpm,
                conf,
                profile.minBpm,
                profile.maxBpm,
            )
        if (refined.first > bpm * 0.9) return bpm to conf
        return refined
    }

    private fun gateNoveltyBpm(
        raw: Double,
        nowMs: Long,
    ): Double? {
        val tol = 0.5
        val sameCandidate =
            noveltyCandidateBpm != null && abs(raw - noveltyCandidateBpm!!) <= tol
        if (!sameCandidate) {
            val sameConfirmed =
                noveltyConfirmedBpm != null && abs(raw - noveltyConfirmedBpm!!) <= tol
            if (!sameConfirmed) {
                noveltyCandidateBpm = raw
                noveltyCandidateSinceMs = nowMs
            }
        }
        val candidateAge = nowMs - (noveltyCandidateSinceMs ?: nowMs)
        val requiredMs = if (noveltyConfirmedBpm == null) 700L else 1_000L
        if (noveltyCandidateBpm != null && abs(raw - noveltyCandidateBpm!!) <= tol &&
            candidateAge >= requiredMs
        ) {
            noveltyConfirmedBpm = noveltyCandidateBpm
        }
        if (
            noveltyConfirmedBpm != null &&
            noveltyCandidateBpm != null &&
            noveltyCandidateBpm!! > noveltyConfirmedBpm!! + 5.0 &&
            candidateAge >= 500L
        ) {
            noveltyConfirmedBpm = noveltyCandidateBpm
        }
        noveltyConfirmedBpm?.let { return it }
        if (noveltyCandidateBpm != null && candidateAge >= 250) {
            return noveltyCandidateBpm
        }
        return null
    }

    private fun noveltyGenreFor(p: GenreProfile): NoveltyAnalyzer.NoveltyGenre =
        when (p.id) {
            "rock_pop" -> NoveltyAnalyzer.NoveltyGenre.Rock
            else -> NoveltyAnalyzer.NoveltyGenre.Electro
        }

    private fun appendFrameRmsOnsets(samples: FloatArray, nowMs: Long) {
        if (samples.isEmpty()) return
        var sumSq = 0.0
        for (s in samples) {
            sumSq += s * s
        }
        val rms = sqrt(sumSq / samples.size).toFloat()
        rmsEnvelope.addLast(nowMs to rms)
        while (rmsEnvelope.size > 256) {
            rmsEnvelope.removeFirst()
        }
        if (rmsEnvelope.size < 3) return
        val idx = rmsEnvelope.size - 2
        val (t, energy) = rmsEnvelope.elementAt(idx)
        val prev = rmsEnvelope.elementAt(idx - 1).second
        val next = rmsEnvelope.last().second
        val sorted = rmsEnvelope.map { it.second }.sorted()
        val median = sorted[sorted.size / 2]
        val floor = (median * 1.04f).coerceAtLeast(0.0015f)
        if (energy < prev || energy <= next || energy < floor) return
        val lastOnset = onsetTimes.lastOrNull() ?: 0L
        if (t - lastOnset >= 280L) {
            onsetTimes.addLast(t)
        }
    }
}