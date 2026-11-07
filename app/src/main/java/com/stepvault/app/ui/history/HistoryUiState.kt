package com.stepvault.app.ui.history

import com.stepvault.app.domain.HistorySnapshot
import com.stepvault.app.domain.HourlySteps
import com.stepvault.app.domain.StepStatus
import java.time.LocalDate

data class HistoryUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val snapshot: HistorySnapshot? = null,
    val selectedWeekStart: LocalDate? = null,
    val selectedDate: LocalDate? = null,
    val selectedHours: List<HourlySteps> = emptyList(),
    val hoursLoading: Boolean = false,
    val hoursStatus: StepStatus = StepStatus.READY,
    val hoursMessage: String? = null,
    val showDaySheet: Boolean = false,
    val error: String? = null,
)
