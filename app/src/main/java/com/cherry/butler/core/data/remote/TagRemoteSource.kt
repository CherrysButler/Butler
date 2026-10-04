package com.cherry.butler.core.data.remote

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.dto.TagDto
import com.cherry.butler.core.network.ApiCall
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The tag vocabulary used by the browse filter.
 *
 * `GET /tags` returns a **bare array**, not the `{data, page, …}` envelope the character
 * endpoints use — verified 2026-09-23. It is small and effectively static, so it is
 * fetched once per session rather than mirrored to Room.
 */
@Singleton
class TagRemoteSource @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
) {
    suspend fun tags(): List<TagDto> {
        val request = Request.Builder()
            .url("${JanitorConfig.BACKEND_BASE}/tags")
            .get()
            .build()
        return apiCall.execute(request) { body ->
            json.decodeFromString(ListSerializer(TagDto.serializer()), body)
        }
    }
}
