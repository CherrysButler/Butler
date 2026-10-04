package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What OpenRouter understands and Janitor doesn't carry: a preset in place of the model
 * (`@preset/roleplay`), and provider routing (`provider: {order, allow_fallbacks, sort}`).
 * Butler adds them to the payload Janitor assembled, just before sending it to OpenRouter
 * (docs/JANITOR_API.md §30), so they exist only on this phone, per proxy configuration.
 */
@Serializable
data class OpenRouterOptions(
    /** e.g. `@preset/roleplay`; replaces the model when set. */
    val preset: String = "",
    /** Provider slugs to try first, in order (`deepinfra`, `together`, …). */
    val providers: List<String> = emptyList(),
    /** false: only the providers listed, never others. */
    val allowFallbacks: Boolean = true,
    val prefer: Prefer = Prefer.Default,
) {
    @Serializable
    enum class Prefer(val wire: String?) { Default(null), Price("price"), Throughput("throughput"), Latency("latency") }

    val isEmpty: Boolean get() = preset.isBlank() && providers.isEmpty() && allowFallbacks && prefer == Prefer.Default

    /** The payload as OpenRouter should receive it. */
    fun applyTo(payload: JsonObject): JsonObject {
        if (isEmpty) return payload
        val out = payload.toMutableMap()
        preset.trim().takeIf { it.isNotEmpty() }?.let { out["model"] = JsonPrimitive(it) }
        if (providers.isNotEmpty() || !allowFallbacks || prefer.wire != null) {
            out["provider"] = buildJsonObject {
                if (providers.isNotEmpty()) put("order", JsonArray(providers.map { JsonPrimitive(it) }))
                if (!allowFallbacks) put("allow_fallbacks", JsonPrimitive(false))
                prefer.wire?.let { put("sort", JsonPrimitive(it)) }
            }
        }
        return JsonObject(out)
    }

    companion object {
        fun isOpenRouter(url: String): Boolean =
            runCatching { URI(url.trim()).host.orEmpty() }.getOrDefault("").let { it == "openrouter.ai" || it.endsWith(".openrouter.ai") }

        /** "deepinfra, Together ,  fireworks" → [deepinfra, together, fireworks] */
        fun parseProviders(text: String): List<String> =
            text.split(',', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
    }
}

/** [OpenRouterOptions] per proxy configuration id, in plain preferences (nothing secret). */
@Singleton
class OpenRouterOptionsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)
    private val serializer = MapSerializer(String.serializer(), OpenRouterOptions.serializer())

    private val _all = MutableStateFlow(
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty(),
    )
    val all: StateFlow<Map<String, OpenRouterOptions>> = _all.asStateFlow()

    fun get(proxyId: String?): OpenRouterOptions = proxyId?.let { _all.value[it] } ?: OpenRouterOptions()

    /**
     * Saved under every id the proxy goes by: Janitor names one proxy differently in its
     * API settings and in the profile config that generation reads.
     */
    fun set(proxyIds: Collection<String>, options: OpenRouterOptions) {
        save(if (options.isEmpty) _all.value - proxyIds.toSet() else _all.value + proxyIds.associateWith { options })
    }

    fun remove(proxyId: String) = save(_all.value - proxyId)

    fun clear() = save(emptyMap())

    private fun save(next: Map<String, OpenRouterOptions>) {
        _all.value = next
        prefs.edit().putString(KEY, json.encodeToString(serializer, next)).apply()
    }

    private companion object {
        const val KEY = "openrouter_options"
    }
}
