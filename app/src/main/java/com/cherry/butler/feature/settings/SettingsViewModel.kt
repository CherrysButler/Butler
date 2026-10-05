package com.cherry.butler.feature.settings

import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.AiSettings
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.Provider
import com.cherry.butler.core.data.SettingsRepository
import com.cherry.butler.core.data.ThemePrefs
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ChatStyle
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

/**
 * The Settings tab. Every change is written to Janitor straight away and the screen
 * re-renders from what the server sends back, so what is shown is what generations use.
 * A refused write leaves the old value on screen and says why.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val memory: MemoryPrefs,
    private val themes: ThemePrefs,
    private val writerPrefs: com.cherry.butler.core.data.WriterPrefs,
    private val richTyping: com.cherry.butler.core.data.RichTypingPrefs,
) : ViewModel() {

    val richOn: StateFlow<Boolean> = richTyping.enabled
    val richDefault: StateFlow<String?> = richTyping.defaultMark
    fun setRich(on: Boolean) = richTyping.setEnabled(on)
    fun setRichDefault(key: String?) = richTyping.setDefaultMark(key)

    /** Which model writes the user's own lines. */
    val writer: StateFlow<com.cherry.butler.core.data.Writer> = writerPrefs.writer

    fun setWriter(writer: com.cherry.butler.core.data.Writer) = writerPrefs.set(writer)


    val theme: StateFlow<AppTheme> = themes.theme

    fun setTheme(theme: AppTheme) = themes.set(theme)

    /** What the Custom look is made from. */
    val custom: StateFlow<com.cherry.butler.core.design.CustomColors> = themes.custom
    fun setCustom(colors: com.cherry.butler.core.design.CustomColors) = themes.setCustom(colors)

    val recentColors: StateFlow<List<Long>> = themes.recentColors
    fun addRecent(argb: Long) = themes.addRecent(argb)

    val chatStyle: StateFlow<ChatStyle> = themes.chatStyle

    fun setChatStyle(style: ChatStyle) = themes.setChatStyle(style)

    /**
     * Changes to Janitor-side settings wait here until Save: switching JLLM and the proxy,
     * picking a proxy, moving a sampler value. Nothing is sent while the user is still
     * deciding, so every control answers at once.
     */
    data class Draft(
        val provider: Provider? = null,
        val proxyId: String? = null,
        val generation: Map<String, JsonElement> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = provider == null && proxyId == null && generation.isEmpty()
    }

    private val _draft = MutableStateFlow(Draft())

    /** What Janitor has, with the unsaved changes laid over it: what the screen shows. */
    val settings: StateFlow<AiSettings?> = combine(repository.settings, _draft) { saved, d ->
        saved?.copy(
            provider = d.provider ?: saved.provider,
            selectedProxyId = d.proxyId ?: saved.selectedProxyId,
            generation = if (d.generation.isEmpty()) saved.generation else JsonObject(saved.generation + d.generation),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repository.settings.value)

    /** Whether anything waits for Save. */
    val dirty: StateFlow<Boolean> = _draft.map { !it.isEmpty }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val replacesHistory: StateFlow<Boolean> = memory.replacesHistory
    val autoSummarize: StateFlow<Boolean> = memory.autoSummarize

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadError = MutableStateFlow<ApiError?>(null)
    val loadError: StateFlow<ApiError?> = _loadError.asStateFlow()

    /** Which setting is mid-save, so its row can say so instead of the whole screen. */
    private val _saving = MutableStateFlow<String?>(null)
    val saving: StateFlow<String?> = _saving.asStateFlow()

    private val _notices = Channel<String>(Channel.BUFFERED)
    val notices: Flow<String> = _notices.receiveAsFlow()

    private val _premium = MutableStateFlow<Boolean?>(null)

    /** Janitor+ or not (null: unknown); JLLM's reasoning switches are Janitor+ only. */
    val premium: StateFlow<Boolean?> = _premium.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { _premium.value = repository.hasPremium() }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _loadError.value = null
            runCatching { repository.load(refresh = true) }
                .onFailure { _loadError.value = it as? ApiError ?: ApiError.Unknown(it) }
            _loading.value = false
        }
    }

    fun setProvider(provider: Provider) = _draft.update { d ->
        d.copy(provider = provider.takeIf { it != repository.settings.value?.provider })
    }

    fun selectProxy(id: String) = _draft.update { d ->
        d.copy(proxyId = id.takeIf { it != repository.settings.value?.selectedProxyId })
    }

    /** A value moved back to what Janitor already has is no longer a change. */
    fun setGeneration(key: String, value: JsonElement) = _draft.update { d ->
        val saved = repository.settings.value?.generation?.get(key)
        d.copy(generation = if (saved == value) d.generation - key else d.generation + (key to value))
    }

    fun discard() {
        _draft.value = Draft()
    }

    /**
     * Sends the changes, in Janitor's order (provider, then the proxy, then the sampler as
     * one request), and runs [then] once all are in. A part that went through leaves the
     * draft even if a later one fails, so a retry sends only what is still waiting.
     */
    fun save(then: () -> Unit = {}) {
        val d = _draft.value
        if (d.isEmpty) return then()
        if (_saving.value != null) return
        viewModelScope.launch {
            _saving.value = SAVING_ALL
            val result = runCatching {
                d.provider?.let { repository.setProvider(it); _draft.update { x -> x.copy(provider = null) } }
                d.proxyId?.let { repository.selectProxy(it); _draft.update { x -> x.copy(proxyId = null) } }
                if (d.generation.isNotEmpty()) {
                    repository.setGeneration(d.generation)
                    _draft.update { x -> x.copy(generation = x.generation - d.generation.keys) }
                }
            }
            _saving.value = null
            result
                .onSuccess { _notices.send("Saved"); then() }
                .onFailure { e -> _notices.send("Not saved. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") }
        }
    }

    fun setReplacesHistory(on: Boolean) {
        memory.setReplacesHistory(on)
        if (!on) memory.setAutoSummarize(false)
    }

    fun setAutoSummarize(on: Boolean) {
        memory.setAutoSummarize(on)
        if (on) memory.setReplacesHistory(true)
    }

    companion object {
        /** [saving] while the whole draft goes out. */
        const val SAVING_ALL = "all"
    }

    private fun write(tag: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _saving.value = tag
            runCatching { block() }
                .onFailure { e -> _notices.send("Not saved. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") }
            _saving.value = null
        }
    }
}
