package com.cherry.butler.core.data

import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.local.CharacterEntity
import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.data.remote.ChatRemoteSource
import com.cherry.butler.core.data.remote.dto.CharacterChatDto
import com.cherry.butler.core.data.remote.dto.CharacterDetailDto
import com.cherry.butler.core.model.CharacterChat
import com.cherry.butler.core.model.CharacterDetail
import com.cherry.butler.core.model.Lorebook
import com.cherry.butler.core.util.IsoTime
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One character in full. The browse mirror already holds enough to paint the detail
 * screen instantly ([observeMirror]); the full object is fetched behind it and cached in
 * memory for the session — definitions rarely change and a per-open fetch is enough.
 */
@Singleton
class CharacterDetailRepository @Inject constructor(
    private val remote: CharacterRemoteSource,
    private val chatRemote: ChatRemoteSource,
    db: ButlerDatabase,
) {
    private val characterDao = db.characterDao()
    private val cache = ConcurrentHashMap<String, CharacterDetail>()

    fun observeMirror(characterId: String): Flow<CharacterEntity?> = characterDao.observeAny(characterId)

    fun cached(characterId: String): CharacterDetail? = cache[characterId]

    suspend fun detail(characterId: String, refresh: Boolean = false): CharacterDetail {
        if (!refresh) cache[characterId]?.let { return it }
        return remote.detail(characterId).toDomain().also { cache[characterId] = it }
    }

    suspend fun chatsWith(characterId: String): List<CharacterChat> =
        chatRemote.characterChats(characterId).chats.map { it.toDomain() }
}

private fun CharacterDetailDto.toDomain() = CharacterDetail(
    id = id,
    name = name,
    chatName = chatName?.takeIf { it.isNotBlank() },
    avatar = avatar,
    description = description,
    personality = personality?.takeIf { it.isNotBlank() },
    scenario = scenario?.takeIf { it.isNotBlank() },
    exampleDialogs = exampleDialogs?.takeIf { it.isNotBlank() },
    firstMessages = firstMessages.filterNotNull().filter { it.isNotBlank() }.ifEmpty { listOfNotNull(firstMessage) },
    totalTokens = tokenCounts?.total ?: 0,
    tagNames = tags.map { it.name },
    customTags = customTags.orEmpty(),
    chatCount = stats.chat,
    messageCount = stats.message,
    creatorId = creatorId,
    creatorName = creatorName,
    creatorVerified = creatorVerified,
    isNsfw = isNsfw,
    isImageNsfw = isImageNsfw,
    allowProxy = allowProxy,
    showDefinition = showdefinition || showDefinitionOverride,
    createdAt = IsoTime.parseMillis(createdAt),
    lorebooks = scripts.map { Lorebook(it.id, it.title.ifBlank { "Lorebook" }, it.isPublic, IsoTime.parseMillis(it.updatedAt), it.messageCount) },
)

private fun CharacterChatDto.toDomain() = CharacterChat(
    id = id,
    lastMessagePreview = lastMessagePreview,
    messageCount = chatCount,
    updatedAt = IsoTime.parseMillis(updatedAt),
    personaId = personaId,
)
