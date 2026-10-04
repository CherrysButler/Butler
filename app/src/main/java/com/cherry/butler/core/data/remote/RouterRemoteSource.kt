package com.cherry.butler.core.data.remote

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.dto.RouterCatalogDto
import com.cherry.butler.core.data.remote.dto.RouterConfigDto
import com.cherry.butler.core.data.remote.dto.RouterWalletDto
import com.cherry.butler.core.network.ApiCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** `/mb/janitor-router/…` (docs/JANITOR_API.md §17.4). Reads work on any account; writes need Janitor Plus. */
@Singleton
class RouterRemoteSource @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
) {
    private val base = "${JanitorConfig.BACKEND_BASE}/janitor-router"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun catalog(): RouterCatalogDto = get("$base/catalog") { json.decodeFromString(RouterCatalogDto.serializer(), it) }

    suspend fun config(): RouterConfigDto = get("$base/config") { json.decodeFromString(RouterConfigDto.serializer(), it) }

    suspend fun wallet(): RouterWalletDto = get("$base/wallet") { json.decodeFromString(RouterWalletDto.serializer(), it) }

    /**
     * `PUT /config` takes the whole config (`enabled` is required: "enabled must be a boolean
     * value", probed 2026-10-04). Answers 403 `JANITOR_ROUTER_DISABLED` without Janitor Plus.
     */
    suspend fun putConfig(body: JsonObject) {
        apiCall.execute(Request.Builder().url("$base/config").put(body.toString().toRequestBody(jsonMedia)).build(), maxAttempts = 1) { }
    }

    /** `POST /favorites {model_id, favorited}` ("favorited must be a boolean value", probed 2026-10-04). */
    suspend fun setFavorite(modelId: String, on: Boolean) {
        val body = buildJsonObject { put("model_id", modelId); put("favorited", on) }
        apiCall.execute(Request.Builder().url("$base/favorites").post(body.toString().toRequestBody(jsonMedia)).build(), maxAttempts = 1) { }
    }

    suspend fun claimBonus() {
        apiCall.execute(Request.Builder().url("$base/signup-bonus/claim").post(JsonObject(emptyMap()).toString().toRequestBody(jsonMedia)).build(), maxAttempts = 1) { }
    }

    private suspend fun <T> get(url: String, decode: (String) -> T): T =
        apiCall.execute(Request.Builder().url(url).get().build()) { decode(it) }
}
