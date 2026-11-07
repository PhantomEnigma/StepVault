package com.stepvault.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryAggregatorTest {

    private fun day(date: LocalDate, steps: Int): DaySteps = DaySteps(
        date = date,
        steps = steps,
        distanceMeters = steps * 0.6,
        calories = steps * 0.04,
        activeSeconds = 0L,
    )

    @Test
    fun weekStart_isMondayForEveryDayOfWeek() {
        val sunday = LocalDate.of(2026, 10, 25)
        val monday = LocalDate.of(2026, 10, 19)

        assertEquals(monday, HistoryAggregator.weekStart(sunday))
        assertEquals(monday, HistoryAggregator.weekStart(monday))
        assertEquals(monday, HistoryAggregator.weekStart(monday.plusDays(3)))
    }

    @Test
    fun weeks_groupIsoWeeksAndSummarize() {
        val start = LocalDate.of(2026, 10, 12)
        val days = (0 until 14).map { offset ->
            val date = start.plusDays(offset.toLong())
            val steps = when (date) {
                LocalDate.of(2026, 10, 12) -> 1_000
                LocalDate.of(2026, 10, 19) -> 2_000
                LocalDate.of(2026, 10, 20) -> 8_000
                LocalDate.of(2026, 10, 25) -> 9_000
                else -> 0
            }
            day(date, steps)
        }

        val weeks = HistoryAggregator.weeks(days, goal = 8_000)

        assertEquals(2, weeks.size)
        assertEquals(1_000, weeks[0].totalSteps)
        assertEquals(142, weeks[0].averageSteps)
        assertEquals(0, weeks[0].goalDays)
        assertEquals(LocalDate.of(2026, 10, 12), weeks[0].start)
        assertEquals(LocalDate.of(2026, 10, 18), weeks[0].end)

        assertEquals(19_000, weeks[1].totalSteps)
        assertEquals(2_714, weeks[1].averageSteps)
        assertEquals(2, weeks[1].goalDays)
        assertEquals(LocalDate.of(2026, 10, 19), weeks[1].start)
        assertEquals(LocalDate.of(2026, 10, 25), weeks[1].end)
    }

    @Test
    fun currentWeekEndsTodayAndIsNotZeroFilledIntoTheFuture() {
        val days = listOf(
            day(LocalDate.of(2026, 10, 19), 2_000),
            day(LocalDate.of(2026, 10, 20), 4_000),
            day(LocalDate.of(2026, 10, 21), 6_000),
        )

        val weeks = HistoryAggregator.weeks(days, goal = 8_000)

        assertEquals(1, weeks.size)
        assertEquals(LocalDate.of(2026, 10, 21), weeks.single().end)
        assertEquals(3, weeks.single().days.size)
        assertEquals(4_000, weeks.single().averageSteps)
    }

    @Test
    fun weeks_spanMonthAndYearBoundaries() {
        val days = listOf(
            day(LocalDate.of(2026, 12, 28), 1_000),
            day(LocalDate.of(2026, 12, 31), 2_000),
            day(LocalDate.of(2027, 1, 1), 3_000),
            day(LocalDate.of(2027, 1, 3), 4_000),
        )

        val weeks = HistoryAggregator.weeks(days, goal = 8_000)

        assertEquals(1, weeks.size)
        assertEquals(LocalDate.of(2026, 12, 28), weeks.single().start)
        assertEquals(LocalDate.of(2027, 1, 3), weeks.single().end)
        assertEquals(10_000, weeks.single().totalSteps)
    }

    @Test
    fun goalDays_countDaysAtOrAboveGoal() {
        val days = listOf(
            day(LocalDate.of(2026, 10, 19), 7_999),
            day(LocalDate.of(2026, 10, 20), 8_000),
            day(LocalDate.of(2026, 10, 21), 9_000),
        )

        val weeks = HistoryAggregator.weeks(days, goal = 8_000)

        assertEquals(2, weeks.single().goalDays)
    }

    @Test
    fun historySnapshot_computesTotalAverageAndBestDay() {
        val days = listOf(
            day(LocalDate.of(2026, 10, 19), 1_000),
            day(LocalDate.of(2026, 10, 20), 3_000),
        )
        val snapshot = HistorySnapshot(
            days = days,
            weeks = HistoryAggregator.weeks(days, 8_000),
            goal = 8_000,
            status = StepStatus.READY,
        )

        assertEquals(4_000, snapshot.totalSteps)
        assertEquals(2_000, snapshot.averageSteps)
        assertEquals(LocalDate.of(2026, 10, 20), snapshot.bestDay?.date)
    }

    @Test
    fun weeks_emptyDaysReturnsEmpty() {
        assertEquals(emptyList<WeekSummary>(), HistoryAggregator.weeks(emptyList(), 8_000))
    }
}
