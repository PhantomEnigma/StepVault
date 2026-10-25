package com.stepvault.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stepvault.app.R
import com.stepvault.app.domain.HourlySteps
import com.stepvault.app.util.Formatters
import java.time.LocalDate

@Composable
fun HourlyChart(
    hours: List<HourlySteps>,
    date: LocalDate,
    defaultHour: Int,
    modifier: Modifier = Modifier,
) {
    var selectedHour by rememberSaveable(date.toEpochDay()) {
        mutableIntStateOf(defaultHour.coerceIn(0, 23))
    }
    val selected = hours.getOrNull(selectedHour) ?: HourlySteps.empty(selectedHour)
    val accent = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .pointerInput(hours) {
                    if (hours.isEmpty()) return@pointerInput
                    detectTapGestures { position ->
                        val slot = size.width.toFloat() / hours.size.coerceAtLeast(1)
                        selectedHour = (position.x / slot).toInt().coerceIn(0, 23)
                    }
                },
        ) {
            if (hours.isEmpty()) return@Canvas
            val usableHeight = size.height - 2.dp.toPx()
            val slot = size.width / 24f
            val barWidth = (slot * 0.58f).coerceAtLeast(2.dp.toPx())
            val maxValue = (hours.maxOfOrNull { it.steps } ?: 0).coerceAtLeast(1)

            drawRoundRect(
                color = highlightColor,
                topLeft = Offset(slot * selectedHour, 0f),
                size = Size(slot, usableHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )

            drawLine(
                color = baselineColor,
                start = Offset(0f, usableHeight),
                end = Offset(size.width, usableHeight),
                strokeWidth = 1.dp.toPx(),
            )

            hours.take(24).forEachIndexed { index, hour ->
                if (hour.steps <= 0) return@forEachIndexed
                val barHeight = (usableHeight * hour.steps.toFloat() / maxValue)
                    .coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    color = if (index == selectedHour) accent else barColor,
                    topLeft = Offset(
                        slot * index + (slot - barWidth) / 2f,
                        usableHeight - barHeight,
                    ),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00:00", "06:00", "12:00", "18:00").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(9.dp))

        Text(
            text = stringResource(
                R.string.hourly_detail_format,
                Formatters.hourRange(selected.hour),
                Formatters.steps(selected.steps),
                Formatters.distanceKm(selected.distanceKm),
                Formatters.calories(selected.calories),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
