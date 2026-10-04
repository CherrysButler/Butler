package com.cherry.butler.core.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Janitor emits three timestamp dialects (docs/JANITOR_API.md §4.1, §17.2):
 *
 * - `2026-09-23T01:40:11.265Z`             — messages, chats
 * - `2023-06-04T00:55:17.988502+00:00`     — characters
 * - `2023-06-04T00:55:17.988502`           — `first_published_at`, no zone at all
 *
 * All three normalise to epoch millis on ingest so nothing downstream parses dates.
 */
object IsoTime {

    fun parseMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(value).toEpochMilli() }
            // No zone suffix: the server is UTC, so read it as such rather than local time.
            .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrNull()
    }

    /** The `Z` dialect, which is what the official client sends on bot messages. */
    fun format(millis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
}
