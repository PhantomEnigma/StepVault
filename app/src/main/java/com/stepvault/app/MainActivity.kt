package com.stepvault.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stepvault.app.ui.StepScreen
import com.stepvault.app.ui.StepViewModel
import com.stepvault.app.ui.theme.StepVaultTheme
import com.stepvault.app.widget.WidgetRenderer

class MainActivity : ComponentActivity() {
    private val goalDialogRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureGoalRequest(intent)

        setContent {
            StepVaultTheme {
                val stepViewModel: StepViewModel = viewModel(
                    factory = StepViewModel.factory(application),
                )
                val request = goalDialogRequest.intValue
                LaunchedEffect(request) {
                    if (request > 0) stepViewModel.showGoalDialog(true)
                }
                StepScreen(viewModel = stepViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureGoalRequest(intent)
    }

    private fun captureGoalRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(WidgetRenderer.EXTRA_OPEN_GOAL, false) == true) {
            goalDialogRequest.intValue++
            intent.removeExtra(WidgetRenderer.EXTRA_OPEN_GOAL)
        }
    }
}
