package com.stepvault.app.data

import com.stepvault.app.data.local.StepDao
import com.stepvault.app.data.local.toDaySteps
import com.stepvault.app.data.local.toEntity
import com.stepvault.app.data.local.toHourlySteps
import com.stepvault.app.domain.DaySteps
import com.stepvault.app.domain.HourlySteps
import com.stepvault.app.domain.StepAggregator
import com.stepvault.app.domain.StepSnapshot
import com.stepvault.app.domain.StepStatus
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class StepRepository(
    private val dao: StepDao,
    private val goalStore: GoalStore,
    private val providerClient: StepProviderClient,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun refresh(): StepSnapshot = withContext(Dispatchers.IO) {
        val today = LocalDate.now(clock)
        val from = today.minusDays(WINDOW_DAYS - 1)
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

        when (result) {
            is StepProviderClient.QueryResult.Success -> {
                val now = clock.millis()
                val providerDays = StepAggregator.aggregate(result.samples, zone, from, today)
                val cached = dao.getRange(from.toEpochDay(), today.toEpochDay())
                    .associateBy { it.epochDay }

                val days = providerDays.map { day ->
                    val hasProviderData = day.steps > 0 || day.activeSeconds > 0
                    if (hasProviderData || day.date == today) {
                        day
                    } else {
                        cached[day.date.toEpochDay()]?.toDaySteps() ?: day
                    }
                }

                val rows = providerDays.filter { day ->
                    day.date == today || day.steps > 0 || day.activeSeconds > 0
                }
                dao.upsertAll(rows.map { it.toEntity(now) })

                val cacheCutoff = today.minusDays(CACHE_DAYS).toEpochDay()
                for (day in rows) {
                    val hourly = StepAggregator.aggregateHourly(result.samples, zone, day.date)
                    dao.deleteHourlyForDay(day.date.toEpochDay())
                    dao.upsertHourlyAll(
                        hourly.filter { it.steps > 0 }
                            .map { it.toEntity(day.date.toEpochDay(), now) },
                    )
                }
                dao.deleteOlderThan(cacheCutoff)
                dao.deleteHourlyOlderThan(cacheCutoff)

                StepSnapshot(
                    today = days.last(),
                    week = days,
                    goal = goal,
                    status = StepStatus.READY,
                    lastSync = now,
                    hoursByDay = loadHourly(from, today),
                )
            }

            StepProviderClient.QueryResult.PermissionRequired -> cachedSnapshot(
                today = today,
                from = from,
                goal = goal,
                status = StepStatus.PERMISSION_REQUIRED,
                message = null,
            )

            StepProviderClient.QueryResult.PermissionDenied -> cachedSnapshot(
                today = today,
                from = from,
                goal = goal,
                status = StepStatus.PERMISSION_DENIED,
                message = "MIUI denied access to the step provider",
            )

            StepProviderClient.QueryResult.ProviderUnavailable -> cachedSnapshot(
                today = today,
                from = from,
                goal = goal,
                status = StepStatus.PROVIDER_UNAVAILABLE,
                message = "MIUI step provider not found",
            )

            is StepProviderClient.QueryResult.Error -> cachedSnapshot(
                today = today,
                from = from,
                goal = goal,
                status = StepStatus.ERROR,
                message = result.message,
            )
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
        cachedSnapshot(today, from, goal, status, null)
    }

    suspend fun setGoal(value: Int) {
        goalStore.setGoal(value)
    }

    private suspend fun cachedSnapshot(
        today: LocalDate,
        from: LocalDate,
        goal: Int,
        status: StepStatus,
        message: String?,
    ): StepSnapshot {
        val rows = dao.getRange(from.toEpochDay(), today.toEpochDay())
            .associateBy { it.epochDay }

        val week = buildList {
            var date = from
            while (!date.isAfter(today)) {
                add(rows[date.toEpochDay()]?.toDaySteps() ?: DaySteps.empty(date))
                date = date.plusDays(1)
            }
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
    }
}
