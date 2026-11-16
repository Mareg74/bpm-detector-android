package com.Mareg74.bpmdetector.domain

enum class MeasureState {
    Idle,
    Starting,
    Tracking,
    Resyncing,
    ;

    fun uiLabel(): String? = when (this) {
        Idle -> null
        Starting -> "Starting…"
        Tracking -> "Tracking beat…"
        Resyncing -> "Resyncing…"
    }
}