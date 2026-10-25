package com.stepvault.app.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepvault.app.R
import com.stepvault.app.domain.StepAggregator
import com.stepvault.app.domain.StepSnapshot
import com.stepvault.app.domain.StepStatus
import com.stepvault.app.ui.components.AccessCard
import com.stepvault.app.ui.components.ErrorCard
import com.stepvault.app.ui.components.GoalDialog
import com.stepvault.app.ui.components.StatCard
import com.stepvault.app.ui.components.StepRing
import com.stepvault.app.ui.components.WeekChart
import com.stepvault.app.util.Formatters
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepScreen(
    viewModel: StepViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
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
                        onClick = viewModel::refresh,
                        enabled = !state.loading,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
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
                        onRetry = viewModel::refresh,
                    )
                }
            }

            else -> {
                Dashboard(
                    snapshot = snapshot,
                    loading = state.loading,
                    error = state.error,
                    contentPadding = innerPadding,
                    onRefresh = viewModel::refresh,
                    onSetGoal = { viewModel.showGoalDialog(true) },
                    onAllowAccess = {
                        permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    },
                    onOpenSettings = openSettings,
                )
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
    loading: Boolean,
    error: String?,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onSetGoal: () -> Unit,
    onAllowAccess: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (error != null && snapshot.status != StepStatus.ERROR) {
            item {
                ErrorCard(message = error, onRetry = onRefresh)
            }
        }

        if (loading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        when (snapshot.status) {
            StepStatus.PROVIDER_UNAVAILABLE -> item {
                AccessCard(
                    title = stringResource(R.string.provider_missing_title),
                    body = stringResource(R.string.provider_missing_body),
                )
            }

            StepStatus.PERMISSION_REQUIRED -> item {
                AccessCard(
                    title = stringResource(R.string.access_title),
                    body = stringResource(R.string.access_body),
                    actionLabel = stringResource(R.string.allow_access),
                    onAction = onAllowAccess,
                    secondaryLabel = stringResource(R.string.open_settings),
                    onSecondaryAction = onOpenSettings,
                )
            }

            StepStatus.PERMISSION_DENIED -> item {
                AccessCard(
                    title = stringResource(R.string.permission_denied_title),
                    body = stringResource(R.string.permission_denied_body),
                    actionLabel = stringResource(R.string.open_settings),
                    onAction = onOpenSettings,
                )
            }

            StepStatus.ERROR -> item {
                AccessCard(
                    title = stringResource(R.string.error_title),
                    body = snapshot.message ?: stringResource(R.string.error_title),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRefresh,
                )
            }

            StepStatus.READY -> Unit
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StepRing(
                    steps = snapshot.today.steps,
                    goal = snapshot.goal,
                    progress = snapshot.progressFraction,
                    modifier = Modifier.size(260.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    label = stringResource(R.string.distance_label),
                    value = "${Formatters.distanceKm(snapshot.today.distanceKm)} km",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.calories_label),
                    value = "${Formatters.calories(snapshot.today.calories)} kcal",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.active_label),
                    value = Formatters.activeTime(snapshot.today.activeMinutes),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.this_week),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(
                                R.string.week_goal_legend,
                                Formatters.steps(snapshot.goal),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    WeekChart(
                        week = snapshot.week,
                        goal = snapshot.goal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onSetGoal,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.set_goal))
                }
                Button(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.refresh))
                }
            }
        }

        item {
            val syncTime = snapshot.lastSync?.let { Formatters.time(it) }
            val syncText = if (syncTime != null) {
                stringResource(R.string.last_sync_format, syncTime)
            } else {
                stringResource(R.string.not_synced)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.source_format),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = syncText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
                if (snapshot.status != StepStatus.READY && snapshot.lastSync != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.cached_data_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
