package com.Mareg74.bpmdetector.domain

data class BpmReading(
    val bpm: Double?,
    val confidence: Double,
    val state: MeasureState,
    val beatPulse: Boolean,
    val timestampMs: Long,
)