package com.stepvault.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stepvault.app.domain.DaySteps
import com.stepvault.app.util.Formatters

@Composable
fun WeekChart(
    week: List<DaySteps>,
    goal: Int,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    val today = week.lastOrNull()?.date

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(148.dp)) {
            if (week.isEmpty()) return@Canvas
            val usableHeight = size.height - 4.dp.toPx()
            val slot = size.width / week.size
            val barWidth = (slot * 0.42f).coerceAtLeast(4.dp.toPx())
            val maxValue = maxOf(goal, week.maxOfOrNull { it.steps } ?: 0, 1)
            val goalY = usableHeight * (1f - goal.toFloat() / maxValue)

            drawLine(
                color = gridColor,
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
            )

            week.forEachIndexed { index, day ->
                if (day.steps <= 0) return@forEachIndexed
                val barHeight = (usableHeight * day.steps.toFloat() / maxValue)
                    .coerceAtLeast(3.dp.toPx())
                val left = slot * index + (slot - barWidth) / 2f
                drawRoundRect(
                    color = if (day.date == today) accent else barColor,
                    topLeft = Offset(left, usableHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { day ->
                Text(
                    text = Formatters.weekday(day.date),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.date == today) accent else labelColor,
                    maxLines = 1,
                )
            }
        }
    }
}
