package com.stepvault.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stepvault.app.R
import com.stepvault.app.domain.DaySteps
import com.stepvault.app.domain.HourlySteps
import com.stepvault.app.domain.StepStatus
import com.stepvault.app.ui.components.HourlyChart
import com.stepvault.app.util.Formatters
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    date: LocalDate,
    day: DaySteps?,
    goal: Int,
    hours: List<HourlySteps>,
    loading: Boolean,
    status: StepStatus,
    message: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        val data = day ?: DaySteps.empty(date)
        val percent = ((data.steps.toLong() * 100L) / goal.coerceAtLeast(1))
            .coerceIn(0L, 100L)
            .toInt()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                text = Formatters.fullDate(date),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(14.dp))
            DayMetrics(data = data, goal = goal, percent = percent)
            Spacer(modifier = Modifier.height(16.dp))

            if (loading && hours.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                HourlyChart(
                    hours = hours,
                    date = date,
                    defaultHour = if (date == LocalDate.now()) {
                        LocalTime.now().hour
                    } else {
                        12
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!loading && status != StepStatus.READY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message ?: stringResource(R.string.hours_cached),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayMetrics(
    data: DaySteps,
    goal: Int,
    percent: Int,
) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Metric(
            label = stringResource(R.string.day_sheet_steps),
            value = Formatters.steps(data.steps),
            modifier = Modifier.weight(1f),
        )
        Divider(color = dividerColor)
        Metric(
            label = stringResource(R.string.distance_label),
            value = stringResource(
                R.string.distance_value_format,
                Formatters.distanceKm(data.distanceKm),
            ),
            modifier = Modifier.weight(1f),
        )
        Divider(color = dividerColor)
        Metric(
            label = stringResource(R.string.calories_label),
            value = stringResource(
                R.string.calories_value_format,
                Formatters.calories(data.calories),
            ),
            modifier = Modifier.weight(1f),
        )
        Divider(color = dividerColor)
        Metric(
            label = stringResource(R.string.active_label),
            value = Formatters.activeTime(data.activeMinutes),
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(
            R.string.day_goal_format,
            Formatters.steps(goal),
            percent,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun Divider(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(color),
    )
}
