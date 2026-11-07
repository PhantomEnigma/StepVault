package com.stepvault.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object HistoryAggregator {

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weeks(days: List<DaySteps>, goal: Int): List<WeekSummary> {
        if (days.isEmpty()) return emptyList()
        val sorted = days.sortedBy { it.date }
        val lastDate = sorted.last().date
        val grouped = LinkedHashMap<LocalDate, MutableList<DaySteps>>()

        for (day in sorted) {
            val start = weekStart(day.date)
            grouped.getOrPut(start) { mutableListOf() }.add(day)
        }

        return grouped.map { (start, weekDays) ->
            val end = minOf(start.plusDays(6), lastDate)
            val total = weekDays.sumOf { it.steps }
            WeekSummary(
                start = start,
                end = end,
                days = weekDays.toList(),
                totalSteps = total,
                averageSteps = if (weekDays.isEmpty()) 0 else total / weekDays.size,
                goalDays = weekDays.count { it.steps >= goal },
            )
        }
    }
}
