package com.cherry.butler.core.data.remote

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.network.ApiCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The media library pictures in a bio or a character's description come from (the website's
 * editor, captured 2026-10-12, docs/JANITOR_API.md §32):
 *
 * 1. `POST /media/files {content_type, name}` → `{id, url}`, `url` presigned for the PUT;
 * 2. the bytes PUT to `url` (Cloudflare R2, no Janitor headers);
 * 3. `POST /media/files/{id}/upload-complete` → `{queued: true}`;
 * 4. `GET /media/files/{id}/moderation-status` → `{status: "pending"}` until
 *    `{status: "approved", url}`, the picture's address on `media-approved/`.
 */
@Singleton
class MediaRemoteSource @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
) {
    private val base = JanitorConfig.BACKEND_BASE
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Serializable
    data class NewFile(val id: String, val url: String)

    @Serializable
    data class Moderation(val status: String = "", val url: String? = null)

    suspend fun newFile(contentType: String, name: String): NewFile {
        val body = buildJsonObject { put("content_type", JsonPrimitive(contentType)); put("name", JsonPrimitive(name)) }
        val request = Request.Builder().url("$base/media/files").post(body.toString().toRequestBody(jsonMedia)).build()
        return apiCall.execute(request, maxAttempts = 1) { raw -> json.decodeFromString(NewFile.serializer(), raw) }
    }

    suspend fun uploaded(id: String) {
        // No body at all, as the website sends it.
        val request = Request.Builder().url("$base/media/files/$id/upload-complete").post(ByteArray(0).toRequestBody(null)).build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    suspend fun moderation(id: String): Moderation {
        val request = Request.Builder().url("$base/media/files/$id/moderation-status").get().build()
        return apiCall.execute(request) { raw -> json.decodeFromString(Moderation.serializer(), raw) }
    }
}
