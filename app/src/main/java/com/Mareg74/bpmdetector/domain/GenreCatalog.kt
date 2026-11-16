package com.Mareg74.bpmdetector.domain

object GenreCatalog {
    private val wide =
        GenreProfile("wide", "Wide", 60.0, 200.0, 0.5, 2500, 6.0)

    private val presets =
        listOf(
            GenreProfile("auto", "Auto", 60.0, 200.0, 0.5, 2500, 6.0),
            GenreProfile("house_techno", "House / Techno", 120.0, 135.0, 0.55, 2000, 4.0),
            GenreProfile("hip_hop", "Hip-Hop", 80.0, 100.0, 0.45, 2500, 4.0),
            GenreProfile("dnb", "DnB", 160.0, 180.0, 0.6, 1800, 5.0),
            GenreProfile("rock_pop", "Rock / Pop", 100.0, 130.0, 0.5, 2200, 5.0),
            wide,
        )

    fun all(): List<GenreProfile> = presets

    fun byId(id: String): GenreProfile = presets.first { it.id == id }

    fun autoNarrow(seedBpm: Double): GenreProfile {
        val pad = 10.0
        return GenreProfile(
            id = "auto",
            label = "Auto",
            minBpm = (seedBpm - pad).coerceAtLeast(60.0),
            maxBpm = (seedBpm + pad).coerceAtMost(200.0),
            onsetSensitivity = 0.5,
            lockWindowMs = 2200,
            resyncDeltaBpm = 5.0,
        )
    }
}