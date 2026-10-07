package com.cherry.butler.core.generation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.cherry.butler.core.data.OpenRouterOptions
import com.cherry.butler.core.data.OpenRouterOptionsStore
import com.cherry.butler.core.data.PicturePrefs
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.SettingsRepository
import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.data.Writer
import com.cherry.butler.core.data.WriterPrefs
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.network.ButlerUserAgent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Butler special, beta: a picture in a message. The user puts one picture into their
 * draft, where `[pic]` stands for it; a model that can see is shown the picture with the
 * chat's last lines and the draft, and writes what the picture shows as the user would have
 * written it, in their tone. That text takes `[pic]`'s place in the draft; the user reads
 * it, changes what they like, and sends the line themselves. Nothing of the picture goes to
 * Janitor, only to the chosen proxy, and nothing is sent until the user sends.
 *
 * Which model: Settings › Model › "Pictures use", or failing that Write for me's, which has
 * to be a proxy (JLLM cannot see). The picture is scaled down to [MAX_SIDE] and sent as a
 * JPEG data URL in the OpenAI shape every vision proxy takes.
 */
@Singleton
class PictureDescriber @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient,
    private val json: Json,
    db: ButlerDatabase,
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
    private val openRouter: OpenRouterOptionsStore,
    private val picturePrefs: PicturePrefs,
    private val writerPrefs: WriterPrefs,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    private val http = client.newBuilder()
        .apply { interceptors().clear(); networkInterceptors().clear() }
        .addInterceptor(ButlerUserAgent)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Keeps a small copy of the picked picture for [chatId]; the picker's grant doesn't last. */
    suspend fun keep(chatId: Long, uri: Uri): File = withContext(Dispatchers.IO) {
        val bytes = shrink(uri) ?: throw IOException("Couldn't read that picture.")
        File(context.cacheDir, "picture-$chatId.jpg").apply { writeBytes(bytes) }
    }

    fun forget(chatId: Long) {
        File(context.cacheDir, "picture-$chatId.jpg").delete()
    }

    /** What [picture] shows, written for the draft's `[pic]` in the chat's tone. */
    suspend fun describe(chatId: Long, draft: String, picture: File): String = withContext(Dispatchers.IO) {
        val chat = chatDao.get(chatId) ?: error("unknown chat $chatId")
        val history = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.serverId != null }.takeLast(RECENT)
        val profile = profiles.profile()
        val played = profiles.playedPersona(chat, history)
        val who = played.persona?.name ?: chat.personaName ?: profile.name.ifBlank { profile.userName }
        val character = chat.shownName

        val target = resolve(profile)
        val image = Base64.encodeToString(picture.readBytes(), Base64.NO_WRAP)

        val context = buildString {
            append("The roleplay is between ").append(who).append(" (the user) and ").append(character).append(".\n")
            if (history.isNotEmpty()) {
                append("\nThe last lines:\n")
                history.forEach { m ->
                    append(if (m.isBot) character else who).append(": ")
                    append(m.text.replace("{{user}}", who, ignoreCase = true).replace("{{char}}", character, ignoreCase = true).take(600)).append('\n')
                }
            }
            append("\n").append(who).append("'s draft, where [pic] is the picture:\n").append(draft.ifBlank { "[pic]" })
        }

        var payload = buildJsonObject {
            put("model", target.model)
            put("stream", false)
            put("temperature", 0.7)
            put("max_tokens", 400)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", INSTRUCTION) })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject { put("type", "text"); put("text", context) })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$image") })
                        })
                    })
                })
            })
        }
        if (OpenRouterOptions.isOpenRouter(target.url)) payload = openRouter.get(target.presetId).applyTo(payload)
        // The options may carry the preset's own model; the picked one wins.
        payload = kotlinx.serialization.json.JsonObject(payload + ("model" to kotlinx.serialization.json.JsonPrimitive(target.model)))

        val request = Request.Builder()
            .url(target.url)
            .post(payload.toString().toRequestBody(JSON))
            .header("Authorization", "Bearer ${target.key}")
            .header("Accept", "application/json")
            .header("HTTP-Referer", "https://janitorai.com")
            .header("X-Title", "janitor")
            .build()
        val response = try { http.newCall(request).execute() } catch (e: IOException) { throw ApiError.Network(ApiError.Network.Kind.Unknown, e) }
        response.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val message = when (r.code) {
                    400 -> "The proxy didn't take the picture. The model may not be able to see; pick one that can in Settings › Model."
                    401, 403 -> "The proxy rejected the key."
                    402 -> "The proxy has no balance left."
                    429 -> "The proxy asked for a pause. Try again in a moment."
                    else -> "The proxy answered ${r.code}."
                }
                throw ApiError.Api(r.code, janitorCode = "PROXY_ERROR", serverMessage = message, retryable = false)
            }
            val content = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?.get("choices")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")
            val text = when (content) {
                null -> ""
                else -> runCatching { content.jsonPrimitive.contentOrNull }.getOrNull()
                    ?: runCatching { content.jsonArray.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("") }.getOrDefault("")
            }
            clean(text).ifBlank { throw ApiError.Api(0, janitorCode = "EMPTY", serverMessage = "Nothing came back. Try again.", retryable = false) }
        }
    }

    private class Target(val presetId: String, val url: String, val key: String, val model: String)

    /** The proxy that sees pictures: the one picked for pictures, else Write for me's; never JLLM. */
    private suspend fun resolve(profile: com.cherry.butler.core.data.remote.dto.ProfileDto): Target {
        val none = ApiError.Api(0, janitorCode = "NO_VISION", serverMessage = "Pictures need a proxy preset with a model that can see. Pick one in Settings › Model › Pictures use.", retryable = false)
        val pick = picturePrefs.describer.value ?: when (val w = writerPrefs.writer.value) {
            is Writer.Proxy -> w
            Writer.SameAsChat -> profiles.selectedProxy(profile)?.let { Writer.Proxy(it.id) } ?: throw none
            Writer.Jllm -> throw none
        }
        val saved = profiles.proxy(profile, pick.id) ?: run {
            // The preset may be known by its other id.
            settings.load().proxies.firstOrNull { pick.id in it.ids }?.let { p -> profiles.proxy(profile, p.clientId ?: p.id) }
        } ?: throw ApiError.Api(0, janitorCode = "NO_PRESET", serverMessage = "That preset isn't saved any more.", retryable = false)
        val model = pick.model ?: saved.model ?: throw none
        return Target(presetId = saved.id, url = saved.url, key = saved.key, model = model)
    }

    /** Reads [uri], scaled to at most [MAX_SIDE] a side, as a JPEG. */
    private fun shrink(uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Bounds only: the decode itself comes back null here by design; the size is what counts.
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true) else decoded
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return out.toByteArray()
    }

    /** No reasoning, no quotes around the whole, no "[pic]" echoed back. */
    private fun clean(text: String): String = text
        .replace(Regex("(?is)<think(?:ing)?>.*?</think(?:ing)?>"), "")
        .trim()
        .removeSurrounding("\"")
        .replace("[pic]", "", ignoreCase = true)
        .trim()

    private companion object {
        const val RECENT = 10
        const val MAX_SIDE = 1024
        val JSON = "application/json".toMediaType()

        val INSTRUCTION = """
            You help someone in a text roleplay put a picture into their own message. You are shown the picture, the last lines of the roleplay, and the person's draft, in which [pic] marks where the picture goes.
            Write the words that take the place of [pic]: what the picture shows, told the way this person writes. Match their voice exactly: the same person (first person if they write in first person), tense, formatting (asterisks for narration if they use them, quotes for speech if they do) and tone. It must read as part of their message, not as a caption.
            Describe what matters in the picture as the roleplay would notice it; leave out things nobody would mention. One to three sentences. Do not mention a picture, photo or image unless the draft itself does. Output only the replacement text: no preamble, no quotes around it, no notes.
        """.trimIndent()
    }
}
