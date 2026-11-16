package com.Mareg74.bpmdetector.ui.measure

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Mareg74.bpmdetector.ui.theme.BpmDecimalStyle
import com.Mareg74.bpmdetector.ui.theme.BpmIntegerStyle
import kotlin.math.roundToInt

@Composable
fun BpmDisplay(
    bpm: Double?,
    showDecimal: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val color =
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = if (dimmed) 0.4f else 1f,
        )
    if (bpm == null) {
        Text(
            text = "—",
            style = BpmIntegerStyle,
            color = color,
            modifier = modifier,
        )
        return
    }
    if (showDecimal) {
        val integer = bpm.roundToInt()
        val fractionalDigit = ((bpm * 10).roundToInt() % 10).coerceIn(0, 9)
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = integer.toString(),
                style = BpmIntegerStyle,
                color = color,
            )
            Text(
                text = ".$fractionalDigit",
                style = BpmDecimalStyle,
                color = color,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    } else {
        Text(
            text = bpm.roundToInt().toString(),
            style = BpmIntegerStyle,
            color = color,
            modifier = modifier,
        )
    }
}