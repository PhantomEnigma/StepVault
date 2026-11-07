package com.stepvault.app.ui.history

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stepvault.app.StepVaultApp
import com.stepvault.app.data.StepRepository
import com.stepvault.app.domain.HistorySnapshot
import com.stepvault.app.domain.StepStatus
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: StepRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var hoursJob: Job? = null

    fun onVisible() {
        load(force = false)
    }

    fun refresh(force: Boolean = true) {
        load(force = force)
    }

    fun selectWeek(start: LocalDate) {
        _uiState.update { it.copy(selectedWeekStart = start) }
    }

    fun selectDay(date: LocalDate, force: Boolean = false) {
        hoursJob?.cancel()
        _uiState.update {
            it.copy(
                selectedDate = date,
                selectedHours = if (it.selectedDate == date) {
                    it.selectedHours
                } else {
                    emptyList()
                },
                hoursLoading = true,
                hoursStatus = StepStatus.READY,
                hoursMessage = null,
                showDaySheet = true,
            )
        }
        hoursJob = viewModelScope.launch {
            try {
                val day = repository.loadDayHours(date, force = force)
                _uiState.update {
                    it.copy(
                        selectedHours = day.hours,
                        hoursLoading = false,
                        hoursStatus = day.status,
                        hoursMessage = day.message,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        hoursLoading = false,
                        hoursStatus = StepStatus.ERROR,
                        hoursMessage = error.message ?: "Unable to read hours",
                    )
                }
            }
        }
    }

    fun retryHours() {
        _uiState.value.selectedDate?.let { selectDay(it, force = true) }
    }

    fun dismissDay() {
        _uiState.update { it.copy(showDaySheet = false) }
    }

    fun onGoalChanged() {
        viewModelScope.launch {
            val cached = repository.loadHistory()
            applySnapshot(cached)
        }
    }

    private fun load(force: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = it.snapshot == null,
                    refreshing = it.snapshot != null,
                    error = null,
                )
            }
            try {
                val cached = repository.loadHistory()
                applySnapshot(cached)

                val snapshot = repository.refreshHistory(force = force)
                applySnapshot(snapshot)
                _uiState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = snapshot.message.takeIf {
                            snapshot.status == StepStatus.ERROR
                        },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = error.message ?: "Unable to read history",
                    )
                }
            }
        }
    }

    private fun applySnapshot(snapshot: HistorySnapshot) {
        _uiState.update { state ->
            val selectedWeek = state.selectedWeekStart
                ?.takeIf { start -> snapshot.weeks.any { it.start == start } }
                ?: snapshot.weeks.lastOrNull()?.start
            state.copy(
                snapshot = snapshot,
                selectedWeekStart = selectedWeek,
            )
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as StepVaultApp
                HistoryViewModel(app.container.repository)
            }
        }
    }
}
