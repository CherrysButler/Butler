package com.cherry.butler.feature.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cherry.butler.core.background.BackgroundReplies
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.data.AiSettings
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.Provider
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ChatStyle
import com.cherry.butler.core.design.ButlerTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.cherry.butler.core.design.tones
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.feature.chats.ScreenHead
import com.cherry.butler.ui.components.CenteredMessage
import com.cherry.butler.ui.components.SegmentedChoice
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import com.cherry.butler.ui.components.SkeletonRosterRow
import com.cherry.butler.ui.components.card
import com.cherry.butler.ui.components.isRetryable
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.ui.components.userTitle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Settings, kept short: the model (provider, the proxy in use, links to generation and
 * prompts), memory, the look, and what Butler does on the phone. Janitor-side values are
 * saved the moment they change and rendered back from what Janitor answered.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onEditProxy: (String?) -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenCustomize: () -> Unit = {},
    onOpenGeneration: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenBlocked: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenRouter: () -> Unit = {},
    /** [SettingsPage.Model] from a chat's menu, [SettingsPage.Generation] from the tab; both with a back arrow. */
    page: SettingsPage = SettingsPage.Main,
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val replaces by viewModel.replacesHistory.collectAsStateWithLifecycle()
    val auto by viewModel.autoSummarize.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.notices.collect { snackbar.showSnackbar(it) } }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (onBack != null) {
                Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        when (page) {
                            SettingsPage.Main -> "Settings"
                            SettingsPage.Model -> "Model settings"
                            SettingsPage.Generation -> "Generation"
                        }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                ScreenHead(title = "Settings")
            }
            val s = settings
            when {
                s == null && loading -> Column { repeat(5) { SkeletonRosterRow(withMargin = false) } }
                s == null && loadError != null -> CenteredMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = loadError!!.userTitle(),
                    body = loadError!!.userMessage(),
                ) {
                    if (loadError!!.isRetryable()) {
                        Spacer(Modifier.height(16.dp))
                        KeyButton(label = "Try again", onClick = viewModel::refresh)
                    }
                }
                s != null -> SettingsBody(
                    settings = s,
                    saving = saving,
                    replaces = replaces,
                    auto = auto,
                    viewModel = viewModel,
                    onEditProxy = onEditProxy,
                    onOpenPrompts = onOpenPrompts,
                    onOpenCustomize = onOpenCustomize,
                    onOpenGeneration = onOpenGeneration,
                    onOpenNotifications = onOpenNotifications,
                    onOpenBlocked = onOpenBlocked,
                    onOpenDiagnostics = onOpenDiagnostics,
                    onOpenRouter = onOpenRouter,
                    page = page,
                )
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter)) { data ->
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .card(color = ButlerTheme.colors.surfaceHigh)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun SettingsBody(
    settings: AiSettings,
    saving: String?,
    replaces: Boolean,
    auto: Boolean,
    viewModel: SettingsViewModel,
    onEditProxy: (String?) -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenCustomize: () -> Unit,
    onOpenGeneration: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenRouter: () -> Unit,
    page: SettingsPage,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        when (page) {
            SettingsPage.Main -> {
                ModelSection(settings, saving, viewModel, onEditProxy, onOpenPrompts, onOpenGeneration, onOpenRouter)
                SettingsSection(title = "Memory") {
                    SwitchRow("Summary replaces old messages", null, replaces, viewModel::setReplacesHistory)
                    SwitchRow("Summarize every ${MemoryPrefs.AUTO_EVERY} messages", null, auto, viewModel::setAutoSummarize)
                }
                LookSection(viewModel, onOpenCustomize)
                AppSection(onOpenNotifications, onOpenBlocked, onOpenDiagnostics)
            }
            SettingsPage.Model -> {
                ModelSection(settings, saving, viewModel, onEditProxy, onOpenPrompts, onOpenGeneration = null, onOpenRouter = onOpenRouter)
                GenerationSections(settings.generation, saving, viewModel)
            }
            SettingsPage.Generation -> GenerationSections(settings.generation, saving, viewModel)
        }
    }
}

/** Which of the settings pages this is. */
enum class SettingsPage { Main, Model, Generation }

