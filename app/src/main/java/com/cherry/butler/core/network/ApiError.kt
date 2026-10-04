package com.cherry.butler.core.network

/**
 * The single typed error model every network call funnels into. The whole point
 * of Butler over the official app is that failures are *classified* — we know
 * which ones are worth retrying, which mean "show the paywall," and which are
 * genuinely fatal — instead of silently hanging.
 *
 * [retryable] is the source of truth the retry layer reads. It's derived from
 * the server's `x-error-retryable` header when present, else inferred from the
 * failure kind (network blips and 5xx/429 are retryable; 4xx generally isn't).
 */
sealed class ApiError(
    open val retryable: Boolean,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** No usable connectivity, DNS failure, socket/read timeout. Always worth a retry. */
    data class Network(
        val kind: Kind,
        override val cause: Throwable? = null,
    ) : ApiError(retryable = true, message = "Network error: $kind", cause = cause) {
        enum class Kind { NoConnectivity, Timeout, ConnectionReset, Unknown }
    }

    /** Auth token missing/expired (401). Triggers a single-flight refresh, then one replay. */
    data object Unauthorized : ApiError(retryable = false, message = "Unauthorized (401)")

    /** Forbidden (403) — e.g. Turnstile/permission. Not retryable without user action. */
    data object Forbidden : ApiError(retryable = false, message = "Forbidden (403)")

    /** 429. Honor Retry-After if the server sent one. Retryable, but backed off hard. */
    data class RateLimited(
        val retryAfterMillis: Long? = null,
    ) : ApiError(retryable = true, message = "Rate limited (429)")

    /** 5xx. Upstream is unhappy; retry with backoff. */
    data class Server(
        val code: Int,
        val upstreamStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : ApiError(retryable = true, message = "Server error ($code)", cause = cause)

    /**
     * A classified 4xx with a Janitor error code (see [JanitorErrorCode]). Most are
     * terminal and drive UI (paywall, empty wallet). Retryability comes from the
     * server's `x-error-retryable` header, defaulting to false.
     */
    data class Api(
        val code: Int,
        val janitorCode: String?,
        override val retryable: Boolean = false,
        val serverMessage: String? = null,
    ) : ApiError(retryable = retryable, message = "API error ($code${janitorCode?.let { ": $it" } ?: ""})")

    /** Response body couldn't be parsed into the expected shape. Never retried. */
    data class Serialization(
        override val cause: Throwable? = null,
    ) : ApiError(retryable = false, message = "Failed to parse response", cause = cause)

    /** The caller's coroutine was cancelled (screen left, generation stopped). Not an error to surface. */
    data object Cancelled : ApiError(retryable = false, message = "Cancelled")

    /** Anything we didn't classify. Conservatively not retried. */
    data class Unknown(
        override val cause: Throwable? = null,
    ) : ApiError(retryable = false, message = "Unknown error", cause = cause)
}

/**
 * Janitor backend error codes seen in response bodies / the `x-error-code` header.
 * These drive concrete UI decisions rather than generic error toasts.
 */
object JanitorErrorCode {
    const val ENHANCEMENT_CREDITS_EXHAUSTED = "ENHANCEMENT_CREDITS_EXHAUSTED" // 402 → paywall
    const val ROUTER_INSUFFICIENT_BALANCE = "JANITOR_ROUTER_INSUFFICIENT_BALANCE"
    const val ROUTER_DISABLED = "JANITOR_ROUTER_DISABLED"
    const val ROUTER_MODEL_UNAVAILABLE = "JANITOR_ROUTER_MODEL_UNAVAILABLE"
    const val ROUTER_CONFIG_UNAVAILABLE = "JANITOR_ROUTER_CONFIG_UNAVAILABLE"

    /** Codes that mean "stop and show the user something," never silently retry. */
    val terminalBillingCodes = setOf(
        ENHANCEMENT_CREDITS_EXHAUSTED,
        ROUTER_INSUFFICIENT_BALANCE,
    )
}
