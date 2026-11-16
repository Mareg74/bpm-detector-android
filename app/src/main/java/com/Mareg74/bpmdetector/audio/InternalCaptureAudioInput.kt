package com.Mareg74.bpmdetector.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
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
import kotlin.math.sqrt

/**
 * Captures device playback via [AudioPlaybackCaptureConfiguration] (Android 10 / API 29+).
 *
 * [MediaProjection] must be granted from an Activity ([android.media.projection.MediaProjectionManager]
 * consent dialog). Task 13 will wire that UX; until then call [attachMediaProjection] after the
 * user approves capture, or [start] will emit [AudioError.SourceUnavailable].
 */
class InternalCaptureAudioInput(
    private val context: Context,
) : AudioInput {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _frames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = FRAME_BUFFER_CAPACITY)
    private val _errors = MutableSharedFlow<AudioError>(extraBufferCapacity = ERROR_BUFFER_CAPACITY)
    private var record: AudioRecord? = null
    private var captureJob: Job? = null
    private var mediaProjection: MediaProjection? = null

    override val frames: Flow<AudioFrame> = _frames.asSharedFlow()
    override val errors: SharedFlow<AudioError> = _errors.asSharedFlow()

    override fun isAvailable(): Boolean = isSupported()

    fun attachMediaProjection(projection: MediaProjection) {
        mediaProjection = projection
    }

    override suspend fun start() {
        mutex.withLock {
            if (captureJob?.isActive == true) return
            if (!isSupported()) {
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Internal,
                        "Playback capture requires Android 10 or newer",
                    ),
                )
                return
            }
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                _errors.emit(AudioError.PermissionDenied)
                return
            }
            val projection = mediaProjection
            if (projection == null) {
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Internal,
                        "Playback capture requires MediaProjection consent (wired in Task 13)",
                    ),
                )
                return
            }
            val bufferSize = bufferSizeBytes()
            if (bufferSize <= 0) {
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Internal,
                        "Playback capture is not available on this device",
                    ),
                )
                return
            }
            val captureConfig =
                AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
            val audioRecord =
                try {
                    AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(captureConfig)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE_HZ)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build(),
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .build()
                } catch (e: SecurityException) {
                    _errors.emit(AudioError.PermissionDenied)
                    return
                } catch (e: UnsupportedOperationException) {
                    _errors.emit(
                        AudioError.SourceUnavailable(
                            AudioSourceKind.Internal,
                            e.message ?: "OEM blocked playback capture",
                        ),
                    )
                    return
                }
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Internal,
                        "Failed to initialize playback capture",
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
                        AudioSourceKind.Internal,
                        e.message ?: "Failed to start playback capture",
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
            var sumSq = 0.0
            for (i in 0 until read) {
                val v = shorts[i] / PCM_16_SCALE
                samples[i] = v
                sumSq += v * v
            }
            val rms = sqrt(sumSq / read).toFloat()
            _frames.emit(
                AudioFrame(
                    samples = samples,
                    sampleRate = SAMPLE_RATE_HZ,
                    rms = rms,
                ),
            )
        }
    }

    companion object {
        private const val SAMPLE_RATE_HZ = MicAudioInput.SAMPLE_RATE_HZ
        private const val BYTES_PER_SAMPLE = 2
        private const val PCM_16_SCALE = 32768f
        private const val FRAME_BUFFER_CAPACITY = 16
        private const val ERROR_BUFFER_CAPACITY = 4

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

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