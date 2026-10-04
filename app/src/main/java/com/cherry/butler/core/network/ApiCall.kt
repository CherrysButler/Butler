package com.cherry.butler.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The single funnel every Janitor HTTP call goes through.
 *
 * Butler exists because the official client hangs silently on failure, so nothing here
 * is allowed to fail anonymously: every outcome becomes either a value or a typed
 * [ApiError] carrying whether a retry is worth attempting.
 */
class ApiCall(
    private val client: OkHttpClient,
    private val json: Json,
) {

    /**
     * Executes [request] and decodes the body with [decode].
     *
     * Retries only errors the server or the failure kind marks retryable, with
     * exponential backoff plus jitter. Jitter matters: without it, a burst of list
     * requests failing together would retry in lockstep and hammer the server in waves.
     */
    suspend fun <T> execute(
        request: Request,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        decode: (String) -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                val body = executeOnce(request)
                return try {
                    decode(body)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // A body we can't parse is never worth retrying — the same bytes
                    // would come back. Surface it so the shape mismatch gets fixed.
                    throw ApiError.Serialization(e)
                }
            } catch (e: ApiError) {
                attempt++
                if (!e.retryable || attempt >= maxAttempts) throw e
                delay(backoffMillis(attempt, (e as? ApiError.RateLimited)?.retryAfterMillis))
            }
        }
    }

    private suspend fun executeOnce(request: Request): String = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).await()
        } catch (e: CancellationException) {
            throw ApiError.Cancelled
        } catch (e: IOException) {
            com.cherry.butler.core.diagnostics.Diagnostics.record("net", "${e.javaClass.simpleName} ${request.method} ${request.url.encodedPath}")
            throw ApiError.Network(e.toNetworkKind(), e)
        }

        response.use { r ->
            val body = r.body?.string().orEmpty()
            if (r.isSuccessful) return@withContext body
            throw r.toApiError(body)
        }
    }

    /**
     * Maps a failed response to [ApiError].
     *
     * Retryability comes from the server's `x-error-retryable` header when present —
     * it is the only party that actually knows — and falls back to the status class.
     */
    /** Classifies a failed response for callers that stream the body themselves. */
    fun errorFor(response: Response, body: String): ApiError = response.toApiError(body)

    /** The network-failure mapping, for callers that run their own OkHttp call. */
    fun networkError(e: IOException): ApiError = ApiError.Network(e.toNetworkKind(), e)

    private fun Response.toApiError(body: String): ApiError {
        val serverRetryable = header("x-error-retryable")?.equals("true", ignoreCase = true)
        val janitorCode = header("x-error-code") ?: body.extractJanitorCode()
        val upstream = header("x-upstream-status")?.toIntOrNull()

        if (code == 401) {
            // Whether a token went out at all, never the token itself.
            android.util.Log.w("ApiCall", "401 on ${request.method} ${request.url.encodedPath} auth=${request.header("Authorization") != null}")
        }
        // Status, method and path for the diagnostics report; never the body.
        com.cherry.butler.core.diagnostics.Diagnostics.record("http", "$code ${request.method} ${request.url.encodedPath}" + (janitorCode?.let { " $it" } ?: ""))
        return when (code) {
            401 -> ApiError.Unauthorized
            403 -> ApiError.Forbidden
            429 -> ApiError.RateLimited(retryAfterMillis = parseRetryAfter())
            in 500..599 -> ApiError.Server(code, upstream)
            else -> ApiError.Api(
                code = code,
                janitorCode = janitorCode,
                retryable = serverRetryable ?: false,
                serverMessage = body.extractMessage(),
            )
        }
    }

    private fun Response.parseRetryAfter(): Long? =
        header("Retry-After")?.toLongOrNull()?.times(1_000)

    /**
     * Janitor's 4xx bodies are NestJS-shaped, but `message` is a string array on
     * validation failures and a bare string otherwise (`?page=0` returns
     * `{"message":"","statusCode":400}` with no `error` key at all). Decoding it
     * strictly throws on the second shape, so both are handled.
     */
    private fun String.extractMessage(): String? = runCatching {
        when (val m = json.parseToJsonElement(this).jsonObjectOrNull()?.get("message")) {
            is JsonArray -> m.mapNotNull { it.jsonPrimitive.contentOrNull }
                .joinToString("; ").takeIf { it.isNotBlank() }
            is JsonPrimitive -> m.contentOrNull?.takeIf { it.isNotBlank() }
            else -> null
        }
    }.getOrNull()

    private fun String.extractJanitorCode(): String? = runCatching {
        json.parseToJsonElement(this).jsonObjectOrNull()
            ?.get("code")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
        this as? JsonObject

    private fun backoffMillis(attempt: Int, serverHint: Long?): Long {
        if (serverHint != null) return serverHint.coerceAtMost(MAX_BACKOFF_MS)
        return Backoff.millis(attempt, BASE_BACKOFF_MS, MAX_BACKOFF_MS, jitterFloor = 0.5)
    }

    private fun IOException.toNetworkKind(): ApiError.Network.Kind = when (this) {
        is UnknownHostException -> ApiError.Network.Kind.NoConnectivity
        is SocketTimeoutException -> ApiError.Network.Kind.Timeout
        is SSLException -> ApiError.Network.Kind.ConnectionReset
        else -> ApiError.Network.Kind.Unknown
    }

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val BASE_BACKOFF_MS = 500L
        const val MAX_BACKOFF_MS = 15_000L
    }
}

/** Bridges OkHttp's callback API to coroutines, cancelling the call when the scope dies. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
