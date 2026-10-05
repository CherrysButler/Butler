package com.cherry.butler.core.generation

import android.util.Log
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.network.ApiCall
import com.cherry.butler.core.network.ApiError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * JLLM over the WebSocket at `/mobile/generateAlpha` (docs/JANITOR_API.md §19.4; the mobile route
 * answers the upgrade with 101, verified 2026-10-03).
 *
 * One socket per generation: the website multiplexes requests over one socket, but a
 * per-generation socket makes cancellation, failure and reconnection trivially correct —
 * a stop closes this generation's socket and nothing else — at the cost of one handshake.
 *
 * The frame is the ordinary envelope with the bearer token and a client-made `requestId`
 * added inside it (a WebSocket cannot carry the header). Frames back:
 * `meta` (model, then the context gauge), `chunk` whose `data` is a JSON *string* holding
 * an OpenAI chunk, and `done`. Frames are routed by `requestId` regardless.
 */
class JllmTransport(
    baseClient: OkHttpClient,
    private val apiCall: ApiCall,
    private val json: Json,
    private val accessToken: () -> String?,
) : GenerationTransport {

    private val client = baseClient.newBuilder()
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .pingInterval(PING_S, TimeUnit.SECONDS)
        .build()

    override fun generate(envelope: JsonObject, proxy: ProxyTarget?): Flow<GenerationEvent> = callbackFlow {
        val token = accessToken() ?: throw ApiError.Unauthorized
        val requestId = "req_${UUID.randomUUID()}"
        val frame = buildJsonObject {
            put("Authorization", JsonPrimitive("Bearer $token"))
            put("requestId", JsonPrimitive(requestId))
            envelope.forEach { (k, v) -> put(k, v) }
        }.toString()

        var finished = false
        val socket = client.newWebSocket(
            Request.Builder().url(URL).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(frame)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                    // A shared socket would interleave requests; ours never should, but
                    // routing on the id keeps that a non-issue rather than a silent mix-up.
                    obj["requestId"]?.jsonPrimitive?.contentOrNull?.let { if (it != requestId) return }
                    when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                        "meta" -> trySend(
                            GenerationEvent.Meta(
                                requestId = requestId,
                                contextUsagePercent = obj["contextUsagePercent"]?.jsonPrimitive?.intOrNull,
                                contextWindowFull = obj["contextWindowFull"]?.jsonPrimitive?.booleanOrNull,
                                model = obj["modelVersion"]?.jsonPrimitive?.contentOrNull,
                            ),
                        )
                        "chunk" -> onChunk(obj)
                        "done" -> {
                            finished = true
                            trySend(GenerationEvent.Done)
                            webSocket.close(NORMAL, null)
                            channel.close()
                        }
                        "error" -> fail(webSocket, frameError(obj))
                        else -> obj["error"]?.let { fail(webSocket, frameError(obj)) }
                    }
                }

                private fun onChunk(obj: JsonObject) {
                    // `data` is double-encoded: a string containing the OpenAI chunk.
                    val raw = obj["data"]?.jsonPrimitive?.contentOrNull ?: return
                    val chunk = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
                    chunk["error"]?.let { fail(null, frameError(chunk)); return }
                    val delta = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: return
                    delta["reasoning"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { trySend(GenerationEvent.Reasoning(it)) }
                    delta["reasoning_content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { trySend(GenerationEvent.Reasoning(it)) }
                    // The first chunk's content is empty by design; it is not the end.
                    delta["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { trySend(GenerationEvent.Delta(it)) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(NORMAL, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    // Closed without `done`: the caller sees no Done and decides between a
                    // cut-short reply and a failure from what it already received.
                    if (!finished) Log.w(TAG, "socket closed before done ($code)")
                    channel.close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (finished) return
                    val error = when {
                        response != null && !response.isSuccessful ->
                            apiCall.errorFor(response, runCatching { response.body?.string() }.getOrNull().orEmpty())
                        t is IOException -> apiCall.networkError(t)
                        else -> ApiError.Unknown(t)
                    }
                    channel.close(error)
                }

                private fun fail(webSocket: WebSocket?, error: ApiError) {
                    finished = true
                    webSocket?.close(NORMAL, null)
                    channel.close(error)
                }
            },
        )
        awaitClose { if (!finished) socket.cancel() }
    }.buffer(Channel.UNLIMITED)

    private fun frameError(obj: JsonObject): ApiError {
        val err = obj["error"]
        val inner = runCatching { err?.jsonObject }.getOrNull()
        val message = inner?.get("message")?.jsonPrimitive?.contentOrNull
            ?: obj["message"]?.jsonPrimitive?.contentOrNull
            ?: err?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        val code = inner?.get("code")?.jsonPrimitive?.intOrNull ?: obj["code"]?.jsonPrimitive?.intOrNull ?: 502
        val janitorCode = inner?.get("type")?.jsonPrimitive?.contentOrNull ?: obj["code"]?.jsonPrimitive?.contentOrNull
        return ApiError.Api(code = code, janitorCode = janitorCode, serverMessage = message, retryable = code >= 500 || code == 429)
    }

    private companion object {
        const val TAG = "JllmTransport"
        val URL = "${JanitorConfig.LLM_BASE}/generateAlpha".replaceFirst("https://", "wss://")
        const val NORMAL = 1000
        const val READ_TIMEOUT_S = 120L
        const val PING_S = 20L
    }
}

/**
 * Picks the transport for each generation from the envelope itself: the profile config the
 * server keeps in sync says `api: "janitor"` when JLLM is the provider and `"openai"` for a
 * proxy (verified on both, 2026-10-03), so a provider switch takes effect on the next send.
 */
class RoutingTransport(
    private val proxyPath: GenerationTransport,
    private val jllm: GenerationTransport,
) : GenerationTransport {
    override fun generate(envelope: JsonObject, proxy: ProxyTarget?): Flow<GenerationEvent> {
        val api = runCatching { envelope["userConfig"]?.jsonObject?.get("api")?.jsonPrimitive?.contentOrNull }.getOrNull()
        // No `api` at all is a fresh account that never chose: JLLM, as on the website.
        return if (api == null || api == "janitor") jllm.generate(envelope, proxy) else proxyPath.generate(envelope, proxy)
    }
}
