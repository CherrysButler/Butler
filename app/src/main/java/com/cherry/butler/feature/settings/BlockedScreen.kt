package com.cherry.butler.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.BlockRepository
import com.cherry.butler.core.data.Blocks
import com.cherry.butler.core.data.BlockedContentDto
import com.cherry.butler.core.data.remote.dto.TagDto
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SkeletonRosterRow
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockedViewModel @Inject constructor(private val repository: BlockRepository) : ViewModel() {
    private val _blocks = MutableStateFlow<Blocks?>(null)
    val blocks: StateFlow<Blocks?> = _blocks.asStateFlow()

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _tags = MutableStateFlow<List<TagDto>>(emptyList())
    val tags: StateFlow<List<TagDto>> = _tags.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _error.value = null
            runCatching { repository.load() }
                .onSuccess { _blocks.value = it }
                .onFailure { _error.value = it as? ApiError ?: ApiError.Unknown(it) }
        }
    }

    fun loadTags() {
        if (_tags.value.isNotEmpty()) return
        viewModelScope.launch { runCatching { repository.allTags() }.onSuccess { _tags.value = it } }
    }

    fun unblockCharacter(id: String) = write { repository.setCharacter(id, false) }
    fun unblockCreator(id: String) = write { repository.setCreator(id, false) }
    fun setTag(id: Int, blocked: Boolean) = write { repository.setTag(id, blocked) }
    fun setKeyword(word: String, blocked: Boolean) {
        if (word.isBlank()) return
        write { repository.setKeyword(word, blocked) }
    }

    /** Writes, then shows the list as written (or, after a refusal, as Janitor holds it). */
    private fun write(block: suspend () -> BlockedContentDto) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            val written = runCatching { block() }.onFailure { _error.value = it as? ApiError ?: ApiError.Unknown(it) }.getOrNull()
            runCatching { repository.load(written) }.onSuccess { _blocks.value = it }
            _busy.value = false
        }
    }
}

/** Everything blocked on Janitor, each with a way back, and keywords and tags to add. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BlockedScreen(onBack: () -> Unit, viewModel: BlockedViewModel = hiltViewModel()) {
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val allTags by viewModel.tags.collectAsStateWithLifecycle()
    var pickingTag by remember { mutableStateOf(false) }

    if (pickingTag) {
        ModalBottomSheet(
            onDismissRequest = { pickingTag = false },
            shape = com.cherry.butler.core.design.SheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrimColor = com.cherry.butler.ui.components.SheetScrim,
            dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
        ) {
            val blocked = blocks?.tags.orEmpty().map { it.id }.toSet()
            Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                Text("Block a tag", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 8.dp))
                if (allTags.isEmpty()) {
                    repeat(3) { SkeletonRosterRow(withMargin = false) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allTags.filterNot { it.id in blocked }.forEach { tag ->
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(Pill)
                                .background(ButlerTheme.colors.surfaceHigh)
                                .clickable { pickingTag = false; viewModel.setTag(tag.id, true) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Blocked", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            val b = blocks
            error?.let { e ->
                if (b == null) {
                    InlineErrorCard(e, onRetry = viewModel::load, modifier = Modifier.padding(16.dp))
                } else {
                    Text(
                        "Not changed. ${e.userMessage()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ButlerTheme.colors.danger,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    )
                }
            }
            if (b == null && error == null) {
                Column(modifier = Modifier.padding(12.dp)) { repeat(4) { SkeletonRosterRow(withMargin = false) } }
            }
            if (b != null) {
                if (b.characters.isNotEmpty()) SettingsSection(title = "Characters") {
                    b.characters.forEach { c ->
                        BlockedRow(c.avatarUrl, c.name, null, busy) { viewModel.unblockCharacter(c.id) }
                    }
                }
                if (b.creators.isNotEmpty()) SettingsSection(title = "Creators") {
                    b.creators.forEach { c ->
                        BlockedRow(
                            avatarUrl = JanitorConfig.personaAvatarUrl(c.avatar),
                            title = c.name?.takeIf { it.isNotBlank() } ?: c.userName.orEmpty(),
                            subtitle = c.userName?.let { "@$it" },
                            busy = busy,
                        ) { viewModel.unblockCreator(c.id) }
                    }
                }
                SettingsSection(title = "Tags") {
                    b.tags.forEach { t -> BlockedRow(null, t.name, null, busy, showAvatar = false) { viewModel.setTag(t.id, false) } }
                    LinkRow(title = "Block a tag", subtitle = null, onClick = { viewModel.loadTags(); pickingTag = true })
                }
                SettingsSection(title = "Keywords") {
                    b.keywords.forEach { k -> BlockedRow(null, k, null, busy, showAvatar = false) { viewModel.setKeyword(k, false) } }
                    KeywordAdder(busy) { viewModel.setKeyword(it, true) }
                }
            }
        }
    }
}

@Composable
private fun BlockedRow(avatarUrl: String?, title: String, subtitle: String?, busy: Boolean, showAvatar: Boolean = true, onUnblock: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAvatar) {
                Avatar(url = avatarUrl, name = title, size = 40.dp)
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow, maxLines = 1)
            }
            Text(
                text = "Unblock",
                style = MaterialTheme.typography.labelLarge,
                color = if (busy) ButlerTheme.colors.textLow else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = !busy, onClick = onUnblock).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        RowDivider()
    }
}

/** A word, typed, and blocked on Add. */
@Composable
private fun KeywordAdder(busy: Boolean, onAdd: (String) -> Unit) {
    var word by remember { mutableStateOf("") }
    Row(modifier = Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            FieldBlock(label = "Block a keyword", value = word, onChange = { word = it }, placeholder = "A word or phrase")
        }
        KeyButton(
            label = "Add",
            onClick = { onAdd(word); word = "" },
            enabled = !busy && word.isNotBlank(),
            primary = true,
            modifier = Modifier.padding(top = 26.dp).height(44.dp),
        )
    }
}
