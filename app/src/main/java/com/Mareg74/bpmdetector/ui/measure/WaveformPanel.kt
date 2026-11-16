package com.Mareg74.bpmdetector.ui.measure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun WaveformPanel(
    waveform: FloatArray,
    modifier: Modifier = Modifier,
) {
    val strokeColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Waveform",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(surfaceColor)
                    .testTag("waveform_panel"),
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                if (waveform.isEmpty()) return@Canvas
                val maxVal = waveform.maxOrNull()?.coerceAtLeast(1e-6f) ?: 1f
                val midY = size.height / 2f
                val stepX =
                    if (waveform.size > 1) {
                        size.width / (waveform.size - 1)
                    } else {
                        size.width
                    }
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val path = Path()
                waveform.forEachIndexed { index, sample ->
                    val x = index * stepX
                    val normalized = (sample / maxVal).coerceIn(0f, 1f)
                    val y = midY - normalized * midY * 0.92f
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(path, color = strokeColor, style = stroke)
                drawLine(
                    color = strokeColor.copy(alpha = 0.25f),
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
}