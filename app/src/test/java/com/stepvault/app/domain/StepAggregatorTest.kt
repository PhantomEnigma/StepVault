package com.stepvault.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class StepAggregatorTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 25)

    private fun sample(
        start: ZonedDateTime,
        durationSeconds: Long,
        steps: Int,
        mode: Int = 2,
    ) = StepSample(
        beginTime = start.toInstant().toEpochMilli(),
        endTime = start.plusSeconds(durationSeconds).toInstant().toEpochMilli(),
        mode = mode,
        steps = steps,
    )

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime =
        today.atTime(hour, minute).atZone(zone)

    @Test
    fun aggregate_groupsByLocalDayAndZeroFillsMissingDays() {
        val samples = listOf(
            sample(at(9), durationSeconds = 600, steps = 1_000),
            sample(at(18), durationSeconds = 600, steps = 500),
            sample(today.minusDays(2).atTime(9, 0).atZone(zone), 600, 700),
        )

        val result = StepAggregator.aggregate(
            samples = samples,
            zone = zone,
            from = today.minusDays(6),
            to = today,
        )

        assertEquals(7, result.size)
        assertEquals(0, result[0].steps)
        assertEquals(0, result[1].steps)
        assertEquals(0, result[2].steps)
        assertEquals(0, result[3].steps)
        assertEquals(700, result[4].steps)
        assertEquals(0, result[5].steps)
        assertEquals(1_500, result[6].steps)
        assertEquals(900.0, result[6].distanceMeters, 0.0001)
        assertEquals(60.0, result[6].calories, 0.0001)
        assertEquals(1_200L, result[6].activeSeconds)
    }

    @Test
    fun aggregate_ignoresSamplesOutsideRequestedWindow() {
        val samples = listOf(
            sample(today.minusDays(8).atTime(9, 0).atZone(zone), 600, 10_000),
            sample(today.plusDays(1).atTime(9, 0).atZone(zone), 600, 10_000),
        )

        val result = StepAggregator.aggregate(
            samples = samples,
            zone = zone,
            from = today.minusDays(6),
            to = today,
        )

        assertEquals(7, result.size)
        assertEquals(0, result.sumOf { it.steps })
    }

    @Test
    fun metrics_useSixTenthsOfAMeterPerStep() {
        val metrics = StepAggregator.metrics(
            sample(at(9), durationSeconds = 600, steps = 1_000),
        )

        assertEquals(600.0, metrics.distanceMeters, 0.0001)
        assertEquals(600L, metrics.activeSeconds)
    }

    @Test
    fun metrics_useSpeedBasedCalorieBands() {
        val slow = StepAggregator.metrics(sample(at(9), 1_000, 1_000))
        val moderate = StepAggregator.metrics(sample(at(10), 300, 1_000))
        val fast = StepAggregator.metrics(sample(at(11), 100, 1_000))
        val stationary = StepAggregator.metrics(sample(at(12), 0, 1_000))

        assertEquals(40.0, slow.calories, 0.0001)
        assertEquals(60.0, moderate.calories, 0.0001)
        assertEquals(100.0, fast.calories, 0.0001)
        assertEquals(40.0, stationary.calories, 0.0001)
    }

    @Test
    fun metrics_clampNegativeDurationToZero() {
        val metrics = StepAggregator.metrics(
            sample(at(9), durationSeconds = -600, steps = 1_000),
        )

        assertEquals(0L, metrics.activeSeconds)
        assertEquals(40.0, metrics.calories, 0.0001)
    }

    @Test
    fun aggregate_skipsZeroStepRows() {
        val result = StepAggregator.aggregate(
            samples = listOf(sample(at(9), 600, 0)),
            zone = zone,
            from = today,
            to = today,
        )

        assertEquals(0, result.single().steps)
        assertEquals(0L, result.single().activeSeconds)
    }

    @Test
    fun aggregateHourly_bucketsSamplesByBeginHourAndZeroFills() {
        val samples = listOf(
            sample(at(9), durationSeconds = 600, steps = 1_000),
            sample(at(9, 30), durationSeconds = 600, steps = 500),
            sample(at(18), durationSeconds = 600, steps = 700),
            sample(today.minusDays(1).atTime(9, 0).atZone(zone), 600, 10_000),
        )

        val result = StepAggregator.aggregateHourly(
            samples = samples,
            zone = zone,
            date = today,
        )

        assertEquals(24, result.size)
        assertEquals(1_500, result[9].steps)
        assertEquals(700, result[18].steps)
        assertEquals(0, result[0].steps)
        assertEquals(2_200, result.sumOf { it.steps })
        assertEquals(1_320.0, result.sumOf { it.distanceMeters }, 0.0001)
    }

    @Test
    fun progress_isClampedAtOneHundredPercent() {
        val snapshot = StepSnapshot(
            today = DaySteps(today, 12_000, 7_200.0, 480.0, 3_600L),
            week = emptyList(),
            goal = 8_000,
            status = StepStatus.READY,
        )

        assertEquals(1f, snapshot.progressFraction, 0.0001f)
        assertEquals(100, snapshot.progressPercent)
        assertEquals(0, snapshot.remainingSteps)
    }
}
