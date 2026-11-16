package com.Mareg74.bpmdetector.ui.measure

import android.media.projection.MediaProjection
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Mareg74.bpmdetector.audio.AudioError
import com.Mareg74.bpmdetector.audio.AudioInput
import com.Mareg74.bpmdetector.audio.AudioInputFactory
import com.Mareg74.bpmdetector.audio.AudioSourceKind
import com.Mareg74.bpmdetector.audio.InternalCaptureAudioInput
import com.Mareg74.bpmdetector.data.SettingsStore
import com.Mareg74.bpmdetector.data.ThemeMode
import com.Mareg74.bpmdetector.data.UserSettings
import com.Mareg74.bpmdetector.domain.BpmEngine
import com.Mareg74.bpmdetector.domain.GenreCatalog
import com.Mareg74.bpmdetector.domain.MeasureState
import com.Mareg74.bpmdetector.domain.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val WAVEFORM_POINTS = 128

data class MeasureUiState(
    val state: MeasureState,
    val bpm: Double?,
    val bpmDimmed: Boolean,
    val statusLabel: String?,
    val showDecimal: Boolean,
    val beatPulse: Boolean,
    val waveform: FloatArray,
    val history: List<Pair<Long, Double>>,
    val settings: UserSettings,
    val sourceAvailability: Map<AudioSourceKind, Boolean>,
    val errorBanner: String?,
    val secondaryVisible: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeasureUiState) return false
        return state == other.state &&
            bpm == other.bpm &&
            bpmDimmed == other.bpmDimmed &&
            statusLabel == other.statusLabel &&
            showDecimal == other.showDecimal &&
            beatPulse == other.beatPulse &&
            waveform.contentEquals(other.waveform) &&
            history == other.history &&
            settings == other.settings &&
            sourceAvailability == other.sourceAvailability &&
            errorBanner == other.errorBanner &&
            secondaryVisible == other.secondaryVisible
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + (bpm?.hashCode() ?: 0)
        result = 31 * result + bpmDimmed.hashCode()
        result = 31 * result + (statusLabel?.hashCode() ?: 0)
        result = 31 * result + showDecimal.hashCode()
        result = 31 * result + beatPulse.hashCode()
        result = 31 * result + waveform.contentHashCode()
        result = 31 * result + history.hashCode()
        result = 31 * result + settings.hashCode()
        result = 31 * result + sourceAvailability.hashCode()
        result = 31 * result + (errorBanner?.hashCode() ?: 0)
        result = 31 * result + secondaryVisible.hashCode()
        return result
    }

    companion object {
        fun idle(
            settings: UserSettings = defaultSettings(),
            sourceAvailability: Map<AudioSourceKind, Boolean> = emptyMap(),
        ): MeasureUiState =
            MeasureUiState(
                state = MeasureState.Idle,
                bpm = null,
                bpmDimmed = false,
                statusLabel = null,
                showDecimal = settings.showDecimal,
                beatPulse = false,
                waveform = FloatArray(WAVEFORM_POINTS),
                history = emptyList(),
                settings = settings,
                sourceAvailability = sourceAvailability,
                errorBanner = null,
                secondaryVisible = false,
            )

        private fun defaultSettings() =
            UserSettings(
                source = AudioSourceKind.Mic,
                genreId = GenreCatalog.byId("auto").id,
                showDecimal = false,
                theme = ThemeMode.Day,
            )
    }
}

