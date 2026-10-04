package com.cherry.butler.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.BufferedSource

/**
 * A minimal Server-Sent Events reader over an OkHttp body.
 *
 * Follows the spec's line grammar: `field: value` lines accumulate, a blank line
 * dispatches, `:` lines are comments, and multiple `data:` lines join with `\n`. Nothing
 * OpenAI-specific lives here — the caller decides what a `data` payload means, including
 * the `[DONE]` sentinel OpenAI-style streams use.
 */
object SseReader {

    data class Event(val event: String?, val data: String, val id: String?)

    fun events(source: BufferedSource): Flow<Event> = flow {
        var event: String? = null
        var id: String? = null
        val data = StringBuilder()
        var hasData = false

        fun reset() {
            event = null; id = null; data.setLength(0); hasData = false
        }

        while (true) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> {
                    if (hasData) emit(Event(event, data.toString(), id))
                    reset()
                }
                line.startsWith(":") -> Unit
                else -> {
                    val colon = line.indexOf(':')
                    val field = if (colon < 0) line else line.substring(0, colon)
                    var value = if (colon < 0) "" else line.substring(colon + 1)
                    if (value.startsWith(" ")) value = value.substring(1)
                    when (field) {
                        "data" -> { if (hasData) data.append('\n'); data.append(value); hasData = true }
                        "event" -> event = value
                        "id" -> id = value
                        // "retry" and unknown fields are ignored on purpose.
                    }
                }
            }
        }
        // A stream that closes without a trailing blank line still dispatches its last event.
        if (hasData) emit(Event(event, data.toString(), id))
    }.flowOn(Dispatchers.IO)
}
