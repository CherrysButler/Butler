package com.cherry.butler.core.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    /**
     * The chats tab: one row per character, pinned first, then most recently active. SQLite
     * fills the bare columns from the row that won `MAX(...)`, so the name, avatar and preview
     * are the latest chat's — documented SQLite behaviour, relied on deliberately.
     *
     * [archived] picks the archive or the main list; [folder] narrows to one Janitor folder
     * (and then counts only the chats in it).
     */
    @Query(
        """SELECT c.characterId, c.characterName, c.characterAvatar, c.characterDeleted, c.id AS latestChatId,
           c.lastMessagePreview, c.personaName, MAX(COALESCE(c.lastMessageAt, 0)) AS lastMessageAt, COUNT(*) AS chatCount,
           (m.pinnedAt IS NOT NULL) AS pinned
           FROM chats c LEFT JOIN group_marks m ON m.characterId = c.characterId
           WHERE COALESCE(m.archived, 0) = :archived
             AND (:folder IS NULL OR c.folderIds LIKE '%,' || :folder || ',%')
           GROUP BY c.characterId ORDER BY pinned DESC, m.pinnedAt DESC, lastMessageAt DESC""",
    )
    fun groupedPagingSource(folder: String? = null, archived: Boolean = false): PagingSource<Int, ChatGroupRow>

    /** How many characters are archived; the Archived tab shows only when there are some. */
    @Query("SELECT COUNT(*) FROM group_marks WHERE archived = 1")
    fun observeArchivedCount(): Flow<Int>

    /** The same grouping, only characters whose name contains [query] (case-insensitive for ASCII). */
    @Query(
        """SELECT c.characterId, c.characterName, c.characterAvatar, c.characterDeleted, c.id AS latestChatId,
           c.lastMessagePreview, c.personaName, MAX(COALESCE(c.lastMessageAt, 0)) AS lastMessageAt, COUNT(*) AS chatCount,
           (m.pinnedAt IS NOT NULL) AS pinned
           FROM chats c LEFT JOIN group_marks m ON m.characterId = c.characterId
           WHERE c.characterName LIKE '%' || :query || '%' GROUP BY c.characterId ORDER BY lastMessageAt DESC""",
    )
    fun searchGroupedPagingSource(query: String): PagingSource<Int, ChatGroupRow>

    @Query("SELECT * FROM chats WHERE characterId = :characterId ORDER BY lastMessageAt DESC, id DESC")
    fun observeByCharacter(characterId: String): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE characterId = :characterId ORDER BY lastMessageAt DESC, id DESC LIMIT 1")
    suspend fun latestForCharacter(characterId: String): ChatEntity?

    @Query("SELECT id FROM chats WHERE characterId = :characterId")
    suspend fun idsForCharacter(characterId: String): List<Long>

    @Query("DELETE FROM chats WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    /** What the per-character endpoint knows: activity only; it never carries a name. */
    @Query("UPDATE chats SET lastMessageAt = :lastMessageAt, lastMessagePreview = :lastMessagePreview, messageCount = :messageCount, isPublic = :isPublic, cachedAt = :cachedAt WHERE id = :id")
    suspend fun updateActivity(id: Long, lastMessageAt: Long?, lastMessagePreview: String?, messageCount: Int, isPublic: Boolean, cachedAt: Long): Int

    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun observe(chatId: Long): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun get(chatId: Long): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(chats: List<ChatEntity>)

    /**
     * A list refresh only knows the summary fields. Writing the whole row would reset
     * [ChatEntity.detailLoaded] and drop the summary, so list refreshes go through here
     * and touch only what the list endpoint actually returned.
     */
    @Query(
        """UPDATE chats SET characterName = :characterName, characterAvatar = :characterAvatar,
           characterDeleted = :characterDeleted, lastMessageAt = :lastMessageAt,
           lastMessagePreview = :lastMessagePreview, messageCount = :messageCount,
           isPublic = :isPublic, folderIds = :folderIds, cachedAt = :cachedAt WHERE id = :id""",
    )
    suspend fun updateSummary(
        id: Long,
        characterName: String,
        characterAvatar: String?,
        characterDeleted: Boolean,
        lastMessageAt: Long?,
        lastMessagePreview: String?,
        messageCount: Int,
        isPublic: Boolean,
        folderIds: String,
        cachedAt: Long,
    ): Int

    /** A chat joined or left a folder, here as on Janitor. */
    @Query("UPDATE chats SET folderIds = :folderIds WHERE id = :id")
    suspend fun setFolderIds(id: Long, folderIds: String)

    @Query("SELECT id, folderIds FROM chats WHERE characterId = :characterId")
    suspend fun folderIdsForCharacter(characterId: String): List<ChatFolders>

    @Query("SELECT id, characterId, folderIds, lastMessageAt FROM chats")
    suspend fun allFolderIds(): List<ChatCharacterFolders>

    /** The same, live: every change to a chat's folders (a sync, a move) emits. */
    @Query("SELECT id, characterId, folderIds, lastMessageAt FROM chats")
    fun observeAllFolderIds(): Flow<List<ChatCharacterFolders>>

    /** A folder was deleted on Janitor: no chat is in it any more. */
    @Query("UPDATE chats SET folderIds = REPLACE(folderIds, ',' || :folder || ',', ',') WHERE folderIds LIKE '%,' || :folder || ',%'")
    suspend fun dropFolder(folder: String)

    @Query("UPDATE chats SET lastMessageAt = :at, lastMessagePreview = :preview, messageCount = messageCount + :delta WHERE id = :chatId")
    suspend fun touch(chatId: Long, at: Long, preview: String, delta: Int)

    /** After a delete: the chat's activity is whatever is now last, not an increment. */
    @Query("UPDATE chats SET lastMessageAt = :at, lastMessagePreview = :preview, messageCount = :count WHERE id = :chatId")
    suspend fun setActivity(chatId: Long, at: Long?, preview: String?, count: Int)

    @Query("UPDATE chats SET summary = :summary, summaryChatId = :summaryChatId WHERE id = :chatId")
    suspend fun setSummary(chatId: Long, summary: String?, summaryChatId: Long?)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun delete(chatId: Long)

    @Query("DELETE FROM chats")
    suspend fun clearAll()
}

