package com.cherry.butler.core.generation

import android.util.Log
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.network.ApiCall
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.network.SseReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What a generation produces, in order, on whichever transport carried it. */
sealed interface GenerationEvent {
    /** Correlation + the context gauge, from response headers or a `meta` frame. */
    data class Meta(
        val requestId: String?,
        val contextUsagePercent: Int?,
        val contextWindowFull: Boolean?,
        val model: String?,
    ) : GenerationEvent

    /** Raw reply text. May contain tags; the caller runs it through the tag parser. */
    data class Delta(val text: String) : GenerationEvent

    /** Reasoning the upstream separated out itself (`delta.reasoning`), when it does. */
    data class Reasoning(val text: String) : GenerationEvent

    data object Done : GenerationEvent
}

interface GenerationTransport {
    /**
     * Runs one generation for an already-built envelope. Cancelling the collector cancels
     * the network call — a stopped generation must not keep streaming into the void.
     */
    fun generate(envelope: JsonObject, proxy: ProxyTarget?): Flow<GenerationEvent>
}

/**
 * Where to send the assembled payload when Janitor hands one back instead of streaming.
 * Built from the user's selected proxy configuration; the key never leaves this object
 * except as an `Authorization` header to [url].
 */
class ProxyTarget(
    val url: String,
    key: String,
    /** Last touch on the assembled payload before it leaves (OpenRouter preset and routing). */
    val shape: (JsonObject) -> JsonObject = { it },
) {
    private val bearer = "Bearer $key"
    // Kept only so [then] can build the same target; never read anywhere else.
    private val key = key

    fun authorization(): String = bearer

    /** The same proxy, with one more change made to the payload after this one's own. */
    fun then(next: (JsonObject) -> JsonObject): ProxyTarget = ProxyTarget(url, key) { next(shape(it)) }

    override fun toString(): String = "ProxyTarget($url)"
}

/**
 * The HTTP transport for `POST /generateAlpha` (the website's route), honouring the decision recorded in
 * docs/ROADMAP.md: **prefer direct, fall back to whatever Janitor returns.**
 *
 * Janitor answers this call in one of two ways (docs/JANITOR_API.md §18.1, §24):
 *
 * 1. `application/json` carrying an assembled OpenAI payload (`messages`, `model`, …). The
 *    client is expected to POST that to the user's own proxy and stream from there. This
 *    is the web's behaviour and the preferred one — the upstream call is made by the
 *    device, so the user's key is used only from the device.
 * 2. `text/event-stream` — Janitor streams the completion itself (`x-proxy-mode: server`,
 *    observed on mobile). Read as-is.
 *
 * Both streams are OpenAI chunk format, so one reader serves both, and the decision of
 * which happened is made from the response's content type, never assumed.
 */
