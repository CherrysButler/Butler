package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.core.network.ButlerUserAgent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenRouter's routing shortcuts: a word on the end of a model's id that says how its
 * provider is picked. `:nitro` asks for the fastest, `:floor` for the cheapest; with neither
 * it is OpenRouter's own balance. They are not models of their own, so the list has no rows
 * for them: a model is picked, then one of these is put on it
 * (`moonshotai/kimi-k2.6` becomes `moonshotai/kimi-k2.6:nitro`).
 */
enum class ModelVariant(val suffix: String, val label: String) {
    Standard("", "Standard"),
    Nitro(":nitro", "Nitro"),
    Floor(":floor", "Floor"),
    ;

    companion object {
        /** The shortcut on [model]'s end; [Standard] for none. */
        fun of(model: String): ModelVariant =
            entries.firstOrNull { it.suffix.isNotEmpty() && model.trim().endsWith(it.suffix, ignoreCase = true) } ?: Standard

        /** [model] without its shortcut: the id the list knows it by. */
        fun base(model: String): String = model.trim().let { it.dropLast(of(it).suffix.length) }

        /** [model] with [variant] in place of whatever shortcut it had. */
        fun with(model: String, variant: ModelVariant): String = base(model) + variant.suffix

        /**
         * Whether a shortcut can be put on the model with this [base] id. One whose id already
         * ends in a word of its own (`:free`, `:extended`) is a fixed offer and takes none.
         */
        fun takes(base: String): Boolean = base.isNotBlank() && ':' !in base
    }
}

/** One model a router offers, with what a picker shows of it. */
@Serializable
data class CatalogModel(
    /** What goes in a proxy's Model field: `anthropic/claude-sonnet-5.5`. */
    val id: String,
    /** Its name without the maker: "Claude Sonnet 5.5". */
    val name: String,
    /** Who makes it: "Anthropic". */
    val maker: String,
    val context: Int,
    /** Dollars per million tokens read and written; null when the router does not say, or it varies. */
    val priceIn: Double? = null,
    val priceOut: Double? = null,
    val images: Boolean = false,
    val reasoning: Boolean = false,
    /** In the router's own roleplay list. */
    val roleplay: Boolean = false,
    /** When the router added it, in seconds. */
    val added: Long = 0,
) {
    val free: Boolean get() = priceIn == 0.0 && priceOut == 0.0
}

/**
 * OpenRouter's list of models, for picking one instead of typing its id. The list is public
 * (no key goes with the request, and nothing of the user's); it is fetched whole, kept on the
 * device, and searched there, so a search is instant and works offline. A kept list older than
 * a day is refreshed the next time a picker opens, behind the list already on show.
 */
