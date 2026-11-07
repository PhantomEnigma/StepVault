package com.stepvault.app.data

import com.stepvault.app.data.local.DailyStepEntity
import com.stepvault.app.data.local.HourlyStepEntity
import com.stepvault.app.data.local.StepDao
import com.stepvault.app.domain.StepSample
import com.stepvault.app.domain.StepStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepRepositoryTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 25)
    private val clock = MutableClock(
        Instant.parse("2026-10-25T12:00:00Z"),
        zone,
    )

    private val dao = FakeStepDao()
    private val goals = FakeGoalSource(8_000)
    private val provider = FakeProvider()

    private val repository = StepRepository(
        dao = dao,
        goalStore = goals,
        providerClient = provider,
        zone = zone,
        clock = clock,
    )

    @Test
    fun emptyProviderResult_preservesTodayAndHourlyCache() = runTest {
        seedDaily(today, steps = 5_000)
        seedHourly(today, hour = 9, steps = 400)
        provider.result = StepProviderClient.QueryResult.Success(emptyList())

        val snapshot = repository.refreshToday(force = true)

        assertEquals(StepStatus.READY, snapshot.status)
        assertEquals(5_000, snapshot.today.steps)
        assertEquals(400, snapshot.hours[9].steps)
        assertEquals(5_000, dailyRow(today).steps)
        assertEquals(1, dao.getHourlyRange(today.toEpochDay(), today.toEpochDay()).size)
    }

    @Test
    fun emptyProviderResult_preservesPriorDayCache() = runTest {
        val yesterday = today.minusDays(1)
        seedDaily(yesterday, steps = 4_200)
        provider.result = StepProviderClient.QueryResult.Success(emptyList())

        val snapshot = repository.refreshHistory(force = true)

        assertEquals(4_200, snapshot.days.first { it.date == yesterday }.steps)
        assertEquals(4_200, dailyRow(yesterday).steps)
    }

    @Test
    fun providerData_replacesToday() = runTest {
        seedDaily(today, steps = 5_000)
        provider.result = StepProviderClient.QueryResult.Success(
            listOf(sample(today, hour = 9, steps = 1_200)),
        )

        val snapshot = repository.refreshToday(force = true)

        assertEquals(1_200, snapshot.today.steps)
        assertEquals(1_200, dailyRow(today).steps)
        assertEquals(1, dao.getHourlyRange(today.toEpochDay(), today.toEpochDay()).size)
    }

    @Test
    fun todayRefresh_queriesOnlyToday() = runTest {
        provider.result = StepProviderClient.QueryResult.Success(emptyList())

        repository.refreshToday(force = true)

        assertEquals(startOf(today), provider.lastStart)
        assertEquals(startOf(today.plusDays(1)), provider.lastEnd)
    }

    @Test
    fun historyRefresh_queriesSixtyDayWindow() = runTest {
        provider.result = StepProviderClient.QueryResult.Success(emptyList())

        repository.refreshHistory(force = true)

        assertEquals(startOf(today.minusDays(59)), provider.lastStart)
        assertEquals(startOf(today.plusDays(1)), provider.lastEnd)
    }

    @Test
    fun todayRefresh_isCoalescedWithinTtl() = runTest {
        provider.result = StepProviderClient.QueryResult.Success(emptyList())

        repository.refreshToday(force = true)
        repository.refreshToday()
        assertEquals(1, provider.queries)

        clock.instant = clock.instant.plusSeconds(30)
        repository.refreshToday()
        assertEquals(2, provider.queries)
    }

    @Test
    fun historyRefresh_isCoalescedWithinFiveMinutes() = runTest {
        provider.result = StepProviderClient.QueryResult.Success(emptyList())

        repository.refreshHistory(force = true)
        repository.refreshHistory()
        assertEquals(1, provider.queries)

        clock.instant = clock.instant.plusSeconds(4 * 60)
        repository.refreshHistory()
        assertEquals(1, provider.queries)

        clock.instant = clock.instant.plusSeconds(2 * 60)
        repository.refreshHistory()
        assertEquals(2, provider.queries)
    }

    @Test
    fun todayAndHistory_haveIndependentFreshness() = runTest {
        provider.result = StepProviderClient.QueryResult.Success(emptyList())

        repository.refreshToday(force = true)
        repository.refreshHistory()
        assertEquals(2, provider.queries)
        assertEquals(startOf(today.minusDays(59)), provider.lastStart)
    }

    @Test
    fun permissionRequired_keepsCachedToday() = runTest {
        seedDaily(today, steps = 3_000)
        provider.result = StepProviderClient.QueryResult.PermissionRequired

        val snapshot = repository.refreshToday(force = true)

        assertEquals(StepStatus.PERMISSION_REQUIRED, snapshot.status)
        assertEquals(3_000, snapshot.today.steps)
        assertEquals(3_000, dailyRow(today).steps)
    }

    @Test
    fun emptyProviderResult_doesNotEraseCachedHourlyRows() = runTest {
        seedHourly(today, hour = 7, steps = 250)
        provider.result = StepProviderClient.QueryResult.Success(emptyList())

        repository.refreshToday(force = true)

        val rows = dao.getHourlyRange(today.toEpochDay(), today.toEpochDay())
        assertEquals(1, rows.size)
        assertEquals(250, rows.single().steps)
    }

    @Test
    fun loadDayHours_usesCacheWithoutProviderQuery() = runTest {
        seedHourly(today, hour = 7, steps = 250)

        val hours = repository.loadDayHours(today)

        assertEquals(0, provider.queries)
        assertEquals(24, hours.hours.size)
        assertEquals(250, hours.hours[7].steps)
    }

    @Test
    fun loadDayHours_queriesWhenCacheMissing() = runTest {
        provider.result = StepProviderClient.QueryResult.Success(
            listOf(sample(today, hour = 9, steps = 1_200)),
        )

        val hours = repository.loadDayHours(today)

        assertEquals(1, provider.queries)
        assertEquals(startOf(today), provider.lastStart)
        assertEquals(startOf(today.plusDays(1)), provider.lastEnd)
        assertEquals(1_200, hours.hours[9].steps)
        assertEquals(1, dao.getHourlyRange(today.toEpochDay(), today.toEpochDay()).size)
    }

    @Test
    fun loadDayHours_emptyProviderPreservesCache() = runTest {
        seedHourly(today, hour = 7, steps = 250)
        provider.result = StepProviderClient.QueryResult.Success(emptyList())

        val hours = repository.loadDayHours(today, force = true)

        assertEquals(250, hours.hours[7].steps)
        assertEquals(1, dao.getHourlyRange(today.toEpochDay(), today.toEpochDay()).size)
    }

    @Test
    fun historyRefresh_prunesRowsOlderThanCacheWindow() = runTest {
        seedDaily(today.minusDays(61), steps = 1_000)
        provider.result = StepProviderClient.QueryResult.Success(emptyList())

        repository.refreshHistory(force = true)

        assertNull(dailyRowOrNull(today.minusDays(61)))
    }

    @Test
    fun refreshHistory_cachedGoalChangeRebuildsWithoutQuery() = runTest {
        provider.result = StepProviderClient.QueryResult.Success(emptyList())
        repository.refreshHistory(force = true)
        assertEquals(1, provider.queries)

        goals.setGoal(10_000)
        val snapshot = repository.refreshHistory()

        assertEquals(1, provider.queries)
        assertEquals(10_000, snapshot.goal)
    }

    @Test
    fun loadHistory_usesUpdatedGoalWithoutProviderQuery() = runTest {
        goals.setGoal(10_000)

        val snapshot = repository.loadHistory()

        assertEquals(0, provider.queries)
        assertEquals(10_000, snapshot.goal)
    }

    private suspend fun seedDaily(date: LocalDate, steps: Int) {
        dao.upsertAll(
            listOf(
                DailyStepEntity(
                    epochDay = date.toEpochDay(),
                    steps = steps,
                    distanceMeters = steps * 0.6,
                    calories = steps * 0.04,
                    activeSeconds = 600L,
                    updatedAt = 1L,
                ),
            ),
        )
    }

    private suspend fun seedHourly(date: LocalDate, hour: Int, steps: Int) {
        dao.upsertHourlyAll(
            listOf(
                HourlyStepEntity(
                    epochDay = date.toEpochDay(),
                    hour = hour,
                    steps = steps,
                    distanceMeters = steps * 0.6,
                    calories = steps * 0.04,
                    activeSeconds = 600L,
                    updatedAt = 1L,
                ),
            ),
        )
    }

    private suspend fun dailyRow(date: LocalDate): DailyStepEntity =
        dao.getRange(date.toEpochDay(), date.toEpochDay()).single()

    private suspend fun dailyRowOrNull(date: LocalDate): DailyStepEntity? =
        dao.getRange(date.toEpochDay(), date.toEpochDay()).firstOrNull()

    private fun startOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun sample(date: LocalDate, hour: Int, steps: Int): StepSample {
        val begin = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        return StepSample(
            beginTime = begin,
            endTime = begin + 10 * 60 * 1_000L,
            mode = 2,
            steps = steps,
        )
    }

    private class MutableClock(
        var instant: Instant,
        private val zone: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
    }

    private class FakeGoalSource(initial: Int) : GoalDataSource {
        private val state = MutableStateFlow(initial)
        override val goal: Flow<Int> = state
        override suspend fun setGoal(value: Int) {
            state.value = value
        }
    }

    private class FakeProvider : StepProviderDataSource {
        var result: StepProviderClient.QueryResult =
            StepProviderClient.QueryResult.Success(emptyList())
        var queries = 0
        var lastStart = 0L
        var lastEnd = 0L

        override fun providerAvailable(): Boolean = true
        override fun activityPermissionGranted(): Boolean = true

        override fun query(startMillis: Long, endMillis: Long): StepProviderClient.QueryResult {
            queries++
            lastStart = startMillis
            lastEnd = endMillis
            return result
        }
    }

    private class FakeStepDao : StepDao {
        private val daily = sortedMapOf<Long, DailyStepEntity>()
        private val hourly = sortedMapOf<Pair<Long, Int>, HourlyStepEntity>()

        override suspend fun getRange(from: Long, to: Long): List<DailyStepEntity> =
            daily.values.filter { it.epochDay in from..to }

        override suspend fun upsertAll(rows: List<DailyStepEntity>) {
            rows.forEach { daily[it.epochDay] = it }
        }

        override suspend fun deleteOlderThan(before: Long) {
            daily.keys.filter { it < before }.toList().forEach { daily.remove(it) }
        }

        override suspend fun getHourlyRange(from: Long, to: Long): List<HourlyStepEntity> =
            hourly.values.filter { it.epochDay in from..to }

        override suspend fun upsertHourlyAll(rows: List<HourlyStepEntity>) {
            rows.forEach { hourly[it.epochDay to it.hour] = it }
        }

        override suspend fun deleteHourlyForDay(epochDay: Long) {
            hourly.keys.filter { it.first == epochDay }.toList().forEach { hourly.remove(it) }
        }

        override suspend fun deleteHourlyOlderThan(before: Long) {
            hourly.keys.filter { it.first < before }.toList().forEach { hourly.remove(it) }
        }
    }
}