class MeasureViewModel(
    private val settingsStore: SettingsStore,
    private val audioInputFactory: AudioInputFactory,
    private val engine: BpmEngine,
    private val session: SessionStore,
    createInput: (AudioSourceKind) -> AudioInput = { audioInputFactory.create(it) },
    availabilityProbe: () -> Map<AudioSourceKind, Boolean> = { audioInputFactory.availability() },
) : ViewModel() {
    companion object {
        const val RECORD_AUDIO_DENIED_BANNER =
            "Microphone permission is required. Grant RECORD_AUDIO in Settings to measure."
        const val MEDIA_PROJECTION_DENIED_BANNER =
            "Screen capture permission is required for internal playback capture."
        const val INTERNAL_UNSUPPORTED_BANNER =
            "Internal playback capture is not supported on this device (requires Android 10+)."
    }

    private val openInput = createInput
    private var mediaProjection: MediaProjection? = null

    private val _uiState =
        MutableStateFlow(
            MeasureUiState.idle(sourceAvailability = availabilityProbe()),
        )
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    private var activeInput: AudioInput? = null
    private var measureJob: Job? = null
    private var errorJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                engine.setProfile(GenreCatalog.byId(settings.genreId))
                _uiState.update {
                    it.copy(
                        settings = settings,
                        showDecimal = settings.showDecimal,
                        sourceAvailability = availabilityProbe(),
                    )
                }
            }
        }
    }

    fun setSecondaryVisible(visible: Boolean) {
        _uiState.update { it.copy(secondaryVisible = visible) }
    }

    fun dismissErrorBanner() {
        _uiState.update { it.copy(errorBanner = null) }
    }

    fun attachMediaProjection(projection: MediaProjection) {
        releaseMediaProjection()
        mediaProjection = projection
    }

    fun reportStartBlocked(message: String) {
        _uiState.update { it.copy(errorBanner = message) }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }

    fun startMeasurement() {
        if (measureJob?.isActive == true) return
        val settings = _uiState.value.settings
        session.clear()
        engine.setProfile(GenreCatalog.byId(settings.genreId))
        engine.reset()

        val input = openInput(settings.source)
        if (input is InternalCaptureAudioInput) {
            mediaProjection?.let { input.attachMediaProjection(it) }
        }
        activeInput = input

        _uiState.update {
            it.copy(
                state = MeasureState.Starting,
                bpm = null,
                bpmDimmed = false,
                statusLabel = MeasureState.Starting.uiLabel(),
                beatPulse = false,
                waveform = FloatArray(WAVEFORM_POINTS),
                history = emptyList(),
                errorBanner = null,
            )
        }

        errorJob =
            viewModelScope.launch {
                input.errors.collect { error -> handleAudioError(error) }
            }
        measureJob =
            viewModelScope.launch {
                input.start()
                input.frames.collect { frame ->
                    val nowMs = SystemClock.elapsedRealtime()
                    val reading = engine.push(frame.samples, frame.sampleRate, nowMs)
                    session.append(reading)
                    _uiState.update { current ->
                        current.copy(
                            state = reading.state,
                            bpm = reading.bpm,
                            bpmDimmed = reading.state == MeasureState.Resyncing,
                            statusLabel = reading.state.uiLabel(),
                            beatPulse =
                                reading.beatPulse && reading.state == MeasureState.Tracking,
                            waveform = shiftWaveform(current.waveform, frame.rms),
                            history = session.snapshot(),
                        )
                    }
                }
            }
    }

    fun stopMeasurement() {
        viewModelScope.launch { stopMeasurementInternal() }
    }

    private suspend fun stopMeasurementInternal() {
        errorJob?.cancel()
        errorJob = null
        measureJob?.cancel()
        measureJob = null
        activeInput?.stop()
        activeInput = null
        releaseMediaProjection()
        session.freeze()
        _uiState.update { current ->
            current.copy(
                state = MeasureState.Idle,
                bpm = null,
                bpmDimmed = false,
                statusLabel = null,
                beatPulse = false,
                history = session.snapshot(),
            )
        }
    }

    private fun releaseMediaProjection() {
        mediaProjection?.stop()
        mediaProjection = null
    }

    private suspend fun handleAudioError(error: AudioError) {
        when (error) {
            AudioError.PermissionDenied -> {
                abortStart(errorBannerFor(error))
            }
            is AudioError.SourceUnavailable -> {
                abortStart(error.message)
            }
            is AudioError.Disconnected -> {
                _uiState.update { current ->
                    current.copy(errorBanner = errorBannerFor(error))
                }
            }
        }
    }

    private suspend fun abortStart(message: String) {
        errorJob?.cancel()
        errorJob = null
        measureJob?.cancel()
        measureJob = null
        activeInput?.stop()
        activeInput = null
        releaseMediaProjection()
        session.freeze()
        _uiState.update { current ->
            current.copy(
                state = MeasureState.Idle,
                bpm = null,
                bpmDimmed = false,
                statusLabel = null,
                beatPulse = false,
                errorBanner = message,
                history = session.snapshot(),
            )
        }
    }

    private fun errorBannerFor(error: AudioError): String =
        when (error) {
            AudioError.PermissionDenied -> RECORD_AUDIO_DENIED_BANNER
            is AudioError.SourceUnavailable -> sourceUnavailableBanner(error)
            is AudioError.Disconnected ->
                when (error.kind) {
                    AudioSourceKind.Usb -> "USB audio disconnected."
                    AudioSourceKind.Internal -> "Internal capture ended."
                    AudioSourceKind.Mic -> "Microphone disconnected."
                }
        }

    private fun sourceUnavailableBanner(error: AudioError.SourceUnavailable): String =
        when (error.kind) {
            AudioSourceKind.Internal ->
                when {
                    !InternalCaptureAudioInput.isSupported() -> INTERNAL_UNSUPPORTED_BANNER
                    error.message.contains("MediaProjection", ignoreCase = true) ->
                        MEDIA_PROJECTION_DENIED_BANNER
                    else -> error.message
                }
            else -> error.message
        }

    private fun shiftWaveform(
        buffer: FloatArray,
        rms: Float,
    ): FloatArray {
        if (buffer.isEmpty()) return buffer
        val next = buffer.copyOf()
        for (i in 0 until next.size - 1) {
            next[i] = next[i + 1]
        }
        next[next.size - 1] = rms
        return next
    }

    override fun onCleared() {
        runBlocking { stopMeasurementInternal() }
        super.onCleared()
    }
}