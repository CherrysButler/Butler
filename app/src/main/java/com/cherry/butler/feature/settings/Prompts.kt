package com.cherry.butler.feature.settings

import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.Prompt
import com.cherry.butler.core.data.SettingsRepository
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.feature.chat.DeleteConfirmDialog
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.card
import com.cherry.butler.ui.components.dropLastPixel
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SkeletonRosterRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---- library ------------------------------------------------------------------------

@HiltViewModel
class PromptsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    /** null until the settings have been read once: the screen shows a skeleton, not "none". */
    val prompts: StateFlow<List<Prompt>?> = repository.settings
        .map { it?.prompts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.settings.value?.prompts)

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _error.value = null
            runCatching { repository.load() }.onFailure { _error.value = it as? ApiError ?: ApiError.Unknown(it) }
        }
    }
}

@Composable
fun PromptsScreen(onBack: () -> Unit, onOpen: (String?) -> Unit, viewModel: PromptsViewModel = hiltViewModel()) {
    val loaded by viewModel.prompts.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val prompts = loaded.orEmpty()
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            PlateText(text = "Prompt library", level = PlateLevel.Name, modifier = Modifier.weight(1f))
            KeyButton(label = "New", onClick = { onOpen(null) }, primary = true, modifier = Modifier.height(40.dp))
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp)) {
            if (loaded == null && error != null) {
                InlineErrorCard(error!!, onRetry = viewModel::reload, modifier = Modifier.padding(16.dp))
            } else if (loaded == null) {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) { repeat(4) { SkeletonRosterRow(withMargin = false) } }
            } else if (prompts.isEmpty()) {
                Text(
                    "No prompts yet. A prompt is the instruction a proxy sends ahead of the chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (prompts.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .card(shape = MaterialTheme.shapes.large)
                        .dropLastPixel(),
                ) {
                    prompts.forEach { p ->
                        LinkRow(
                            title = p.name.ifBlank { "Untitled" },
                            subtitle = (if (p.kind == "prefill") "Prefill · " else "") + p.content.lines().joinToString(" ").take(90),
                            onClick = { onOpen(p.id) },
                        )
                    }
                }
            }
        }
    }
}

// ---- editor -------------------------------------------------------------------------

data class PromptForm(
    val name: String = "",
    val kind: String = "system",
    val content: String = "",
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: ApiError? = null,
)

@HiltViewModel
class PromptEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SettingsRepository,
) : ViewModel() {
    val promptId: String? = savedStateHandle.get<String>(ARG)?.takeIf { it != NEW }
    val isNew: Boolean get() = promptId == null

    private val _form = MutableStateFlow(PromptForm())
    val form: StateFlow<PromptForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val p = runCatching { repository.load() }.getOrNull()?.prompts?.firstOrNull { it.id == promptId }
            _form.value = if (p != null) PromptForm(p.name, p.kind, p.content, loaded = true) else PromptForm(loaded = true)
        }
    }

    fun update(change: (PromptForm) -> PromptForm) = _form.update { change(it).copy(error = null) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.saving || f.name.isBlank()) return
        viewModelScope.launch {
            _form.update { it.copy(saving = true) }
            runCatching { repository.savePrompt(promptId, f.name, f.kind, f.content) }
                .onSuccess { onDone() }
                .onFailure { e -> _form.update { it.copy(saving = false, error = e as? ApiError ?: ApiError.Unknown(e)) } }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = promptId ?: return
        viewModelScope.launch {
            _form.update { it.copy(saving = true) }
            runCatching { repository.deletePrompt(id) }
                .onSuccess { onDone() }
                .onFailure { e -> _form.update { it.copy(saving = false, error = e as? ApiError ?: ApiError.Unknown(e)) } }
        }
    }

    companion object {
        const val ARG = "promptId"
        const val NEW = "new"
    }
}

@Composable
fun PromptEditorScreen(onBack: () -> Unit, viewModel: PromptEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        DeleteConfirmDialog(
            count = 1,
            title = "Delete prompt",
            body = "\"${form.name}\" will be removed from your library. Proxies using it will run without a prompt.",
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    EditorScaffold(
        title = if (viewModel.isNew) "New prompt" else "Edit prompt",
        onBack = onBack,
        saving = form.saving,
        canSave = form.loaded && form.name.isNotBlank(),
        onSave = { viewModel.save(onBack) },
        error = form.error?.let { "Not saved. ${it.userMessage()}" },
        menu = if (viewModel.isNew) null else { close ->
            EditorMenuItem("Delete", androidx.compose.material.icons.Icons.Outlined.Delete, danger = true, enabled = !form.saving) { close(); confirmDelete = true }
        },
    ) {
        FieldBlock("Name", form.name, { v -> viewModel.update { it.copy(name = v) } })
        if (viewModel.isNew) {
            Text(
                "Kind",
                style = MaterialTheme.typography.labelMedium,
                color = ButlerTheme.colors.textMed,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            )
            com.cherry.butler.ui.components.SegmentedChoice(
                options = listOf("System", "Prefill"),
                selected = if (form.kind == "prefill") 1 else 0,
                onSelect = { i -> viewModel.update { it.copy(kind = if (i == 1) "prefill" else "system") } },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        FieldBlock(
            label = "Content",
            value = form.content,
            onChange = { v -> viewModel.update { it.copy(content = v) } },
            singleLine = false,
            minLines = 10,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}
