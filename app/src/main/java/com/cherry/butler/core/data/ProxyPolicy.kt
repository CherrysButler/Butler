package com.cherry.butler.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which characters let people chat on their own proxy (`allow_proxy`), as last read from a
 * character page or a chat. A creator can switch proxies off; Janitor then turns a proxy's
 * message away, again and again behind Cloudflare's check, so Butler says so on the page and
 * in the chat, and doesn't send one ([com.cherry.butler.core.generation.SendPipeline]).
 * Kept in memory: every page and chat opened reads it fresh.
 */
object ProxyPolicy {
    private val _allowed = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val allowed: StateFlow<Map<String, Boolean>> = _allowed.asStateFlow()

    fun note(characterId: String, allowProxy: Boolean) {
        if (characterId.isBlank() || _allowed.value[characterId] == allowProxy) return
        _allowed.update { it + (characterId to allowProxy) }
    }

    /** Whether [characterId] takes proxies; null when Butler hasn't read it yet. */
    fun allows(characterId: String): Boolean? = _allowed.value[characterId]
}