@Dao
interface MessageDao {

    /**
     * The transcript, oldest first. Local (unconfirmed) rows sort by their device-clock
     * `createdAt`, which places a queued send after the last confirmed message — where it
     * will land once the server accepts it.
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, localId ASC")
    fun observeTranscript(chatId: Long): Flow<List<MessageEntity>>

    /** The transcript as a snapshot, for the pipeline; the UI uses [observeTranscript]. */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, localId ASC")
    suspend fun observeTranscriptOnce(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE localId = :localId")
    suspend fun get(localId: Long): MessageEntity?

    /**
     * Re-anchors a local row after its neighbour was confirmed with the server's clock, so
     * a reply placeholder never sorts above the message it answers.
     */
    @Query("UPDATE messages SET createdAt = :createdAt WHERE localId = :localId AND serverId IS NULL")
    suspend fun setLocalCreatedAt(localId: Long, createdAt: Long)

    @Query("SELECT * FROM messages WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isBot = 1 ORDER BY createdAt DESC, localId DESC LIMIT 1")
    suspend fun lastBotMessage(chatId: Long): MessageEntity?

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    /** The streaming write path: text and reasoning only, coalesced by the caller. */
    @Query("UPDATE messages SET text = :text, thinking = :thinking, streamState = :streamState, cachedAt = :now WHERE localId = :localId")
    suspend fun updateStream(localId: Long, text: String, thinking: String?, streamState: String?, now: Long)

    /** A local row becomes a server row: it now has an id and the server's timestamp. */
    @Query("UPDATE messages SET serverId = :serverId, createdAt = :createdAt, cachedAt = :now WHERE localId = :localId")
    suspend fun confirm(localId: Long, serverId: Long, createdAt: Long, now: Long)

    @Query("UPDATE messages SET generationRequestIds = :ids WHERE localId = :localId")
    suspend fun setGenerationRequestIds(localId: Long, ids: List<String>)

    @Query("UPDATE messages SET rating = :rating WHERE localId = :localId")
    suspend fun updateRating(localId: Long, rating: String?)

    @Query("UPDATE messages SET isMain = :isMain WHERE localId = :localId")
    suspend fun setMain(localId: Long, isMain: Boolean)

    @Query("DELETE FROM messages WHERE localId = :localId")
    suspend fun delete(localId: Long)

    @Query("DELETE FROM messages WHERE chatId IN (:chatIds)")
    suspend fun deleteForChats(chatIds: List<Long>)

    /** The user's own lines Janitor has not accepted yet: what a sign-out would lose. */
    @Query("SELECT COUNT(*) FROM messages WHERE isBot = 0 AND serverId IS NULL")
    suspend fun countUnsent(): Int

    @Query("DELETE FROM messages WHERE localId IN (:localIds)")
    suspend fun deleteLocal(localIds: List<Long>)

    @Query("UPDATE messages SET text = :text, cachedAt = :now WHERE localId = :localId")
    suspend fun updateText(localId: Long, text: String, now: Long)

    /** Bot rows that were stopped before any text arrived and never reached the server. */
    @Query("DELETE FROM messages WHERE chatId = :chatId AND isBot = 1 AND serverId IS NULL AND text = '' AND streamState = 'partial'")
    suspend fun deleteEmptyPartials(chatId: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId AND serverId IS NOT NULL AND serverId NOT IN (:keep)")
    suspend fun deleteServerRowsNotIn(chatId: Long, keep: List<Long>)

    /**
     * The duplicate guard (ARCHITECTURE.md §8.3): before re-posting a send whose outcome
     * is unknown, the caller re-reads the chat and asks whether a matching row landed.
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isBot = :isBot AND serverId IS NOT NULL AND text = :text AND createdAt BETWEEN :from AND :to LIMIT 1")
    suspend fun findConfirmedMatch(chatId: Long, isBot: Boolean, text: String, from: Long, to: Long): MessageEntity?

    /**
     * Folds a server snapshot into the mirror without disturbing local-only rows. Existing
     * server rows keep their `localId` (so Compose keys stay stable) and their reasoning,
     * which the server never returns.
     */
    @Transaction
    suspend fun mergeServerSnapshot(chatId: Long, incoming: List<MessageEntity>) {
        val ids = incoming.mapNotNull { it.serverId }
        if (ids.isNotEmpty()) deleteServerRowsNotIn(chatId, ids)
        for (m in incoming) {
            val existing = m.serverId?.let { getByServerId(it) }
            if (existing == null) {
                insert(m)
            } else {
                update(
                    m.copy(
                        localId = existing.localId,
                        thinking = existing.thinking ?: m.thinking,
                        // Janitor never echoes a rating back (§25); the phone's copy stands.
                        rating = m.rating ?: existing.rating,
                        streamState = null,
                    ),
                )
            }
        }
    }
}

@Dao
interface SendJobDao {

    @Insert
    suspend fun insert(job: SendJobEntity): Long

    @Update
    suspend fun update(job: SendJobEntity)

    @Query("SELECT * FROM send_jobs WHERE id = :id")
    suspend fun get(id: Long): SendJobEntity?

    @Query("SELECT * FROM send_jobs WHERE chatId = :chatId AND state IN (:states) ORDER BY id ASC")
    fun observeActive(chatId: Long, states: Set<String>): Flow<List<SendJobEntity>>

    @Query("SELECT * FROM send_jobs WHERE state IN (:states) ORDER BY id ASC")
    suspend fun allInStates(states: Set<String>): List<SendJobEntity>

    @Query("SELECT * FROM send_jobs WHERE chatId = :chatId AND state IN (:states) ORDER BY id ASC LIMIT 1")
    suspend fun firstActive(chatId: Long, states: Set<String>): SendJobEntity?

    /** Every job of a chat that is still in [states] stops mattering, at once. */
    @Query("UPDATE send_jobs SET state = 'cancelled', updatedAt = :now WHERE chatId = :chatId AND state IN (:states)")
    suspend fun cancelAll(chatId: Long, states: Set<String>, now: Long)

    @Query("DELETE FROM send_jobs WHERE state IN (:states) AND updatedAt < :before")
    suspend fun pruneFinished(states: Set<String>, before: Long)
}

data class ChatFolders(val id: Long, val folderIds: String)

data class ChatCharacterFolders(val id: Long, val characterId: String, val folderIds: String, val lastMessageAt: Long?)

@Dao
interface GroupMarkDao {
    @Query("SELECT * FROM group_marks WHERE characterId = :characterId")
    suspend fun get(characterId: String): GroupMarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mark: GroupMarkEntity)

    @Query("DELETE FROM group_marks WHERE characterId = :characterId")
    suspend fun clear(characterId: String)

    @Query("SELECT * FROM group_marks")
    suspend fun all(): List<GroupMarkEntity>
}
