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
import kotlin.math.max

@Composable
fun HistoryPanel(
    history: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
) {
    val strokeColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "BPM history",
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
                    .testTag("history_panel"),
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                if (history.isEmpty()) return@Canvas
                val sorted = history.sortedBy { it.first }
                val tStart = sorted.first().first
                val tEnd = sorted.last().first
                val timeSpan = max(1L, tEnd - tStart)
                var bpmMin = sorted.minOf { it.second }
                var bpmMax = sorted.maxOf { it.second }
                if (bpmMax - bpmMin < 1.0) {
                    bpmMin -= 2.0
                    bpmMax += 2.0
                }
                val bpmSpan = max(1.0, bpmMax - bpmMin)
                val path = Path()
                sorted.forEachIndexed { index, (timeMs, bpm) ->
                    val xProgress = (timeMs - tStart).toFloat() / timeSpan.toFloat()
                    val x = xProgress.coerceIn(0f, 1f) * size.width
                    val yProgress = ((bpm - bpmMin) / bpmSpan).toFloat()
                    val y = size.height - yProgress.coerceIn(0f, 1f) * size.height
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                if (sorted.size == 1) {
                    val (timeMs, bpm) = sorted.first()
                    val x = ((timeMs - tStart).toFloat() / timeSpan) * size.width
                    val yProgress = ((bpm - bpmMin) / bpmSpan).toFloat()
                    val y = size.height - yProgress.coerceIn(0f, 1f) * size.height
                    drawCircle(color = strokeColor, radius = 3.dp.toPx(), center = Offset(x, y))
                } else {
                    drawPath(
                        path,
                        color = strokeColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}