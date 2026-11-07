package com.stepvault.app.domain

import java.time.LocalDate

data class WeekSummary(
    val start: LocalDate,
    val end: LocalDate,
    val days: List<DaySteps>,
    val totalSteps: Int,
    val averageSteps: Int,
    val goalDays: Int,
)

data class HistorySnapshot(
    val days: List<DaySteps>,
    val weeks: List<WeekSummary>,
    val goal: Int,
    val status: StepStatus,
    val message: String? = null,
    val lastSync: Long? = null,
) {
    val totalSteps: Int get() = days.sumOf { it.steps }

    val averageSteps: Int
        get() = if (days.isEmpty()) 0 else totalSteps / days.size

    val bestDay: DaySteps?
        get() = days.maxByOrNull { it.steps }
}

data class DayHours(
    val date: LocalDate,
    val hours: List<HourlySteps>,
    val status: StepStatus,
    val message: String? = null,
)
