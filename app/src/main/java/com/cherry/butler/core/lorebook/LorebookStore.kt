package com.cherry.butler.core.lorebook

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Device-only data, separate from the profile/config that Janitor synchronizes. */
@Singleton
class LorebookStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_lorebooks", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = LorebookLibrary.serializer()
    private val _library = MutableStateFlow(readLibrary())
    val library = _library.asStateFlow()

    @Synchronized
    fun update(change: (LorebookLibrary) -> LorebookLibrary) {
        val next = change(_library.value)
        prefs.edit().putString("library", json.encodeToString(serializer, next)).apply()
        _library.value = next
    }

    fun prepare(envelope: JsonObject): JsonObject = LorebookRequest.prepare(envelope, _library.value)

    private fun readLibrary(): LorebookLibrary =
        prefs.getString("library", null)?.let { saved ->
            runCatching { json.decodeFromString(serializer, saved) }.getOrNull()
        } ?: LorebookLibrary()
}
