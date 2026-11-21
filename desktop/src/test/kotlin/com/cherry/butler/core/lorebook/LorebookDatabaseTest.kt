package com.cherry.butler.core.lorebook

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.cherry.butler.core.auth.clearAllTables
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.local.ChatEntity
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.remote.dto.ProfileDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LorebookDatabaseTest {
    @get:Rule val folder = TemporaryFolder()

    private fun database(): LorebookDatabase =
        Room.databaseBuilder<LorebookDatabase>(folder.root.resolve(LorebookDatabase.NAME).path)
            .setDriver(BundledSQLiteDriver()).build()

    private val first = LoreEntry("Castle", keys = listOf("rain\nfall", "a,b", "\"gate\""), secondaryKeys = listOf("城"), name = "Gate")
    private val second = LoreEntry("Cellar", constant = true)
    private val world = Lorebook("world", "World", listOf(first, second), scanDepth = 2)

    @Test fun `constructing the store performs no disk read and state survives account cleanup and reopening`() = runBlocking {
        val db = database()
        try {
            val store = LorebookStore(db)
            assertFalse(folder.root.resolve(LorebookDatabase.NAME).exists())
            assertEquals(24_000, store.contextCharLimit.first())
            store.save(world, true, "persona:default:owner")
            store.toggleFavorite(world.id)
            store.setContextCharLimit(0)
        } finally { db.close() }

        val chatDb = Room.databaseBuilder<ButlerDatabase>(folder.root.resolve(ButlerDatabase.NAME).path)
            .setDriver(BundledSQLiteDriver()).build()
        try { chatDb.clearAllTables() } finally { chatDb.close() }

        val reopened = database()
        try {
            val store = LorebookStore(reopened)
            assertEquals(0, store.contextCharLimit.first())
            assertEquals(world.copy(favorite = true), store.book(world.id))
            assertEquals(listOf(first, second), reopened.lorebooks().activeEntries("chat:1", "persona:default:owner").map { it.row.entry })
            assertTrue(reopened.lorebooks().activeEntries("chat:1", "persona:default:other").isEmpty())
        } finally { reopened.close() }
    }

    @Test fun `list and generation never decode entries from unselected books`() = runBlocking {
        val db = database()
        try {
            val store = LorebookStore(db)
            store.save(world.copy(entries = listOf(first, second.copy(enabled = false))), true, "persona:p")
            store.save(world.copy(id = "closed", name = "Closed"), true, null)
            db.useWriterConnection { it.usePrepared("UPDATE lore_entries SET keys = 'invalid JSON' WHERE bookId = 'closed' OR enabled = 0") { st -> st.step() } }
            // Any query that materializes these rows through the keyword converter will fail.
            val summaries = store.books("chat:1", "persona:p").first()
            assertEquals(listOf("Closed", "World"), summaries.map { it.name })
            assertEquals(listOf(2, 2), summaries.map { it.entryCount })
            assertTrue(summaries.last().inherited && summaries.last().selected)
            assertFalse(summaries.first().selected)
            store.setEnabled("chat:1", world.id, true)
            val dao = db.lorebooks()
            assertEquals(listOf(first), dao.activeEntries("chat:1", "persona:p").map { it.row.entry })
            store.setEnabled("chat:1", world.id, false)
            val overridden = store.books("chat:1", "persona:p").first().last()
            assertTrue(overridden.inherited)
            assertFalse(overridden.selected)
            assertTrue(dao.activeEntries("chat:1", "persona:p").isEmpty())
            assertEquals(1, dao.activeEntries("chat:2", "persona:p").size)
            store.setEnabled("chat:1", world.id, true)
            assertEquals(1, dao.activeEntries("chat:1", "persona:p").size)
        } finally { db.close() }
    }

    @Test fun `editing one book skips unchanged entries and preserves concurrent favorites and bindings`() = runBlocking {
        val db = database()
        try {
            val store = LorebookStore(db)
            store.save(world, true, "chat:1")
            // Fail immediately if a star, rename or edit rewrites the unchanged first entry.
            db.useWriterConnection { conn ->
                for (op in listOf("INSERT", "UPDATE", "DELETE")) {
                    val row = if (op == "DELETE") "OLD" else "NEW"
                    conn.usePrepared("""CREATE TRIGGER guard_$op BEFORE $op ON lore_entries WHEN $row.position = 0
                        BEGIN SELECT RAISE(ABORT, 'unchanged entry was written'); END""") { it.step() }
                }
            }
            store.toggleFavorite(world.id)
            store.setEnabled("chat:1", world.id, false)
            val edited = world.copy(name = "Renamed", entries = listOf(first, second.copy(content = "Library")))
            store.save(edited, false, "chat:1")
            assertEquals(edited.copy(favorite = true), store.book(world.id))
            store.save(edited.copy(entries = listOf(first)), false, null)
            assertEquals(listOf(first), store.book(world.id)?.entries)
            assertTrue(db.lorebooks().activeEntries("chat:1", null).isEmpty())
        } finally { db.close() }
    }

    @Test fun `generation matches across selected books using the stored limit and active account`() = runBlocking {
        val db = database()
        try {
            val store = LorebookStore(db)
            val chat = ChatEntity(
                id = 1, characterId = "character", characterName = "", characterAvatar = null, characterDeleted = false,
                lastMessageAt = null, lastMessagePreview = null, messageCount = 1, isPublic = false, userId = "",
                personaId = null, defaultPersonaId = null, defaultPersonaAppearance = null, summary = null,
                summaryChatId = null, detailLoaded = true, cachedAt = 0,
            )
            val profile = ProfileDto("owner", name = "Alice")
            val message = MessageEntity(
                serverId = 1, chatId = chat.id, isBot = true, isMain = true, text = "At the gate", createdAt = 0,
                rating = null, personaId = null, generationRequestIds = emptyList(), thinking = null, streamState = null, cachedAt = 0,
            )
            store.save(world.copy(entries = listOf(LoreEntry("Castle", keys = listOf("gate")))), true, "chat:1")
            val inherited = world.copy(id = "inherited", entries = listOf(LoreEntry("{{user}} lives here.", keys = listOf("Castle"))))
            store.save(inherited, true, "persona:default:owner")
            suspend fun context(account: ProfileDto = profile) = store.instructionFor(chat, account, null, listOf(message)).orEmpty()
            assertContains(context(), "Castle\n\nAlice lives here.")
            assertFalse("lives here" in context(ProfileDto("another-owner")))
            store.setContextCharLimit(6)
            assertContains(context(), "Castle")
            assertFalse("lives here" in context())
            store.setContextCharLimit(0)
            assertContains(context(), "Alice lives here.")
        } finally { db.close() }
    }

    @Test fun `new books and bindings commit together and deletion cannot be undone by stale drafts`() = runBlocking {
        val db = database()
        try {
            val store = LorebookStore(db)
            db.useWriterConnection { it.usePrepared("""CREATE TRIGGER reject_scope BEFORE INSERT ON lore_bindings
                WHEN NEW.scope = 'reject' BEGIN SELECT RAISE(ABORT, 'injected binding failure'); END""") { st -> st.step() } }
            assertFailsWith<androidx.sqlite.SQLiteException> { store.save(world, true, "reject") }
            assertNull(store.book(world.id))
            store.save(world, true, "chat:1")
            store.setPersonaBooks("p", setOf(world.id, "deleted"))
            assertTrue(store.books("persona:p").first().single().selected)
            store.clearPersona("p")
            assertFalse(store.books("persona:p").first().single().selected)
            store.delete(world.id)
            assertFailsWith<IllegalStateException> { store.save(world, false, null) }
            // Recreating the same ID must not bring back the removed chat binding.
            store.save(world.copy(entries = listOf(second)), true, null)
            assertEquals(listOf(second), store.book(world.id)?.entries)
            assertFalse(store.books("chat:1").first().single().selected)
        } finally { db.close() }
    }
}
