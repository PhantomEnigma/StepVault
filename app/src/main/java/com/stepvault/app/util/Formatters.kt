package com.stepvault.app.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Formatters {
    fun steps(value: Int): String = NumberFormat.getIntegerInstance().format(value)

    fun distanceKm(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

    fun calories(value: Double): String = String.format(Locale.getDefault(), "%.0f", value)

    fun percent(value: Int): String = "$value%"

    fun weekday(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    fun shortDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))

    fun time(timestamp: Long?): String? = timestamp?.let {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(it))
    }

    fun activeTime(minutes: Long): String = when {
        minutes >= 60L -> "${minutes / 60L}h ${minutes % 60L}m"
        else -> "$minutes min"
    }

    fun hourRange(hour: Int): String = String.format(
        Locale.getDefault(),
        "%02d:00–%02d:00",
        hour.coerceIn(0, 23),
        (hour + 1) % 24,
    )
}
