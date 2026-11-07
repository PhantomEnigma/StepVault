package com.stepvault.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stepvault.app.R
import com.stepvault.app.domain.WeekSummary
import com.stepvault.app.util.Formatters
import java.time.LocalDate

@Composable
fun WeeklyOverview(
    weeks: List<WeekSummary>,
    selectedWeekStart: LocalDate?,
    onSelectWeek: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val selectedIndex = weeks.indexOfFirst { it.start == selectedWeekStart }
    val selected = weeks.getOrNull(selectedIndex) ?: weeks.lastOrNull()
    val hasData = weeks.any { it.totalSteps > 0 }
    val description = stringResource(
        R.string.history_week_description,
        selected?.let { Formatters.dayMonth(it.start) } ?: "",
        Formatters.steps(selected?.totalSteps ?: 0),
    )
    val previousLabel = stringResource(R.string.previous_week)
    val nextLabel = stringResource(R.string.next_week)

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics {
                        contentDescription = description
                        customActions = listOf(
                            CustomAccessibilityAction(previousLabel) {
                                if (weeks.isNotEmpty()) {
                                    val index = (selectedIndex - 1).coerceIn(0, weeks.lastIndex)
                                    onSelectWeek(weeks[index].start)
                                }
                                true
                            },
                            CustomAccessibilityAction(nextLabel) {
                                if (weeks.isNotEmpty()) {
                                    val index = (selectedIndex + 1).coerceIn(0, weeks.lastIndex)
                                    onSelectWeek(weeks[index].start)
                                }
                                true
                            },
                        )
                    }
                    .pointerInput(weeks) {
                        if (weeks.isEmpty()) return@pointerInput
                        detectTapGestures { position ->
                            val slot = size.width.toFloat() / weeks.size
                            val index = (position.x / slot).toInt().coerceIn(0, weeks.lastIndex)
                            onSelectWeek(weeks[index].start)
                        }
                    },
            ) {
                if (weeks.isEmpty()) return@Canvas
                val usableHeight = size.height - 3.dp.toPx()
                val slot = size.width / weeks.size
                val barWidth = (slot * 0.46f).coerceAtLeast(4.dp.toPx())
                val maxValue = (weeks.maxOfOrNull { it.totalSteps } ?: 0).coerceAtLeast(1)

                if (selectedIndex >= 0) {
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = Offset(slot * selectedIndex, 0f),
                        size = Size(slot, usableHeight),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    )
                }

                weeks.forEachIndexed { index, week ->
                    if (week.totalSteps <= 0) return@forEachIndexed
                    val barHeight = (usableHeight * week.totalSteps.toFloat() / maxValue)
                        .coerceAtLeast(3.dp.toPx())
                    val left = slot * index + (slot - barWidth) / 2f
                    drawRoundRect(
                        color = if (index == selectedIndex) accent else barColor,
                        topLeft = Offset(left, usableHeight - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }

            if (!hasData) {
                Text(
                    text = stringResource(R.string.no_week_data),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            weeks.forEachIndexed { index, week ->
                Text(
                    text = Formatters.dayMonth(week.start),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == selectedIndex) accent else labelColor,
                    maxLines = 1,
                )
            }
        }
    }
}
