package com.cherry.butler.core.generation

import android.content.Context
import com.cherry.butler.core.data.OpenRouterOptions
import com.cherry.butler.core.data.OpenRouterOptionsStore
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.ProxyConfig
import com.cherry.butler.core.data.SettingsRepository
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.network.ButlerUserAgent
import com.cherry.butler.core.network.SseReader
import com.cherry.butler.core.stream.TagStreamParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Butler special: the persona editor's ✦. The user writes a description, and one of their
 * proxy presets enhances it: richer, in their own style, keeping every fact they gave. Janitor
 * has nothing like it (asked for on Reddit, 0.2 thread).
 *
 * Proxies only: JLLM is reached through a chat's generation, which carries that chat's
 * history, and a persona has no chat. The preset is called straight, as Butler calls a proxy
 * for replies; no Janitor interceptors or cookies ride along, only Butler's name.
 */
@Singleton
class DescriptionWriter @Inject constructor(
    @ApplicationContext context: Context,
    client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
    private val openRouter: OpenRouterOptionsStore,
) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)
    private val http = client.newBuilder()
        .apply { interceptors().clear(); networkInterceptors().clear() }
        .addInterceptor(ButlerUserAgent)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** The presets that can write: saved proxies with a key. */
    suspend fun presets(): List<ProxyConfig> = settings.load().proxies.filter { it.hasKey }

    /** The preset picked last time, to offer first. */
    var lastPreset: String?
        get() = prefs.getString(KEY_LAST, null)
        set(value) { prefs.edit().putString(KEY_LAST, value).apply() }

    /** The enhanced description, as it streams in. */
    fun enhance(description: String, name: String, pronouns: String?, preset: ProxyConfig): Flow<String> = flow {
        val profile = profiles.profile()
        val proxy = profiles.proxy(profile, preset.clientId ?: preset.id)
            ?: throw ApiError.Api(0, janitorCode = "NO_PRESET", serverMessage = "That preset isn't saved any more.", retryable = false)
        var payload = buildJsonObject {
            put("model", proxy.model ?: preset.model)
            put("stream", true)
            put("temperature", 0.8)
            put("max_tokens", 700)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", INSTRUCTION) })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildString {
                        if (name.isNotBlank()) append("Name: ").append(name.trim()).append('\n')
                        if (!pronouns.isNullOrBlank()) append("Pronouns: ").append(pronouns).append('\n')
                        append("\nDescription:\n").append(description.trim())
                    })
                })
            })
        }
        if (OpenRouterOptions.isOpenRouter(proxy.url)) payload = openRouter.get(preset.id).applyTo(payload)

        val request = Request.Builder()
            .url(proxy.url)
            .post(payload.toString().toRequestBody(JSON))
            .header("Authorization", "Bearer ${proxy.key}")
            .header("Accept", "text/event-stream")
            .header("HTTP-Referer", "https://janitorai.com")
            .header("X-Title", "janitor")
            .build()
        val call = http.newCall(request)
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            val response = try { call.execute() } catch (e: IOException) { throw ApiError.Network(ApiError.Network.Kind.Unknown, e) }
            response.use { r ->
                if (!r.isSuccessful) {
                    val message = when (r.code) {
                        401, 403 -> "The proxy rejected the key."
                        402 -> "The proxy has no balance left."
                        429 -> "The proxy asked for a pause. Try again in a moment."
                        else -> "The proxy answered ${r.code}."
                    }
                    throw ApiError.Api(r.code, janitorCode = "PROXY_ERROR", serverMessage = message, retryable = false)
                }
                val source = r.body?.source() ?: return@use
                // A thinking model's reasoning is not part of the description.
                val parser = TagStreamParser(listOf("think", "thinking"))
                var thinking = false
                SseReader.events(source).collect { event ->
                    val data = event.data.trim()
                    if (data == "[DONE]") return@collect
                    val chunk = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@collect
                    val piece = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull ?: return@collect
                    for (e in parser.feed(piece)) when (e) {
                        is TagStreamParser.Event.Open -> thinking = true
                        is TagStreamParser.Event.Close -> thinking = false
                        is TagStreamParser.Event.Text -> if (!thinking) emit(e.text)
                    }
                }
                for (e in parser.finish()) if (e is TagStreamParser.Event.Text && !thinking) emit(e.text)
            }
        } finally {
            handle.dispose()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        /** Fewer words than this give the model too little to work from. */
        const val MIN_WORDS = 12

        fun words(text: String): Int = text.split(Regex("""\s+""")).count { it.isNotBlank() }

        private const val KEY_LAST = "description_writer_preset"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        const val INSTRUCTION =
            "You enhance persona descriptions for a roleplay chat app. The user wrote a short description of the " +
                "character they play as. Rewrite it into a richer, vivid description. Keep every fact they gave and " +
                "contradict none; don't change their name or invent a different person. Keep their point of view and " +
                "style: if they wrote in first person, stay in first person; if they used a list, keep a list. Cover " +
                "what fits among appearance, personality, mannerisms and background, without inventing major plot. " +
                "Plain text, no headings or markdown, about 120 to 250 words. Reply with the description only."
    }
}