/**
 * Where replies come from: proxy or Janitor's own, then the one proxy in use. The others wait
 * in its menu with editing and adding; nothing about them is spelled out until asked for.
 */
@Composable
private fun ModelSection(
    settings: AiSettings,
    saving: String?,
    viewModel: SettingsViewModel,
    onEditProxy: (String?) -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenGeneration: (() -> Unit)?,
    onOpenRouter: () -> Unit = {},
) {
    SettingsSection(title = "Model") {
        Box(modifier = Modifier.padding(vertical = 12.dp)) {
            SegmentedChoice(
                options = listOf("Your proxy", if (settings.routerEnabled) "Janitor Router" else "JLLM"),
                selected = if (settings.provider == Provider.Proxy) 0 else 1,
                onSelect = { i ->
                    val p = if (i == 0) Provider.Proxy else Provider.Janitor
                    if (p != settings.provider && saving != "provider") viewModel.setProvider(p)
                },
            )
        }
        RowDivider()
        if (settings.provider == Provider.Proxy) ProxyPicker(settings, saving, viewModel, onEditProxy)
        if (settings.provider == Provider.Janitor) {
            LinkRow(
                title = "Janitor Router",
                subtitle = if (settings.routerEnabled) "On: Janitor's paid models answer" else "Janitor's paid models · needs Janitor Plus",
                onClick = onOpenRouter,
            )
        }
        if (onOpenGeneration != null) LinkRow(title = "Generation", subtitle = generationSummary(settings.generation), onClick = onOpenGeneration)
        LinkRow(title = "Prompts", subtitle = "${settings.prompts.size} saved", onClick = onOpenPrompts)
    }
}

@Composable
private fun ProxyPicker(settings: AiSettings, saving: String?, viewModel: SettingsViewModel, onEditProxy: (String?) -> Unit) {
    val chosen = settings.selectedProxy
    DropRow(
        title = chosen?.name?.ifBlank { "Untitled" } ?: "Choose a proxy",
        value = chosen?.let { proxyLine(it.model, it.apiUrl, it.hasKey) } ?: if (settings.proxies.isEmpty()) "None yet" else null,
        valueIsWarning = chosen != null && !chosen.hasKey,
        busy = saving?.startsWith("proxy:") == true,
    ) { close ->
        settings.proxies.forEach { p ->
            DropItem(
                title = p.name.ifBlank { "Untitled" },
                subtitle = proxyLine(p.model, p.apiUrl, p.hasKey),
                selected = p.id == settings.selectedProxyId,
                onClick = {
                    close()
                    if (p.id != settings.selectedProxyId) viewModel.selectProxy(p.id)
                },
                trailing = {
                    IconButton(onClick = { close(); onEditProxy(p.id) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${p.name}", tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
                    }
                },
            )
        }
        if (settings.proxies.isNotEmpty()) DropDivider()
        DropItem(title = "Add a proxy", onClick = { close(); onEditProxy(null) }, trailing = {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.padding(12.dp).size(20.dp))
        })
    }
}

private fun proxyLine(model: String, url: String, hasKey: Boolean): String = buildString {
    append(model.ifBlank { "No model" })
    append(" · ").append(hostOf(url))
    if (!hasKey) append(" · no key")
}

private fun generationSummary(gen: JsonObject): String {
    val temp = gen["temperature"]?.jsonPrimitive?.floatOrNull ?: 0.8f
    val max = gen["max_new_token"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 500
    return "Temperature ${temp.fixed(2)} · replies up to ${"%,d".format(java.util.Locale.US, max)} tokens"
}

/** The look: three themes as tiles, the chat layout as a choice, and the text styling. */
@Composable
private fun LookSection(viewModel: SettingsViewModel, onOpenCustomize: () -> Unit) {
    val current by viewModel.theme.collectAsStateWithLifecycle()
    val chatStyle by viewModel.chatStyle.collectAsStateWithLifecycle()
    SettingsSection(title = "Look") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTheme.entries.forEach { theme -> ThemeTile(theme, theme == current, Modifier.weight(1f)) { viewModel.setTheme(theme) } }
        }
        RowDivider()
        DropRow(title = "Chat layout", value = chatStyle.label) { close ->
            ChatStyle.entries.forEach { style ->
                DropItem(title = style.label, subtitle = style.blurb, selected = style == chatStyle, onClick = { close(); viewModel.setChatStyle(style) })
            }
        }
        LinkRow(title = "Customize chat text", subtitle = null, onClick = onOpenCustomize)
    }
}

