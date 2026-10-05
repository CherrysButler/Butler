package com.cherry.butler.core.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import androidx.room.withTransaction
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.local.ChatEntity
import com.cherry.butler.core.data.local.ChatGroupRow
import com.cherry.butler.core.data.local.encodeFolderIds
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.local.RemoteKeyEntity
import com.cherry.butler.core.data.remote.ChatRemoteSource
import com.cherry.butler.core.data.remote.dto.ChatDetailDto
import com.cherry.butler.core.data.remote.dto.ChatSummaryDto
import com.cherry.butler.core.data.remote.dto.MessageDto
import com.cherry.butler.core.model.ChatGroup
import com.cherry.butler.core.model.ChatSummary
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.util.IsoTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chats and transcripts, offline-first. The chats tab and every transcript render from
 * Room; the network only ever updates what is already on screen.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val remote: ChatRemoteSource,
    private val db: ButlerDatabase,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()

    /**
     * The chats tab: characters, not chats. The server pages a flat chat list; the mirror
     * folds it per character, so a character with thirty chats is one tile. Paging keeps
     * pulling flat pages behind the grouped view until the server runs out.
     */
    @OptIn(ExperimentalPagingApi::class)
    fun chatGroups(search: String? = null, folder: String? = null, archived: Boolean = false): Flow<PagingData<ChatGroup>> =
        if (!search.isNullOrBlank()) searchGroups(search.trim()) else Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            prefetchDistance = PAGE_SIZE / 2,
            initialLoadSize = PAGE_SIZE,
            enablePlaceholders = false,
        ),
        // Only the main list pages the server; a folder or the archive reads what the mirror holds
        // (a folder is filled by its own listing, see FolderRepository).
        remoteMediator = if (folder == null && !archived) ChatListMediator<ChatGroupRow>(remote, db) else null,
        pagingSourceFactory = { chatDao.groupedPagingSource(folder, archived) },
    ).flow.map { paging -> paging.map(ChatGroupRow::toGroup) }

    /**
     * Characters you've talked to whose name matches [query], from the mirror. The server has
     * no chat search (every parameter tried is ignored), so the screen first calls
     * [fillChatMirror] to bring the rest of the list down.
     */
    private fun searchGroups(query: String): Flow<PagingData<ChatGroup>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { chatDao.searchGroupedPagingSource(query) },
    ).flow.map { paging -> paging.map(ChatGroupRow::toGroup) }

    /** Every chat in a folder, into the mirror, so the folder's tab shows all of them. */
    suspend fun storeFolder(folderId: String, maxPages: Int = 40) {
        var page = 1
        while (page <= maxPages) {
            val response = remote.folderList(folderId, page)
            storeChatRows(db, response.items)
            if (!response.hasMore) break
            page++
        }
    }

    /** Pages the rest of `/chats/list` into the mirror, so a search sees every chat. */
    suspend fun fillChatMirror(maxPages: Int = 80) {
        val keys = db.remoteKeyDao()
        val key = keys.get(CHAT_LIST_KEY)
        var next = when {
            key == null -> 1
            key.hasMore -> key.nextPage ?: return
            else -> return
        }
        repeat(maxPages) {
            val hasMore = storeChatListPage(remote, db, next)
            if (!hasMore) return
            next += 1
        }
    }

    /** Every chat with one character, from the mirror, newest activity first. */
    fun observeChatsWith(characterId: String): Flow<List<ChatSummary>> =
        chatDao.observeByCharacter(characterId).map { rows -> rows.map(ChatEntity::toSummary) }

    /**
     * Reconciles one character's chats with the server. The endpoint carries activity but
     * no character name, so new rows borrow the name from a sibling row; rows the server
     * no longer lists are dropped along with their messages — they were deleted elsewhere.
     */
    suspend fun refreshChatsWith(characterId: String) {
        val response = remote.characterChats(characterId)
        val now = System.currentTimeMillis()
        db.withTransaction {
            val sibling = chatDao.latestForCharacter(characterId)
            for (c in response.chats) {
                val at = IsoTime.parseMillis(c.updatedAt)
                val updated = chatDao.updateActivity(
                    id = c.id, lastMessageAt = at, lastMessagePreview = c.lastMessagePreview,
                    messageCount = c.chatCount, isPublic = c.isPublic, cachedAt = now,
                )
                if (updated == 0) {
                    chatDao.upsert(
                        ChatEntity(
                            id = c.id, characterId = characterId,
                            characterName = c.characterName ?: sibling?.characterName ?: "",
                            characterAvatar = c.characterAvatar ?: sibling?.characterAvatar,
                            characterDeleted = sibling?.characterDeleted ?: false,
                            lastMessageAt = at, lastMessagePreview = c.lastMessagePreview, messageCount = c.chatCount,
                            isPublic = c.isPublic, userId = sibling?.userId ?: "", personaId = c.personaId,
                            defaultPersonaId = null, defaultPersonaAppearance = null, personaName = sibling?.personaName, summary = c.summary,
                            summaryChatId = null, detailLoaded = false, cachedAt = now,
                        ),
                    )
                }
            }
            if (!response.hasMore) {
                val keep = response.chats.map { it.id }.toSet()
                val gone = chatDao.idsForCharacter(characterId).filterNot { it in keep }
                if (gone.isNotEmpty()) {
                    messageDao.deleteForChats(gone)
                    chatDao.deleteAll(gone)
                }
            }
        }
    }

    /** Deletes the chat on Janitor, then from the phone. */
    suspend fun deleteChat(chatId: Long) {
        remote.deleteChat(chatId)
        db.withTransaction {
            messageDao.deleteForChats(listOf(chatId))
            chatDao.delete(chatId)
        }
    }

    fun observeChat(chatId: Long): Flow<ChatEntity?> = chatDao.observe(chatId)

    fun observeTranscript(chatId: Long): Flow<List<MessageEntity>> = messageDao.observeTranscript(chatId)

    suspend fun chat(chatId: Long): ChatEntity? = chatDao.get(chatId)

    /**
     * Pulls the chat and its messages and folds them into the mirror. Local-only rows —
     * a queued send, a reply mid-stream — are untouched, and confirmed rows keep their
     * local ids so the transcript's keys stay stable across a refresh.
     */
    suspend fun refreshChat(chatId: Long): ChatEntity {
        val detail = remote.detail(chatId)
        val now = System.currentTimeMillis()
        val entity = detail.toEntity(existing = chatDao.get(chatId), now = now)
        db.withTransaction {
            chatDao.upsert(entity)
            messageDao.mergeServerSnapshot(chatId, detail.chatMessages.map { it.toEntity(now) })
        }
        return entity
    }

    /**
     * Creates a chat and mirrors it. The server seeds the character's first message as a
     * real row (verified: a fresh chat's detail carries one message), so nothing is
     * synthesised locally.
     */
    suspend fun createChat(characterId: String, persona: PersonaOption? = null): Long {
        val created = remote.create(characterId, persona?.id)
        return try {
            refreshChat(created.id).id
        } catch (e: ApiError) {
            // The chat exists server-side even if the follow-up read failed; a minimal
            // row lets the user open it and the transcript will refresh on its own.
            chatDao.upsert(
                ChatEntity(
                    id = created.id, characterId = characterId, characterName = "", characterAvatar = null,
                    characterDeleted = false, lastMessageAt = IsoTime.parseMillis(created.createdAt),
                    lastMessagePreview = null, messageCount = 0, isPublic = created.isPublic,
                    userId = created.userId, personaId = created.personaId, defaultPersonaId = null,
                    defaultPersonaAppearance = null, personaName = persona?.name, summary = created.summary, summaryChatId = created.summaryChatId,
                    detailLoaded = false, cachedAt = System.currentTimeMillis(),
                ),
            )
            created.id
        }
    }

    /**
     * Shows [chosen] as its turn's reply: on screen at once, then on the server. The server
     * never unsets a variant on its own (see [Variants]), so the others are marked
     * `is_main: false` too and the turn is left with exactly one main reply. Any failed
     * write puts the previous choice back and rethrows.
     */
    suspend fun selectVariant(chatId: Long, variants: List<MessageEntity>, chosen: MessageEntity) {
        val before = variants.associate { it.localId to it.isMain }
        db.withTransaction {
            for (v in variants) messageDao.setMain(v.localId, v.localId == chosen.localId)
        }
        try {
            chosen.serverId?.let { remote.patchMessage(chatId, it, buildJsonObject { put("is_main", true) }) }
            for (v in variants) {
                if (v.localId != chosen.localId && v.isMain) {
                    v.serverId?.let { remote.patchMessage(chatId, it, buildJsonObject { put("is_main", false) }) }
                }
            }
        } catch (e: ApiError) {
            db.withTransaction {
                for (v in variants) messageDao.setMain(v.localId, before[v.localId] == true)
            }
            throw e
        }
    }

    /**
     * Rewrites a message's text. The server is asked first — `PATCH {message}` alone,
     * verified 2026-10-03 — and the mirror changes only once it agreed, so a failed edit
     * never leaves the phone showing words Janitor does not have.
     */
    suspend fun rateMessage(chatId: Long, message: MessageEntity, rating: Int) {
        val serverId = message.serverId ?: return
        remote.rateMessage(chatId, serverId, rating)
        messageDao.updateRating(message.localId, rating.toString())
    }

    suspend fun editMessage(chatId: Long, message: MessageEntity, text: String) {
        message.serverId?.let { remote.patchMessage(chatId, it, buildJsonObject { put("message", text) }) }
        messageDao.updateText(message.localId, text, System.currentTimeMillis())
        val transcript = messageDao.observeTranscriptOnce(chatId)
        if (transcript.lastOrNull()?.localId == message.localId) {
            chatDao.setActivity(chatId, message.createdAt, text.take(160), transcript.size)
        }
    }

    /**
     * Makes the intro picked on screen the chat's opening line, at the first send. The
     * phone's copy changes at once, so the reply being asked for already answers it; Janitor
     * is told by [pushIntro] (`PATCH {message}`, verified 2026-10-05).
     */
    suspend fun keepIntroLocally(chatId: Long, opening: MessageEntity, text: String) {
        messageDao.updateText(opening.localId, text, System.currentTimeMillis())
        chatDao.setActivity(chatId, opening.createdAt, text.take(160), 1)
    }

    suspend fun pushIntro(chatId: Long, opening: MessageEntity, text: String) {
        opening.serverId?.let { remote.patchMessage(chatId, it, buildJsonObject { put("message", text) }) }
    }

    /**
     * Deletes [from] and everything after it, the way the official client does. A reply
     * goes with all of its variants, so deleting what is on screen never uncovers an
     * alternate underneath. Returns the number of lines removed.
     */
    suspend fun deleteFrom(chatId: Long, from: MessageEntity): Int {
        val transcript = messageDao.observeTranscriptOnce(chatId)
        var start = transcript.indexOfFirst { it.localId == from.localId }
        if (start < 0) return 0
        if (from.isBot) while (start > 0 && transcript[start - 1].isBot) start--
        val doomed = transcript.subList(start, transcript.size)
        val serverIds = doomed.mapNotNull { it.serverId }
        if (serverIds.isNotEmpty()) remote.deleteMessages(chatId, serverIds)
        db.withTransaction {
            messageDao.deleteLocal(doomed.map { it.localId })
            val last = transcript.getOrNull(start - 1)
            chatDao.setActivity(chatId, last?.createdAt, last?.text?.take(160), start)
        }
        return doomed.size
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

// ---- mapping ----------------------------------------------------------------------

private fun ChatEntity.toSummary() = ChatSummary(
    id = id,
    characterId = characterId,
    characterName = characterName,
    characterAvatar = characterAvatar,
    characterDeleted = characterDeleted,
    lastMessageAt = lastMessageAt,
    lastMessagePreview = lastMessagePreview,
    messageCount = messageCount,
    personaName = personaName,
)

private fun ChatGroupRow.toGroup() = ChatGroup(
    characterId = characterId,
    characterName = characterName,
    characterAvatar = characterAvatar,
    characterDeleted = characterDeleted,
    latestChatId = latestChatId,
    lastMessageAt = lastMessageAt.takeIf { it > 0 },
    lastMessagePreview = lastMessagePreview,
    personaName = personaName,
    chatCount = chatCount,
    pinned = pinned,
)

private fun ChatDetailDto.toEntity(existing: ChatEntity?, now: Long): ChatEntity {
    val last = chatMessages.maxByOrNull { IsoTime.parseMillis(it.createdAt) ?: 0L }
    val defaultPersona = personas.firstOrNull()
    val persona = personas.firstOrNull { it.id == chat.personaId } ?: defaultPersona
    return ChatEntity(
        id = chat.id,
        characterId = chat.characterId,
        // The title and `chat_name` (the name it goes by in chat) are kept apart: lists
        // refresh the title, and the chat shows the chat name over it (ChatEntity.shownName).
        characterName = character.name,
        chatName = character.chatName?.takeIf { it.isNotBlank() } ?: existing?.chatName,
        characterAvatar = character.avatar,
        characterDeleted = character.isDeleted || character.isForceRemoved,
        lastMessageAt = IsoTime.parseMillis(last?.createdAt) ?: existing?.lastMessageAt,
        lastMessagePreview = last?.message?.take(160) ?: existing?.lastMessagePreview,
        messageCount = chatMessages.size,
        isPublic = chat.isPublic,
        userId = chat.userId,
        personaId = chat.personaId,
        defaultPersonaId = defaultPersona?.id,
        defaultPersonaAppearance = defaultPersona?.appearance,
        personaName = persona?.name?.takeIf { it.isNotBlank() } ?: existing?.personaName,
        summary = chat.summary,
        summaryChatId = chat.summaryChatId,
        detailLoaded = true,
        cachedAt = now,
        // The detail doesn't carry folders; the row is replaced whole, so keep what the list said.
        folderIds = existing?.folderIds.orEmpty(),
        intros = character.firstMessages.filterNotNull().filter { it.isNotBlank() }
            .ifEmpty { listOfNotNull(character.firstMessage?.takeIf { it.isNotBlank() }) },
    )
}

private fun MessageDto.toEntity(now: Long) = MessageEntity(
    serverId = id,
    chatId = chatId,
    isBot = isBot,
    isMain = isMain,
    text = message,
    createdAt = IsoTime.parseMillis(createdAt) ?: now,
    rating = rating?.toString()?.takeIf { it != "null" },
    personaId = metadata?.personaId,
    generationRequestIds = metadata?.generationRequestIdStrings.orEmpty(),
    thinking = null,
    streamState = null,
    cachedAt = now,
)

/**
 * Pages `/chats/list` into the mirror. A refresh merges page 1 over what is on disk rather
 * than replacing it: rows beyond page 1 stay valid, and a row that already has its detail
 * keeps it — only the summary fields the list endpoint returns are touched.
 */
@OptIn(ExperimentalPagingApi::class)
private class ChatListMediator<T : Any>(
    private val remote: ChatRemoteSource,
    private val db: ButlerDatabase,
) : RemoteMediator<Int, T>() {

    private val chatDao = db.chatDao()
    private val remoteKeyDao = db.remoteKeyDao()

    override suspend fun initialize(): InitializeAction {
        val key = remoteKeyDao.get(KEY) ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        val age = System.currentTimeMillis() - key.lastRefreshedAt
        return if (age > FRESH_MS) InitializeAction.LAUNCH_INITIAL_REFRESH else InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, T>): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> 1
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val key = remoteKeyDao.get(KEY) ?: return MediatorResult.Success(endOfPaginationReached = true)
                if (!key.hasMore) return MediatorResult.Success(endOfPaginationReached = true)
                key.nextPage ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }
        return try {
            val hasMore = storeChatListPage(remote, db, page)
            MediatorResult.Success(endOfPaginationReached = !hasMore)
        } catch (e: ApiError) {
            MediatorResult.Error(e)
        }
    }

    private companion object {
        const val KEY = CHAT_LIST_KEY
        const val FRESH_MS = 60 * 1000L
    }
}

