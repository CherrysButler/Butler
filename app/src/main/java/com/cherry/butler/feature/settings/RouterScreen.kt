package com.cherry.butler.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.RouterGroup
import com.cherry.butler.core.data.RouterRepository
import com.cherry.butler.core.data.remote.dto.RouterModelDto
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.network.JanitorErrorCode
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim
import com.cherry.butler.ui.components.SkeletonRosterRow
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RouterViewModel @Inject constructor(private val repository: RouterRepository) : ViewModel() {
    val catalog = repository.catalog
    val config = repository.config
    val wallet = repository.wallet

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The last refused write, in Janitor's words. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _error.value = null
            runCatching { repository.load() }.onFailure { _error.value = it as? ApiError ?: ApiError.Unknown(it) }
        }
    }

    fun groups(): List<RouterGroup> = repository.groups()

    fun setEnabled(on: Boolean) = write { repository.setEnabled(on) }
    fun setModel(id: String) = write { repository.setModel(id) }
    fun setShowThinking(on: Boolean) = write { repository.setShowThinking(on) }
    fun setFavorite(id: String, on: Boolean) = write { repository.setFavorite(id, on) }
    fun claimBonus() = write { repository.claimBonus() }
    fun dismissNotice() { _notice.value = null }

    private fun write(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _notice.value = null
            runCatching { block() }.onFailure { e ->
                val api = e as? ApiError ?: ApiError.Unknown(e)
                _notice.value = if ((api as? ApiError.Api)?.janitorCode == JanitorErrorCode.ROUTER_DISABLED) {
                    "Janitor Router needs Janitor Plus. Butler can show it, but only Janitor can turn it on for this account."
                } else "Not saved. ${api.userMessage()}"
            }
            _busy.value = false
        }
    }
}

/**
 * Janitor Router: Janitor's own metered models. The wallet, the switch, the model and
 * its price, and the catalogue to pick from. Reads on any account; the writes need
 * Janitor Plus, and the screen says so when Janitor refuses.
 *
 * UNTESTED (2026-10-04): no Plus account was available, so every write here answered
 * `JANITOR_ROUTER_DISABLED`. The body shapes come from the API's own validation messages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterScreen(onBack: () -> Unit, viewModel: RouterViewModel = hiltViewModel()) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        ModelPickerSheet(
            groups = viewModel.groups(),
            selected = config?.modelId,
            favorites = config?.favoriteModelIds.orEmpty().toSet(),
            onPick = { picking = false; viewModel.setModel(it.modelId) },
            onFavorite = { m, on -> viewModel.setFavorite(m.modelId, on) },
            onDismiss = { picking = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Janitor Router", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        val c = config
        when {
            c == null && error != null -> InlineErrorCard(error!!, onRetry = viewModel::load, modifier = Modifier.padding(16.dp))
            c == null -> Column(modifier = Modifier.padding(12.dp)) { repeat(4) { SkeletonRosterRow(withMargin = false) } }
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
                item {
                    notice?.let { msg ->
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = ButlerTheme.colors.danger,
                            modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::dismissNotice).padding(horizontal = 28.dp, vertical = 8.dp),
                        )
                    }
                    SettingsSection(
                        title = "Wallet",
                        footnote = wallet?.let { w ->
                            when {
                                !w.walletAvailable -> "This account has no router wallet."
                                !w.canGenerate -> "Nothing to spend yet: the router can't answer until the wallet holds something."
                                w.lowBalance -> "Running low."
                                else -> null
                            }
                        },
                    ) {
                        val w = wallet
                        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Balance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    text = w?.let { usd(it.totalMicros) } ?: "—",
                                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                                    color = if (w?.canGenerate == true) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.textLow,
                                )
                            }
                            val bonus = w?.signupBonus
                            if (bonus != null && bonus.claimable && !bonus.claimed) {
                                KeyButton(label = "Claim ${usd(bonus.amountMicros)}", onClick = viewModel::claimBonus, enabled = !busy, primary = true)
                            }
                        }
                    }
                    SettingsSection(title = "Router") {
                        SwitchRow(
                            title = "Answer with Janitor Router",
                            subtitle = if (c.enabled) "Replies come from the model below, billed to the wallet" else "Off: Janitor's free model (JLLM) answers",
                            checked = c.enabled,
                            onChange = viewModel::setEnabled,
                            enabled = !busy,
                        )
                        val chosen = catalog?.models?.firstOrNull { it.modelId == c.modelId }
                        LinkRow(
                            title = chosen?.displayName ?: c.modelId ?: "Choose a model",
                            subtitle = chosen?.let { priceLine(it) } ?: catalog?.let { "${it.models.size} models in the catalogue" },
                            onClick = { picking = true },
                        )
                        SwitchRow(
                            title = "Show the model's thinking",
                            subtitle = "For models that reason before they answer",
                            checked = c.showThinking,
                            onChange = viewModel::setShowThinking,
                            enabled = !busy,
                        )
                    }
                    Text(
                        text = "Janitor Router is Janitor's own paid service: models it hosts, billed per token from a wallet on your account. It needs Janitor Plus. Your own proxy is unaffected by anything here.\n\nUntested: turning the router on, choosing a model and the favourites were built against Janitor's error messages, not a Plus account. If one misbehaves, Settings › Report a problem will show what Janitor answered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ButlerTheme.colors.textLow,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

/** The catalogue, grouped as Janitor groups it, with prices and a star to keep a favourite. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    groups: List<RouterGroup>,
    selected: String?,
    favorites: Set<String>,
    onPick: (RouterModelDto) -> Unit,
    onFavorite: (RouterModelDto, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        LazyColumn(modifier = Modifier.navigationBarsPadding().heightIn(max = 680.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
            item {
                Text("Model", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            val favs = groups.flatMap { it.models }.distinctBy { it.modelId }.filter { it.modelId in favorites }
            val all = (if (favs.isEmpty()) emptyList() else listOf(RouterGroup("Favourites", favs))) + groups
            all.forEach { g ->
                item(key = "g-${g.label}") {
                    Text(
                        g.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ButlerTheme.colors.textMed,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
                items(g.models, key = { "${g.label}/${it.modelId}" }) { m ->
                    val on = m.modelId == selected
                    val fav = m.modelId in favorites
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(m) }.heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    m.displayName.ifBlank { m.modelId },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (m.recommended) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("Recommended", style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.onAccentSoft, modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, Pill).padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text(priceLine(m), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = ButlerTheme.colors.textLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (on) Icon(Icons.Rounded.Check, contentDescription = "Chosen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        IconButton(onClick = { onFavorite(m, !fav) }) {
                            Icon(
                                if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = if (fav) "Remove from favourites" else "Add to favourites",
                                tint = if (fav) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "$2.00 in · $6.00 out per 1M · 500k context" from the catalogue's micro-dollars. */
private fun priceLine(m: RouterModelDto): String = buildString {
    append(usd(m.inputMicrosPer1m)).append(" in · ").append(usd(m.outputMicrosPer1m)).append(" out per 1M")
    if (m.contextLength > 0) append(" · ").append(compact(m.contextLength)).append(" context")
    if (m.creator.isNotBlank()) append(" · ").append(m.creator)
}

private fun usd(micros: Long): String = String.format(Locale.US, "$%.2f", micros / 1_000_000.0)

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).removeSuffix(".0M").let { if (it.endsWith("M")) it else it + "M" }
    n >= 1_000 -> "${n / 1_000}k"
    else -> n.toString()
}
