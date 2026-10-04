package com.cherry.butler.feature.settings

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
) : ViewModel() {

    val theme: StateFlow<AppTheme> = themes.theme

    fun setTheme(theme: AppTheme) = themes.set(theme)

    val chatStyle: StateFlow<ChatStyle> = themes.chatStyle

    fun setChatStyle(style: ChatStyle) = themes.setChatStyle(style)

    val settings: StateFlow<AiSettings?> = repository.settings
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

    init {
        refresh()
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

    fun setProvider(provider: Provider) = write("provider") { repository.setProvider(provider) }

    fun selectProxy(id: String) = write("proxy:$id") { repository.selectProxy(id) }

    fun setGeneration(key: String, value: JsonElement) = write("gen:$key") { repository.setGeneration(key, value) }

    fun setReplacesHistory(on: Boolean) {
        memory.setReplacesHistory(on)
        if (!on) memory.setAutoSummarize(false)
    }

    fun setAutoSummarize(on: Boolean) {
        memory.setAutoSummarize(on)
        if (on) memory.setReplacesHistory(true)
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
