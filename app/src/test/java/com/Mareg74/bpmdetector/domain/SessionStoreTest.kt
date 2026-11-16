package com.Mareg74.bpmdetector.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionStoreTest {
    @Test
    fun labels() {
        assertThat(MeasureState.Starting.uiLabel()).isEqualTo("Starting…")
        assertThat(MeasureState.Tracking.uiLabel()).isEqualTo("Tracking beat…")
        assertThat(MeasureState.Resyncing.uiLabel()).isEqualTo("Resyncing…")
        assertThat(MeasureState.Idle.uiLabel()).isNull()
    }

    @Test
    fun session_appends_only_numeric_bpm_until_freeze() {
        val s = SessionStore()
        s.append(BpmReading(120.0, 0.9, MeasureState.Tracking, false, 1))
        s.freeze()
        s.append(BpmReading(121.0, 0.9, MeasureState.Tracking, false, 2))
        assertThat(s.snapshot()).hasSize(1)
        s.clear()
        assertThat(s.snapshot()).isEmpty()
    }
}