private const val CHAT_LIST_KEY = "chats:list"

/**
 * One page of `/chats/list` into the mirror, merged over what is there (rows with their
 * detail keep it), and the paging key moved on. Returns whether more pages follow.
 */
private suspend fun storeChatListPage(remote: ChatRemoteSource, db: ButlerDatabase, page: Int): Boolean {
    val response = remote.list(page)
    storeChatRows(db, response.items)
    val now = System.currentTimeMillis()
    db.withTransaction {
        db.remoteKeyDao().upsert(
            RemoteKeyEntity(
                queryKey = CHAT_LIST_KEY,
                nextPage = if (response.hasMore) page + 1 else null,
                hasMore = response.hasMore,
                lastRefreshedAt = now,
            ),
        )
    }
    return response.hasMore
}

/** List rows into the mirror, touching only what a list row carries (folders included). */
private suspend fun storeChatRows(db: ButlerDatabase, items: List<ChatSummaryDto>) {
    val chatDao = db.chatDao()
    val now = System.currentTimeMillis()
    db.withTransaction {
        for (item in items) {
            val updated = chatDao.updateSummary(
                id = item.id,
                characterName = item.character.name,
                characterAvatar = item.character.avatar,
                characterDeleted = item.character.isDeleted || item.character.isForceRemoved,
                lastMessageAt = IsoTime.parseMillis(item.lastMessageAt),
                lastMessagePreview = item.lastMessagePreview,
                messageCount = item.messageCount,
                isPublic = item.isPublic,
                folderIds = encodeFolderIds(item.folderIds),
                cachedAt = now,
            )
            if (updated == 0) chatDao.upsert(item.toEntity(now))
        }
    }
}

private fun ChatSummaryDto.toEntity(now: Long) = ChatEntity(
    id = id,
    characterId = character.id,
    characterName = character.name,
    characterAvatar = character.avatar,
    characterDeleted = character.isDeleted || character.isForceRemoved,
    lastMessageAt = IsoTime.parseMillis(lastMessageAt),
    lastMessagePreview = lastMessagePreview,
    messageCount = messageCount,
    isPublic = isPublic,
    userId = "",
    personaId = null,
    defaultPersonaId = null,
    defaultPersonaAppearance = null,
    summary = null,
    summaryChatId = null,
    detailLoaded = false,
    cachedAt = now,
    folderIds = encodeFolderIds(folderIds),
)
