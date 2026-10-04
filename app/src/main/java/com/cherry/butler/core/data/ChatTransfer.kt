package com.cherry.butler.core.data

import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.remote.ChatRemoteSource
import com.cherry.butler.core.data.remote.dto.PostMessageRequest
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.util.IsoTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** One line of a conversation on its way out of, or into, a chat. */
data class TransferLine(val isBot: Boolean, val text: String, val time: Long? = null)

/** A conversation as a file holds it. [characterId] only when Butler wrote the file. */
data class TransferChat(
    val characterId: String?,
    val characterName: String,
    val personaName: String,
    val lines: List<TransferLine>,
)

/**
 * The two file shapes Butler reads and writes: its own JSON, and SillyTavern's JSONL (a
 * header line `{user_name, character_name, create_date, chat_metadata}`, then one line per
 * message `{name, is_user, is_system, send_date, mes, extra}`).
 */
object ChatFormats {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val line = Json { ignoreUnknownKeys = true }

    const val BUTLER = "butler.chat"

    fun toButler(chat: TransferChat, exportedAt: Long): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", BUTLER)
            put("version", 1)
            put("exported_at", IsoTime.format(exportedAt))
            put("character", buildJsonObject {
                chat.characterId?.let { put("id", it) }
                put("name", chat.characterName)
            })
            put("persona", chat.personaName)
            put("messages", buildJsonArray {
                for (l in chat.lines) add(buildJsonObject {
                    put("role", if (l.isBot) "character" else "user")
                    put("text", l.text)
                    l.time?.let { put("time", IsoTime.format(it)) }
                })
            })
        },
    )

    fun toSillyTavern(chat: TransferChat, exportedAt: Long): String = buildString {
        appendLine(
            line.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("user_name", chat.personaName)
                    put("character_name", chat.characterName)
                    put("create_date", IsoTime.format(exportedAt))
                    put("chat_metadata", JsonObject(emptyMap()))
                },
            ),
        )
        for (l in chat.lines) appendLine(
            line.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("name", if (l.isBot) chat.characterName else chat.personaName)
                    put("is_user", !l.isBot)
                    put("is_system", false)
                    put("send_date", IsoTime.format(l.time ?: exportedAt))
                    put("mes", l.text)
                    put("extra", JsonObject(emptyMap()))
                },
            ),
        )
    }

    /** Either shape, recognised by its content. Throws [IllegalArgumentException] for anything else. */
    fun parse(text: String): TransferChat {
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
            ?.takeIf { it["format"]?.jsonPrimitive?.contentOrNull == BUTLER }
            ?.let { return parseButler(it) }
        return parseSillyTavern(trimmed)
    }

    private fun parseButler(o: JsonObject): TransferChat {
        val character = o["character"]?.jsonObject
        val lines = o["messages"]?.jsonArray.orEmpty().mapNotNull { e ->
            val m = e as? JsonObject ?: return@mapNotNull null
            val text = m["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TransferLine(m["role"]?.jsonPrimitive?.contentOrNull == "character", text, m["time"]?.jsonPrimitive?.contentOrNull?.let(IsoTime::parseMillis))
        }
        require(lines.isNotEmpty()) { "The file has no messages." }
        return TransferChat(
            characterId = character?.get("id")?.jsonPrimitive?.contentOrNull,
            characterName = character?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
            personaName = o["persona"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            lines = lines,
        )
    }

    private fun parseSillyTavern(text: String): TransferChat {
        var user = ""
        var character = ""
        val lines = ArrayList<TransferLine>()
        for (raw in text.lineSequence()) {
            val o = runCatching { line.parseToJsonElement(raw.trim()).jsonObject }.getOrNull() ?: continue
            if ("mes" !in o) {
                o["user_name"]?.jsonPrimitive?.contentOrNull?.let { user = it }
                o["character_name"]?.jsonPrimitive?.contentOrNull?.let { character = it }
                continue
            }
            if (o["is_system"]?.jsonPrimitive?.booleanOrNull == true) continue
            val mes = o["mes"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            val isUser = o["is_user"]?.jsonPrimitive?.booleanOrNull == true
            lines += TransferLine(isBot = !isUser, text = mes, time = o["send_date"]?.jsonPrimitive?.contentOrNull?.let(IsoTime::parseMillis))
        }
        require(lines.isNotEmpty()) { "That isn't a Butler or SillyTavern chat file." }
        return TransferChat(null, character, user, lines)
    }
}

/**
 * Moves conversations in and out of Janitor: export a chat, rebuild one from a file, and
 * branch. Rebuilding is what lets a branch work where Janitor's own fork won't (a deleted,
 * moderated or private character): a new chat, then every line posted in order. Janitor
 * seeds each new chat with the character's greeting; the copy's first reply takes its place
 * (or the greeting goes, when the copy opens with the user's line).
 */
@Singleton
class ChatTransfer @Inject constructor(
    private val remote: ChatRemoteSource,
    private val chats: ChatRepository,
    db: ButlerDatabase,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()

    /** The chat as it reads on screen: the shown reply of each turn, oldest first. */
    suspend fun export(chatId: Long): TransferChat {
        runCatching { chats.refreshChat(chatId) }
        val chat = chatDao.get(chatId) ?: throw ApiError.Api(404, "BUTLER_NO_CHAT", serverMessage = "That chat isn't on this phone.")
        val rows = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.text.isNotBlank() }
        return TransferChat(
            characterId = chat.characterId,
            characterName = chat.characterName,
            personaName = chat.personaName.orEmpty(),
            lines = rows.map { TransferLine(it.isBot, it.text, it.createdAt) },
        )
    }

    /**
     * A new chat with [characterId] holding [lines]. [progress] gets (written, total).
     * Returns the new chat's id once it is in the mirror.
     */
    suspend fun rebuild(characterId: String, personaId: String?, lines: List<TransferLine>, progress: (Int, Int) -> Unit = { _, _ -> }): Long {
        require(lines.isNotEmpty()) { "Nothing to copy." }
        val created = remote.create(characterId, personaId)
        val seeded = remote.detail(created.id).chatMessages.firstOrNull { it.isBot }
        var done = 0
        progress(done, lines.size)
        lines.forEachIndexed { i, line ->
            if (i == 0 && line.isBot && seeded != null) {
                remote.patchMessage(created.id, seeded.id, buildJsonObject {
                    put("id", seeded.id)
                    put("chat_id", created.id)
                    put("character_id", characterId)
                    seeded.createdAt?.let { put("created_at", it) }
                    put("is_bot", true)
                    put("is_main", true)
                    put("message", line.text)
                    put("rating", JsonNull)
                    put("metadata", buildJsonObject { put("generation_request_ids", JsonArray(emptyList())) })
                })
            } else {
                remote.postMessage(
                    created.id,
                    PostMessageRequest(
                        isBot = line.isBot,
                        isMain = true,
                        message = line.text,
                        metadata = if (line.isBot) {
                            buildJsonObject { put("generation_request_ids", JsonArray(emptyList())) }
                        } else {
                            buildJsonObject { put("persona_id", personaId?.let(::JsonPrimitive) ?: JsonNull) }
                        },
                        characterId = characterId,
                        chatId = created.id,
                        createdAt = if (line.isBot) IsoTime.format(System.currentTimeMillis()) else null,
                        rating = JsonNull,
                    ),
                )
            }
            progress(++done, lines.size)
        }
        if (seeded != null && !lines.first().isBot) remote.deleteMessages(created.id, listOf(seeded.id))
        return chats.refreshChat(created.id).id
    }

    /**
     * A new chat holding everything up to and including [upTo]. Janitor's own fork first;
     * when it refuses (the character is deleted, moderated or private), the conversation is
     * rebuilt line by line. Returns the new chat's id.
     */
    suspend fun branch(chatId: Long, upTo: MessageEntity, progress: (Int, Int) -> Unit = { _, _ -> }): Long {
        val chat = chatDao.get(chatId) ?: throw ApiError.Api(404, "BUTLER_NO_CHAT", serverMessage = "That chat isn't on this phone.")
        val serverId = upTo.serverId
        if (serverId != null) {
            val forked = runCatching { remote.fork(chatId, serverId, chat.personaId) }
                .onFailure { android.util.Log.w("ChatTransfer", "fork refused, copying instead: ${it.message}") }
                .getOrNull()
            if (forked != null) return chats.refreshChat(forked).id
        }
        val rows = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.text.isNotBlank() }
        val until = rows.indexOfFirst { it.localId == upTo.localId }.takeIf { it >= 0 } ?: rows.lastIndex
        return rebuild(chat.characterId, chat.personaId, rows.take(until + 1).map { TransferLine(it.isBot, it.text, it.createdAt) }, progress)
    }
}
