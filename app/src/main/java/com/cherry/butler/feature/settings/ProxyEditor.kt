package com.cherry.butler.feature.settings

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.UnfoldMore
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.Prompt
import com.cherry.butler.core.data.ProxyConfig
import com.cherry.butler.core.data.ProxyDraft
import com.cherry.butler.core.data.SettingsRepository
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.feature.chat.DeleteConfirmDialog
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.hairlineFrame
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.cherry.butler.core.data.OpenRouterOptions
import com.cherry.butler.core.data.OpenRouterOptionsStore
import javax.inject.Inject

data class ProxyForm(
    val name: String = "",
    val apiUrl: String = DEFAULT_URL,
    val model: String = "",
    /** What the user typed for the key. Empty means "keep the saved key". */
    val newKey: String = "",
    val promptId: String? = null,
    /** OpenRouter only, kept on this phone (see [OpenRouterOptions]). */
    val preset: String = "",
    val providers: String = "",
    val allowFallbacks: Boolean = true,
    val prefer: OpenRouterOptions.Prefer = OpenRouterOptions.Prefer.Default,
    val thinking: OpenRouterOptions.Thinking = OpenRouterOptions.Thinking.Default,
    val hasSavedKey: Boolean = false,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: ApiError? = null,
) {
    val isOpenRouter: Boolean get() = OpenRouterOptions.isOpenRouter(apiUrl)

    fun openRouterOptions() = OpenRouterOptions(
        preset = preset.trim(),
        providers = OpenRouterOptions.parseProviders(providers),
        allowFallbacks = allowFallbacks,
        prefer = prefer,
        thinking = thinking,
    )

    companion object {
        const val DEFAULT_URL = "https://openrouter.ai/api/v1/chat/completions"
    }
}

/**
 * One proxy configuration. The key is write-only: the form never holds the saved key,
 * only whether one exists; typing a new one replaces it, leaving the field empty keeps it.
 * The typed key lives only in this view model's memory — not in saved state, not on disk.
 */
@HiltViewModel
class ProxyEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SettingsRepository,
    private val openRouter: OpenRouterOptionsStore,
) : ViewModel() {

    val proxyId: String? = savedStateHandle.get<String>(ARG)?.takeIf { it != NEW }
    val isNew: Boolean get() = proxyId == null

    private val _form = MutableStateFlow(ProxyForm())
    val form: StateFlow<ProxyForm> = _form.asStateFlow()

    val prompts: List<Prompt> get() = repository.settings.value?.prompts.orEmpty()

    init {
        viewModelScope.launch {
            // Fresh, never the cache: saving writes every field back, so a stale form would
            // undo a change made elsewhere (the model sheet, the website).
            val settings = runCatching { repository.load(refresh = true) }.getOrNull()
            val existing: ProxyConfig? = settings?.proxies?.firstOrNull { it.id == proxyId }
            _form.value = if (existing != null) {
                val options = openRouter.get(existing.id)
                ProxyForm(
                    name = existing.name, apiUrl = existing.apiUrl, model = existing.model, promptId = existing.promptId,
                    preset = options.preset, providers = options.providers.joinToString(", "),
                    allowFallbacks = options.allowFallbacks, prefer = options.prefer, thinking = options.thinking,
                    hasSavedKey = existing.hasKey, loaded = true,
                )
            } else {
                ProxyForm(loaded = true)
            }
        }
    }

    fun update(change: (ProxyForm) -> ProxyForm) = _form.update { change(it).copy(error = null) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.saving || f.name.isBlank() || f.apiUrl.isBlank()) return
        viewModelScope.launch {
            _form.update { it.copy(saving = true, error = null) }
            runCatching {
                val before = repository.settings.value?.proxies.orEmpty().map { it.id }.toSet()
                val after = repository.saveProxy(ProxyDraft(proxyId, f.name, f.apiUrl, f.model, f.newKey.takeIf { it.isNotBlank() }, f.promptId))
                val saved = after.proxies.firstOrNull { it.id == proxyId } ?: after.proxies.firstOrNull { it.id !in before }
                // Options only mean something to OpenRouter; another host drops them.
                if (saved != null) openRouter.set(saved.ids, if (f.isOpenRouter) f.openRouterOptions() else OpenRouterOptions())
            }.onSuccess {
                _form.update { it.copy(saving = false, newKey = "") }
                onDone()
            }.onFailure { e ->
                _form.update { it.copy(saving = false, error = e as? ApiError ?: ApiError.Unknown(e)) }
            }
        }
    }

    /** A copy of this proxy, key and OpenRouter options included; the key never surfaces. */
    fun copy(onDone: () -> Unit) {
        val id = proxyId ?: return
        viewModelScope.launch {
            _form.update { it.copy(saving = true, error = null) }
            runCatching { repository.cloneProxy(id) }
                .onSuccess { onDone() }
                .onFailure { e -> _form.update { it.copy(saving = false, error = e as? ApiError ?: ApiError.Unknown(e)) } }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = proxyId ?: return
        viewModelScope.launch {
            _form.update { it.copy(saving = true, error = null) }
            runCatching { repository.deleteProxy(id) }
                .onSuccess { onDone() }
                .onFailure { e -> _form.update { it.copy(saving = false, error = e as? ApiError ?: ApiError.Unknown(e)) } }
        }
    }

    companion object {
        const val ARG = "proxyId"
        const val NEW = "new"
    }
}

