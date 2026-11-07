package com.stepvault.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {

    private val date = LocalDate.of(2026, 10, 25)

    private fun snapshot(steps: Int, goal: Int = 8_000): StepSnapshot = StepSnapshot(
        today = DaySteps(date, steps, steps * 0.6, steps * 0.04, 0L),
        goal = goal,
        status = StepStatus.READY,
    )

    @Test
    fun progressPercent_truncatesBelowGoal() {
        assertEquals(0, snapshot(0).progressPercent)
        assertEquals(0, snapshot(1).progressPercent)
        assertEquals(99, snapshot(7_999).progressPercent)
        assertEquals(100, snapshot(8_000).progressPercent)
        assertEquals(100, snapshot(12_000).progressPercent)
    }

    @Test
    fun remainingSteps_isZeroAtOrAboveGoal() {
        assertEquals(8_000, snapshot(0).remainingSteps)
        assertEquals(1, snapshot(7_999).remainingSteps)
        assertEquals(0, snapshot(8_000).remainingSteps)
        assertEquals(0, snapshot(12_000).remainingSteps)
    }
}