/** A thumbnail of the look (its ground, one card, a short red bar) with its name under it. */
@Composable
private fun ThemeTile(theme: AppTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = theme.tones
    val frame by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else ButlerTheme.colors.rule,
        animationSpec = com.cherry.butler.core.design.Motion.enter(com.cherry.butler.core.design.Motion.SHORT),
        label = "theme-frame",
    )
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(t.ground)
                .border(if (selected) 2.dp else 1.dp, frame, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(22.dp).background(t.surfaceElevated, RoundedCornerShape(5.dp)))
            Box(Modifier.width(22.dp).height(5.dp).background(t.red, RoundedCornerShape(3.dp)))
        }
        Text(
            text = theme.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.textMed,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
    }
}

/** The sampler, in three short groups. Each value is saved to Janitor as it is let go of. */
@Composable
private fun GenerationSections(gen: JsonObject, saving: String?, viewModel: SettingsViewModel) {
    fun num(key: String, default: Float) = gen[key]?.jsonPrimitive?.floatOrNull ?: default
    fun int(key: String, default: Int) = gen[key]?.jsonPrimitive?.intOrNull ?: gen[key]?.jsonPrimitive?.floatOrNull?.toInt() ?: default
    fun bool(key: String) = gen[key]?.jsonPrimitive?.booleanOrNull ?: false

    SettingsSection(title = "Length") {
        NumberRow("Reply length", "Tokens, at most", int("max_new_token", 500), { viewModel.setGeneration("max_new_token", JsonPrimitive(it)) }, 16..65_536)
        NumberRow("Context", "Tokens of history sent", int("context_length", 8192), { viewModel.setGeneration("context_length", JsonPrimitive(it)) }, 512..1_000_000)
    }
    SettingsSection(title = "Sampling") {
        SliderRow("Temperature", num("temperature", 0.8f), 0f..2f, 0.05f, { it.fixed(2) }, { viewModel.setGeneration("temperature", JsonPrimitive(it)) }, busy = saving == "gen:temperature")
        SliderRow("Top P", num("top_p", 1f), 0f..1f, 0.01f, { it.fixed(2) }, { viewModel.setGeneration("top_p", JsonPrimitive(it)) }, busy = saving == "gen:top_p")
        SliderRow("Top K", int("top_k", 0).toFloat(), 0f..100f, 1f, { it.toInt().toString() }, { viewModel.setGeneration("top_k", JsonPrimitive(it.toInt())) }, busy = saving == "gen:top_k")
        SliderRow("Repetition penalty", num("repetition_penalty", 0f), 0f..2f, 0.01f, { it.fixed(2) }, { viewModel.setGeneration("repetition_penalty", JsonPrimitive(it)) }, busy = saving == "gen:repetition_penalty")
        SliderRow("Frequency penalty", num("frequency_penalty", 0f), 0f..2f, 0.01f, { it.fixed(2) }, { viewModel.setGeneration("frequency_penalty", JsonPrimitive(it)) }, busy = saving == "gen:frequency_penalty")
    }
    SettingsSection(title = "Replies") {
        SwitchRow("Thinking", null, bool("enable_thinking"), { viewModel.setGeneration("enable_thinking", JsonPrimitive(it)) })
        SwitchRow("Reasoning", null, bool("enable_reasoning"), { viewModel.setGeneration("enable_reasoning", JsonPrimitive(it)) })
        SwitchRow("Short responses", null, bool("enable_short_responses"), { viewModel.setGeneration("enable_short_responses", JsonPrimitive(it)) })
        SwitchRow("Prefill", null, bool("prefill_enabled"), { viewModel.setGeneration("prefill_enabled", JsonPrimitive(it)) })
        if (bool("prefill_enabled")) {
            PrefillField(gen["prefill_text"]?.jsonPrimitive?.contentOrNull.orEmpty()) { viewModel.setGeneration("prefill_text", JsonPrimitive(it)) }
        }
    }
}