@Composable
fun ProxyEditorScreen(onBack: () -> Unit, viewModel: ProxyEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var pickingPrompt by remember { mutableStateOf(false) }
    val promptName = viewModel.prompts.firstOrNull { it.id == form.promptId }?.name
        ?: form.promptId?.let { "Linked prompt" }

    if (confirmDelete) {
        DeleteConfirmDialog(
            count = 1,
            title = "Delete proxy",
            body = "\"${form.name}\" will be removed from your Janitor account, key included.",
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    if (pickingPrompt) {
        PromptPickerSheet(
            prompts = viewModel.prompts,
            selectedId = form.promptId,
            onPick = { id -> viewModel.update { it.copy(promptId = id) }; pickingPrompt = false },
            onDismiss = { pickingPrompt = false },
        )
    }

    EditorScaffold(
        title = if (viewModel.isNew) "New proxy" else "Edit proxy",
        onBack = onBack,
        saving = form.saving,
        canSave = form.loaded && form.name.isNotBlank() && form.apiUrl.isNotBlank(),
        onSave = { viewModel.save(onBack) },
        error = form.error?.let { "Not saved. ${it.userMessage()}" },
        menu = if (viewModel.isNew) null else { close ->
            EditorMenuItem("Make a copy", Icons.Outlined.ContentCopy, enabled = !form.saving) { close(); viewModel.copy(onBack) }
            EditorMenuItem("Delete", Icons.Outlined.Delete, danger = true, enabled = !form.saving) { close(); confirmDelete = true }
        },
    ) {
        FieldBlock("Name", form.name, { v -> viewModel.update { it.copy(name = v) } }, placeholder = "OpenRouter")
        FieldBlock("Endpoint URL", form.apiUrl, { v -> viewModel.update { it.copy(apiUrl = v) } }, keyboardType = KeyboardType.Uri)
        FieldBlock("Model", form.model, { v -> viewModel.update { it.copy(model = v) } }, placeholder = "provider/model-name")
        FieldBlock(
            label = "API key",
            value = form.newKey,
            onChange = { v -> viewModel.update { it.copy(newKey = v) } },
            placeholder = if (form.hasSavedKey) "Saved. Type to replace it" else "Paste your key",
            secret = true,
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Prompt", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(ButlerTheme.colors.surfaceHigh)
                    .clickable { pickingPrompt = true }
                    .heightIn(min = 50.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = promptName ?: "None",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (promptName != null) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.textLow,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.UnfoldMore, contentDescription = "Choose prompt", tint = ButlerTheme.colors.textLow)
            }
        }
        if (form.isOpenRouter) {
            ThinkingRow(form) { change -> viewModel.update(change) }
            OpenRouterSection(form) { change -> viewModel.update(change) }
        }
    }
}

/** The editor frame shared by proxy and prompt: back, a plate title, Save, and the fields. */
@Composable
fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    saving: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    error: String?,
    /** What else can be done to it (copy, delete), behind a ⋮ beside Save. */
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            PlateText(text = title, level = PlateLevel.Name, modifier = Modifier.weight(1f))
            if (menu != null) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = ButlerTheme.colors.textMed)
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = open,
                        onDismissRequest = { open = false },
                        shape = MaterialTheme.shapes.medium,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) { menu { open = false } }
                }
                Spacer(Modifier.width(4.dp))
            }
            KeyButton(label = if (saving) "Saving…" else "Save", onClick = onSave, enabled = canSave && !saving, primary = true, modifier = Modifier.height(40.dp))
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.danger,
                modifier = Modifier.fillMaxWidth().padding(16.dp).clip(MaterialTheme.shapes.medium).background(ButlerTheme.colors.danger.copy(alpha = 0.14f)).padding(12.dp),
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) { content() }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PromptPickerSheet(
    prompts: List<Prompt>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    noneSubtitle: String = "No prompt for this proxy",
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            PlateText("Prompt", PlateLevel.Name, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            ChoiceRow(title = "None", subtitle = noneSubtitle, selected = selectedId == null, onClick = { onPick(null) })
            prompts.forEach { p ->
                ChoiceRow(
                    title = p.name.ifBlank { "Untitled" },
                    subtitle = p.content.lines().joinToString(" ").take(80),
                    selected = p.id == selectedId,
                    onClick = { onPick(p.id) },
                )
            }
            Spacer(Modifier.width(1.dp).height(12.dp))
        }
    }
}

