package com.Mareg74.bpmdetector.audio

data class AudioFrame(
    val samples: FloatArray,
    val sampleRate: Int,
    val rms: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioFrame
        if (!samples.contentEquals(other.samples)) return false
        if (sampleRate != other.sampleRate) return false
        if (rms != other.rms) return false
        return true
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + rms.hashCode()
        return result
    }
}