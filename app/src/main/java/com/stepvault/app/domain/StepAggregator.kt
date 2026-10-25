package com.stepvault.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

object StepAggregator {
    const val DEFAULT_GOAL = 8_000
    const val METERS_PER_STEP = 0.6

    data class SampleMetrics(
        val distanceMeters: Double,
        val calories: Double,
        val activeSeconds: Long,
    )

    fun metrics(sample: StepSample): SampleMetrics {
        val steps = sample.steps.coerceAtLeast(0)
        val seconds = max(0L, (sample.endTime - sample.beginTime) / 1_000L)
        val distance = steps * METERS_PER_STEP
        val speedKmh = if (seconds > 0L) distance / seconds * 3.6 else 0.0
        val kcalPerStep = when {
            speedKmh > 10.0 -> 0.10
            speedKmh >= 5.0 -> 0.06
            else -> 0.04
        }
        return SampleMetrics(
            distanceMeters = distance,
            calories = steps * kcalPerStep,
            activeSeconds = seconds,
        )
    }

    fun aggregate(
        samples: List<StepSample>,
        zone: ZoneId,
        from: LocalDate,
        to: LocalDate,
    ): List<DaySteps> {
        val totals = HashMap<LocalDate, MutableTotals>()

        for (sample in samples) {
            if (sample.steps <= 0) continue
            val date = Instant.ofEpochMilli(sample.beginTime).atZone(zone).toLocalDate()
            if (date.isBefore(from) || date.isAfter(to)) continue

            val metrics = metrics(sample)
            val day = totals.getOrPut(date) { MutableTotals() }
            day.steps += sample.steps
            day.distanceMeters += metrics.distanceMeters
            day.calories += metrics.calories
            day.activeSeconds += metrics.activeSeconds
        }

        val days = ArrayList<DaySteps>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            val day = totals[cursor]
            days += if (day == null) {
                DaySteps.empty(cursor)
            } else {
                DaySteps(
                    date = cursor,
                    steps = day.steps,
                    distanceMeters = day.distanceMeters,
                    calories = day.calories,
                    activeSeconds = day.activeSeconds,
                )
            }
            cursor = cursor.plusDays(1)
        }
        return days
    }

    private class MutableTotals {
        var steps: Int = 0
        var distanceMeters: Double = 0.0
        var calories: Double = 0.0
        var activeSeconds: Long = 0L
    }
}