/**
 * How hard a thinking model thinks, for an OpenRouter proxy: out in the open rather than
 * folded into routing, since it changes the replies themselves. Models that don't think
 * ignore it.
 */
@Composable
private fun ThinkingRow(form: ProxyForm, update: ((ProxyForm) -> ProxyForm) -> Unit) {
    DropRow(title = "Thinking", value = form.thinking.label) { close ->
        OpenRouterOptions.Thinking.entries.forEach { level ->
            DropItem(
                title = level.label,
                subtitle = when (level) {
                    OpenRouterOptions.Thinking.Default -> "Leave it to the model"
                    OpenRouterOptions.Thinking.Off -> "No thinking, where the model allows it"
                    OpenRouterOptions.Thinking.Max -> "Longest thinking, slowest and dearest"
                    else -> null
                },
                selected = level == form.thinking,
                onClick = { close(); update { it.copy(thinking = level) } },
            )
        }
    }
}

/**
 * OpenRouter's own options, which Janitor doesn't carry: a preset in place of the model,
 * and which providers to route to. Shown only for an openrouter.ai endpoint.
 */
@Composable
private fun OpenRouterSection(form: ProxyForm, update: ((ProxyForm) -> ProxyForm) -> Unit) {
    val anySet = form.preset.isNotBlank() || form.providers.isNotBlank() || !form.allowFallbacks || form.prefer != OpenRouterOptions.Prefer.Default
    var open by rememberSaveable { mutableStateOf(anySet) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "or-chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("OpenRouter routing", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = when {
                    form.preset.isNotBlank() -> form.preset
                    form.providers.isNotBlank() -> form.providers
                    else -> "Any provider"
                },
                style = MaterialTheme.typography.labelSmall,
                color = ButlerTheme.colors.textLow,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = if (open) "Hide" else "Show",
            tint = ButlerTheme.colors.textMed,
            modifier = Modifier.graphicsLayer { rotationZ = turn },
        )
    }
    androidx.compose.animation.AnimatedVisibility(visible = open) {
        Column { OpenRouterFields(form, update) }
    }
}

@Composable
private fun OpenRouterFields(form: ProxyForm, update: ((ProxyForm) -> ProxyForm) -> Unit) {
    FieldBlock("Preset, in place of the model", form.preset, { v -> update { it.copy(preset = v) } }, placeholder = "@preset/roleplay")
    FieldBlock("Providers, in order", form.providers, { v -> update { it.copy(providers = v) } }, placeholder = "deepinfra, together")
    SwitchRow(
        title = "Fall back to other providers",
        subtitle = null,
        checked = form.allowFallbacks,
        onChange = { on -> update { it.copy(allowFallbacks = on) } },
    )
    Text(
        "Prefer",
        style = MaterialTheme.typography.labelMedium,
        color = ButlerTheme.colors.textMed,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
    )
    val prefers = OpenRouterOptions.Prefer.entries
    com.cherry.butler.ui.components.SegmentedChoice(
        options = listOf("Any", "Price", "Speed", "Latency"),
        selected = prefers.indexOf(form.prefer),
        onSelect = { i -> update { it.copy(prefer = prefers[i]) } },
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

/** A row in an editor's ⋮ menu. */
@Composable
fun EditorMenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (danger) ButlerTheme.colors.danger else MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.titleSmall, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (danger) tint else ButlerTheme.colors.textMed) },
        enabled = enabled,
        onClick = onClick,
    )
}
