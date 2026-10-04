package com.cherry.butler.core.data.remote

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.dto.CharacterChatsDto
import com.cherry.butler.core.data.remote.dto.ChatCreatedDto
import com.cherry.butler.core.data.remote.dto.ChatDetailDto
import com.cherry.butler.core.data.remote.dto.ChatListPageDto
import com.cherry.butler.core.data.remote.dto.MessageDto
import com.cherry.butler.core.data.remote.dto.PostMessageRequest
import com.cherry.butler.core.data.remote.dto.SuccessDto
import com.cherry.butler.core.network.ApiCall
import com.cherry.butler.core.network.ApiError
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.cherry.butler.core.data.remote.dto.FolderDto
import com.cherry.butler.core.data.remote.dto.FoldersDto
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chats and messages on `/mb`. Every path here is verified against the live mobile API
 * (docs/JANITOR_API.md §17.2, §18.4, §24); the write sequence a send needs is spelled out in
 * ARCHITECTURE.md §8 and owned by the outbox, not by callers of this class.
 */
@Singleton
class ChatRemoteSource @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
) {
    private val base = JanitorConfig.BACKEND_BASE
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** `{items, page, pageSize, hasMore}` — page while `hasMore`; there is no total. */
    suspend fun list(page: Int): ChatListPageDto {
        val url = "$base/chats/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .build()
        return apiCall.execute(Request.Builder().url(url).get().build()) { body ->
            json.decodeFromString(ChatListPageDto.serializer(), body)
        }
    }

    // ---- folders (docs/JANITOR_API.md §31; shapes verified 2026-10-04) ----

    suspend fun folders(): FoldersDto =
        apiCall.execute(Request.Builder().url("$base/chats/folders").get().build()) { json.decodeFromString(FoldersDto.serializer(), it) }

    /** 201 with the new folder. */
    suspend fun createFolder(name: String): FolderDto {
        val body = buildJsonObject { put("name", name) }.toString().toRequestBody(jsonMedia)
        return apiCall.execute(Request.Builder().url("$base/chats/folders").post(body).build(), maxAttempts = 1) {
            json.decodeFromString(FolderDto.serializer(), it)
        }
    }

    suspend fun renameFolder(id: String, name: String) {
        val body = buildJsonObject { put("name", name) }.toString().toRequestBody(jsonMedia)
        apiCall.execute(Request.Builder().url("$base/chats/folders/$id").patch(body).build(), maxAttempts = 1) { }
    }

    suspend fun deleteFolder(id: String) {
        apiCall.execute(Request.Builder().url("$base/chats/folders/$id").delete().build(), maxAttempts = 1) { }
    }

    /** `{chatIds: [...]}` → `{added: n}`. */
    suspend fun addToFolder(folderId: String, chatIds: List<Long>) {
        val body = buildJsonObject { put("chatIds", kotlinx.serialization.json.JsonArray(chatIds.map { kotlinx.serialization.json.JsonPrimitive(it) })) }
            .toString().toRequestBody(jsonMedia)
        apiCall.execute(Request.Builder().url("$base/chats/folders/$folderId/chats").post(body).build(), maxAttempts = 1) { }
    }

    suspend fun removeFromFolder(folderId: String, chatId: Long) {
        apiCall.execute(Request.Builder().url("$base/chats/folders/$folderId/chats/$chatId").delete().build(), maxAttempts = 1) { }
    }

    /** A folder's chats, in the same page shape as [list]. */
    suspend fun folderList(folderId: String, page: Int): ChatListPageDto {
        val url = "$base/chats/folders/$folderId/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .addQueryParameter("pageSize", "50")
            .build()
        return apiCall.execute(Request.Builder().url(url).get().build()) { json.decodeFromString(ChatListPageDto.serializer(), it) }
    }

    /**
     * `POST /chats/{chatId}/fork {from_message_id, persona_id?}` → `{id}`: a new chat holding
     * everything up to and including that message (read from the website's code, §31).
     */
    suspend fun fork(chatId: Long, fromMessageId: Long, personaId: String?): Long {
        val body = buildJsonObject {
            put("from_message_id", fromMessageId)
            personaId?.let { put("persona_id", it) }
        }.toString().toRequestBody(jsonMedia)
        return apiCall.execute(Request.Builder().url("$base/chats/$chatId/fork").post(body).build(), maxAttempts = 1) { raw ->
            json.parseToJsonElement(raw).jsonObject["id"]?.jsonPrimitive?.long ?: throw ApiError.Serialization(IllegalStateException("fork returned no id"))
        }
    }

    /** The user's chats with one character, most recent first. */
    suspend fun characterChats(characterId: String, page: Int = 1): CharacterChatsDto {
        val url = "$base/chats/character/$characterId/chats".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .build()
        return apiCall.execute(Request.Builder().url(url).get().build()) { body ->
            json.decodeFromString(CharacterChatsDto.serializer(), body)
        }
    }

    suspend fun detail(chatId: Long): ChatDetailDto {
        val request = Request.Builder().url("$base/chats/$chatId").get().build()
        return apiCall.execute(request) { body ->
            json.decodeFromString(ChatDetailDto.serializer(), body)
        }
    }

    /**
     * `POST /chats {character_id, persona_id?}` — the website sends `persona_id` only when a
     * persona was picked (captured 2026-09-23); without it the profile plays.
     */
    suspend fun create(characterId: String, personaId: String? = null): ChatCreatedDto {
        val payload = buildJsonObject {
            put("character_id", characterId)
            personaId?.let { put("persona_id", it) }
        }.toString()
        val request = Request.Builder()
            .url("$base/chats")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        return apiCall.execute(request) { body ->
            json.decodeFromString(ChatCreatedDto.serializer(), body)
        }
    }

    /**
     * Posts one message. Not idempotent and the server offers no idempotency key, so a
     * timeout here is ambiguous — the outbox resolves that with the duplicate guard, never
     * by calling this twice blindly.
     */
    suspend fun postMessage(chatId: Long, body: PostMessageRequest): MessageDto {
        val payload = json.encodeToString(PostMessageRequest.serializer(), body)
        val request = Request.Builder()
            .url("$base/chats/$chatId/messages")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        return apiCall.execute(request) { raw ->
            // The server answers with a one-element ARRAY, not the object (verified 2026-09-23).
            json.decodeFromString(ListSerializer(MessageDto.serializer()), raw).firstOrNull()
                ?: throw ApiError.Serialization(IllegalStateException("POST /messages returned an empty array"))
        }
    }

    /**
     * Partial update. `{is_main: true}` selects a swipe variant; a fuller body updates the
     * text after `CONTINUE`. Only the keys present are touched, so callers build exactly the
     * patch they mean.
     */
    suspend fun patchMessage(chatId: Long, messageId: Long, patch: JsonObject): Boolean {
        val request = Request.Builder()
            .url("$base/chats/$chatId/messages/$messageId")
            .patch(patch.toString().toRequestBody(jsonMedia))
            .build()
        return apiCall.execute(request) { raw ->
            runCatching { json.decodeFromString(SuccessDto.serializer(), raw).success }.getOrDefault(true)
        }
    }

    /** `PATCH /chats/{id}` — used for `{summary, summary_chat_id}` by the memory feature. */
    suspend fun patchChat(chatId: Long, patch: JsonObject) {
        val request = Request.Builder()
            .url("$base/chats/$chatId")
            .patch(patch.toString().toRequestBody(jsonMedia))
            .build()
        apiCall.execute(request) { }
    }

    /** `PATCH /ratings/{chatId}/messages/{id} {rating}`: an integer 1–5 (§25). */
    suspend fun rateMessage(chatId: Long, messageId: Long, rating: Int) {
        val request = Request.Builder()
            .url("$base/ratings/$chatId/messages/$messageId")
            .patch(buildJsonObject { put("rating", rating) }.toString().toRequestBody(jsonMedia))
            .build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /** `DELETE /chats/{id}`: the whole chat, messages and all. */
    suspend fun deleteChat(chatId: Long) {
        val request = Request.Builder().url("$base/chats/$chatId").delete().build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /**
     * `DELETE /chats/{id}/messages {"message_ids": [...]}` — verified 2026-10-03. There is
     * no per-message route (`/messages/{id}` is a 404); the server caps a call at 256 ids.
     */
    suspend fun deleteMessages(chatId: Long, messageIds: List<Long>) {
        for (chunk in messageIds.distinct().chunked(MAX_DELETE_IDS)) {
            val body = buildJsonObject {
                put("message_ids", buildJsonArray { chunk.forEach { add(JsonPrimitive(it)) } })
            }
            val request = Request.Builder()
                .url("$base/chats/$chatId/messages")
                .delete(body.toString().toRequestBody(jsonMedia))
                .build()
            apiCall.execute(request) { }
        }
    }

    private companion object {
        const val MAX_DELETE_IDS = 256
    }
}
