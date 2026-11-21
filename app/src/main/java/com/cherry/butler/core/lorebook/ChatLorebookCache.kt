package com.cherry.butler.core.lorebook

/** Access is serialized by LorebookStore. Only immutable selections escape to the matcher. */
internal class ChatLorebookCache(private val chatScope: String, private val dao: LorebookDao) {
    private data class View(val books: List<LorebookRow>, val selection: LorebookSelection)

    private val readers = mutableMapOf<Any, String?>()
    private val views = mutableMapOf<String?, View>()
    private val books = mutableMapOf<String, Map<Int, LorebookCandidate>>()
    private var patterns = WholeWordPatterns()

    val unused get() = readers.isEmpty()

    suspend fun select(reader: Any, personaScope: String?): LorebookSelection {
        val switching = reader in readers && readers[reader] != personaScope
        val previous = readers.put(reader, personaScope)
        try {
            if (personaScope !in views) {
                rebuild(mapOf(personaScope to dao.activeBooks(chatScope, personaScope)))
            }
            return views.getValue(personaScope).selection
        } finally {
            if (switching) dropUnusedView(previous)
        }
    }

    fun release(reader: Any) {
        val scope = readers.remove(reader)
        if (unused) invalidate() else dropUnusedView(scope)
    }

    suspend fun refresh(bindingScope: String?, changedId: String?, savedBook: Lorebook?) {
        val affected = readers.values.distinct().filter { persona ->
            val view = views[persona]
            view == null || (bindingScope != null && (bindingScope == chatScope || bindingScope == persona)) ||
                (changedId != null && view.books.any { it.id == changedId })
        }
        if (affected.isEmpty()) return
        rebuild(affected.associateWith { dao.activeBooks(chatScope, it) }, savedBook)
        dropUnusedBooks()
    }

    private suspend fun rebuild(selections: Map<String?, List<LorebookRow>>, savedBook: Lorebook? = null) {
        val load = selections.values.flatten().distinctBy { it.id }.filter { it.id !in books || it.id == savedBook?.id }
        // Finish the cancellable reads before updating any reference counts.
        val rows = load.associateWith { book ->
            if (savedBook != null && savedBook.id == book.id) {
                savedBook.entries.mapIndexedNotNull { position, entry ->
                    if (entry.enabled) LoreEntryRow(book.id, position, entry) else null
                }
            } else dao.enabledEntries(book.id)
        }
        val replaced = mutableListOf<LorebookCandidate>()
        for ((book, entries) in rows) {
            val previous = books[book.id].orEmpty()
            val next = buildMap {
                for ((_, position, entry) in entries) {
                    val old = previous[position]
                    val depth = entry.scanDepth ?: book.scanDepth
                    val candidate = if (old != null && old.entry == entry && old.scanDepth == depth) {
                        old
                    } else {
                        patterns.prepare(entry, book.scanDepth)
                    }
                    if (candidate != null) put(position, candidate)
                }
            }
            previous.forEach { (position, old) -> if (next[position] !== old) replaced += old }
            books[book.id] = next
        }
        // Acquire all replacements first so a moved keyword keeps the same compiled Regex.
        replaced.forEach(patterns::release)
        selections.forEach { (scope, selected) ->
            if (views[scope]?.books != selected || selected.any { it in rows }) {
                views[scope] = View(selected, LorebookSelection(selected.flatMap { books.getValue(it.id).values }))
            }
        }
    }

    private fun dropUnusedView(personaScope: String?) {
        if (personaScope !in readers.values && views.remove(personaScope) != null) dropUnusedBooks()
    }

    private fun dropUnusedBooks() {
        val used = views.values.flatMapTo(mutableSetOf()) { view -> view.books.map { it.id } }
        for (id in books.keys - used) books.remove(id)?.values?.forEach(patterns::release)
    }

    /** A cancelled write can already have committed. The next reader reloads from Room. */
    fun invalidate() {
        views.clear()
        books.clear()
        patterns = WholeWordPatterns()
    }
}
