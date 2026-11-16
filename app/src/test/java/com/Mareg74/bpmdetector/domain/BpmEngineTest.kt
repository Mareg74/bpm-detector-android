package com.Mareg74.bpmdetector.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BpmEngineTest {
    private fun impulseTrain(sr: Int, bpm: Double, seconds: Int): FloatArray {
        val period = (60.0 / bpm * sr).toInt()
        val samples = FloatArray(sr * seconds)
        var i = 0
        while (i < samples.size) {
            for (j in 0 until 24) if (i + j < samples.size) samples[i + j] = 1f
            i += period
        }
        return samples
    }

    @Test
    fun locks_then_tracks() {
        val engine = BpmEngine(GenreCatalog.byId("house_techno"))
        var reading = BpmReading(null, 0.0, MeasureState.Starting, false, 0)
        val sr = 44100
        val chunk = impulseTrain(sr, 128.0, 1)
        var t = 0L
        repeat(6) {
            reading = engine.push(chunk, sr, t)
            t += 1000
        }
        assertThat(reading.state).isEqualTo(MeasureState.Tracking)
        assertThat(reading.bpm!!).isWithin(3.0).of(128.0)
    }

    @Test
    fun major_change_triggers_resync_then_retrack() {
        val engine = BpmEngine(GenreCatalog.byId("wide"))
        val sr = 44100
        var t = 0L
        var reading = BpmReading(null, 0.0, MeasureState.Starting, false, 0)
        repeat(6) {
            reading = engine.push(impulseTrain(sr, 128.0, 1), sr, t)
            t += 1000
        }
        assertThat(reading.state).isEqualTo(MeasureState.Tracking)
        var sawResync = false
        repeat(8) {
            reading = engine.push(impulseTrain(sr, 90.0, 1), sr, t)
            t += 1000
            if (reading.state == MeasureState.Resyncing) sawResync = true
        }
        assertThat(sawResync).isTrue()
        assertThat(reading.state).isEqualTo(MeasureState.Tracking)
        assertThat(reading.bpm!!).isWithin(4.0).of(90.0)
    }

    @Test
    fun chunked_buffers_lock_like_audio_record() {
        val engine = BpmEngine(GenreCatalog.byId("auto"))
        val sr = 44100
        val bpm = 120.0
        val period = (60.0 / bpm * sr).toInt()
        val total = sr * 25
        val all = FloatArray(total)
        for (i in all.indices step period) {
            for (j in 0 until 32) {
                if (i + j < total) all[i + j] = 0.9f
            }
        }
        val readSize = 7168
        var offset = 0
        var t = 0L
        var reading = BpmReading(null, 0.0, MeasureState.Starting, false, 0)
        while (offset < all.size) {
            val end = minOf(offset + readSize, all.size)
            val slice = all.copyOfRange(offset, end)
            reading = engine.push(slice, sr, t)
            t += slice.size * 1000L / sr
            offset = end
        }
        assertThat(reading.state).isEqualTo(MeasureState.Tracking)
        assertThat(reading.bpm!!).isWithin(6.0).of(120.0)
    }

    @Test
    fun weak_signal_stays_starting_without_bpm() {
        val engine = BpmEngine(GenreCatalog.byId("wide"))
        val quiet = FloatArray(44100) { 0.0001f }
        val reading = engine.push(quiet, 44100, 0)
        assertThat(reading.state).isEqualTo(MeasureState.Starting)
        assertThat(reading.bpm).isNull()
    }
}