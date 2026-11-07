package com.stepvault.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stepvault.app.R
import com.stepvault.app.data.GoalStore
import com.stepvault.app.domain.StepStatus

@Composable
fun AccessCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_walk),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(text = actionLabel)
                }
            }
            if (secondaryLabel != null && onSecondaryAction != null) {
                TextButton(onClick = onSecondaryAction) {
                    Text(text = secondaryLabel)
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    status: StepStatus,
    message: String?,
    onRefresh: () -> Unit,
    onAllowAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (status) {
        StepStatus.PROVIDER_UNAVAILABLE -> AccessCard(
            title = stringResource(R.string.provider_missing_title),
            body = stringResource(R.string.provider_missing_body),
            modifier = modifier,
        )

        StepStatus.PERMISSION_REQUIRED -> AccessCard(
            title = stringResource(R.string.access_title),
            body = stringResource(R.string.access_body),
            modifier = modifier,
            actionLabel = stringResource(R.string.allow_access),
            onAction = onAllowAccess,
            secondaryLabel = stringResource(R.string.open_settings),
            onSecondaryAction = onOpenSettings,
        )

        StepStatus.PERMISSION_DENIED -> AccessCard(
            title = stringResource(R.string.permission_denied_title),
            body = stringResource(R.string.permission_denied_body),
            modifier = modifier,
            actionLabel = stringResource(R.string.open_settings),
            onAction = onOpenSettings,
        )

        StepStatus.ERROR -> AccessCard(
            title = stringResource(R.string.error_title),
            body = message ?: stringResource(R.string.error_title),
            modifier = modifier,
            actionLabel = stringResource(R.string.retry),
            onAction = onRefresh,
        )

        StepStatus.READY -> Unit
    }
}

@Composable
fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccessCard(
        title = stringResource(R.string.error_title),
        body = message,
        modifier = modifier,
        actionLabel = stringResource(R.string.retry),
        onAction = onRetry,
    )
}

@Composable
fun GoalDialog(
    currentGoal: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var input by rememberSaveable(currentGoal) { mutableStateOf(currentGoal.toString()) }
    val parsed = input.toIntOrNull()
    val valid = parsed != null && parsed in GoalStore.MIN_GOAL..GoalStore.MAX_GOAL
    val presets = listOf(5_000, 8_000, 10_000, 12_000)
    val decreaseLabel = stringResource(R.string.decrease)
    val increaseLabel = stringResource(R.string.increase)

    fun adjust(delta: Int) {
        val base = parsed ?: currentGoal
        input = (base + delta).coerceIn(GoalStore.MIN_GOAL, GoalStore.MAX_GOAL).toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(text = stringResource(R.string.goal_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.goal_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = parsed == preset,
                            onClick = { input = preset.toString() },
                            label = { Text("${preset / 1_000}k") },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { adjust(-500) },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = decreaseLabel },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { value ->
                            if (value.length <= 6 && value.all { it.isDigit() }) input = value
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.goal_input_label)) },
                        isError = input.isNotEmpty() && !valid,
                        supportingText = {
                            if (input.isNotEmpty() && !valid) {
                                Text(stringResource(R.string.goal_error_range))
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (valid) parsed?.let(onSave)
                            },
                        ),
                    )
                    OutlinedButton(
                        onClick = { adjust(500) },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = increaseLabel },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onSave) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
