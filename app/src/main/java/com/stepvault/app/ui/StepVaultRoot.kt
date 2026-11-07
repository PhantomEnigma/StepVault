package com.stepvault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.stepvault.app.R
import com.stepvault.app.ui.history.HistoryScreen
import com.stepvault.app.ui.history.HistoryViewModel

private const val TAB_TODAY = 0
private const val TAB_HISTORY = 1

@Composable
fun StepVaultRoot(
    todayViewModel: StepViewModel,
    historyViewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
    openGoalRequest: Int = 0,
) {
    var selectedTab by rememberSaveable { mutableStateOf(TAB_TODAY) }

    LaunchedEffect(openGoalRequest) {
        if (openGoalRequest > 0) {
            selectedTab = TAB_TODAY
            todayViewModel.showGoalDialog(true)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (selectedTab == TAB_HISTORY) {
            historyViewModel.onVisible()
        } else {
            todayViewModel.onVisible()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == TAB_HISTORY) {
            historyViewModel.onVisible()
        } else {
            todayViewModel.onVisible()
        }
    }

    BackHandler(enabled = selectedTab == TAB_HISTORY) {
        selectedTab = TAB_TODAY
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (selectedTab) {
            TAB_HISTORY -> HistoryScreen(
                viewModel = historyViewModel,
                contentBottomPadding = 72.dp,
            )

            else -> StepScreen(
                viewModel = todayViewModel,
                contentBottomPadding = 72.dp,
            )
        }

        StepVaultBottomBar(
            selectedTab = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun StepVaultBottomBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BottomTab(
                    label = stringResource(R.string.tab_today),
                    iconRes = R.drawable.ic_today,
                    selected = selectedTab == TAB_TODAY,
                    onClick = { onSelect(TAB_TODAY) },
                    modifier = Modifier.weight(1f),
                )
                BottomTab(
                    label = stringResource(R.string.tab_history),
                    iconRes = R.drawable.ic_history,
                    selected = selectedTab == TAB_HISTORY,
                    onClick = { onSelect(TAB_HISTORY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomTab(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    },
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.width(18.dp).height(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
