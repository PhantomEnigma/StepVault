package com.stepvault.app.data

import com.stepvault.app.data.local.HourlyStepEntity
import com.stepvault.app.data.local.StepDao
import com.stepvault.app.data.local.toDaySteps
import com.stepvault.app.data.local.toEntity
import com.stepvault.app.data.local.toHourlySteps
import com.stepvault.app.domain.DayHours
import com.stepvault.app.domain.DaySteps
import com.stepvault.app.domain.HistoryAggregator
import com.stepvault.app.domain.HistorySnapshot
import com.stepvault.app.domain.HourlySteps
import com.stepvault.app.domain.StepAggregator
import com.stepvault.app.domain.StepSample
import com.stepvault.app.domain.StepSnapshot
import com.stepvault.app.domain.StepStatus
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class StepRepository(
    private val dao: StepDao,
    private val goalStore: GoalDataSource,
    private val providerClient: StepProviderDataSource,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val refreshMutex = Mutex()
    private var lastToday: StepSnapshot? = null
    private var lastTodayAt: Long = 0L
    private var lastHistory: HistorySnapshot? = null
    private var lastHistoryAt: Long = 0L
    private var lastPruneEpochDay: Long = Long.MIN_VALUE

    suspend fun refreshToday(force: Boolean = false): StepSnapshot = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val now = clock.millis()
            val today = LocalDate.now(clock)
            val goal = goalStore.goal.first()
            val cached = lastToday
            if (
                !force &&
                cached != null &&
                cached.today.date == today &&
                isFresh(lastTodayAt, now, TODAY_TTL_MS)
            ) {
                if (cached.goal == goal) return@withLock cached
                val updated = cached.copy(goal = goal)
                lastToday = updated
                return@withLock updated
            }

            val snapshot = when (val result = query(today, today)) {
                is StepProviderClient.QueryResult.Success -> successToday(
                    samples = result.samples,
                    today = today,
                    goal = goal,
                    now = now,
                )

                StepProviderClient.QueryResult.PermissionRequired -> cachedToday(
                    today = today,
                    goal = goal,
                    status = StepStatus.PERMISSION_REQUIRED,
                    message = null,
                )

                StepProviderClient.QueryResult.PermissionDenied -> cachedToday(
                    today = today,
                    goal = goal,
                    status = StepStatus.PERMISSION_DENIED,
                    message = "MIUI denied access to the step provider",
                )

                StepProviderClient.QueryResult.ProviderUnavailable -> cachedToday(
                    today = today,
                    goal = goal,
                    status = StepStatus.PROVIDER_UNAVAILABLE,
                    message = "MIUI step provider not found",
                )

                is StepProviderClient.QueryResult.Error -> cachedToday(
                    today = today,
                    goal = goal,
                    status = StepStatus.ERROR,
                    message = result.message,
                )
            }

            lastToday = snapshot
            lastTodayAt = now
            snapshot
        }
    }

    suspend fun loadToday(): StepSnapshot = withContext(Dispatchers.IO) {
        val today = LocalDate.now(clock)
        cachedToday(
            today = today,
            goal = goalStore.goal.first(),
            status = providerStatus(),
            message = null,
        )
    }

    suspend fun refreshHistory(force: Boolean = false): HistorySnapshot = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val now = clock.millis()
            val today = LocalDate.now(clock)
            val goal = goalStore.goal.first()
            val cached = lastHistory
            if (
                !force &&
                cached != null &&
                cached.days.lastOrNull()?.date == today &&
                isFresh(lastHistoryAt, now, HISTORY_TTL_MS)
            ) {
                if (cached.goal == goal) return@withLock cached
                val updated = cached.copy(
                    goal = goal,
                    weeks = HistoryAggregator.weeks(cached.days, goal),
                )
                lastHistory = updated
                return@withLock updated
            }

            val from = today.minusDays(HISTORY_DAYS - 1)
            val snapshot = when (val result = query(from, today)) {
                is StepProviderClient.QueryResult.Success -> successHistory(
                    samples = result.samples,
                    today = today,
                    goal = goal,
                    now = now,
                )

                StepProviderClient.QueryResult.PermissionRequired -> cachedHistory(
                    today = today,
                    goal = goal,
                    status = StepStatus.PERMISSION_REQUIRED,
                    message = null,
                )

                StepProviderClient.QueryResult.PermissionDenied -> cachedHistory(
                    today = today,
                    goal = goal,
                    status = StepStatus.PERMISSION_DENIED,
                    message = "MIUI denied access to the step provider",
                )

                StepProviderClient.QueryResult.ProviderUnavailable -> cachedHistory(
                    today = today,
                    goal = goal,
                    status = StepStatus.PROVIDER_UNAVAILABLE,
                    message = "MIUI step provider not found",
                )

                is StepProviderClient.QueryResult.Error -> cachedHistory(
                    today = today,
                    goal = goal,
                    status = StepStatus.ERROR,
                    message = result.message,
                )
            }

            lastHistory = snapshot
            lastHistoryAt = now
            snapshot
        }
    }

    suspend fun loadHistory(): HistorySnapshot = withContext(Dispatchers.IO) {
        cachedHistory(
            today = LocalDate.now(clock),
            goal = goalStore.goal.first(),
            status = providerStatus(),
            message = null,
        )
    }

    suspend fun loadDayHours(date: LocalDate, force: Boolean = false): DayHours =
        withContext(Dispatchers.IO) {
            val cachedHours = loadHours(date)
            if (!force && cachedHours.any { it.steps > 0 }) {
                return@withContext DayHours(date, cachedHours, StepStatus.READY)
            }

            refreshMutex.withLock {
                val now = clock.millis()
                when (val result = query(date, date)) {
                    is StepProviderClient.QueryResult.Success -> {
                        val hourly = StepAggregator.aggregateHourly(result.samples, zone, date)
                        if (hourly.any { it.steps > 0 }) {
                            val rows = hourly.filter { it.steps > 0 }
                                .map { it.toEntity(date.toEpochDay(), now) }
                            dao.deleteHourlyForDay(date.toEpochDay())
                            dao.upsertHourlyAll(rows)
                            DayHours(date, hourly, StepStatus.READY)
                        } else {
                            DayHours(date, cachedHours, StepStatus.READY)
                        }
                    }

                    StepProviderClient.QueryResult.PermissionRequired -> DayHours(
                        date,
                        cachedHours,
                        StepStatus.PERMISSION_REQUIRED,
                    )

                    StepProviderClient.QueryResult.PermissionDenied -> DayHours(
                        date,
                        cachedHours,
                        StepStatus.PERMISSION_DENIED,
                        "MIUI denied access to the step provider",
                    )

                    StepProviderClient.QueryResult.ProviderUnavailable -> DayHours(
                        date,
                        cachedHours,
                        StepStatus.PROVIDER_UNAVAILABLE,
                        "MIUI step provider not found",
                    )

                    is StepProviderClient.QueryResult.Error -> DayHours(
                        date,
                        cachedHours,
                        StepStatus.ERROR,
                        result.message,
                    )
                }
            }
        }

    suspend fun setGoal(value: Int) {
        goalStore.setGoal(value)
    }

    private fun query(from: LocalDate, to: LocalDate): StepProviderClient.QueryResult {
        val startMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return try {
            providerClient.query(startMillis, endMillis)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            StepProviderClient.QueryResult.Error(
                error.message ?: "Unable to read the step provider",
            )
        }
    }

    private fun isFresh(lastRefreshAt: Long, now: Long, ttl: Long): Boolean =
        lastRefreshAt > 0L && now - lastRefreshAt < ttl

    private fun providerStatus(): StepStatus = when {
        !providerClient.providerAvailable() -> StepStatus.PROVIDER_UNAVAILABLE
        !providerClient.activityPermissionGranted() -> StepStatus.PERMISSION_REQUIRED
        else -> StepStatus.READY
    }

    private suspend fun successToday(
        samples: List<StepSample>,
        today: LocalDate,
        goal: Int,
        now: Long,
    ): StepSnapshot {
        val providerDay = StepAggregator.aggregate(samples, zone, today, today).single()
        val cachedRows = dao.getRange(today.toEpochDay(), today.toEpochDay())
        val hasProviderData = providerDay.steps > 0 || providerDay.activeSeconds > 0
        val day = if (hasProviderData) {
            providerDay
        } else {
            cachedRows.firstOrNull()?.toDaySteps() ?: providerDay
        }

        if (hasProviderData) {
            dao.upsertAll(listOf(day.toEntity(now)))
            val hourly = StepAggregator.aggregateHourly(samples, zone, today)
            dao.deleteHourlyForDay(today.toEpochDay())
            dao.upsertHourlyAll(
                hourly.filter { it.steps > 0 }
                    .map { it.toEntity(today.toEpochDay(), now) },
            )
        }

        val lastSync = dao.getRange(today.toEpochDay(), today.toEpochDay())
            .maxOfOrNull { it.updatedAt }
        return StepSnapshot(
            today = day,
            goal = goal,
            status = StepStatus.READY,
            lastSync = lastSync,
            hours = loadHours(today),
        )
    }

    private suspend fun successHistory(
        samples: List<StepSample>,
        today: LocalDate,
        goal: Int,
        now: Long,
    ): HistorySnapshot {
        val from = today.minusDays(HISTORY_DAYS - 1)
        val providerDays = StepAggregator.aggregate(samples, zone, from, today)
        val cached = dao.getRange(from.toEpochDay(), today.toEpochDay())
            .associateBy { it.epochDay }

        val days = providerDays.map { day ->
            val hasProviderData = day.steps > 0 || day.activeSeconds > 0
            if (hasProviderData) {
                day
            } else {
                cached[day.date.toEpochDay()]?.toDaySteps() ?: day
            }
        }

        val rows = providerDays.filter { it.steps > 0 || it.activeSeconds > 0 }
        if (rows.isNotEmpty()) {
            dao.upsertAll(rows.map { it.toEntity(now) })
        }

        if (lastPruneEpochDay != today.toEpochDay()) {
            val cacheCutoff = today.minusDays(CACHE_DAYS).toEpochDay()
            dao.deleteOlderThan(cacheCutoff)
            dao.deleteHourlyOlderThan(cacheCutoff)
            lastPruneEpochDay = today.toEpochDay()
        }

        val lastSync = dao.getRange(from.toEpochDay(), today.toEpochDay())
            .maxOfOrNull { it.updatedAt }
        return HistorySnapshot(
            days = days,
            weeks = HistoryAggregator.weeks(days, goal),
            goal = goal,
            status = StepStatus.READY,
            lastSync = lastSync,
        )
    }

    private suspend fun cachedToday(
        today: LocalDate,
        goal: Int,
        status: StepStatus,
        message: String?,
    ): StepSnapshot {
        val row = dao.getRange(today.toEpochDay(), today.toEpochDay()).firstOrNull()
        return StepSnapshot(
            today = row?.toDaySteps() ?: DaySteps.empty(today),
            goal = goal,
            status = status,
            message = message,
            lastSync = row?.updatedAt,
            hours = loadHours(today),
        )
    }

    private suspend fun cachedHistory(
        today: LocalDate,
        goal: Int,
        status: StepStatus,
        message: String?,
    ): HistorySnapshot {
        val from = today.minusDays(HISTORY_DAYS - 1)
        val rows = dao.getRange(from.toEpochDay(), today.toEpochDay())
            .associateBy { it.epochDay }

        val days = buildList {
            var date = from
            while (!date.isAfter(today)) {
                add(rows[date.toEpochDay()]?.toDaySteps() ?: DaySteps.empty(date))
                date = date.plusDays(1)
            }
        }

        return HistorySnapshot(
            days = days,
            weeks = HistoryAggregator.weeks(days, goal),
            goal = goal,
            status = status,
            message = message,
            lastSync = rows.values.maxOfOrNull { it.updatedAt },
        )
    }

    private suspend fun loadHours(date: LocalDate): List<HourlySteps> {
        val rows = dao.getHourlyRange(date.toEpochDay(), date.toEpochDay())
        return zeroFillHourly(rows)
    }

    private fun zeroFillHourly(rows: List<HourlyStepEntity>): List<HourlySteps> {
        val byHour = rows.associateBy { it.hour }
        return List(24) { hour ->
            byHour[hour]?.toHourlySteps() ?: HourlySteps.empty(hour)
        }
    }

    companion object {
        const val HISTORY_DAYS = 60L
        private const val CACHE_DAYS = 60L
        private const val TODAY_TTL_MS = 20_000L
        private const val HISTORY_TTL_MS = 300_000L
    }
}
