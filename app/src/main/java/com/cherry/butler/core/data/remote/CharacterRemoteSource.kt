package com.cherry.butler.core.data.remote

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.dto.CharacterDetailDto
import com.cherry.butler.core.data.remote.dto.CharacterPageDto
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.BrowseSource
import com.cherry.butler.core.network.ApiCall
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the character catalogue.
 *
 * The query parameters here are the verified ones, which are **not** what the
 * reverse-engineered notes originally said (`docs/JANITOR_API.md` §4.1). The two that were wrong
 * failed *silently* — the server returns an unfiltered page 1 rather than an error — so
 * a mistake here looks like a working screen that ignores its own filters. Change these
 * names only against a live response.
 */
@Singleton
class CharacterRemoteSource @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
) {

    suspend fun browse(query: BrowseQuery, page: Int): CharacterPageDto {
        val url = "${JanitorConfig.BACKEND_BASE}/characters".toHttpUrl().newBuilder().apply {
            // 1-indexed. page=0 is a 400.
            addQueryParameter("page", page.coerceAtLeast(1).toString())
            addQueryParameter("sort", query.sort.wire)
            // Trending windows and Hidden Gems; the server applies it instead of `sort`.
            query.special?.let { addQueryParameter("special_mode", it.wire) }
            addQueryParameter("mode", query.mode.wire)
            when (query.source) {
                BrowseSource.Following -> addQueryParameter("following", "true")
                BrowseSource.Favorites -> addQueryParameter("favorites", "true")
                BrowseSource.All -> Unit
            }
            query.search?.takeIf { it.isNotBlank() }?.let { addQueryParameter("search", it.trim()) }
            // Bracket form, verified 2026-09-23. The bare `tag_id=N` form is a 400
            // ("tag_id must contain ... elements") when exactly one tag is sent — the
            // validator wants an array and a single bare value isn't parsed as one.
            // `tag_id[]` works for one or many, and is what the web client sends.
            query.tagIds.forEach { addQueryParameter("tag_id[]", it.toString()) }
        }.build()

        val request = Request.Builder().url(url).get().build()
        return apiCall.execute(request) { body ->
            json.decodeFromString(CharacterPageDto.serializer(), body)
        }
    }

    /**
     * The full character. Mobile's route is the **plural** `/characters/{id}` — the
     * singular `/character/{id}` documented from the bundle is a 404 (docs/JANITOR_API.md §24).
     */
    suspend fun detail(characterId: String): CharacterDetailDto {
        val request = Request.Builder()
            .url("${JanitorConfig.BACKEND_BASE}/characters/$characterId")
            .get()
            .build()
        return apiCall.execute(request) { body ->
            json.decodeFromString(CharacterDetailDto.serializer(), body)
        }
    }

    companion object {
        /**
         * Server-fixed page size (`size` is accepted but ignored). Paging's page size must
         * match, or Paging 3 sees every page as "short" and stops after the first.
         */
        const val PAGE_SIZE = 34
    }
}
