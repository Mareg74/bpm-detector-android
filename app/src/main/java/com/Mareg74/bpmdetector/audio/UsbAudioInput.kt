package com.Mareg74.bpmdetector.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
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
import kotlin.math.sqrt

class UsbAudioInput(
    private val context: Context,
) : AudioInput {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _frames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = FRAME_BUFFER_CAPACITY)
    private val _errors = MutableSharedFlow<AudioError>(extraBufferCapacity = ERROR_BUFFER_CAPACITY)
    private var record: AudioRecord? = null
    private var captureJob: Job? = null
    private var trackedDeviceId: Int? = null
    private var audioManager: AudioManager? = null

    private val deviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                val id = trackedDeviceId ?: return
                if (removedDevices.any { it.id == id }) {
                    scope.launch { onUsbDisconnected() }
                }
            }
        }

    override val frames: Flow<AudioFrame> = _frames.asSharedFlow()
    override val errors: SharedFlow<AudioError> = _errors.asSharedFlow()

    override fun isAvailable(): Boolean = hasDevice(context)

    override suspend fun start() {
        mutex.withLock {
            if (captureJob?.isActive == true) return
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                _errors.emit(AudioError.PermissionDenied)
                return
            }
            val device = findUsbInputDevice(context)
            if (device == null) {
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Usb,
                        "No USB audio input device connected",
                    ),
                )
                return
            }
            val bufferSize = bufferSizeBytes()
            if (bufferSize <= 0) {
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Usb,
                        "USB capture is not available on this device",
                    ),
                )
                return
            }
            val audioRecord =
                try {
                    AudioRecord(
                        MediaRecorder.AudioSource.UNPROCESSED,
                        SAMPLE_RATE_HZ,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                    )
                } catch (e: SecurityException) {
                    _errors.emit(AudioError.PermissionDenied)
                    return
                }
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Usb,
                        "Failed to initialize USB AudioRecord",
                    ),
                )
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (!audioRecord.setPreferredDevice(device)) {
                    audioRecord.release()
                    _errors.emit(
                        AudioError.SourceUnavailable(
                            AudioSourceKind.Usb,
                            "Failed to route capture to USB device",
                        ),
                    )
                    return
                }
            }
            try {
                audioRecord.startRecording()
            } catch (e: IllegalStateException) {
                audioRecord.release()
                _errors.emit(
                    AudioError.SourceUnavailable(
                        AudioSourceKind.Usb,
                        e.message ?: "Failed to start USB recording",
                    ),
                )
                return
            }
            record = audioRecord
            trackedDeviceId = device.id
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager = am
            am.registerAudioDeviceCallback(deviceCallback, null)
            captureJob =
                scope.launch(Dispatchers.IO) {
                    captureLoop(audioRecord, bufferSize)
                }
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            unregisterDeviceCallback()
            captureJob?.cancel()
            captureJob = null
            releaseRecord()
        }
    }

    private suspend fun onUsbDisconnected() {
        mutex.withLock {
            if (captureJob == null) return
            _errors.emit(AudioError.Disconnected(AudioSourceKind.Usb))
            captureJob?.cancel()
            captureJob = null
            unregisterDeviceCallback()
            releaseRecord()
        }
    }

    private fun unregisterDeviceCallback() {
        audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        audioManager = null
        trackedDeviceId = null
    }

    private fun releaseRecord() {
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

    private suspend fun captureLoop(
        audioRecord: AudioRecord,
        bufferSize: Int,
    ) {
        val shorts = ShortArray(bufferSize / BYTES_PER_SAMPLE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = audioRecord.read(shorts, 0, shorts.size)
            if (read < 0) {
                onUsbDisconnected()
                return
            }
            if (read == 0) continue
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

        fun hasDevice(context: Context): Boolean = findUsbInputDevice(context) != null

        private fun findUsbInputDevice(context: Context): AudioDeviceInfo? {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
            return am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { info ->
                info.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    info.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
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