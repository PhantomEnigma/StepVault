package com.stepvault.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stepvault.app.StepVaultApp
import com.stepvault.app.data.StepRepository
import com.stepvault.app.domain.StepSnapshot
import com.stepvault.app.domain.StepStatus
import com.stepvault.app.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StepUiState(
    val loading: Boolean = true,
    val snapshot: StepSnapshot? = null,
    val error: String? = null,
    val showGoalDialog: Boolean = false,
)

class StepViewModel(
    private val repository: StepRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StepUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    fun setGoal(value: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.setGoal(value)
            load()
        }
    }

    fun showGoalDialog(show: Boolean) {
        _uiState.update { it.copy(showGoalDialog = show) }
    }

    private suspend fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        try {
            if (_uiState.value.snapshot == null) {
                val cached = repository.loadSnapshot()
                if (cached.lastSync != null) {
                    _uiState.update { it.copy(snapshot = cached) }
                }
            }
            val snapshot = repository.refresh()
            _uiState.update {
                it.copy(
                    loading = false,
                    snapshot = snapshot,
                    error = snapshot.message.takeIf { snapshot.status == StepStatus.ERROR },
                )
            }
            try {
                WidgetUpdater.updateAll(appContext, snapshot)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            _uiState.update {
                it.copy(
                    loading = false,
                    error = error.message ?: "Unable to read steps",
                )
            }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as StepVaultApp
                StepViewModel(app.container.repository, app)
            }
        }
    }
}
