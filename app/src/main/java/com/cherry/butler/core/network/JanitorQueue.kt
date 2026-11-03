package com.cherry.butler.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether Butler is standing in Janitor's queue right now, and what Janitor last said the wait
 * was. At busy times Janitor answers with a "You're in line" page instead of its data (2026-11-03:
 * "Estimated wait: 1 minute", refreshed every 20 seconds); calls then wait their turn at that pace
 * (ApiCall) and the app shows a quiet banner (QueueBanner) until a call is let through.
 */
object JanitorQueue {
    data class Waiting(
        /** Janitor's own words for the wait, like "1 minute"; null when it didn't say. */
        val estimate: String?,
        /** When Butler joined the line. */
        val since: Long,
        /** When a queue page last came back: a line nobody is asking through anymore fades. */
        val lastSeen: Long,
    )

    private val _state = MutableStateFlow<Waiting?>(null)
    val state: StateFlow<Waiting?> = _state.asStateFlow()

    /** A queue page came back. */
    fun inLine(estimate: String?) {
        val now = System.currentTimeMillis()
        _state.value = _state.value?.copy(estimate = estimate ?: _state.value?.estimate, lastSeen = now)
            ?: Waiting(estimate, since = now, lastSeen = now)
    }

    /** A call got Janitor's own answer: Butler is through. */
    fun through() {
        if (_state.value != null) _state.value = null
    }

    /** How long a queue page counts for when nothing else comes back. */
    const val STALE_MS = 60_000L
}
