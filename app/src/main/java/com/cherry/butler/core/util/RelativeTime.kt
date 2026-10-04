package com.cherry.butler.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The compact timestamps a list row can afford: `now`, `4m`, `3h`, `Yesterday`, `Tue`,
 * `12 Sep`, `4/10/25`. Chosen so the column never wraps and never needs the word "ago".
 */
object RelativeTime {

    fun short(millis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val delta = now - millis
        if (delta < 60_000L) return "now"
        if (delta < 3_600_000L) return "${delta / 60_000L}m"
        if (delta < 86_400_000L) return "${delta / 3_600_000L}h"

        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            date == today.minusDays(1) -> "Yesterday"
            date.isAfter(today.minusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault()))
        }
    }

    /** "Apr 10, 2025" in the reader's locale: for a line that has room to say when. */
    fun fullDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

    fun isSameDay(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    fun dayLabel(millis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
            else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
        }
    }

    fun timeOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
}
