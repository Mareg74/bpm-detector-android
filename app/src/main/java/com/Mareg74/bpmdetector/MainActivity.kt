package com.Mareg74.bpmdetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Mareg74.bpmdetector.data.ThemeMode
import com.Mareg74.bpmdetector.ui.measure.MeasureScreen
import com.Mareg74.bpmdetector.ui.measure.MeasureViewModel
import com.Mareg74.bpmdetector.ui.measure.MeasureViewModelFactory
import com.Mareg74.bpmdetector.ui.theme.BpmDetectorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MeasureViewModel by viewModels {
        MeasureViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = uiState.settings.theme == ThemeMode.Night
            BpmDetectorTheme(darkTheme = darkTheme, dynamicColor = false) {
                MeasureScreen(viewModel = viewModel)
            }
        }
    }
}