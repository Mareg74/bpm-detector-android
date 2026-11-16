package com.Mareg74.bpmdetector.domain

data class GenreProfile(
    val id: String,
    val label: String,
    val minBpm: Double,
    val maxBpm: Double,
    val onsetSensitivity: Double,
    val lockWindowMs: Long,
    val resyncDeltaBpm: Double,
) {
    fun clamp(bpm: Double): Double = bpm.coerceIn(minBpm, maxBpm)

    fun contains(bpm: Double): Boolean = bpm in minBpm..maxBpm
}