package com.stepvault.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepvault.app.R
import com.stepvault.app.domain.HourlySteps
import com.stepvault.app.domain.StepAggregator
import com.stepvault.app.domain.StepSnapshot
import com.stepvault.app.domain.StepStatus
import com.stepvault.app.ui.components.AccessCard
import com.stepvault.app.ui.components.ErrorCard
import com.stepvault.app.ui.components.GoalDialog
import com.stepvault.app.ui.components.HourlyChart
import com.stepvault.app.ui.components.StepRing
import com.stepvault.app.ui.components.WeekChart
import com.stepvault.app.util.Formatters
import java.time.LocalDate
import java.time.LocalTime

private const val CHART_WEEK = 0
private const val CHART_HOURLY = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepScreen(
    viewModel: StepViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

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
    val headerDate = state.snapshot?.today?.date ?: LocalDate.now()

    LaunchedEffect(state.error) {
        val error = state.error
        if (error != null && state.snapshot != null) {
            snackbarHostState.showSnackbar(error)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.steps_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = if (headerDate == LocalDate.now()) {
                                stringResource(
                                    R.string.today_header,
                                    Formatters.shortDate(headerDate),
                                )
                            } else {
                                Formatters.shortDate(headerDate)
                            },
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
                    isRefreshing = state.loading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    Dashboard(
                        snapshot = snapshot,
                        onSetGoal = { viewModel.showGoalDialog(true) },
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

    if (state.showGoalDialog) {
        GoalDialog(
            currentGoal = state.snapshot?.goal ?: StepAggregator.DEFAULT_GOAL,
            onDismiss = { viewModel.showGoalDialog(false) },
            onSave = { goal ->
                viewModel.showGoalDialog(false)
                viewModel.setGoal(goal)
            },
        )
    }
}

@Composable
private fun Dashboard(
    snapshot: StepSnapshot,
    onSetGoal: () -> Unit,
    onRefresh: () -> Unit,
    onAllowAccess: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var selectedEpochDay by rememberSaveable {
        mutableStateOf(snapshot.today.date.toEpochDay())
    }
    var chartMode by rememberSaveable { mutableStateOf(CHART_WEEK) }
    val selectedDate = snapshot.week
        .firstOrNull { it.date.toEpochDay() == selectedEpochDay }
        ?.date
        ?: snapshot.today.date
    val selectedHours = snapshot.hoursByDay[selectedDate].orEmpty()
    val selectedIsToday = selectedDate == LocalDate.now()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (snapshot.status != StepStatus.READY) {
                item {
                    StatusCard(
                        snapshot = snapshot,
                        onRefresh = onRefresh,
                        onAllowAccess = onAllowAccess,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }

            if (wide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            HeroCard(
                                snapshot = snapshot,
                                onSetGoal = onSetGoal,
                            )
                            MetricsStrip(snapshot = snapshot)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            InsightsCard(
                                snapshot = snapshot,
                                selectedDate = selectedDate,
                                selectedHours = selectedHours,
                                selectedIsToday = selectedIsToday,
                                chartMode = chartMode,
                                onChartModeChange = { chartMode = it },
                                onSelectDate = { date -> selectedEpochDay = date.toEpochDay() },
                            )
                        }
                    }
                }
            } else {
                item {
                    HeroCard(
                        snapshot = snapshot,
                        onSetGoal = onSetGoal,
                    )
                }
                item { MetricsStrip(snapshot = snapshot) }
                item {
                    InsightsCard(
                        snapshot = snapshot,
                        selectedDate = selectedDate,
                        selectedHours = selectedHours,
                        selectedIsToday = selectedIsToday,
                        chartMode = chartMode,
                        onChartModeChange = { chartMode = it },
                        onSelectDate = { date -> selectedEpochDay = date.toEpochDay() },
                    )
                }
            }

            item { Footer(snapshot = snapshot) }
        }
    }
}

