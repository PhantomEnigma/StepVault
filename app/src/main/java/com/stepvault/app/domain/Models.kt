package com.stepvault.app.domain

import java.time.LocalDate
import kotlin.math.roundToInt

data class StepSample(
    val beginTime: Long,
    val endTime: Long,
    val mode: Int,
    val steps: Int,
)

data class DaySteps(
    val date: LocalDate,
    val steps: Int,
    val distanceMeters: Double,
    val calories: Double,
    val activeSeconds: Long,
) {
    val distanceKm: Double get() = distanceMeters / 1_000.0
    val activeMinutes: Long get() = activeSeconds / 60

    companion object {
        fun empty(date: LocalDate): DaySteps = DaySteps(
            date = date,
            steps = 0,
            distanceMeters = 0.0,
            calories = 0.0,
            activeSeconds = 0L,
        )
    }
}

enum class StepStatus {
    READY,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    PROVIDER_UNAVAILABLE,
    ERROR,
}

data class StepSnapshot(
    val today: DaySteps,
    val week: List<DaySteps>,
    val goal: Int,
    val status: StepStatus,
    val message: String? = null,
    val lastSync: Long? = null,
) {
    val progressFraction: Float
        get() = (today.steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)

    val progressPercent: Int
        get() = (progressFraction * 100f).roundToInt()

    val remainingSteps: Int
        get() = (goal - today.steps).coerceAtLeast(0)
}
