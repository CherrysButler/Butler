package com.cherry.butler.core.data

import androidx.room.withTransaction
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.local.GroupMarkEntity
import com.cherry.butler.core.data.local.decodeFolderIds
import com.cherry.butler.core.data.local.encodeFolderIds
import com.cherry.butler.core.data.remote.ChatRemoteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** A Janitor chat folder (docs/JANITOR_API.md §31). */
data class ChatFolder(val id: String, val name: String, val chatCount: Int)

/**
 * The chats tab's organisation. Folders are Janitor's own (`/chats/folders`, verified
 * 2026-10-04), so they match the website and the official app; a character's row goes into
 * a folder by putting all of that character's chats in it.
 *
 * Pinning and archiving have no route of their own, so they are two folders on Janitor too,
 * named [PINNED_FOLDER] and [ARCHIVE_FOLDER], made the first time they are needed and kept
 * out of Butler's folder tabs. The phone's [GroupMarkEntity] rows are a projection of that
 * membership for the list queries: [reconcile] rebuilds them from the chats' `folder_ids`
 * after every folder refresh, so a fresh sign-in, another phone or the website all agree,
 * and signing out loses nothing.
 */
@Singleton
class FolderRepository @Inject constructor(
    private val remote: ChatRemoteSource,
    private val db: ButlerDatabase,
    private val chats: ChatRepository,
) {
    private val chatDao = db.chatDao()
    private val marks = db.groupMarkDao()

    private val _folders = MutableStateFlow<List<ChatFolder>>(emptyList())
    val folders: StateFlow<List<ChatFolder>> = _folders.asStateFlow()

    val archivedCount: Flow<Int> = chatDao.observeArchivedCount()

    /** Every chat's folders, live. Whoever shows the chats tab collects it and calls [reconcile] on each change. */
    val membership: Flow<List<com.cherry.butler.core.data.local.ChatCharacterFolders>> = chatDao.observeAllFolderIds()

    /** Butler's own two folders are organisation, not places: they don't get a tab or a picker row. */
    fun isSpecial(folder: ChatFolder): Boolean = folder.name.equals(ARCHIVE_FOLDER, true) || folder.name.equals(PINNED_FOLDER, true)

    suspend fun refresh() {
        _folders.value = remote.folders().folders.map { ChatFolder(it.id, it.name, it.chatCount) }
        runCatching { reconcile() }
    }

    /**
     * The phone's pin and archive marks, rebuilt from what Janitor's folders say. Janitor
     * wins: a character archived on the website shows archived here, and one taken out of
     * the Archive folder there comes back here. A pin keeps its time if it already had one.
     *
     * A character's standing is read from its most recent chat, the row the list shows and
     * the sync keeps current; an older chat on a page never fetched may still carry a folder
     * it has since left, and must not hold the character in place.
     */
    suspend fun reconcile() {
        val archive = specialId(ARCHIVE_FOLDER)
        val pinned = specialId(PINNED_FOLDER)
        if (archive == null && pinned == null) return
        val rows = chatDao.allFolderIds()
        val latest = rows.groupBy { it.characterId }.mapValues { (_, list) -> list.maxByOrNull { it.lastMessageAt ?: 0L }!! }
        val archived = HashSet<String>()
        val pins = HashSet<String>()
        for ((character, r) in latest) {
            val ids = decodeFolderIds(r.folderIds)
            if (archive != null && archive in ids) archived += character
            if (pinned != null && pinned in ids) pins += character
        }
        val known = latest.keys
        val marks = marks.all().associateBy { it.characterId }
        db.withTransaction {
            for (c in known) {
                val m = marks[c]
                val wantArchived = c in archived
                val wantPinnedAt = if (c in pins && !wantArchived) (m?.pinnedAt ?: System.currentTimeMillis()) else null
                if ((m?.archived ?: false) != wantArchived || m?.pinnedAt != wantPinnedAt) save(c, wantPinnedAt, wantArchived)
            }
        }
    }

    private fun specialId(name: String): String? = _folders.value.firstOrNull { it.name.equals(name, true) }?.id

    /** The folder's id, made on Janitor if it isn't there yet. */
    private suspend fun specialFolder(name: String): String {
        if (_folders.value.isEmpty()) runCatching { _folders.value = remote.folders().folders.map { ChatFolder(it.id, it.name, it.chatCount) } }
        return specialId(name) ?: create(name).id
    }

    suspend fun create(name: String): ChatFolder {
        val made = remote.createFolder(name.trim())
        val folder = ChatFolder(made.id, made.name, made.chatCount)
        _folders.update { it + folder }
        return folder
    }

    suspend fun rename(id: String, name: String) {
        remote.renameFolder(id, name.trim())
        _folders.update { all -> all.map { if (it.id == id) it.copy(name = name.trim()) else it } }
    }

    /** Removes the folder on Janitor; its chats stay, just no longer in it. */
    suspend fun delete(id: String) {
        remote.deleteFolder(id)
        chatDao.dropFolder(id)
        _folders.update { all -> all.filterNot { it.id == id } }
    }

    /** Whether this character's chats are in [folderId], as far as this phone knows. */
    suspend fun isIn(characterId: String, folderId: String): Boolean =
        chatDao.folderIdsForCharacter(characterId).any { folderId in decodeFolderIds(it.folderIds) }

    /** Puts every chat with this character into the folder, or takes them all out. */
    suspend fun setInFolder(characterId: String, folderId: String, inFolder: Boolean) {
        val rows = chatDao.folderIdsForCharacter(characterId)
        if (inFolder) {
            val missing = rows.filter { folderId !in decodeFolderIds(it.folderIds) }
            if (missing.isNotEmpty()) remote.addToFolder(folderId, missing.map { it.id })
            db.withTransaction { for (r in missing) chatDao.setFolderIds(r.id, encodeFolderIds(decodeFolderIds(r.folderIds) + folderId)) }
        } else {
            for (r in rows.filter { folderId in decodeFolderIds(it.folderIds) }) {
                remote.removeFromFolder(folderId, r.id)
                chatDao.setFolderIds(r.id, encodeFolderIds(decodeFolderIds(r.folderIds) - folderId))
            }
        }
        runCatching { refresh() } // counts
    }

    /** Brings a folder's chats into the mirror, so its tab is complete. */
    suspend fun fill(folderId: String) = chats.storeFolder(folderId)

    /** Shown at once on the phone; then written to Janitor as membership of the Pinned folder. */
    suspend fun setPinned(characterId: String, pinned: Boolean) {
        val now = marks.get(characterId)
        save(characterId, pinnedAt = if (pinned) System.currentTimeMillis() else null, archived = now?.archived == true)
        val folder = if (pinned) specialFolder(PINNED_FOLDER) else specialId(PINNED_FOLDER) ?: return
        setInFolder(characterId, folder, pinned)
    }

    /** Archiving unpins, as in Telegram: an archived row is out of the way, not on top. */
    suspend fun setArchived(characterId: String, archived: Boolean) {
        val now = marks.get(characterId)
        save(characterId, pinnedAt = if (archived) null else now?.pinnedAt, archived = archived)
        if (archived) specialId(PINNED_FOLDER)?.let { runCatching { setInFolder(characterId, it, false) } }
        val folder = if (archived) specialFolder(ARCHIVE_FOLDER) else specialId(ARCHIVE_FOLDER) ?: return
        setInFolder(characterId, folder, archived)
    }

    suspend fun mark(characterId: String): GroupMarkEntity? = marks.get(characterId)

    private suspend fun save(characterId: String, pinnedAt: Long?, archived: Boolean) {
        if (pinnedAt == null && !archived) marks.clear(characterId) else marks.upsert(GroupMarkEntity(characterId, pinnedAt, archived))
    }

    companion object {
        /** The folder on Janitor that holds archived characters' chats. */
        const val ARCHIVE_FOLDER = "Archive"
        /** The folder on Janitor that holds pinned characters' chats. */
        const val PINNED_FOLDER = "Pinned"
    }
}