class HttpGenerationTransport(
    baseClient: OkHttpClient,
    private val apiCall: ApiCall,
    private val json: Json,
) : GenerationTransport {

    // A stream can be silent for longer than an ordinary call while the model thinks.
    private val client = baseClient.newBuilder()
        .readTimeout(STREAM_READ_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    /**
     * For the user's own proxy: none of Janitor's interceptors. Those stamp Janitor's
     * session as `Authorization` (which replaced the proxy key, so the proxy answered 401)
     * and add Janitor's `apikey`, which no third party should receive.
     */
    private val proxyClient = client.newBuilder()
        .apply { interceptors().clear(); networkInterceptors().clear() }
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override fun generate(envelope: JsonObject, proxy: ProxyTarget?): Flow<GenerationEvent> = flow {
        val chat = envelope["chat"]?.jsonObject
        val request = Request.Builder()
            .url("${JanitorConfig.WEB_LLM_BASE}/generateAlpha")
            .post(envelope.toString().toRequestBody(jsonMedia))
            // The headers the website sends with this call (captured 2026-10-06). Without them
            // Janitor's firewall answered "Access Restricted"; with them, the same body went
            // through. No `apikey` here: AuthInterceptor leaves it off this path.
            .header("Accept", "text/event-stream")
            .header("Origin", JanitorConfig.WEB_LLM_BASE)
            .header("Referer", "${JanitorConfig.WEB_LLM_BASE}/chats/${chat?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()}")
            .header("x-app-version", JanitorConfig.WEB_APP_VERSION)
            .apply { chat?.get("user_id")?.jsonPrimitive?.contentOrNull?.let { header("X-Request-ID", it) } }
            .build()

        executeStreaming(request) { response ->
            val contentType = response.header("Content-Type").orEmpty()
            emit(
                GenerationEvent.Meta(
                    requestId = response.header("x-generation-request-id"),
                    contextUsagePercent = response.header("x-context-usage-percent")?.toIntOrNull(),
                    contextWindowFull = response.header("x-jllm-context-window-full")?.toBooleanStrictOrNull(),
                    model = null,
                ),
            )
            // Janitor labels its own proxied stream inconsistently, so the body decides: a
            // stream opens with an SSE field, a payload opens with a brace. Trusting the
            // header alone meant buffering a whole reply and then failing to parse it.
            val head = response.body?.source()?.let { src ->
                src.peek().use { peek -> peek.request(48); peek.buffer.snapshot().utf8() }
            }.orEmpty().trimStart()
            Log.i(TAG, "generateAlpha ${response.code} type=$contentType head=${head.take(24).lines().joinToString(" ")}")
            when {
                contentType.startsWith("text/event-stream") || head.startsWith("data:") || head.startsWith("event:") || head.startsWith(":") ->
                    readOpenAiStream(response)
                else -> {
                    val body = response.body?.string().orEmpty()
                    val payload = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                        ?: run {
                            Log.w(TAG, "generateAlpha body is neither a stream nor JSON: type=$contentType len=${body.length}")
                            throw ApiError.Serialization(IllegalStateException("generateAlpha returned neither a stream nor JSON ($contentType)"))
                        }
                    if (payload["messages"] == null || payload["model"] == null) {
                        // JSON that is not a payload is an error body wearing a 200.
                        throw ApiError.Api(
                            code = response.code,
                            janitorCode = payload["code"]?.jsonPrimitive?.contentOrNull,
                            serverMessage = payload["message"]?.jsonPrimitive?.contentOrNull,
                        )
                    }
                    val target = proxy ?: throw ApiError.Api(
                        code = response.code,
                        janitorCode = "BUTLER_NO_PROXY_TARGET",
                        serverMessage = "Janitor handed back a payload but no proxy is configured to send it to.",
                    )
                    streamFromProxy(payload, target)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** The second hop: the assembled payload goes to the user's proxy host. */
    private suspend fun FlowCollector<GenerationEvent>.streamFromProxy(payload: JsonObject, target: ProxyTarget) {
        val shaped = target.shape(payload)
        // What leaves for the proxy, minus anything secret: the model and whether routing rides along.
        Log.i(TAG, "proxy: model=${shaped["model"]} provider=${shaped["provider"] ?: "-"}")
        val request = Request.Builder()
            .url(target.url)
            .post(shaped.toString().toRequestBody(jsonMedia))
            .header("Authorization", target.authorization())
            .header("Accept", "text/event-stream")
            // The headers the official client sends to a reverse proxy (docs/JANITOR_API.md §7.5).
            .header("HTTP-Referer", "https://janitorai.com")
            .header("X-Title", "janitor")
            .build()
        executeStreaming(request, direct = true) { response -> readOpenAiStream(response) }
    }

    /**
     * OpenAI chat-completion chunks over SSE. `data: [DONE]` ends the stream; a closed
     * connection without it also ends it — the caller distinguishes a clean finish from a
     * cut-off by whether [GenerationEvent.Done] arrived.
     */
    private suspend fun FlowCollector<GenerationEvent>.readOpenAiStream(response: Response) {
        val source = response.body?.source() ?: return
        val started = System.currentTimeMillis()
        var events = 0
        var firstAt = 0L
        SseReader.events(source).collect { event ->
            if (events++ == 0) {
                firstAt = System.currentTimeMillis()
                Log.i(TAG, "stream: first event after ${firstAt - started} ms")
            }
            val data = event.data.trim()
            if (data == "[DONE]") {
                Log.i(TAG, "stream: done after $events events, ${System.currentTimeMillis() - started} ms")
                emit(GenerationEvent.Done)
                return@collect
            }
            val chunk = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@collect
            chunk["error"]?.let { err ->
                val obj = runCatching { err.jsonObject }.getOrNull()
                throw ApiError.Api(
                    code = obj?.get("code")?.jsonPrimitive?.intOrNull ?: 502,
                    janitorCode = null,
                    serverMessage = obj?.get("message")?.jsonPrimitive?.contentOrNull ?: err.toString(),
                )
            }
            val choice = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val delta = choice?.get("delta")?.jsonObject
            delta?.get("reasoning")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Reasoning(it)) }
            delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Reasoning(it)) }
            delta?.get("content")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Delta(it)) }
            chunk["model"]?.jsonPrimitive?.contentOrNull?.let { model ->
                // First chunk names the model; later ones repeat it. Cheap enough to re-emit.
                emit(GenerationEvent.Meta(requestId = null, contextUsagePercent = null, contextWindowFull = null, model = model))
            }
            choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull?.let {
                // Some proxies never send [DONE]; a finish_reason is the other clean ending.
                emit(GenerationEvent.Done)
            }
        }
    }

    /**
     * Runs a call whose body is consumed as a stream. Cancellation of the collecting
     * coroutine cancels the OkHttp call, which unblocks the reader — a blocking socket read
     * does not notice coroutine cancellation on its own.
     */
    private suspend fun executeStreaming(
        request: Request,
        direct: Boolean = false,
        block: suspend (Response) -> Unit,
    ) {
        val call = (if (direct) proxyClient else client).newCall(request)
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                if (call.isCanceled()) throw ApiError.Cancelled
                throw apiCall.networkError(e)
            }
            response.use { r ->
                if (!r.isSuccessful) {
                    val body = r.body?.string().orEmpty()
                    throw if (direct) proxyError(r.code, body) else apiCall.errorFor(r, body)
                }
                try {
                    block(r)
                } catch (e: IOException) {
                    if (call.isCanceled()) throw ApiError.Cancelled
                    throw apiCall.networkError(e)
                }
            }
        } finally {
            handle.dispose()
        }
    }

    /** A failure from the user's proxy host, not from Janitor: classified by status only. */
    private fun proxyError(code: Int, body: String): ApiError {
        // The proxy's own answer (never the request) is what explains a refusal.
        Log.w(TAG, "proxy refused ($code): ${body.take(1500)}")
        return when (code) {
            401, 403 -> ApiError.Api(code, janitorCode = "PROXY_AUTH", serverMessage = "The proxy rejected the API key.", retryable = false)
            429 -> ApiError.RateLimited()
            in 500..599 -> ApiError.Server(code)
            else -> ApiError.Api(code, janitorCode = "PROXY_ERROR", serverMessage = body.take(200), retryable = false)
        }
    }

    private companion object {
        const val TAG = "GenerationTransport"
        const val STREAM_READ_TIMEOUT_S = 120L
    }
}
