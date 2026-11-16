package com.Mareg74.bpmdetector.domain

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GenreProfileTest {
    @Test
    fun catalog_contains_auto_and_presets() {
        val ids = GenreCatalog.all().map { it.id }
        assertThat(ids).containsExactly(
            "auto",
            "house_techno",
            "hip_hop",
            "dnb",
            "rock_pop",
            "wide",
        ).inOrder()
    }

    @Test
    fun house_range() {
        val p = GenreCatalog.byId("house_techno")
        assertThat(p.minBpm).isEqualTo(120.0)
        assertThat(p.maxBpm).isEqualTo(135.0)
    }

    @Test
    fun auto_narrow_around_seed() {
        val narrowed = GenreCatalog.autoNarrow(128.0)
        assertThat(narrowed.minBpm).isAtMost(118.0)
        assertThat(narrowed.maxBpm).isAtLeast(138.0)
        assertThat(128.0).isIn(Range.closed(narrowed.minBpm, narrowed.maxBpm))
    }

    @Test
    fun clamp_bpm_to_profile() {
        val p = GenreCatalog.byId("hip_hop")
        assertThat(p.clamp(70.0)).isEqualTo(80.0)
        assertThat(p.clamp(95.0)).isEqualTo(95.0)
        assertThat(p.clamp(140.0)).isEqualTo(100.0)
    }
}