@Composable
private fun HeroCard(
    snapshot: StepSnapshot,
    onSetGoal: () -> Unit,
) {
    val remaining = snapshot.remainingSteps
    val goalDescription = stringResource(R.string.goal_edit_description)
    val goalText = if (remaining == 0) {
        stringResource(R.string.goal_reached)
    } else {
        stringResource(R.string.remaining_format, Formatters.steps(remaining))
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepRing(
                steps = snapshot.today.steps,
                progress = snapshot.progressFraction,
                modifier = Modifier.size(220.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSetGoal)
                    .semantics { contentDescription = goalDescription }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flag),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        R.string.week_goal_legend,
                        Formatters.steps(snapshot.goal),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " · $goalText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MetricsStrip(snapshot: StepSnapshot) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Metric(
                label = stringResource(R.string.distance_label),
                value = stringResource(
                    R.string.distance_value_format,
                    Formatters.distanceKm(snapshot.today.distanceKm),
                ),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(dividerColor),
            )
            Metric(
                label = stringResource(R.string.calories_label),
                value = stringResource(
                    R.string.calories_value_format,
                    Formatters.calories(snapshot.today.calories),
                ),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(dividerColor),
            )
            Metric(
                label = stringResource(R.string.active_label),
                value = Formatters.activeTime(snapshot.today.activeMinutes),
                modifier = Modifier.weight(1f),
            )
        }
    }
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
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun InsightsCard(
    snapshot: StepSnapshot,
    selectedDate: LocalDate,
    selectedHours: List<HourlySteps>,
    selectedIsToday: Boolean,
    chartMode: Int,
    onChartModeChange: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (chartMode == CHART_WEEK) {
                            stringResource(R.string.chart_week_title)
                        } else {
                            stringResource(R.string.chart_hourly_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (chartMode == CHART_WEEK) {
                            stringResource(
                                R.string.week_goal_legend,
                                Formatters.steps(snapshot.goal),
                            )
                        } else {
                            Formatters.shortDate(selectedDate)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChartTabs(
                    selectedMode = chartMode,
                    onSelect = onChartModeChange,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (chartMode == CHART_WEEK) {
                WeekChart(
                    week = snapshot.week,
                    goal = snapshot.goal,
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                HourlyChart(
                    hours = selectedHours,
                    date = selectedDate,
                    defaultHour = if (selectedIsToday) LocalTime.now().hour else 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ChartTabs(
    selectedMode: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ChartTab(
            label = stringResource(R.string.chart_week),
            selected = selectedMode == CHART_WEEK,
            onClick = { onSelect(CHART_WEEK) },
        )
        ChartTab(
            label = stringResource(R.string.chart_hourly),
            selected = selectedMode == CHART_HOURLY,
            onClick = { onSelect(CHART_HOURLY) },
        )
    }
}

@Composable
private fun ChartTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(shape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StatusCard(
    snapshot: StepSnapshot,
    onRefresh: () -> Unit,
    onAllowAccess: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (snapshot.status) {
        StepStatus.PROVIDER_UNAVAILABLE -> AccessCard(
            title = stringResource(R.string.provider_missing_title),
            body = stringResource(R.string.provider_missing_body),
        )

        StepStatus.PERMISSION_REQUIRED -> AccessCard(
            title = stringResource(R.string.access_title),
            body = stringResource(R.string.access_body),
            actionLabel = stringResource(R.string.allow_access),
            onAction = onAllowAccess,
            secondaryLabel = stringResource(R.string.open_settings),
            onSecondaryAction = onOpenSettings,
        )

        StepStatus.PERMISSION_DENIED -> AccessCard(
            title = stringResource(R.string.permission_denied_title),
            body = stringResource(R.string.permission_denied_body),
            actionLabel = stringResource(R.string.open_settings),
            onAction = onOpenSettings,
        )

        StepStatus.ERROR -> AccessCard(
            title = stringResource(R.string.error_title),
            body = snapshot.message ?: stringResource(R.string.error_title),
            actionLabel = stringResource(R.string.retry),
            onAction = onRefresh,
        )

        StepStatus.READY -> Unit
    }
}

@Composable
private fun Footer(snapshot: StepSnapshot) {
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
