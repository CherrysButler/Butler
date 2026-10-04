package com.cherry.butler.core.network

import kotlin.math.pow
import kotlin.random.Random

/** The one retry-delay shape both retry layers (ApiCall, SendPipeline) use. */
object Backoff {
    /**
     * Doubling from [baseMs] per [attempt] (1-based), capped at [maxMs], then scaled by a
     * random factor in [jitterFloor, 1] so failures that happen together don't retry in step.
     */
    fun millis(attempt: Int, baseMs: Long, maxMs: Long, jitterFloor: Double): Long {
        val capped = (baseMs * 2.0.pow(attempt - 1)).coerceAtMost(maxMs.toDouble())
        return (capped * (jitterFloor + Random.nextDouble() * (1 - jitterFloor))).toLong()
    }
}
