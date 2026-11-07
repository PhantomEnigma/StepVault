package com.stepvault.app.data

import com.stepvault.app.data.local.StepDao
import com.stepvault.app.data.local.toDaySteps
import com.stepvault.app.data.local.toEntity
import com.stepvault.app.data.local.toHourlySteps
import com.stepvault.app.domain.DaySteps
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

enum class RefreshMode {
    FULL,
    TODAY,
}

class StepRepository(
    private val dao: StepDao,
    private val goalStore: GoalDataSource,
    private val providerClient: StepProviderDataSource,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val refreshMutex = Mutex()
    private var lastSnapshot: StepSnapshot? = null
    private var lastSnapshotMode: RefreshMode? = null
    private var lastRefreshAt: Long = 0L
    private var lastFullRefreshAt: Long = 0L
    private var lastPruneEpochDay: Long = Long.MIN_VALUE

    suspend fun refresh(
        mode: RefreshMode = RefreshMode.FULL,
        force: Boolean = false,
    ): StepSnapshot = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val now = clock.millis()
            val cached = lastSnapshot
            if (!force && cached != null && isFresh(mode, now)) {
                return@withLock cached
            }

            val today = LocalDate.now(clock)
            val from = when (mode) {
                RefreshMode.FULL -> today.minusDays(WINDOW_DAYS - 1)
                RefreshMode.TODAY -> today
            }
            val goal = goalStore.goal.first()
            val startMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val result = try {
                providerClient.query(startMillis, endMillis)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                StepProviderClient.QueryResult.Error(
                    error.message ?: "Unable to read the step provider",
                )
            }

            val snapshot = when (result) {
                is StepProviderClient.QueryResult.Success -> successSnapshot(
                    samples = result.samples,
                    today = today,
                    from = from,
                    goal = goal,
                    mode = mode,
                    now = now,
                )

                StepProviderClient.QueryResult.PermissionRequired -> cachedSnapshot(
                    today = today,
                    from = from,
                    goal = goal,
                    status = StepStatus.PERMISSION_REQUIRED,
                    message = null,
                    mode = mode,
                )

                StepProviderClient.QueryResult.PermissionDenied -> cachedSnapshot(
                    today = today,
                    from = from,
                    goal = goal,
                    status = StepStatus.PERMISSION_DENIED,
                    message = "MIUI denied access to the step provider",
                    mode = mode,
                )

                StepProviderClient.QueryResult.ProviderUnavailable -> cachedSnapshot(
                    today = today,
                    from = from,
                    goal = goal,
                    status = StepStatus.PROVIDER_UNAVAILABLE,
                    message = "MIUI step provider not found",
                    mode = mode,
                )

                is StepProviderClient.QueryResult.Error -> cachedSnapshot(
                    today = today,
                    from = from,
                    goal = goal,
                    status = StepStatus.ERROR,
                    message = result.message,
                    mode = mode,
                )
            }

            lastSnapshot = snapshot
            lastSnapshotMode = mode
            lastRefreshAt = now
            if (mode == RefreshMode.FULL) {
                lastFullRefreshAt = now
            }
            snapshot
        }
    }

    suspend fun loadSnapshot(): StepSnapshot = withContext(Dispatchers.IO) {
        val today = LocalDate.now(clock)
        val from = today.minusDays(WINDOW_DAYS - 1)
        val goal = goalStore.goal.first()
        val status = when {
            !providerClient.providerAvailable() -> StepStatus.PROVIDER_UNAVAILABLE
            !providerClient.activityPermissionGranted() -> StepStatus.PERMISSION_REQUIRED
            else -> StepStatus.READY
        }
        cachedSnapshot(
            today = today,
            from = from,
            goal = goal,
            status = status,
            message = null,
            mode = RefreshMode.FULL,
        )
    }

    suspend fun setGoal(value: Int) {
        goalStore.setGoal(value)
    }

    private fun isFresh(mode: RefreshMode, now: Long): Boolean = when (mode) {
        RefreshMode.FULL -> lastSnapshotMode == RefreshMode.FULL &&
            lastFullRefreshAt > 0L && now - lastFullRefreshAt < SNAPSHOT_TTL_MS

        RefreshMode.TODAY -> lastRefreshAt > 0L && now - lastRefreshAt < SNAPSHOT_TTL_MS
    }

    private suspend fun successSnapshot(
        samples: List<StepSample>,
        today: LocalDate,
        from: LocalDate,
        goal: Int,
        mode: RefreshMode,
        now: Long,
    ): StepSnapshot {
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

        val rows = providerDays.filter { day ->
            day.steps > 0 || day.activeSeconds > 0
        }
        if (rows.isNotEmpty()) {
            dao.upsertAll(rows.map { it.toEntity(now) })
            for (day in rows) {
                val hourly = StepAggregator.aggregateHourly(samples, zone, day.date)
                dao.deleteHourlyForDay(day.date.toEpochDay())
                dao.upsertHourlyAll(
                    hourly.filter { it.steps > 0 }
                        .map { it.toEntity(day.date.toEpochDay(), now) },
                )
            }
        }

        if (mode == RefreshMode.FULL && lastPruneEpochDay != today.toEpochDay()) {
            val cacheCutoff = today.minusDays(CACHE_DAYS).toEpochDay()
            dao.deleteOlderThan(cacheCutoff)
            dao.deleteHourlyOlderThan(cacheCutoff)
            lastPruneEpochDay = today.toEpochDay()
        }

        val week = if (mode == RefreshMode.FULL) days else listOf(days.last())
        return StepSnapshot(
            today = days.last(),
            week = week,
            goal = goal,
            status = StepStatus.READY,
            lastSync = now,
            hoursByDay = loadHourly(from, today),
        )
    }

    private suspend fun cachedSnapshot(
        today: LocalDate,
        from: LocalDate,
        goal: Int,
        status: StepStatus,
        message: String?,
        mode: RefreshMode,
    ): StepSnapshot {
        val rows = dao.getRange(from.toEpochDay(), today.toEpochDay())
            .associateBy { it.epochDay }

        val week = if (mode == RefreshMode.FULL) {
            buildList {
                var date = from
                while (!date.isAfter(today)) {
                    add(rows[date.toEpochDay()]?.toDaySteps() ?: DaySteps.empty(date))
                    date = date.plusDays(1)
                }
            }
        } else {
            listOf(rows[today.toEpochDay()]?.toDaySteps() ?: DaySteps.empty(today))
        }

        return StepSnapshot(
            today = week.last(),
            week = week,
            goal = goal,
            status = status,
            message = message,
            lastSync = rows.values.maxOfOrNull { it.updatedAt },
            hoursByDay = loadHourly(from, today),
        )
    }

    private suspend fun loadHourly(
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, List<HourlySteps>> {
        val rows = dao.getHourlyRange(from.toEpochDay(), to.toEpochDay())
            .associateBy { it.epochDay to it.hour }
        val result = LinkedHashMap<LocalDate, List<HourlySteps>>()
        var date = from
        while (!date.isAfter(to)) {
            val epochDay = date.toEpochDay()
            result[date] = List(24) { hour ->
                rows[epochDay to hour]?.toHourlySteps() ?: HourlySteps.empty(hour)
            }
            date = date.plusDays(1)
        }
        return result
    }

    companion object {
        private const val WINDOW_DAYS = 7L
        private const val CACHE_DAYS = 60L
        private const val SNAPSHOT_TTL_MS = 20_000L
    }
}
