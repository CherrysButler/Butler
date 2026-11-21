package com.cherry.butler.core.lorebook

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.cherry.butler.core.auth.clearAllTables
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.local.ChatEntity
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.remote.dto.ProfileDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LorebookDatabaseTest {
    @get:Rule val folder = TemporaryFolder()

    private fun database(): LorebookDatabase =
        Room.databaseBuilder<LorebookDatabase>(folder.root.resolve(LorebookDatabase.NAME).path)
            .setDriver(BundledSQLiteDriver()).build()

    private suspend fun LorebookDao.activeEntries(chatScope: String, personaScope: String?) =
        activeBooks(chatScope, personaScope).flatMap { enabledEntries(it.id) }

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
            assertEquals(listOf(first, second), reopened.lorebooks().activeEntries("chat:1", "persona:default:owner").map { it.entry })
            assertTrue(reopened.lorebooks().activeEntries("chat:1", "persona:default:other").isEmpty())
        } finally { reopened.close() }
    }

    @Test fun `Room stores secondary keyword rules as the original integer codes`() = runBlocking {
        val db = database()
        try {
            val store = LorebookStore(db)
            val rules = listOf(SecondaryKeywordRule.MatchAny, SecondaryKeywordRule.NotAll, SecondaryKeywordRule.MatchNone, SecondaryKeywordRule.MatchAll)
            store.save(world.copy(entries = rules.map { first.copy(selectiveLogic = it) }), true, null)
            db.useWriterConnection { conn ->
                conn.usePrepared("SELECT selectiveLogic, typeof(selectiveLogic) FROM lore_entries ORDER BY position") { row ->
                    val codes = buildList {
                        while (row.step()) {
                            assertEquals("integer", row.getText(1))
                            add(row.getLong(0))
                        }
                    }
                    assertEquals(listOf(0L, 1L, 2L, 3L), codes)
                }
            }
            assertEquals(rules, store.book(world.id)?.entries?.map { it.selectiveLogic })
        } finally {
            db.close()
        }
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
            assertEquals(listOf(first), dao.activeEntries("chat:1", "persona:p").map { it.entry })
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

    @Test fun `active books share patterns across sends and edit or release only their own entries`() = runBlocking {
        val db = database()
        try {
            val store = LorebookStore(db)
            val ready = CompletableDeferred<Unit>()
            val screen = launch {
                store.keepChatOpen(1, flow {
                    emit("persona:p")
                    ready.complete(Unit)
                    awaitCancellation()
                })
            }
            ready.await()
            val weather = LoreEntry("Weather", keys = listOf("rain"), wholeWords = true)
            val pedestrian = weather.copy(content = "Umbrella")
            val fighter = weather.copy(content = "Fighter", keys = listOf("Rain"), caseSensitive = true)
            val weatherBook = world.copy(entries = listOf(weather))
            val street = world.copy(id = "street", entries = listOf(pedestrian, fighter))
            store.save(weatherBook, true, "persona:p")
            store.save(street, true, "chat:1")
            suspend fun snapshot(chat: Long = 1, persona: String = "persona:p") = store.withSelection(chat, persona) { it }
            fun LorebookSelection.pattern(content: String, key: String) =
                candidates.single { it.entry.content == content }.patterns.getValue(key)

            val original = snapshot()
            val rain = original.pattern("Weather", "rain")
            val properName = original.pattern("Fighter", "Rain")
            assertSame(rain, original.pattern("Umbrella", "rain"))
            assertSame(original, snapshot())
            assertNotSame(rain, snapshot(2).pattern("Weather", "rain"))
            assertSame(rain, snapshot(persona = "persona:other").pattern("Umbrella", "rain"))

            store.setEnabled("chat:1", weatherBook.id, true)
            store.clearPersona("p") // Direct binding keeps Weather active.
            assertSame(rain, snapshot().pattern("Weather", "rain"))
            store.setEnabled("chat:1", weatherBook.id, false)
            assertEquals(listOf("Umbrella", "Fighter"), snapshot().candidates.map { it.entry.content })
            assertSame(rain, snapshot().pattern("Umbrella", "rain"))
            store.save(street.copy(entries = listOf(fighter, pedestrian.copy(content = "Coat"))), false, null)
            assertSame(rain, snapshot().pattern("Coat", "rain"))
            assertSame(properName, snapshot().pattern("Fighter", "Rain"))
            store.save(street.copy(entries = listOf(fighter, pedestrian.copy(enabled = false))), false, null)
            store.setEnabled("chat:1", weatherBook.id, true)
            assertNotSame(rain, snapshot().pattern("Weather", "rain"))
            assertSame(properName, snapshot().pattern("Fighter", "Rain"))
            store.delete(weatherBook.id)
            assertEquals(listOf("Fighter"), snapshot().candidates.map { it.entry.content })

            screen.cancelAndJoin()
            assertNotSame(properName, snapshot().pattern("Fighter", "Rain"))
        } finally { db.close() }
    }

    @Test fun `a running match keeps its snapshot through a persona switch and deletion`() = runBlocking {
        val db = database()
        try {
            val store = LorebookStore(db)
            val rain = LoreEntry("Drizzle", keys = listOf("rain"), wholeWords = true)
            store.save(world.copy(entries = listOf(rain)), true, "persona:p")
            val switch = CompletableDeferred<Unit>()
            val opened = CompletableDeferred<Unit>()
            val switched = CompletableDeferred<Unit>()
            val screen = launch {
                store.keepChatOpen(1, flow {
                    emit("persona:p")
                    opened.complete(Unit)
                    switch.await()
                    emit("persona:q")
                    switched.complete(Unit)
                    awaitCancellation()
                })
            }
            opened.await()
            val started = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            val send = async {
                store.withSelection(1, "persona:p") { selection ->
                    started.complete(Unit)
                    resume.await()
                    LorebookMatcher.context(selection, listOf("rain"), 100)
                }
            }
            started.await()
            switch.complete(Unit)
            switched.await()
            assertTrue(store.withSelection(1, "persona:q") { it.candidates.isEmpty() })
            store.setPersonaBooks("q", setOf(world.id))
            assertEquals("Drizzle", store.withSelection(1, "persona:q") { LorebookMatcher.context(it, listOf("rain"), 100) })
            screen.cancelAndJoin()
            store.delete(world.id)
            assertTrue(store.withSelection(1, "persona:p") { it.candidates.isEmpty() })
            resume.complete(Unit)
            assertEquals("Drizzle", send.await())
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
