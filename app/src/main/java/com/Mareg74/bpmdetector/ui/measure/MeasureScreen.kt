package com.Mareg74.bpmdetector.ui.measure

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.Mareg74.bpmdetector.ui.settings.SettingsSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Mareg74.bpmdetector.domain.MeasureState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureScreen(
    viewModel: MeasureViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    MeasureScreenContent(
        uiState = uiState,
        onStart = viewModel::startMeasurement,
        onStop = viewModel::stopMeasurement,
        onOpenSettings = { settingsOpen = true },
        modifier = modifier,
    )
    if (settingsOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = sheetState,
        ) {
            SettingsSheet(
                settings = uiState.settings,
                sourceAvailability = uiState.sourceAvailability,
                onSourceChange = { kind ->
                    viewModel.updateSettings { it.copy(source = kind) }
                },
                onGenreChange = { id ->
                    viewModel.updateSettings { it.copy(genreId = id) }
                },
                onShowDecimalChange = { show ->
                    viewModel.updateSettings { it.copy(showDecimal = show) }
                },
                onThemeChange = { theme ->
                    viewModel.updateSettings { it.copy(theme = theme) }
                },
            )
        }
    }
}

@Composable
fun MeasureScreenContent(
    uiState: MeasureUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val measuring = uiState.state != MeasureState.Idle
    Row(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BpmDisplay(
                bpm = uiState.bpm,
                showDecimal = uiState.showDecimal,
                dimmed = uiState.bpmDimmed,
            )
            uiState.statusLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    modifier =
                        Modifier
                            .padding(top = 12.dp)
                            .testTag("status"),
                )
            }
            SyncPulseDot(
                tracking = uiState.state == MeasureState.Tracking,
                beatPulse = uiState.beatPulse,
                modifier = Modifier.padding(top = 16.dp),
            )
            uiState.errorBanner?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Button(
                onClick = if (measuring) onStop else onStart,
                modifier = Modifier.testTag("start_stop"),
            ) {
                Text(if (measuring) "STOP" else "START")
            }
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("settings"),
            ) {
                Text("Settings")
            }
        }
    }
}

@Composable
private fun SyncPulseDot(
    tracking: Boolean,
    beatPulse: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseAlpha =
        when {
            tracking && beatPulse -> null
            tracking -> 0.65f
            else -> 0.2f
        }
    val pulseAlpha =
        if (baseAlpha == null) {
            val transition = rememberInfiniteTransition(label = "sync_pulse")
            transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 280),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "sync_alpha",
            ).value
        } else {
            baseAlpha
        }
    Box(
        modifier =
            modifier
                .size(14.dp)
                .alpha(pulseAlpha)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .testTag("sync_dot"),
    )
}