@Composable
private fun PrefillField(value: String, onCommit: (String) -> Unit) {
    var text by rememberSynced(value)
    Column {
        FieldBlock(label = "Prefill text", value = text, onChange = { text = it }, singleLine = false, minLines = 2)
        if (text != value) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = Alignment.CenterEnd) {
                KeyButton(label = "Save prefill", onClick = { onCommit(text) }, primary = true)
            }
        }
    }
}

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.") ?: url.ifBlank { "no URL" }

/**
 * What Butler does on this phone. Background replies: on most phones Android's own "run in
 * the background?" question; on phones that freeze apps anyway (Transsion's), only a
 * home-screen widget keeps Butler running, so turning it on explains that in a pop-up and
 * places the widget. Then the lock, behind the phone's fingerprint or screen lock.
 */
@Composable
private fun AppSection(onOpenNotifications: () -> Unit, onOpenBlocked: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val widgets = remember(context) {
        dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, WidgetsEntryPoint::class.java).widgets()
    }
    val lock = remember(context) {
        dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, LockEntryPoint::class.java).lock()
    }
    val freezes = BackgroundReplies.freezesAnyway
    fun isOn() = if (freezes) widgets.isPlaced() else BackgroundReplies.isExempt(context)
    var on by remember { mutableStateOf(isOn()) }
    // The answer comes from a system or launcher screen; read it again on return.
    LifecycleResumeEffect(Unit) {
        on = isOn()
        onPauseOrDispose { }
    }
    // null: closed; false: the explanation; true: manual steps (the launcher can't place it).
    var popup by remember { mutableStateOf<Boolean?>(null) }
    popup?.let { manual ->
        WidgetNeededDialog(
            manual = manual,
            onAdd = { popup = if (widgets.requestPin()) null else true },
            onDismiss = { popup = null },
        )
    }
    val locked by lock.enabled.collectAsStateWithLifecycle()
    val grace by lock.graceMs.collectAsStateWithLifecycle()
    val activity = context as? androidx.fragment.app.FragmentActivity

    SettingsSection(title = "App") {
        LinkRow(title = "Notifications", subtitle = null, onClick = onOpenNotifications)
        LinkRow(title = "Blocked", subtitle = null, onClick = onOpenBlocked)
        LinkRow(title = "Report a problem", subtitle = "A report you read first, then share yourself", onClick = onOpenDiagnostics)
        SwitchRow(
            title = "Finish replies in the background",
            subtitle = null,
            checked = on,
            onChange = { turnOn ->
                when {
                    freezes && turnOn -> popup = false
                    freezes -> android.widget.Toast.makeText(
                        context, "Remove the Butler widget from your home screen to turn this off.", android.widget.Toast.LENGTH_LONG,
                    ).show()
                    else -> {
                        val intent = if (turnOn) BackgroundReplies.exemptIntent(context) else BackgroundReplies.exemptSettingsIntent()
                        runCatching { context.startActivity(intent) }
                            .onFailure { runCatching { context.startActivity(BackgroundReplies.exemptSettingsIntent()) } }
                    }
                }
            },
        )
        SwitchRow(
            title = "Lock Butler",
            subtitle = if (lock.canLock() || locked) null else "Set a screen lock on your phone first",
            checked = locked,
            enabled = lock.canLock() || locked,
            onChange = { turnOn ->
                // Proving it works once before relying on it, both ways.
                activity?.let { a ->
                    lock.authenticate(a, title = if (turnOn) "Turn on the lock" else "Turn off the lock") { ok -> if (ok) lock.setEnabled(turnOn) }
                }
            },
        )
        if (locked) {
            val graces = listOf(0L to "Right away", 60_000L to "After 1 minute away", 300_000L to "After 5 minutes away", 900_000L to "After 15 minutes away")
            DropRow(title = "Lock", value = graces.firstOrNull { it.first == grace }?.second ?: "Right away") { close ->
                graces.forEach { (ms, label) -> DropItem(title = label, selected = grace == ms, onClick = { close(); lock.setGrace(ms) }) }
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface WidgetsEntryPoint {
    fun widgets(): com.cherry.butler.core.background.ButlerWidgets
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface LockEntryPoint {
    fun lock(): com.cherry.butler.core.security.AppLock
}
