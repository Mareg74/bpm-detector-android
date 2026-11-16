package com.Mareg74.bpmdetector.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.sqrt

class MicAudioInput(
    private val context: Context,
) : AudioInput {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _frames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = FRAME_BUFFER_CAPACITY)
    private val _errors = MutableSharedFlow<AudioError>(extraBufferCapacity = ERROR_BUFFER_CAPACITY)
    private var record: AudioRecord? = null
    private var captureJob: Job? = null

    override val frames: Flow<AudioFrame> = _frames.asSharedFlow()
    override val errors: SharedFlow<AudioError> = _errors.asSharedFlow()

    override fun isAvailable(): Boolean = isHardwareAvailable()

    override suspend fun start() {
        mutex.withLock {
            if (captureJob?.isActive == true) return
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                _errors.emit(AudioError.PermissionDenied)
                return
            }
            val bufferSize = bufferSizeBytes()
            if (bufferSize <= 0) {
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Mic,
                        "Microphone capture is not available on this device",
                    ),
                )
                return
            }
            val audioRecord =
                try {
                    openMicRecord(bufferSize)
                } catch (e: SecurityException) {
                    _errors.emit(AudioError.PermissionDenied)
                    return
                }
            if (audioRecord == null) {
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Mic,
                        "Failed to open microphone",
                    ),
                )
                return
            }
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Mic,
                        "Failed to initialize AudioRecord",
                    ),
                )
                return
            }
            try {
                audioRecord.startRecording()
            } catch (e: IllegalStateException) {
                audioRecord.release()
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Mic,
                        e.message ?: "Failed to start recording",
                    ),
                )
                return
            }
            record = audioRecord
            captureJob =
                scope.launch(Dispatchers.IO) {
                    captureLoop(audioRecord, bufferSize)
                }
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            captureJob?.cancel()
            captureJob = null
            record?.let { r ->
                try {
                    if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        r.stop()
                    }
                } catch (_: IllegalStateException) {
                }
                r.release()
            }
            record = null
        }
    }

    private suspend fun captureLoop(
        audioRecord: AudioRecord,
        bufferSize: Int,
    ) {
        val shorts = ShortArray(bufferSize / BYTES_PER_SAMPLE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = audioRecord.read(shorts, 0, shorts.size)
            if (read <= 0) continue
            val samples = FloatArray(read)
            for (i in 0 until read) {
                samples[i] = shorts[i] / PCM_16_SCALE
            }
            val analysis = amplifyQuietMic(samples)
            var sumSq = 0.0
            for (v in analysis) {
                sumSq += v * v
            }
            val rms = sqrt(sumSq / analysis.size).toFloat()
            _frames.emit(
                AudioFrame(
                    samples = analysis,
                    sampleRate = SAMPLE_RATE_HZ,
                    rms = rms,
                ),
            )
        }
    }

    private fun amplifyQuietMic(samples: FloatArray): FloatArray {
        var peak = 0f
        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        if (peak < 0.08f) {
            val target = 0.28f
            val gain = (target / peak.coerceAtLeast(1e-6f)).coerceIn(1f, 40f)
            return FloatArray(samples.size) { i ->
                (samples[i] * gain).coerceIn(-1f, 1f)
            }
        }
        return samples
    }

    private fun openMicRecord(bufferSize: Int): AudioRecord? {
        val sources =
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    add(MediaRecorder.AudioSource.UNPROCESSED)
                }
                add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                add(MediaRecorder.AudioSource.MIC)
            }
        for (source in sources) {
            val record =
                try {
                    AudioRecord(
                        source,
                        SAMPLE_RATE_HZ,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                    )
                } catch (_: IllegalArgumentException) {
                    null
                } ?: continue
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        return null
    }

    companion object {
        const val SAMPLE_RATE_HZ = 44_100
        private const val BYTES_PER_SAMPLE = 2
        private const val PCM_16_SCALE = 32768f
        private const val FRAME_BUFFER_CAPACITY = 16
        private const val ERROR_BUFFER_CAPACITY = 4

        fun isHardwareAvailable(): Boolean {
            val min =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            return min > 0
        }

        private fun bufferSizeBytes(): Int {
            val min =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            return if (min > 0) min * 2 else min
        }
    }
}