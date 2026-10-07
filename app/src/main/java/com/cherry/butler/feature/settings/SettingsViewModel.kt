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
    private val addons: com.cherry.butler.core.generation.PromptAddons,
    private val fonts: com.cherry.butler.core.data.FontPrefs,
    private val content: com.cherry.butler.core.data.ContentPrefs,
    private val updates: com.cherry.butler.core.update.UpdateChecker,
) : ViewModel() {

    /** Asking GitHub for a newer Butler: on a tap, or on every open when switched on. */
    val updateAuto: StateFlow<Boolean> = updates.auto
    fun setUpdateAuto(on: Boolean) = updates.setAuto(on)
    val currentVersion: String get() = updates.current
    private val _update = MutableStateFlow<com.cherry.butler.core.update.UpdateChecker.Result?>(null)
    val update: StateFlow<com.cherry.butler.core.update.UpdateChecker.Result?> = _update.asStateFlow()
    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()
    fun checkForUpdate() {
        if (_checkingUpdate.value) return
        viewModelScope.launch {
            _checkingUpdate.value = true
            _update.value = updates.check()
            _checkingUpdate.value = false
        }
    }
    fun clearUpdate() { _update.value = null }

    /** `allow_mobile_nsfw`, sent with every reply. */
    val allowMobileNsfw: StateFlow<Boolean> = content.allowMobileNsfw
    fun setAllowMobileNsfw(on: Boolean) = content.setAllowMobileNsfw(on)

    /** The chat font and the app font (keys, see FontPrefs), and the font files added. */
    val chatFont: StateFlow<String> = fonts.chat
    val appFont: StateFlow<String> = fonts.app
    val addedFonts: StateFlow<List<String>> = fonts.added
    fun setChatFont(key: String) = fonts.setChat(key)
    fun setAppFont(key: String) = fonts.setApp(key)
    fun removeFont(name: String) = fonts.remove(name)
    val appTextScale: StateFlow<Float> = fonts.appScale
    fun setAppTextScale(scale: Float) = fonts.setAppScale(scale)

    /** Adds a picked font file and puts it to use as the chat or the app font. */
    fun addFont(uri: android.net.Uri, forChat: Boolean) {
        viewModelScope.launch {
            runCatching { fonts.add(uri) }
                .onSuccess { key -> if (forChat) fonts.setChat(key) else fonts.setApp(key) }
                .onFailure { e -> _notices.send("Couldn\u2019t add that font: ${e.message ?: "unreadable file"}.") }
        }
    }

    /** Butler's specials: instructions added to the user's model (see PromptAddons). */
    val butter: StateFlow<Boolean> = addons.butter
    val stripTags: StateFlow<Boolean> = addons.stripTags
    fun setButter(on: Boolean) = addons.setButter(on)
    val butterTint: StateFlow<Boolean> = addons.butterTint
    val highlights: StateFlow<Boolean> = addons.highlights
    fun setHighlights(on: Boolean) = addons.setHighlights(on)
    val moods: StateFlow<Set<com.cherry.butler.core.generation.Mood>> = addons.moods
    fun setMood(mood: com.cherry.butler.core.generation.Mood, on: Boolean) = addons.setMood(mood, on)
    fun setButterTint(on: Boolean) = addons.setButterTint(on)
    fun setStripTags(strip: Boolean) = addons.setStripTags(strip)

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
        /** JLLM's prompt as picked, when it differs from Janitor's; [PromptPick.id] null is "none". */
        val jllmPrompt: PromptPick? = null,
    ) {
        val isEmpty: Boolean get() = provider == null && proxyId == null && generation.isEmpty() && jllmPrompt == null
    }

    /** A picked prompt; wrapped so "none" (a null id) differs from "no change" (no pick). */
    data class PromptPick(val id: String?)

    private val _draft = MutableStateFlow(Draft())

    /** What Janitor has, with the unsaved changes laid over it: what the screen shows. */
    val settings: StateFlow<AiSettings?> = combine(repository.settings, _draft) { saved, d ->
        saved?.copy(
            provider = d.provider ?: saved.provider,
            selectedProxyId = d.proxyId ?: saved.selectedProxyId,
            generation = if (d.generation.isEmpty()) saved.generation else JsonObject(saved.generation + d.generation),
            jllmPromptId = d.jllmPrompt?.let { it.id } ?: saved.jllmPromptId,
            jllmPromptName = d.jllmPrompt?.let { pick -> saved.prompts.firstOrNull { it.id == pick.id }?.name } ?: saved.jllmPromptName,
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

    fun setJllmPrompt(id: String?) = _draft.update { d ->
        d.copy(jllmPrompt = PromptPick(id).takeIf { id != repository.settings.value?.jllmPromptId })
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
                d.jllmPrompt?.let { repository.setJllmPrompt(it.id); _draft.update { x -> x.copy(jllmPrompt = null) } }
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
