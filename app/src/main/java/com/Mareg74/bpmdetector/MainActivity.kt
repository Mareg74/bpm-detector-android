package com.Mareg74.bpmdetector

import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Mareg74.bpmdetector.audio.AudioSourceKind
import com.Mareg74.bpmdetector.audio.InternalCaptureAudioInput
import com.Mareg74.bpmdetector.data.ThemeMode
import com.Mareg74.bpmdetector.ui.measure.MeasureScreen
import com.Mareg74.bpmdetector.ui.measure.MeasureViewModel
import com.Mareg74.bpmdetector.ui.measure.MeasureViewModelFactory
import com.Mareg74.bpmdetector.ui.theme.BpmDetectorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MeasureViewModel by viewModels {
        MeasureViewModelFactory(applicationContext)
    }

    private var awaitingStartAfterRecordAudio = false

    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (awaitingStartAfterRecordAudio) {
                    awaitingStartAfterRecordAudio = false
                    continueStartAfterRecordAudioGranted()
                }
            } else {
                awaitingStartAfterRecordAudio = false
                viewModel.reportStartBlocked(MeasureViewModel.RECORD_AUDIO_DENIED_BANNER)
            }
        }

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val manager = getSystemService(MediaProjectionManager::class.java)
                val projection = manager.getMediaProjection(result.resultCode, result.data!!)
                viewModel.attachMediaProjection(projection)
                viewModel.startMeasurement()
            } else {
                viewModel.reportStartBlocked(MeasureViewModel.MEDIA_PROJECTION_DENIED_BANNER)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    viewModel.stopMeasurement()
                }
            },
        )
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = uiState.settings.theme == ThemeMode.Night
            BpmDetectorTheme(darkTheme = darkTheme, dynamicColor = false) {
                MeasureScreen(
                    viewModel = viewModel,
                    onStartMeasurement = ::requestStartMeasurement,
                )
            }
        }
    }

    private fun requestStartMeasurement() {
        val source = viewModel.uiState.value.settings.source
        if (source == AudioSourceKind.Internal && !InternalCaptureAudioInput.isSupported()) {
            viewModel.reportStartBlocked(MeasureViewModel.INTERNAL_UNSUPPORTED_BANNER)
            return
        }
        if (!hasRecordAudioPermission()) {
            awaitingStartAfterRecordAudio = true
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        continueStartAfterRecordAudioGranted()
    }

    private fun continueStartAfterRecordAudioGranted() {
        when (viewModel.uiState.value.settings.source) {
            AudioSourceKind.Internal -> {
                val manager = getSystemService(MediaProjectionManager::class.java)
                mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
            }
            AudioSourceKind.Mic,
            AudioSourceKind.Usb,
            -> viewModel.startMeasurement()
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}