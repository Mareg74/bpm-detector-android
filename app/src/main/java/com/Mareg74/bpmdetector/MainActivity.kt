package com.Mareg74.bpmdetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.Mareg74.bpmdetector.ui.theme.BpmDetectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BpmDetectorTheme {
                Text("BPM Detector")
            }
        }
    }
}