@Singleton
class ModelCatalog @Inject constructor(
    @ApplicationContext context: Context,
    client: OkHttpClient,
    private val json: Json,
) {
    data class State(
        val models: List<CatalogModel> = emptyList(),
        val loading: Boolean = false,
        /** The last fetch failed; [models] is what was kept before, if anything. */
        val failed: Boolean = false,
    )

    @Serializable
    private data class Kept(val at: Long, val models: List<CatalogModel>)

    // The app's client carries Janitor's session; a router gets none of it.
    private val http = client.newBuilder()
        .apply { interceptors().clear(); networkInterceptors().clear() }
        .addInterceptor(ButlerUserAgent)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val file = File(context.filesDir, "openrouter-models.json")
    private val mutex = Mutex()
    private var keptAt = 0L

    private val _openRouter = MutableStateFlow(State())
    val openRouter: StateFlow<State> = _openRouter.asStateFlow()

    /** Brings the list up: what was kept, at once, and a fresh one if that is old or [force]d. */
    suspend fun load(force: Boolean = false) = mutex.withLock {
        if (_openRouter.value.models.isEmpty()) {
            read()?.let { kept ->
                keptAt = kept.at
                _openRouter.value = State(kept.models)
            }
        }
        val stale = System.currentTimeMillis() - keptAt > FRESH_FOR_MS
        if (!force && !stale && _openRouter.value.models.isNotEmpty()) return@withLock
        _openRouter.value = _openRouter.value.copy(loading = true, failed = false)
        val fetched = runCatching { fetch() }.getOrNull()
        if (fetched.isNullOrEmpty()) {
            _openRouter.value = _openRouter.value.copy(loading = false, failed = true)
        } else {
            keptAt = System.currentTimeMillis()
            _openRouter.value = State(fetched)
            runCatching { write(Kept(keptAt, fetched)) }
        }
    }

    private suspend fun fetch(): List<CatalogModel> = withContext(Dispatchers.IO) {
        val all = get("$BASE/models")
        // The roleplay list is a courtesy: without it the models are simply not marked.
        val roleplay = runCatching { get("$BASE/models?category=roleplay") }.getOrDefault(JsonArray(emptyList()))
            .mapNotNullTo(HashSet()) { (it as? JsonObject)?.string("id") }
        all.mapNotNull { (it as? JsonObject)?.let { m -> parse(m, roleplay) } }
    }

    private fun get(url: String): JsonArray {
        http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
            if (!response.isSuccessful) error("models: HTTP ${response.code}")
            val body = response.body?.string() ?: error("models: empty")
            return json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: error("models: no data")
        }
    }

    private suspend fun read(): Kept? = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) json.decodeFromString(Kept.serializer(), file.readText()) else null }.getOrNull()
    }

    private suspend fun write(kept: Kept) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Kept.serializer(), kept))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    companion object {
        private const val BASE = "https://openrouter.ai/api/v1"
        private const val FRESH_FOR_MS = 24 * 60 * 60 * 1000L

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        internal fun parse(m: JsonObject, roleplay: Set<String>): CatalogModel? {
            val id = m.string("id")?.takeIf { it.isNotBlank() } ?: return null
            val full = m.string("name")?.takeIf { it.isNotBlank() } ?: id
            // "Anthropic: Claude Sonnet 5.5" names its maker; an id alone gives "anthropic".
            val split = full.indexOf(": ")
            val maker = if (split > 0) full.substring(0, split) else id.substringBefore('/', "").replaceFirstChar { it.uppercase() }
            val name = if (split > 0) full.substring(split + 2) else full
            val pricing = m["pricing"] as? JsonObject
            val inputs = ((m["architecture"] as? JsonObject)?.get("input_modalities") as? JsonArray).orEmpty()
            val params = (m["supported_parameters"] as? JsonArray).orEmpty()
            return CatalogModel(
                id = id,
                name = name,
                maker = maker,
                context = (m["context_length"] as? JsonPrimitive)?.longOrNull?.toInt() ?: 0,
                priceIn = perMillion(pricing?.string("prompt")),
                priceOut = perMillion(pricing?.string("completion")),
                images = inputs.any { (it as? JsonPrimitive)?.contentOrNull == "image" },
                reasoning = params.any { (it as? JsonPrimitive)?.contentOrNull == "reasoning" },
                roleplay = id in roleplay,
                added = (m["created"] as? JsonPrimitive)?.longOrNull ?: 0,
            )
        }

        /** A price per token, as the router gives it, in dollars per million; a negative one varies. */
        private fun perMillion(perToken: String?): Double? =
            perToken?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { Math.round(it * 1_000_000 * 10_000) / 10_000.0 }

        /**
         * The models a search finds, best first: every word of [query] has to be in the model's
         * name, maker or id. One that starts a word of the name outranks one inside a word, and
         * the name outranks the id; ties keep the router's own order (newest first).
         */
        fun search(models: List<CatalogModel>, query: String): List<CatalogModel> {
            val words = query.lowercase().split(' ', '/', '-', ':').filter { it.isNotBlank() }
            if (words.isEmpty()) return models
            return models.mapIndexedNotNull { at, m ->
                val name = m.name.lowercase()
                val hay = "${m.maker.lowercase()} $name ${m.id.lowercase()}"
                var score = 0
                for (w in words) {
                    val inName = name.indexOf(w)
                    score += when {
                        inName == 0 -> 0
                        inName > 0 && !name[inName - 1].isLetterOrDigit() -> 1
                        inName > 0 -> 3
                        hay.contains(w) -> 4
                        else -> return@mapIndexedNotNull null
                    }
                }
                Triple(score, at, m)
            }.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
        }
    }
}
