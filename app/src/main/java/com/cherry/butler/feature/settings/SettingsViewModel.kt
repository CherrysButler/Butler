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
/** What signing out would cost, asked before it happens. */
data class SignOutCheck(val unsent: Int)

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
    private val chat: com.cherry.butler.core.data.ChatPrefs,
    private val browse: com.cherry.butler.core.data.BrowsePrefs,
    private val pictures: com.cherry.butler.core.data.PicturePrefs,
    private val agent: com.cherry.butler.core.data.AgentPrefs,
    private val textLook: com.cherry.butler.core.data.TextLookPrefs,
    private val profiles: com.cherry.butler.core.data.LookProfiles,
    private val account: com.cherry.butler.core.auth.AccountService,
) : ViewModel() {

    // ---- signing out -------------------------------------------------------------------

    /** What signing out would cost (messages not yet on Janitor), asked before it happens. */
    private val _signOutCheck = MutableStateFlow<SignOutCheck?>(null)
    val signOutCheck: StateFlow<SignOutCheck?> = _signOutCheck.asStateFlow()

    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    /** Step one: find out what would be lost, so the dialog can say it. */
    fun askSignOut() {
        viewModelScope.launch { _signOutCheck.value = SignOutCheck(runCatching { account.unsentCount() }.getOrDefault(0)) }
    }

    fun cancelSignOut() {
        _signOutCheck.value = null
    }

    fun signOut() {
        if (_signingOut.value) return
        viewModelScope.launch {
            _signingOut.value = true
            _signOutCheck.value = null
            runCatching { account.signOut() }.onFailure { _notices.trySend("Couldn’t sign out. ${it.userMessage()}") }
            _signingOut.value = false
        }
    }

    /** Agent mode (beta): a reply drafted, checked against goals, fixed, then delivered. */
    val agentOn: StateFlow<Boolean> = agent.enabled
    fun setAgentOn(on: Boolean) = agent.setEnabled(on)
    val agentEffort: StateFlow<com.cherry.butler.core.data.AgentPrefs.Effort> = agent.effort
    fun setAgentEffort(effort: com.cherry.butler.core.data.AgentPrefs.Effort) = agent.setEffort(effort)
    val agentGoals: StateFlow<String> = agent.goals
    fun setAgentGoals(text: String) = agent.setGoals(text)

    /** Which proxy preset writes pictures into a line (beta); null: Write for me's. */
    val pictureDescriber: StateFlow<com.cherry.butler.core.data.Writer.Proxy?> = pictures.describer
    fun setPictureDescriber(proxy: com.cherry.butler.core.data.Writer.Proxy?) = pictures.set(proxy)
    val picturesOn: StateFlow<Boolean> = pictures.enabled
    fun setPicturesOn(on: Boolean) = pictures.setEnabled(on)
    val picturesAuto: StateFlow<Boolean> = pictures.auto
    fun setPicturesAuto(on: Boolean) = pictures.setAuto(on)

    /** Write for me's voice: first, third or second person. */
    val perspective: StateFlow<com.cherry.butler.core.data.Perspective> = writerPrefs.perspective
    fun setPerspective(perspective: com.cherry.butler.core.data.Perspective) = writerPrefs.setPerspective(perspective)

    /** The keyboard goes down as a message is sent. */
    val closeKeyboardOnSend: StateFlow<Boolean> = chat.closeKeyboardOnSend
    fun setCloseKeyboardOnSend(on: Boolean) = chat.setCloseKeyboardOnSend(on)

    /** Thinking words: Claude's list (on or off, added to or taken from) and the user's lists. */
    val claudeOn: StateFlow<Boolean> = chat.claudeOn
    fun setClaudeOn(on: Boolean) = chat.setClaudeOn(on)
    val claudeWords: StateFlow<List<String>> = chat.claudeWords
    val claudeEdited: Boolean get() = chat.claudeEdited
    fun saveClaudeList(text: String) = chat.saveClaudeList(text)
    fun resetClaudeList() = chat.resetClaudeList()
    val thinkingLists: StateFlow<List<com.cherry.butler.core.data.ChatPrefs.WordList>> = chat.thinkingLists
    fun saveThinkingList(id: String?, name: String, text: String) = chat.saveList(id, name, text)
    fun setThinkingListOn(id: String, on: Boolean) = chat.setListOn(id, on)
    fun deleteThinkingList(id: String) = chat.deleteList(id)

    /** Home one page at a time instead of the endless scroll. */
    val pagedHome: StateFlow<Boolean> = browse.paged
    fun setPagedHome(on: Boolean) = browse.setPaged(on)

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

    /** The chat font and the app font (keys, see FontPrefs), and the fonts added. */
    val chatFont: StateFlow<String> = fonts.chat
    val appFont: StateFlow<String> = fonts.app
    val addedFonts: StateFlow<List<com.cherry.butler.core.data.FontPrefs.Added>> = fonts.added
    fun setChatFont(key: String) = fonts.setChat(key)
    fun setAppFont(key: String) = fonts.setApp(key)
    fun removeFont(key: String) = fonts.remove(key)
    val appTextScale: StateFlow<Float> = fonts.appScale
    fun setAppTextScale(scale: Float) = fonts.setAppScale(scale)

    /** Font files picked and read, waiting to hear which family they join, for the chat or the app font. */
    data class FontPick(val picked: com.cherry.butler.core.data.FontPrefs.Picked, val forChat: Boolean)
    private val _fontPick = MutableStateFlow<FontPick?>(null)
    val fontPick: StateFlow<FontPick?> = _fontPick.asStateFlow()

    /**
     * Reads picked font files. With no added font to join they become a new family at once;
     * otherwise [fontPick] asks which family they go to.
     */
    fun pickFonts(uris: List<android.net.Uri>, forChat: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runCatching { fonts.pick(uris) }
                .onSuccess { picked ->
                    when {
                        picked.faces.isEmpty() -> _notices.send(
                            if (uris.size == 1) "Couldn\u2019t add that font: it isn\u2019t a .ttf or .otf Butler can read."
                            else "Couldn\u2019t add those fonts: none is a .ttf or .otf Butler can read.",
                        )
                        fonts.added.value.isEmpty() -> placeFonts(FontPick(picked, forChat), into = null)
                        else -> _fontPick.value = FontPick(picked, forChat)
                    }
                }
                .onFailure { e -> _notices.send("Couldn\u2019t add that font: ${e.message ?: "unreadable file"}.") }
        }
    }

    /** The picked faces join [into] (an added font's key), or a new family when null, and are put to use. */
    fun placeFonts(into: String?) {
        val pick = _fontPick.value ?: return
        _fontPick.value = null
        viewModelScope.launch { placeFonts(pick, into) }
    }

    fun cancelFontPick() {
        _fontPick.value?.let { fonts.drop(it.picked) }
        _fontPick.value = null
    }

    private suspend fun placeFonts(pick: FontPick, into: String?) {
        runCatching { fonts.place(pick.picked, into) }
            .onSuccess { placed ->
                if (placed.added.isNotEmpty()) {
                    if (pick.forChat) fonts.setChat(placed.key) else fonts.setApp(placed.key)
                }
                val left = pick.picked.refused.size + placed.refused
                _notices.send(
                    buildString {
                        if (placed.added.isNotEmpty()) append("Added ${spoken(placed.added.map { it.label })} to ${placed.name}.")
                        if (placed.refused > 0) append(" A family holds ${com.cherry.butler.core.data.FontPrefs.MAX_FACES} faces at most.")
                        if (left > 0) append(" $left ${if (left == 1) "file was" else "files were"} left out.")
                    }.trim(),
                )
            }
            .onFailure { e -> _notices.send("Couldn\u2019t add that font: ${e.message ?: "unreadable file"}.") }
    }

    /** "Regular, Bold and Italic". */
    private fun spoken(names: List<String>): String =
        if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()

    /** Saved looks (Settings > Look > Profiles): saved, put on, renamed, removed, shared. */
    val lookProfiles: StateFlow<List<com.cherry.butler.core.data.LookProfile>> = profiles.profiles

    /** How chat text is drawn now, so Profiles can tell which one is on. */
    val textLookNow: StateFlow<com.cherry.butler.core.design.RpLook> = textLook.look

    fun saveLookProfile(name: String) {
        val saved = profiles.saveCurrent(name)
        viewModelScope.launch { _notices.send("Saved the look as ${saved.name}") }
    }

    fun applyLookProfile(id: String) {
        val name = profiles.profiles.value.firstOrNull { it.id == id }?.name ?: return
        val missing = profiles.apply(id)
        viewModelScope.launch {
            _notices.send(
                if (missing.isEmpty()) "$name is on"
                else "$name is on. ${spoken(missing)} ${if (missing.size == 1) "isn\u2019t" else "aren\u2019t"} here, so that font stayed as it was.",
            )
        }
    }

    fun renameLookProfile(id: String, name: String) = profiles.rename(id, name)
    fun deleteLookProfile(id: String) = profiles.delete(id)

    /** A profile as a file to share: a name for it and its text. */
    fun exportLookProfile(id: String): Pair<String, String>? = profiles.export(id)

    /** Adds the profile in a shared file beside the others; it goes on only when tapped. */
    fun importLookProfile(text: String?) {
        viewModelScope.launch {
            if (text == null) return@launch _notices.send("Couldn\u2019t read that file.")
            runCatching { profiles.import(text) }
                .onSuccess { _notices.send("Added ${it.name}. Tap it to put it on.") }
                .onFailure { e -> _notices.send(e.message ?: "That file isn\u2019t a look profile.") }
        }
    }

    fun reportLookTransfer(message: String) {
        viewModelScope.launch { _notices.send(message) }
    }

    /** Highlights' own wording (null: Butler's), editable in Settings. */
    val highlightsPrompt: StateFlow<String?> = addons.highlightsPrompt
    fun setHighlightsPrompt(text: String?) = addons.setHighlightsPrompt(text)

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

    /** Butler's reply length (Settings › Model), asked of the model in words. */
    val replyLength: StateFlow<com.cherry.butler.core.generation.ReplyLength> = addons.length
    fun setReplyLength(length: com.cherry.butler.core.generation.ReplyLength) = addons.setLength(length)

    /** Compact mode (Settings › Chat › Memory), a Butler special. */
    val compact: StateFlow<Boolean> = memory.compact
    val compactKeep: StateFlow<Int> = memory.compactKeep
    val compactAt: StateFlow<Int> = memory.compactAt
    fun setCompact(on: Boolean) = memory.setCompact(on)
    fun setCompactKeep(n: Int) = memory.setCompactKeep(n)
    fun setCompactAt(percent: Int) = memory.setCompactAt(percent)

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
    val thinkingLook: StateFlow<com.cherry.butler.core.design.ThinkingLook> = themes.thinkingLook
    fun setThinkingLook(look: com.cherry.butler.core.design.ThinkingLook) = themes.setThinkingLook(look)
    val thoughtLook: StateFlow<com.cherry.butler.core.design.ThoughtLook> = themes.thoughtLook
    fun setThoughtLook(look: com.cherry.butler.core.design.ThoughtLook) = themes.setThoughtLook(look)

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
