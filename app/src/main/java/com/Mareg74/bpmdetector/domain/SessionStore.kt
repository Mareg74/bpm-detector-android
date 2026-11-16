package com.Mareg74.bpmdetector.domain

class SessionStore {
    private val points = mutableListOf<Pair<Long, Double>>()
    private var frozen = false

    fun clear() {
        points.clear()
        frozen = false
    }

    fun freeze() {
        frozen = true
    }

    fun append(reading: BpmReading) {
        if (frozen) return
        val bpm = reading.bpm ?: return
        points += reading.timestampMs to bpm
    }

    fun snapshot(): List<Pair<Long, Double>> = points.toList()
}