package com.Mareg74.bpmdetector.domain

import kotlin.math.abs
import kotlin.math.sqrt

class BpmEngine(private var profile: GenreProfile) {
    private fun onsetWindowForEstimate(onsets: List<Long>, minUnaligned: Int = 4): List<Long> {
        val unaligned = onsets.filter { it % 1000L != 0L }
        return if (unaligned.size >= minUnaligned) unaligned else onsets
    }
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
        if (p.id != "auto") autoNarrowDone = true
    }

    fun reset() {
        onsetDetector.reset()
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

        val estimate =
            AutocorrelationTempo.estimateBpm(
                onsetTimes.toList(),
                profile.minBpm,
                profile.maxBpm,
            )
        var beatPulse = false
        if (estimate == null) {
            return BpmReading(
                bpm = if (state == MeasureState.Resyncing) lockedBpm else null,
                confidence = 0.0,
                state = state,
                beatPulse = false,
                timestampMs = nowMs,
            )
        }
        var (bpm, conf) = estimate
        bpm = profile.clamp(bpm)
        val recentOnsets = onsetTimes.filter { it >= nowMs - profile.lockWindowMs }
        val recentMinOnsets = if (state == MeasureState.Resyncing) 3 else 4
        val recentWindow =
            onsetWindowForEstimate(
                recentOnsets,
                minUnaligned = if (state == MeasureState.Resyncing) 3 else 4,
            )
        val recentTempo =
            AutocorrelationTempo.medianIntervalBpm(recentOnsets, profile.minBpm, profile.maxBpm)
                ?: AutocorrelationTempo.estimateBpm(
                    recentWindow,
                    profile.minBpm,
                    profile.maxBpm,
                    minOnsets = recentMinOnsets,
                )
        val recentBpm = recentTempo?.first?.let { profile.clamp(it) } ?: bpm
        if (!autoNarrowDone && conf > 0.55) {
            profile = GenreCatalog.autoNarrow(bpm)
            onsetDetector.updateSensitivity(profile.onsetSensitivity)
            autoNarrowDone = true
        }
        when (state) {
            MeasureState.Starting, MeasureState.Resyncing -> {
                val lockConf = if (state == MeasureState.Resyncing) 0.45 else 0.35
                val canLock =
                    if (state == MeasureState.Resyncing) {
                        recentTempo != null && (recentTempo.second >= lockConf)
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
                val probeBpm = recentBpm
                val probeConf = recentTempo?.second ?: conf
                if (abs(probeBpm - locked) > profile.resyncDeltaBpm && probeConf >= 0.5) {
                    if (resyncSinceMs == null) resyncSinceMs = nowMs
                    if (nowMs - (resyncSinceMs ?: nowMs) >= profile.lockWindowMs / 2) {
                        state = MeasureState.Resyncing
                        candidateSinceMs = null
                        onsetTimes.clear()
                        onsetTimes.addAll(newOnsets)
                    }
                } else {
                    resyncSinceMs = null
                    if (abs(probeBpm - locked) <= profile.resyncDeltaBpm / 2) {
                        lockedBpm = locked * 0.8 + recentBpm * 0.2
                    }
                    beatPulse = newOnsets.isNotEmpty()
                }
            }
            MeasureState.Idle -> Unit
        }
        val displayBpm =
            when (state) {
                MeasureState.Starting ->
                    if (conf >= 0.3) profile.clamp(bpm) else null
                MeasureState.Tracking -> lockedBpm
                MeasureState.Resyncing -> lockedBpm
                MeasureState.Idle -> null
            }
        return BpmReading(displayBpm, conf, state, beatPulse, nowMs)
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
        if (t - lastOnset >= 140L) {
            onsetTimes.addLast(t)
        }
    }
}