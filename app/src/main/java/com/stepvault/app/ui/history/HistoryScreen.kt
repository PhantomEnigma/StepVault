package com.stepvault.app.ui.history

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepvault.app.R
import com.stepvault.app.data.StepRepository
import com.stepvault.app.domain.HistorySnapshot
import com.stepvault.app.domain.StepAggregator
import com.stepvault.app.domain.StepStatus
import com.stepvault.app.domain.WeekSummary
import com.stepvault.app.ui.components.AccessCard
import com.stepvault.app.ui.components.ErrorCard
import com.stepvault.app.ui.components.HistoryDayRow
import com.stepvault.app.ui.components.StatusCard
import com.stepvault.app.ui.components.WeeklyOverview
import com.stepvault.app.util.Formatters
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refresh()
    }
    val openSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(
                                R.string.history_subtitle,
                                StepRepository.HISTORY_DAYS.toInt(),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.loading,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.refresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        val snapshot = state.snapshot
        when {
            snapshot == null && state.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            snapshot == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(20.dp),
                ) {
                    ErrorCard(
                        message = state.error ?: stringResource(R.string.error_title),
                        onRetry = { viewModel.refresh() },
                    )
                }
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    HistoryContent(
                        snapshot = snapshot,
                        state = state,
                        contentBottomPadding = contentBottomPadding,
                        onSelectWeek = viewModel::selectWeek,
                        onSelectDay = { date -> viewModel.selectDay(date) },
                        onRefresh = { viewModel.refresh() },
                        onAllowAccess = {
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        },
                        onOpenSettings = openSettings,
                    )
                }
            }
        }
    }

    val selectedDate = state.selectedDate
    if (state.showDaySheet && selectedDate != null) {
        DayDetailSheet(
            date = selectedDate,
            day = state.snapshot?.days?.firstOrNull { it.date == selectedDate },
            goal = state.snapshot?.goal ?: StepAggregator.DEFAULT_GOAL,
            hours = state.selectedHours,
            loading = state.hoursLoading,
            status = state.hoursStatus,
            message = state.hoursMessage,
            onRetry = viewModel::retryHours,
            onDismiss = viewModel::dismissDay,
        )
    }
}

@Composable
private fun HistoryContent(
    snapshot: HistorySnapshot,
    state: HistoryUiState,
    contentBottomPadding: Dp,
    onSelectWeek: (LocalDate) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    onAllowAccess: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val selectedWeek = snapshot.weeks.firstOrNull { it.start == state.selectedWeekStart }
        ?: snapshot.weeks.lastOrNull()
    val hasData = snapshot.days.any { it.steps > 0 }
    val today = LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 6.dp,
            bottom = 28.dp + contentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (snapshot.status != StepStatus.READY) {
            item {
                StatusCard(
                    status = snapshot.status,
                    message = snapshot.message,
                    onRefresh = onRefresh,
                    onAllowAccess = onAllowAccess,
                    onOpenSettings = onOpenSettings,
                )
            }
        }

        if (!hasData) {
            if (snapshot.status == StepStatus.READY) {
                item {
                    AccessCard(
                        title = stringResource(R.string.history_empty_title),
                        body = stringResource(R.string.history_empty_body),
                        actionLabel = stringResource(R.string.retry),
                        onAction = onRefresh,
                    )
                }
            }
        } else {
            item { SummaryCard(snapshot = snapshot) }

            if (selectedWeek != null) {
                item {
                    WeeklyCard(
                        weeks = snapshot.weeks,
                        selectedWeekStart = selectedWeek.start,
                        onSelectWeek = onSelectWeek,
                    )
                }
                item { WeekHeader(week = selectedWeek) }
                items(
                    items = selectedWeek.days.asReversed(),
                    key = { it.date.toEpochDay() },
                ) { day ->
                    HistoryDayRow(
                        day = day,
                        goal = snapshot.goal,
                        isToday = day.date == today,
                        onClick = { onSelectDay(day.date) },
                    )
                }
            }

            item { HistoryFooter(snapshot = snapshot) }
        }
    }
}

@Composable
private fun SummaryCard(snapshot: HistorySnapshot) {
    val best = snapshot.bestDay
    val bestValue = if (best != null) {
        stringResource(R.string.steps_count_format, Formatters.steps(best.steps))
    } else {
        "—"
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = stringResource(R.string.history_total_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Formatters.steps(snapshot.totalSteps),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryMetric(
                    label = stringResource(R.string.history_average_label),
                    value = Formatters.steps(snapshot.averageSteps),
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = stringResource(R.string.history_best_label),
                    value = bestValue,
                    caption = best?.let { Formatters.shortDate(it.date) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (snapshot.status != StepStatus.READY) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.history_cached),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WeeklyCard(
    weeks: List<WeekSummary>,
    selectedWeekStart: LocalDate,
    onSelectWeek: (LocalDate) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = stringResource(R.string.chart_week_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            WeeklyOverview(
                weeks = weeks,
                selectedWeekStart = selectedWeekStart,
                onSelectWeek = onSelectWeek,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WeekHeader(week: WeekSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.history_week_range,
                Formatters.dayMonth(week.start),
                Formatters.dayMonth(week.end),
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                R.string.history_week_total,
                Formatters.steps(week.totalSteps),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryFooter(snapshot: HistorySnapshot) {
    val syncTime = snapshot.lastSync?.let { Formatters.time(it) }
    val text = when {
        syncTime == null -> stringResource(R.string.footer_not_synced)
        snapshot.status != StepStatus.READY -> {
            stringResource(R.string.footer_cached_format, syncTime)
        }

        else -> stringResource(R.string.footer_format, syncTime)
    }

    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
    )
}
