package com.stepvault.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stepvault.app.domain.DaySteps
import java.time.LocalDate

@Entity(tableName = "daily_steps")
data class DailyStepEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Int,
    val distanceMeters: Double,
    val calories: Double,
    val activeSeconds: Long,
    val updatedAt: Long,
)

fun DaySteps.toEntity(updatedAt: Long): DailyStepEntity = DailyStepEntity(
    epochDay = date.toEpochDay(),
    steps = steps,
    distanceMeters = distanceMeters,
    calories = calories,
    activeSeconds = activeSeconds,
    updatedAt = updatedAt,
)

fun DailyStepEntity.toDaySteps(): DaySteps = DaySteps(
    date = LocalDate.ofEpochDay(epochDay),
    steps = steps,
    distanceMeters = distanceMeters,
    calories = calories,
    activeSeconds = activeSeconds,
)
