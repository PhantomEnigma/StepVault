package com.stepvault.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stepvault.app.R
import com.stepvault.app.domain.DaySteps
import com.stepvault.app.util.Formatters
import java.time.LocalDate

@Composable
fun WeekChart(
    week: List<DaySteps>,
    goal: Int,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val today = week.lastOrNull()?.date
    val selected = week.firstOrNull { it.date == selectedDate } ?: week.lastOrNull()

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(week) {
                    if (week.isEmpty()) return@pointerInput
                    detectTapGestures { position ->
                        val slot = size.width.toFloat() / week.size
                        val index = (position.x / slot).toInt().coerceIn(0, week.lastIndex)
                        onSelectDate(week[index].date)
                    }
                },
        ) {
            if (week.isEmpty()) return@Canvas
            val usableHeight = size.height - 4.dp.toPx()
            val slot = size.width / week.size
            val barWidth = (slot * 0.42f).coerceAtLeast(4.dp.toPx())
            val maxValue = maxOf(goal, week.maxOfOrNull { it.steps } ?: 0, 1)
            val goalY = usableHeight * (1f - goal.toFloat() / maxValue)
            val selectedIndex = week.indexOfFirst { it.date == selectedDate }

            if (selectedIndex >= 0) {
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(slot * selectedIndex, 0f),
                    size = Size(slot, usableHeight),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                )
            }

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
                    color = if (day.date == selectedDate) accent else barColor,
                    topLeft = Offset(left, usableHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { day ->
                Text(
                    text = Formatters.weekday(day.date),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (day.date == today) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (day.date == selectedDate) accent else labelColor,
                    maxLines = 1,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selected != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selected.date == today) {
                            stringResource(
                                R.string.today_header,
                                Formatters.shortDate(selected.date),
                            )
                        } else {
                            Formatters.shortDate(selected.date)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected.date == selectedDate) {
                            accent
                        } else {
                            labelColor
                        },
                    )
                    Text(
                        text = stringResource(
                            R.string.steps_count_format,
                            Formatters.steps(selected.steps),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.week_day_metrics_format,
                        Formatters.distanceKm(selected.distanceKm),
                        Formatters.calories(selected.calories),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
