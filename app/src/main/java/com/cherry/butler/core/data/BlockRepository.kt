package com.cherry.butler.core.data

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.TagRemoteSource
import com.cherry.butler.core.data.remote.dto.CharacterPageDto
import com.cherry.butler.core.data.remote.dto.TagDto
import com.cherry.butler.core.network.ApiCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** `GET /profiles/mine/blocked-content` (verified 2026-10-04). */
@Serializable
data class BlockedContentDto(
    /** Character ids. Janitor calls characters "bots" here. */
    val bots: List<String> = emptyList(),
    /** Sent as user ids; answered as these objects. */
    val creators: List<BlockedCreatorDto> = emptyList(),
    val keywords: List<String> = emptyList(),
    /** Tag ids, ints like everywhere else. */
    val tags: List<Int> = emptyList(),
)

@Serializable
data class BlockedCreatorDto(
    val id: String,
    val name: String? = null,
    @SerialName("user_name") val userName: String? = null,
    val avatar: String? = null,
)

data class BlockedCharacter(val id: String, val name: String, val avatarUrl: String?)

data class Blocks(
    val characters: List<BlockedCharacter>,
    val creators: List<BlockedCreatorDto>,
    val tags: List<TagDto>,
    val keywords: List<String>,
)

/**
 * What the user has blocked on Janitor: characters, creators, tags and keywords. The list
 * lives on the profile as `block_list` and is written whole (`PATCH /profiles/mine
 * {block_list: {bots, creators, keywords, tags}}`), so every change reads the current list
 * first and sends it back with one thing added or taken out.
 */
@Singleton
class BlockRepository @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
    private val tags: TagRemoteSource,
) {
    private val base = JanitorConfig.BACKEND_BASE
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** The blocked characters as last fetched, for the bot ids they were fetched for. */
    private var charactersFor: List<String>? = null
    private var characters: List<BlockedCharacter> = emptyList()
    private var tagCatalogue: List<TagDto> = emptyList()

    /**
     * Everything blocked. [known] is a list just written (a change hands it back), saving a
     * read; the paged character list is fetched again only when the blocked ids changed.
     */
    suspend fun load(known: BlockedContentDto? = null): Blocks {
        val content = known ?: content()
        val characters = when {
            content.bots.isEmpty() -> emptyList()
            content.bots == charactersFor -> characters
            else -> blockedCharacters().also { characters = it; charactersFor = content.bots }
        }
        if (content.tags.isNotEmpty() && tagCatalogue.isEmpty()) tagCatalogue = runCatching { tags.tags() }.getOrDefault(emptyList())
        val allTags = tagCatalogue
        return Blocks(
            characters = characters,
            creators = content.creators,
            tags = content.tags.map { id -> allTags.firstOrNull { it.id == id } ?: TagDto(id = id, name = "Tag $id") },
            keywords = content.keywords,
        )
    }

    suspend fun allTags(): List<TagDto> = tags.tags()

    suspend fun isCharacterBlocked(id: String): Boolean = id in content().bots

    suspend fun setCharacter(id: String, blocked: Boolean) = change { it.copy(bots = it.bots.toggled(id, blocked)) }

    suspend fun setCreator(userId: String, blocked: Boolean) = change { c ->
        c.copy(creators = if (blocked) c.creators.filterNot { it.id == userId } + BlockedCreatorDto(userId) else c.creators.filterNot { it.id == userId })
    }

    suspend fun setTag(id: Int, blocked: Boolean) = change { it.copy(tags = it.tags.toggled(id, blocked)) }

    suspend fun setKeyword(word: String, blocked: Boolean) = change { it.copy(keywords = it.keywords.toggled(word.trim(), blocked)) }

    private suspend fun content(): BlockedContentDto {
        val request = Request.Builder().url("$base/profiles/mine/blocked-content").get().build()
        return apiCall.execute(request) { json.decodeFromString(BlockedContentDto.serializer(), it) }
    }

    /** `GET /characters/v2/blocked?page=` — the character envelope; paged until a short page. */
    private suspend fun blockedCharacters(): List<BlockedCharacter> {
        val all = ArrayList<BlockedCharacter>()
        var page = 1
        while (page <= MAX_PAGES) {
            val request = Request.Builder().url("$base/characters/v2/blocked?page=$page").get().build()
            val dto = apiCall.execute(request) { json.decodeFromString(CharacterPageDto.serializer(), it) }
            dto.data.mapTo(all) { BlockedCharacter(it.id, it.name, JanitorConfig.avatarUrl(it.avatar)) }
            // `size` is the page size; a page shorter than it is the last.
            if (dto.data.size < (dto.size.takeIf { it > 0 } ?: PAGE_FLOOR)) break
            page++
        }
        return all
    }

    /** Reads the list, changes one thing, writes it whole; returns what was written. */
    private suspend fun change(edit: (BlockedContentDto) -> BlockedContentDto): BlockedContentDto {
        val next = edit(content())
        val body = buildJsonObject {
            put("block_list", buildJsonObject {
                put("bots", buildJsonArray { next.bots.forEach { add(JsonPrimitive(it)) } })
                put("creators", buildJsonArray { next.creators.forEach { add(JsonPrimitive(it.id)) } })
                put("keywords", buildJsonArray { next.keywords.forEach { add(JsonPrimitive(it)) } })
                put("tags", buildJsonArray { next.tags.forEach { add(JsonPrimitive(it)) } })
            })
        }
        val request = Request.Builder().url("$base/profiles/mine").patch(body.toString().toRequestBody(jsonMedia)).build()
        apiCall.execute(request, maxAttempts = 1) { }
        return next
    }

    private fun <T> List<T>.toggled(item: T, on: Boolean): List<T> = if (on) (this - item) + item else this - item

    private companion object {
        const val MAX_PAGES = 20
        /** When the response doesn't say its page size. */
        const val PAGE_FLOOR = 80
    }
}
