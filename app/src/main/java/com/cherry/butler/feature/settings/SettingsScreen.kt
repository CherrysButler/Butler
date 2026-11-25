package com.cherry.butler.feature.settings

import com.cherry.butler.ui.components.ButlerMenu
import com.cherry.butler.ui.components.Unfold
import com.cherry.butler.ui.components.rim
import androidx.compose.material.icons.rounded.Layers
import com.cherry.butler.core.design.orDefaultFor
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Replay
import com.cherry.butler.core.design.hardToRead
import com.cherry.butler.core.design.colorOf
import com.cherry.butler.core.design.isLight
import com.cherry.butler.core.design.accentAdjusted
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cherry.butler.core.background.BackgroundReplies
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.outlined.Info
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.data.AiSettings
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.Provider
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ChatStyle
import com.cherry.butler.core.design.ButlerTheme
import androidx.compose.foundation.background
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
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.SegmentedChoice
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
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
 * prompts), memory, the look, and what Butler does on the phone. Janitor-side values wait
 * in a draft until Save in the corner (every control answers at once); leaving with changes
 * waiting asks first. Phone-only settings take effect as they are touched.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onEditProxy: (String?) -> Unit,
    onOpenPrompts: () -> Unit,
    /** Opens one prompt in the editor, or a new one (null). */
    onOpenPrompt: (String?) -> Unit = {},
    onOpenCustomize: () -> Unit = {},
    onOpenGeneration: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenBlocked: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenRouter: () -> Unit = {},
    onOpenLorebooks: () -> Unit = {},
    /** Opens one of the pages listed on the main page. */
    onOpenPage: (SettingsPage) -> Unit = {},
    /** Which page: the main list on the tab, or one page with a back arrow. */
    page: SettingsPage = SettingsPage.Main,
    onBack: (() -> Unit)? = null,
    /** In the PC's settings window: a page with no way back is headed by its own name, not "Settings". */
    windowed: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    // Ctrl+R / F5 on the desktop.
    com.cherry.butler.ui.components.RegisterRefresh { viewModel.refresh() }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.notices.collect { snackbar.showSnackbar(it) } }

    // Unsaved changes: Back, the back arrow and the bottom bar all ask before leaving.
    val dirty by viewModel.dirty.collectAsStateWithLifecycle()
    var leaving by remember { mutableStateOf<(() -> Unit)?>(null) }
    val tabActive = com.cherry.butler.ui.navigation.LocalTabActive.current
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val back: () -> Unit = onBack ?: {
        // On the tab, Back leaves the app: let the system do it once this screen stops asking.
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }
    androidx.activity.compose.BackHandler(enabled = dirty && (onBack != null || tabActive)) { leaving = back }
    com.cherry.butler.ui.navigation.GuardLeaving(active = dirty && onBack == null && tabActive) { proceed -> leaving = proceed }
    leaving?.let { go ->
        UnsavedDialog(
            saving = saving == SettingsViewModel.SAVING_ALL,
            onSave = { viewModel.save { leaving = null; go() } },
            onDiscard = { leaving = null; viewModel.discard(); go() },
            onKeep = { leaving = null },
        )
    }
    val saveKey: @Composable () -> Unit = {
        if (dirty) SaveKey(saving = saving == SettingsViewModel.SAVING_ALL, onClick = { viewModel.save() })
    }

    val signOutCheck by viewModel.signOutCheck.collectAsStateWithLifecycle()
    signOutCheck?.let { c ->
        com.cherry.butler.feature.chat.DeleteConfirmDialog(
            count = 1,
            title = "Sign out",
            body = buildString {
                append("Your chats stay on Janitor. This ${com.cherry.butler.core.platform.Device.NAME} forgets them, and your settings, until you sign in again.")
                if (c.unsent > 0) {
                    append("\n\n")
                    append(if (c.unsent == 1) "1 message you wrote hasn't reached Janitor yet and will be lost." else "${c.unsent} messages you wrote haven't reached Janitor yet and will be lost.")
                }
            },
            confirmLabel = "Sign out",
            onConfirm = viewModel::signOut,
            onDismiss = viewModel::cancelSignOut,
        )
    }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (onBack != null) {
                Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (dirty) leaving = onBack else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(page.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    saveKey()
                }
            } else if (windowed) {
                // The window's own bar says "Settings"; the page says which.
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(page.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    saveKey()
                }
            } else {
                ScreenHead(title = "Settings", trailing = saveKey)
            }
            val s = settings
            // Only the model pages wait on Janitor's settings; the rest are the phone's own.
            val needsJanitor = page == SettingsPage.Model || page == SettingsPage.Generation || page == SettingsPage.Writers || page == SettingsPage.Prompts
            when {
                needsJanitor && s == null && loading -> Column { repeat(5) { SkeletonRosterRow(withMargin = false) } }
                needsJanitor && s == null && loadError != null -> CenteredMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = loadError!!.userTitle(),
                    body = loadError!!.userMessage(),
                ) {
                    if (loadError!!.isRetryable()) {
                        Spacer(Modifier.height(16.dp))
                        KeyButton(label = "Try again", onClick = viewModel::refresh)
                    }
                }
                else -> SettingsBody(
                    settings = s,
                    saving = saving,
                    viewModel = viewModel,
                    onEditProxy = onEditProxy,
                    onOpenPrompts = onOpenPrompts,
                    onOpenPrompt = onOpenPrompt,
                    onOpenCustomize = onOpenCustomize,
                    onOpenGeneration = onOpenGeneration,
                    onOpenNotifications = onOpenNotifications,
                    onOpenBlocked = onOpenBlocked,
                    onOpenDiagnostics = onOpenDiagnostics,
                    onOpenRouter = onOpenRouter,
                    onOpenLorebooks = onOpenLorebooks,
                    onOpenPage = onOpenPage,
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
    settings: AiSettings?,
    saving: String?,
    viewModel: SettingsViewModel,
    onEditProxy: (String?) -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenPrompt: (String?) -> Unit,
    onOpenCustomize: () -> Unit,
    onOpenGeneration: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenRouter: () -> Unit,
    onOpenLorebooks: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    page: SettingsPage,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp + com.cherry.butler.ui.components.bottomChrome())) {
        when (page) {
            SettingsPage.Main -> {
                SettingsHome(onOpenPage, onOpenLorebooks)
                AccountSection(viewModel)
            }
            SettingsPage.Model -> settings?.let { s ->
                ModelSection(s, saving, viewModel, onEditProxy, onOpenPrompts, onOpenGeneration = null, onOpenRouter = onOpenRouter, onOpenPage = onOpenPage)
                AgentSection(viewModel, onProxy = s.provider == Provider.Proxy, settings = s)
                GenerationSections(s.generation, saving, viewModel, jllm = s.provider == Provider.Janitor)
            }
            SettingsPage.Writers -> settings?.let { s -> WhoWritesWhat(s, viewModel) }
            SettingsPage.Prompts -> settings?.let { s -> PromptsPage(s, viewModel, onOpenPrompt) }
            SettingsPage.Generation -> settings?.let { s -> GenerationSections(s.generation, saving, viewModel, jllm = s.provider == Provider.Janitor) }
            SettingsPage.Chat -> ChatSection(viewModel)
            SettingsPage.Look -> LookSection(viewModel, onOpenCustomize, onOpenThinking = { onOpenPage(SettingsPage.Thinking) }, onOpenProfiles = { onOpenPage(SettingsPage.Profiles) })
            SettingsPage.Profiles -> ProfilesSection(viewModel)
            SettingsPage.Thinking -> ThinkingSection(viewModel)
            SettingsPage.RichTyping -> RichTypingSection(viewModel)
            SettingsPage.Specials -> { SpecialsSection(viewModel); RichTypingSection(viewModel) }
            SettingsPage.App -> { AppSection(onOpenNotifications, onOpenBlocked, onOpenDiagnostics); UpdatesSection(viewModel) }
            SettingsPage.Updates -> UpdatesSection(viewModel)
            SettingsPage.Debug -> DebugSection()
            SettingsPage.Account -> AccountSection(viewModel)
        }
    }
}

/** The main page: each settings page as a row with its own mark, a title and a line on what's inside. */
@Composable
private fun SettingsHome(onOpenPage: (SettingsPage) -> Unit, onOpenLorebooks: () -> Unit) {
    val pages = SettingsPage.entries.filter { it.listed && (it != SettingsPage.Debug || com.cherry.butler.BuildConfig.DEBUG) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .card(color = ButlerTheme.colors.surfaceHigh),
    ) {
        pages.forEachIndexed { i, p ->
            SettingsHomeRow(p.title, p.blurb, p.icon) { onOpenPage(p) }
            if (p == SettingsPage.Chat) {
                RowDivider()
                SettingsHomeRow(
                    title = "Local Lorebooks",
                    blurb = "Create and edit your world information",
                    onClick = onOpenLorebooks,
                    icon = com.cherry.butler.R.drawable.ic_set_lorebooks,
                )
            }
            if (i < pages.lastIndex) RowDivider()
        }
    }
}

@Composable
private fun SettingsHomeRow(title: String, blurb: String, icon: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(icon),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ButlerTheme.colors.textLow)
    }
}

/** The account, under the pages: signing out, which asks first and says what it would cost. */
@Composable
private fun AccountSection(viewModel: SettingsViewModel) {
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    SettingsSection(title = "Account") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !signingOut, onClick = viewModel::askSignOut)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = ButlerTheme.colors.danger, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                text = if (signingOut) "Signing out…" else "Sign out",
                style = MaterialTheme.typography.titleMedium,
                color = ButlerTheme.colors.danger,
            )
        }
    }
}

/** Chat habits kept on the phone: the keyboard, Write for me, memory, and what the thinking line says. */
@Composable
private fun ChatSection(viewModel: SettingsViewModel) {
    val closeKeyboard by viewModel.closeKeyboardOnSend.collectAsStateWithLifecycle()
    SettingsSection(title = "Sending", icon = Icons.Rounded.Keyboard) {
        SwitchRow("Keyboard closes on send", null, closeKeyboard, viewModel::setCloseKeyboardOnSend, icon = Icons.Rounded.Keyboard)
        // A PC keyboard's: which of Enter and Shift+Enter sends; the other starts a new line.
        if (com.cherry.butler.feature.chat.LocalEnterSends.current) {
            val shiftSends by viewModel.shiftEnterSends.collectAsStateWithLifecycle()
            DropRow(title = "Send with", value = if (shiftSends) "Shift+Enter" else "Enter", icon = Icons.Rounded.Keyboard) { close ->
                DropItem(title = "Enter", subtitle = null, selected = !shiftSends, onClick = { close(); viewModel.setShiftEnterSends(false) })
                DropItem(title = "Shift+Enter", subtitle = null, selected = shiftSends, onClick = { close(); viewModel.setShiftEnterSends(true) })
            }
        }
    }
    // A PC's: the light round an open chat, which warms and breathes while a reply is written.
    if (com.cherry.butler.feature.chat.LocalEnterSends.current) ReplyGlowSection(viewModel)
    WriteForMeSection(viewModel)
    com.cherry.butler.feature.lessons.LessonsSection()
    CompactSection(viewModel)
    val picturesOn by viewModel.picturesOn.collectAsStateWithLifecycle()
    val picturesAuto by viewModel.picturesAuto.collectAsStateWithLifecycle()
    SettingsSection(title = "Pictures", special = true, icon = Icons.Rounded.Image) {
        SwitchRow("Picture key in the chat", null, picturesOn, viewModel::setPicturesOn, icon = Icons.Rounded.Image)
        Unfold(picturesOn) { Branch { SwitchRow("Write it in right away", null, picturesAuto, viewModel::setPicturesAuto) } }
    }
    ThinkingListsSection(viewModel)
}

/**
 * Memory, which is Compact mode now: near the context limit, all but the newest messages fold
 * into the chat's summary, which stands in for them. A Butler special, switched on through a
 * short word on what it costs.
 */
@Composable
private fun CompactSection(viewModel: SettingsViewModel) {
    val compact by viewModel.compact.collectAsStateWithLifecycle()
    val keep by viewModel.compactKeep.collectAsStateWithLifecycle()
    val at by viewModel.compactAt.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf(false) }
    SettingsSection(title = "Memory", icon = Icons.Rounded.History) {
        SwitchRow(
            "Compact mode",
            null,
            compact,
            { on -> if (on) asking = true else viewModel.setCompact(false) },
            icon = Icons.Rounded.History,
            tag = { ButlerSpecialTag() },
        )
        Unfold(compact) {
            Branch(last = false) {
                NumberRow("Keep the last messages", null, keep, viewModel::setCompactKeep, com.cherry.butler.core.data.MemoryPrefs.KEEP_RANGE)
            }
            Branch {
                SliderRow(
                    "Compact at",
                    at.toFloat(),
                    com.cherry.butler.core.data.MemoryPrefs.AT_RANGE.first.toFloat()..com.cherry.butler.core.data.MemoryPrefs.AT_RANGE.last.toFloat(),
                    5f,
                    { "${it.toInt()}% full" },
                    { viewModel.setCompactAt(it.toInt()) },
                )
            }
        }
    }
    if (asking) {
        AddonWarning(
            name = "Compact mode",
            tokens = 0,
            tag = "",
            strip = false,
            tags = false,
            points = listOf(
                "When" to "the chat reaches $at% of its context",
                "Folds" to "everything but the last $keep messages into one summary",
                "Keeps" to "the newest messages word for word, so nothing feels cut",
                "Chains" to "each compact onto the one before",
                "Costs" to "one extra reply's worth, on your proxy or JLLM",
            ),
            onTurnOn = { asking = false; viewModel.setCompact(true) },
            onCancel = { asking = false },
        )
    }
}

/**
 * Whose voice Write for me writes in. Enhance keeps the draft's own unless its panel is told
 * otherwise, and the panel can change this one for a single line too.
 */
@Composable
internal fun WriteForMeSection(viewModel: SettingsViewModel) {
    val perspective by viewModel.perspective.collectAsStateWithLifecycle()
    SettingsSection(
        title = "Write for me",
        icon = Icons.Rounded.AutoAwesome,
    ) {
        Text(
            "Writes in",
            style = MaterialTheme.typography.labelMedium,
            color = ButlerTheme.colors.textMed,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        )
        val choices = com.cherry.butler.core.data.Perspective.entries
        com.cherry.butler.ui.components.SegmentedChoice(
            options = choices.map { it.label },
            selected = choices.indexOf(perspective).coerceAtLeast(0),
            onSelect = { i -> viewModel.setPerspective(choices[i]) },
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
        )
        com.cherry.butler.core.design.RpText(
            text = perspective.example,
            style = MaterialTheme.typography.bodySmall,
            color = ButlerTheme.colors.textLow,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        )
    }
}

/**
 * The thinking line's words as lists: Claude's (Butler's default), which opens to be added
 * to or taken from, and the user's, each on or off and opened to edit. Everything that is on
 * goes into one pool.
 */
@Composable
private fun ThinkingListsSection(viewModel: SettingsViewModel) {
    val claudeOn by viewModel.claudeOn.collectAsStateWithLifecycle()
    val claudeWords by viewModel.claudeWords.collectAsStateWithLifecycle()
    val lists by viewModel.thinkingLists.collectAsStateWithLifecycle()
    // null: closed; a list to edit; a blank new one (id empty); or Claude's (id CLAUDE_LIST).
    var editing by remember { mutableStateOf<com.cherry.butler.core.data.ChatPrefs.WordList?>(null) }
    SettingsSection(title = "Thinking words", icon = Icons.Rounded.Psychology) {
        WordListRow(
            name = CLAUDE_LIST_NAME,
            words = claudeWords,
            on = claudeOn,
            onToggle = viewModel::setClaudeOn,
            onOpen = { editing = com.cherry.butler.core.data.ChatPrefs.WordList(id = CLAUDE_LIST, name = CLAUDE_LIST_NAME, words = claudeWords) },
        )
        lists.forEach { list ->
            WordListRow(
                name = list.name,
                words = list.words,
                on = list.on,
                onToggle = { viewModel.setThinkingListOn(list.id, it) },
                onOpen = { editing = list },
            )
        }
        LinkRow(title = "Add a list", subtitle = null, onClick = { editing = com.cherry.butler.core.data.ChatPrefs.WordList(id = "", name = "", words = emptyList()) })
    }
    editing?.let { list ->
        val claude = list.id == CLAUDE_LIST
        WordListSheet(
            list = list,
            nameLocked = claude,
            onSave = { name, text ->
                if (claude) viewModel.saveClaudeList(text) else viewModel.saveThinkingList(list.id.ifEmpty { null }, name, text)
                editing = null
            },
            onDelete = if (list.id.isEmpty() || claude) null else ({ viewModel.deleteThinkingList(list.id); editing = null }),
            onReset = if (claude && viewModel.claudeEdited) ({ viewModel.resetClaudeList(); editing = null }) else null,
            onDismiss = { editing = null },
        )
    }
}

private const val CLAUDE_LIST = "claude"
private const val CLAUDE_LIST_NAME = "Claude's list (Butler default)"

/** A list: its name, how many words and the first few, a switch; the row opens it. */
@Composable
private fun WordListRow(name: String, words: List<String>, on: Boolean, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            LinkRow(title = name, subtitle = "${words.size} words · ${words.take(4).joinToString(", ")}", onClick = onOpen)
        }
        androidx.compose.material3.Switch(checked = on, onCheckedChange = onToggle, modifier = Modifier.padding(end = 16.dp))
    }
}

/**
 * One list: its name and its words, one per line. Save keeps it; Delete removes it. Claude's
 * list has its name fixed and Reset instead of Delete, back to the words as shipped.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WordListSheet(
    list: com.cherry.butler.core.data.ChatPrefs.WordList,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    nameLocked: Boolean = false,
    onReset: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(list.name) }
    var text by remember { mutableStateOf(list.words.joinToString("\n")) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text(
                if (list.id.isEmpty()) "New list" else list.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (!nameLocked) FieldBlock(label = "Name", value = name, onChange = { name = it.take(40) }, placeholder = "My words")
            FieldBlock(label = "Words, one per line", value = text, onChange = { text = it }, placeholder = "Pondering\nBrewing\nNoodling", singleLine = false, minLines = 5)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (onReset != null) KeyButton(label = "Reset", onClick = onReset)
                if (onDelete != null) KeyButton(label = "Delete", onClick = onDelete)
                KeyButton(label = "Save", onClick = { onSave(name, text) }, primary = true, enabled = text.isNotBlank())
            }
        }
    }
}

/** Save, in the header's corner, while changes wait; "Saving…" while they go out. */
@Composable
private fun SaveKey(saving: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(enabled = !saving, onClickLabel = "Save changes", onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (saving) "Saving\u2026" else "Save",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Asked when leaving with changes still waiting: send them, drop them, or stay. */
@Composable
private fun UnsavedDialog(saving: Boolean, onSave: () -> Unit, onDiscard: () -> Unit, onKeep: () -> Unit) {
    com.cherry.butler.ui.components.ButlerDialog(onDismissRequest = onKeep) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(top = 20.dp, bottom = 12.dp),
        ) {
            Text("Unsaved changes", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "Your model settings haven\u2019t been sent to Janitor yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = ButlerTheme.colors.textMed,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = com.cherry.butler.ui.components.leaving(onKeep), enabled = !saving) { Text("Keep editing", color = MaterialTheme.colorScheme.onSurface) }
                TextButton(onClick = com.cherry.butler.ui.components.leaving(onDiscard), enabled = !saving) { Text("Discard", color = ButlerTheme.colors.danger) }
                SaveKey(saving = saving, onClick = onSave)
            }
        }
    }
}

/** The settings pages: the main list, and one page each, with its mark and a line on what's inside. */
enum class SettingsPage(val title: String, val blurb: String, val icon: Int, val listed: Boolean = true) {
    Main("Settings", "", 0, listed = false),
    Model("Model", "Where replies come from, prompts, generation", com.cherry.butler.R.drawable.ic_set_model),
    Generation("Generation", "Length, sampling, replies", com.cherry.butler.R.drawable.ic_set_model, listed = false),
    Writers("Who writes what", "Write for me, summaries, pictures", com.cherry.butler.R.drawable.ic_set_model, listed = false),
    Prompts("Prompts", "The prompts each one follows", com.cherry.butler.R.drawable.ic_set_model, listed = false),
    Chat("Chat", "Keyboard, Write for me, memory, pictures, thinking words",com.cherry.butler.R.drawable.ic_set_chat),
    Look("Look", "Theme, fonts, layout, background", com.cherry.butler.R.drawable.ic_set_look),
    Thinking("Thinking line", "While a reply is thought out, and after", com.cherry.butler.R.drawable.ic_set_look, listed = false),
    Profiles("Look profiles", "Saved looks, to put on in a tap or share", com.cherry.butler.R.drawable.ic_set_look, listed = false),
    RichTyping("Rich typing", "Speech, action and bold as you type", com.cherry.butler.R.drawable.ic_set_rich, listed = false),
    Specials("Butler specials", "Butter mode, highlights", com.cherry.butler.R.drawable.ic_set_specials),
    App("App", "Notifications, blocked, background, lock", com.cherry.butler.R.drawable.ic_set_app),
    Updates("Updates", "Optional check against GitHub", com.cherry.butler.R.drawable.ic_set_updates, listed = false),
    Debug("Debug", "Proxy for Burp or mitmproxy", com.cherry.butler.R.drawable.ic_set_debug),
    /** The account on a page of its own, for the PC's settings window (the phone has it under the list). */
    Account("Account", "Signing out", com.cherry.butler.R.drawable.ic_set_app, listed = false),
}

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
    onOpenPage: (SettingsPage) -> Unit = {},
) {
    SettingsSection(title = "Model", icon = Icons.Rounded.SmartToy) {
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
        val writer by viewModel.writer.collectAsStateWithLifecycle()
        val memoryWriter by viewModel.memoryWriter.collectAsStateWithLifecycle()
        // Janitor's own models read it (it keeps JLLM from refusing); a proxy is the user's model and doesn't.
        // So it shows while anything here is written by JLLM: the chat, Write for me, or summaries.
        val jllm = com.cherry.butler.core.data.Writer.Jllm
        if (settings.provider == Provider.Janitor || writer == jllm || memoryWriter == jllm) {
            val nsfw by viewModel.allowMobileNsfw.collectAsStateWithLifecycle()
            SwitchRow(
                title = "NSFW on mobile",
                icon = Icons.Rounded.Visibility,
                subtitle = null,
                checked = nsfw,
                onChange = viewModel::setAllowMobileNsfw,
            )
        }
        if (onOpenGeneration != null) LinkRow(title = "Generation", subtitle = generationSummary(settings.generation), onClick = onOpenGeneration, icon = Icons.Rounded.Tune)
        LinkRow(
            title = "Who writes what",
            subtitle = null,
            onClick = { onOpenPage(SettingsPage.Writers) },
            icon = Icons.Rounded.AutoAwesome,
        )
        LinkRow(
            title = "Prompts",
            subtitle = null,
            onClick = { onOpenPage(SettingsPage.Prompts) },
            icon = Icons.Rounded.Description,
        )
    }
}

/**
 * Who writes what, besides the character (who answers from the Model page's choice): your lines,
 * the chat's memory, and pictures. One card each, with what it uses; a tap opens the choices.
 */
@Composable
private fun WhoWritesWhat(settings: AiSettings, viewModel: SettingsViewModel) {
    val writer by viewModel.writer.collectAsStateWithLifecycle()
    val memoryWriter by viewModel.memoryWriter.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WriterCard("Write for me", Icons.Rounded.AutoAwesome, writer, settings, viewModel::setWriter)
        WriterCard("Summaries", androidx.compose.material.icons.Icons.Rounded.Psychology, memoryWriter, settings, viewModel::setMemoryWriter)
        PictureCard(settings, viewModel)
    }
}

/** The prompts at work (JLLM's, Write for me's) as cards, then the library. */
@Composable
private fun PromptsPage(settings: AiSettings, viewModel: SettingsViewModel, onOpenPrompt: (String?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (settings.provider == Provider.Janitor) JllmPromptCard(settings, viewModel)
        WritePromptCard(settings, viewModel)
    }
    SettingsSection(title = "Your prompts", icon = Icons.Rounded.Description) {
        settings.prompts.forEach { p ->
            LinkRow(title = p.name.ifBlank { "Untitled" }, subtitle = null, onClick = { onOpenPrompt(p.id) })
        }
        LinkRow(title = "New prompt", subtitle = null, onClick = { onOpenPrompt(null) }, icon = Icons.Rounded.Add)
    }
}

/**
 * One job's card: its icon in a soft accent circle, its name and what it does, then what it uses.
 * A tap opens [menu] under the card, or runs [onClick].
 */
@Composable
private fun JobCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    uses: String,
    model: String?,
    special: Boolean = false,
    warning: Boolean = false,
    onClick: (() -> Unit)? = null,
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .card(shape = MaterialTheme.shapes.large)
                .clickable { if (onClick != null) onClick() else open = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (special) {
                        Spacer(Modifier.width(8.dp))
                        ButlerSpecialTag()
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    uses,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (warning) ButlerTheme.colors.danger else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (model != null) {
                    Text(model, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textMed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (onClick != null) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = ButlerTheme.colors.textMed,
            )
        }
        if (menu != null) {
            ButlerMenu(
                expanded = open,
                onDismissRequest = { open = false },
                offset = DpOffset(12.dp, 0.dp),
                modifier = Modifier.widthIn(min = 260.dp),
            ) { menu { open = false } }
        }
    }
}

/**
 * JLLM's custom prompt, as the website has it: one of the saved prompts (Settings › Prompts),
 * or none. Saved with the rest of the page.
 */
@Composable
private fun JllmPromptCard(settings: AiSettings, viewModel: SettingsViewModel) {
    var picking by remember { mutableStateOf(false) }
    val systemPrompts = settings.prompts.filter { it.kind == "system" }
    val name = settings.jllmPromptName ?: settings.jllmPromptId?.let { "Linked prompt" }
    JobCard(
        title = "JLLM",
        icon = Icons.Rounded.SmartToy,
        uses = name ?: if (systemPrompts.isEmpty()) "None · add one to the library" else "None",
        model = null,
        onClick = { picking = true },
    )
    if (picking) {
        PromptPickerSheet(
            prompts = systemPrompts,
            selectedId = settings.jllmPromptId,
            onPick = { id -> viewModel.setJllmPrompt(id); picking = false },
            onDismiss = { picking = false },
            noneSubtitle = "JLLM answers with Janitor's own prompt only",
        )
    }
}

/**
 * A prompt from the library that Write for me follows on top of its own ask: how your lines
 * should read (length, style, rules). Any prompt, system or jailbreak; kept on this device.
 */
@Composable
private fun WritePromptCard(settings: AiSettings, viewModel: SettingsViewModel) {
    var picking by remember { mutableStateOf(false) }
    val id by viewModel.writePrompt.collectAsStateWithLifecycle()
    val picked = settings.prompts.firstOrNull { it.id == id }
    JobCard(
        title = "Write for me",
        icon = Icons.Rounded.AutoAwesome,
        uses = picked?.name?.ifBlank { "Untitled prompt" }
            ?: if (id != null) "None (that prompt is gone)" else if (settings.prompts.isEmpty()) "None · add one to the library" else "None",
        model = null,
        warning = id != null && picked == null,
        onClick = { picking = true },
    )
    if (picking) {
        PromptPickerSheet(
            prompts = settings.prompts,
            selectedId = picked?.id,
            onPick = { pick -> viewModel.setWritePrompt(pick); picking = false },
            onDismiss = { picking = false },
            noneSubtitle = "Write for me follows its own ask only",
        )
    }
}

/**
 * Which model writes something other than the character's replies: the user's own lines
 * (write for me, enhance my draft) or the chat's memory (summaries, compacts). The chat's
 * own, JLLM, or a proxy set: one of the saved presets, with its own model if wanted. The
 * character keeps answering from the choice above.
 */
@Composable
private fun WriterCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    writer: com.cherry.butler.core.data.Writer,
    settings: AiSettings,
    onSet: (com.cherry.butler.core.data.Writer) -> Unit,
) {
    var configuring by remember { mutableStateOf(false) }
    val asProxy = writer as? com.cherry.butler.core.data.Writer.Proxy
    val preset = asProxy?.let { w -> settings.proxies.firstOrNull { w.id in it.ids } }
    val value = when {
        writer == com.cherry.butler.core.data.Writer.Jllm -> "JLLM"
        asProxy != null && preset != null -> "${preset.name.ifBlank { "Untitled" }} · ${asProxy.model ?: preset.model.ifBlank { "no model" }}"
        asProxy != null -> "Same as the chat (that proxy is gone)"
        else -> "Same as the chat"
    }
    JobCard(
        title = title,
        icon = icon,
        uses = when {
            writer == com.cherry.butler.core.data.Writer.Jllm -> "JLLM"
            preset != null -> preset.name.ifBlank { "Untitled" }
            asProxy != null -> "Same as chat · proxy gone"
            else -> "Same as chat"
        },
        model = if (asProxy != null && preset != null) asProxy.model ?: preset.model.ifBlank { "no model" } else null,
        warning = asProxy != null && preset == null,
    ) { close ->
        DropItem(
            title = "Same as the chat",
            subtitle = null,
            selected = writer == com.cherry.butler.core.data.Writer.SameAsChat,
            onClick = { close(); onSet(com.cherry.butler.core.data.Writer.SameAsChat) },
        )
        DropItem(
            title = "A proxy set",
            subtitle = if (preset != null) value else null,
            selected = asProxy != null && preset != null,
            onClick = { close(); configuring = true },
            trailing = {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.padding(12.dp).size(20.dp))
            },
        )
        DropItem(
            title = "JLLM",
            subtitle = null,
            selected = writer == com.cherry.butler.core.data.Writer.Jllm,
            onClick = { close(); onSet(com.cherry.butler.core.data.Writer.Jllm) },
        )
    }
    if (configuring) {
        WriterProxySheet(
            title = title,
            presets = settings.proxies,
            current = asProxy,
            onSave = { configuring = false; onSet(it) },
            onDismiss = { configuring = false },
        )
    }
}

/**
 * Agent mode (a Butler special): a reply is drafted, checked against goals strictly, fixed with exact
 * edits (or rewritten), and only then delivered. Proxies only, meant for reasoning models.
 */
@Composable
private fun AgentSection(viewModel: SettingsViewModel, onProxy: Boolean, settings: AiSettings) {
    val on by viewModel.agentOn.collectAsStateWithLifecycle()
    val effort by viewModel.agentEffort.collectAsStateWithLifecycle()
    val goals by viewModel.agentGoals.collectAsStateWithLifecycle()
    var showSteps by remember { mutableStateOf(false) }
    SettingsSection(
        title = "Agent mode",
        special = true,
        icon = Icons.Rounded.Loop,
        info = { InfoMark(open = showSteps) { showSteps = !showSteps } },
    ) {
        Unfold(showSteps) { AgentSteps() }
        SwitchRow("Agent mode", null, on, viewModel::setAgentOn, icon = Icons.Rounded.Loop)
        Unfold(on) {
            Branch(last = false) {
                DropRow(title = "Effort", value = effort.label) { close ->
                    com.cherry.butler.core.data.AgentPrefs.Effort.entries.forEach { e ->
                        DropItem(title = e.label, subtitle = null, selected = e == effort, onClick = { close(); viewModel.setAgentEffort(e) })
                    }
                }
            }
            Branch(last = false) { AgentCheckerPicker(settings, viewModel) }
            Branch(last = true) { AgentGoalsField(goals, viewModel::setAgentGoals) }
        }
    }
}

/** The (i) by a section's name: tapped, it shows the section's strip in place (no popup). */
@Composable
private fun InfoMark(open: Boolean, onToggle: () -> Unit) {
    Icon(
        Icons.Outlined.Info,
        contentDescription = if (open) "Hide what it does" else "What it does",
        tint = if (open) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
        modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onToggle).padding(4.dp).size(18.dp),
    )
}

/** What a reply goes through in Agent mode, as three marks in a row. */
@Composable
private fun AgentSteps() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                Icons.Rounded.Psychology to "Thinks",
                Icons.Rounded.Edit to "Writes",
                Icons.Rounded.FactCheck to "Checks",
            ).forEachIndexed { i, (icon, label) ->
                if (i > 0) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = ButlerTheme.colors.textLow, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        RowDivider()
    }
}

/** Which model checks the drafts: the chat's own, or another proxy set (often a cheaper, quicker one). */
@Composable
private fun AgentCheckerPicker(settings: AiSettings, viewModel: SettingsViewModel) {
    val pick by viewModel.agentChecker.collectAsStateWithLifecycle()
    var configuring by remember { mutableStateOf(false) }
    val preset = pick?.let { p -> settings.proxies.firstOrNull { p.id in it.ids } }
    val value = when {
        pick != null && preset != null -> "${preset.name.ifBlank { "Untitled" }} · ${pick!!.model ?: preset.model.ifBlank { "no model" }}"
        pick != null -> "The chat's model (that preset is gone)"
        else -> "The chat's model"
    }
    DropRow(title = "Checks use", value = value) { close ->
        DropItem(title = "The chat's model", subtitle = null, selected = pick == null, onClick = { close(); viewModel.setAgentChecker(null) })
        DropItem(
            title = "A proxy set",
            subtitle = if (preset != null) value else null,
            selected = pick != null && preset != null,
            onClick = { close(); configuring = true },
            trailing = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.padding(12.dp).size(20.dp)) },
        )
    }
    if (configuring) {
        WriterProxySheet(
            title = "Checks use",
            presets = settings.proxies,
            current = pick,
            onSave = { configuring = false; viewModel.setAgentChecker(it) },
            onDismiss = { configuring = false },
        )
    }
}

/** The user's own goals for the agent's check, one per line, on top of Butler's rubric. */
@Composable
private fun AgentGoalsField(goals: String, onCommit: (String) -> Unit) {
    var text by rememberSynced(goals)
    Column {
        FieldBlock(label = "Your goals, one per line", value = text, onChange = { text = it }, placeholder = "Keep replies under 200 words\nNever end on a question", singleLine = false, minLines = 3)
        if (text != goals) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = Alignment.CenterEnd) {
                KeyButton(label = "Save goals", onClick = { onCommit(text) }, primary = true)
            }
        }
    }
}

/**
 * Pictures (a Butler special): which proxy preset looks at a picture put into a message and writes it
 * into the line. Write for me's by default; only a proxy can, and its model has to see.
 */
@Composable
private fun PictureCard(settings: AiSettings, viewModel: SettingsViewModel) {
    val pick by viewModel.pictureDescriber.collectAsStateWithLifecycle()
    var configuring by remember { mutableStateOf(false) }
    val preset = pick?.let { p -> settings.proxies.firstOrNull { p.id in it.ids } }
    val value = when {
        pick != null && preset != null -> "${preset.name.ifBlank { "Untitled" }} · ${pick!!.model ?: preset.model.ifBlank { "no model" }}"
        pick != null -> "Same as Write for me (that preset is gone)"
        else -> "Same as Write for me"
    }
    JobCard(
        title = "Pictures",
        icon = Icons.Rounded.Image,
        uses = when {
            preset != null -> preset.name.ifBlank { "Untitled" }
            pick != null -> "Same as Write for me · preset gone"
            else -> "Same as Write for me"
        },
        model = if (pick != null && preset != null) pick!!.model ?: preset.model.ifBlank { "no model" } else null,
        warning = pick != null && preset == null,
        special = true,
    ) { close ->
        DropItem(
            title = "Same as Write for me",
            subtitle = null,
            selected = pick == null,
            onClick = { close(); viewModel.setPictureDescriber(null) },
        )
        DropItem(
            title = "A proxy set",
            subtitle = if (preset != null) value else null,
            selected = pick != null && preset != null,
            onClick = { close(); configuring = true },
            trailing = {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.padding(12.dp).size(20.dp))
            },
        )
    }
    if (configuring) {
        WriterProxySheet(
            title = "Pictures use",
            presets = settings.proxies,
            current = pick,
            onSave = { configuring = false; viewModel.setPictureDescriber(it) },
            onDismiss = { configuring = false },
        )
    }
}

/**
 * The proxy set for one job (the user's lines, summaries, pictures): one of the saved presets
 * (its address and key), and the model, which starts as the preset's own and can be changed
 * for this alone.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WriterProxySheet(
    title: String,
    presets: List<com.cherry.butler.core.data.ProxyConfig>,
    current: com.cherry.butler.core.data.Writer.Proxy?,
    onSave: (com.cherry.butler.core.data.Writer.Proxy) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(presets.firstOrNull { p -> current != null && current.id in p.ids } ?: presets.firstOrNull()) }
    var model by remember { mutableStateOf(current?.model.orEmpty()) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (presets.isEmpty()) {
                Text(
                    "No proxy presets yet. Add one under Model first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }
            Text("Preset", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            presets.forEach { p ->
                DropItem(
                    title = p.name.ifBlank { "Untitled" },
                    subtitle = proxyLine(p.model, p.apiUrl, p.hasKey),
                    selected = p == chosen,
                    onClick = { chosen = p },
                )
            }
            FieldBlock(
                label = "Model",
                value = model,
                onChange = { model = it },
                placeholder = chosen?.model?.ifBlank { null } ?: "The preset's model",
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                val preset = chosen
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(if (preset != null) MaterialTheme.colorScheme.primary else ButlerTheme.colors.surfaceHigh)
                        .clickable(enabled = preset != null) {
                            preset?.let { onSave(com.cherry.butler.core.data.Writer.Proxy(it.clientId ?: it.id, model.trim().ifBlank { null })) }
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                ) {
                    Text("Use this", style = MaterialTheme.typography.labelLarge, color = if (preset != null) MaterialTheme.colorScheme.onPrimary else ButlerTheme.colors.textLow)
                }
            }
        }
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
    val defaults = com.cherry.butler.core.generation.GenerationEnvelope.WEB_GENERATION_DEFAULTS
    val temp = gen["temperature"]?.jsonPrimitive?.floatOrNull ?: defaults["temperature"]?.jsonPrimitive?.floatOrNull ?: 1f
    val max = gen["max_new_token"]?.jsonPrimitive?.floatOrNull?.toInt() ?: defaults["max_new_token"]?.jsonPrimitive?.intOrNull ?: 0
    val length = if (max <= 0) "no reply limit" else "replies up to ${"%,d".format(java.util.Locale.US, max)} tokens"
    return "Temperature ${temp.fixed(2)} · $length"
}

/**
 * The look, in five short groups so each choice has room: the theme and its depth, the layouts,
 * what a chat looks like, the text, and saved looks.
 */
@Composable
private fun LookSection(viewModel: SettingsViewModel, onOpenCustomize: () -> Unit, onOpenThinking: () -> Unit, onOpenProfiles: () -> Unit) {
    val current by viewModel.theme.collectAsStateWithLifecycle()
    val chatStyle by viewModel.chatStyle.collectAsStateWithLifecycle()
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    var customizing by remember { mutableStateOf(false) }

    SettingsSection(title = "Theme", icon = Icons.Rounded.Palette) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppTheme.entries.forEach { theme ->
                ThemeTile(theme, theme.tones(custom), theme == current, Modifier.weight(1f)) {
                    viewModel.setTheme(theme)
                    // Custom's colours are its own: picking it opens them.
                    if (theme == AppTheme.Custom) customizing = true
                }
            }
        }
        if (current == AppTheme.Custom) {
            Branch { LinkRow(title = "Custom colours", subtitle = null, onClick = { customizing = true }, icon = Icons.Rounded.Palette) }
        }
        RowDivider()
        val depthPick by viewModel.depth.collectAsStateWithLifecycle()
        val depth = depthPick.orDefaultFor(current)
        DropRow(title = "Depth", value = depth.label, icon = Icons.Rounded.Layers) { close ->
            com.cherry.butler.core.design.Depth.entries.forEach { d ->
                DropItem(title = d.label, subtitle = null, selected = d == depth, onClick = { close(); viewModel.setDepth(d) })
            }
        }
    }

    SettingsSection(title = "Layout", icon = Icons.Rounded.ViewAgenda) {
        DropRow(title = "Chat layout", value = chatStyle.label, icon = Icons.Rounded.ViewAgenda) { close ->
            ChatStyle.entries.forEach { style ->
                DropItem(title = style.label, subtitle = null, selected = style == chatStyle, onClick = { close(); viewModel.setChatStyle(style) })
            }
        }
        val paged by viewModel.pagedHome.collectAsStateWithLifecycle()
        DropRow(title = "Home list", value = if (paged) "Pages" else "Endless scroll", icon = Icons.Rounded.ViewList) { close ->
            DropItem(title = "Endless scroll", subtitle = null, selected = !paged, onClick = { close(); viewModel.setPagedHome(false) })
            DropItem(title = "Pages", subtitle = null, selected = paged, onClick = { close(); viewModel.setPagedHome(true) })
        }
    }

    SettingsSection(title = "Chat", icon = Icons.Rounded.Forum) {
        val thinkingLook by viewModel.thinkingLook.collectAsStateWithLifecycle()
        val thoughtLook by viewModel.thoughtLook.collectAsStateWithLifecycle()
        LinkRow(title = "Thinking line", subtitle = "${thinkingLook.label} · ${thoughtLook.label}", onClick = onOpenThinking, icon = Icons.Rounded.Lightbulb)
        LinkRow(title = "Chat text", subtitle = null, onClick = onOpenCustomize, icon = Icons.Rounded.FormatColorText)
        BackgroundRow()
    }

    SettingsSection(title = "Text", icon = Icons.Rounded.TextFields) {
        FontRow(title = "Chat font", forChat = true, viewModel = viewModel)
        FontRow(title = "App font", forChat = false, viewModel = viewModel)
        val scale by viewModel.appTextScale.collectAsStateWithLifecycle()
        SliderRow(
            title = "Text size",
            value = scale,
            range = com.cherry.butler.core.data.FontPrefs.MIN_SCALE..com.cherry.butler.core.data.FontPrefs.MAX_SCALE,
            step = 0.05f,
            format = { "${(it * 100).toInt()}%" },
            onCommit = viewModel::setAppTextScale,
            divider = false,
            mark = 1f,
        )
    }

    SettingsSection(title = "Profiles", icon = Icons.Rounded.Bookmarks) {
        val profiles by viewModel.lookProfiles.collectAsStateWithLifecycle()
        LinkRow(
            title = "Look profiles",
            subtitle = if (profiles.isEmpty()) null else "${profiles.size} saved",
            onClick = onOpenProfiles,
            icon = Icons.Rounded.Bookmarks,
        )
    }
    if (customizing) CustomLookSheet(viewModel, onDismiss = { customizing = false })
}

/**
 * The Custom look's two colours. Everything else (cards, rules, text shades) is worked out
 * from them, and the app re-colours as they change. An accent too close to the background
 * to read is nudged, and this says so.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CustomLookSheet(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    val recent by viewModel.recentColors.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    val isDefault = custom == com.cherry.butler.core.design.CustomColors.Default
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Custom look", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp))
            ColorRow("Accent", "Buttons, links, the send key", androidx.compose.ui.graphics.Color(custom.accent)) { editing = "accent" }
            ColorRow("Background", "The ground everything sits on", androidx.compose.ui.graphics.Color(custom.ground)) { editing = "ground" }
            if (custom.accentAdjusted()) {
                Text(
                    "Your accent is shown a little " + (if (AppTheme.Custom.isLight(custom)) "darker" else "lighter") + " so it stays readable on this background.",
                    style = MaterialTheme.typography.labelMedium,
                    color = ButlerTheme.colors.warn,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val setByHand = custom.overrides.size + (if (custom.corners != 1f) 1 else 0)
            LinkRow(
                title = "Advanced",
                subtitle = if (setByHand == 0) null else "$setByHand set by hand",
                onClick = { advanced = true },
            )
            Text(
                "Reset to Butler" + "\u2019" + "s colours",
                style = MaterialTheme.typography.labelLarge,
                color = if (!isDefault) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = !isDefault) { viewModel.setCustom(com.cherry.butler.core.design.CustomColors.Default) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
    if (advanced) AdvancedLookSheet(viewModel, onDismiss = { advanced = false })
    editing?.let { which ->
        com.cherry.butler.ui.components.ColorPickerSheet(
            title = if (which == "accent") "Accent" else "Background",
            initial = androidx.compose.ui.graphics.Color(if (which == "accent") custom.accent else custom.ground),
            recent = recent,
            onPick = { argb ->
                editing = null
                viewModel.addRecent(argb)
                viewModel.setCustom(if (which == "accent") custom.copy(accent = argb) else custom.copy(ground = argb))
            },
            onDismiss = { editing = null },
        )
    }
}

/**
 * Every colour of the Custom look, by group, each Auto (worked out from the accent and the
 * background) until set by hand, with a way back to Auto; and how round the corners are.
 * A hand-set colour that is hard to read on what it sits on says so; it is still the user's call.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedLookSheet(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    val recent by viewModel.recentColors.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<com.cherry.butler.core.design.CustomToken?>(null) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text("Advanced", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp))
            SliderRow(
                title = "Corners",
                value = custom.corners,
                range = 0f..2f,
                step = 0.25f,
                format = { v ->
                    when {
                        v <= 0f -> "Square"
                        v < 1f -> "Sharper"
                        v == 1f -> "Butler"
                        else -> "Rounder"
                    }
                },
                onCommit = { viewModel.setCustom(custom.copy(corners = it)) },
            )
            com.cherry.butler.core.design.CustomToken.entries.groupBy { it.group }.forEach { (group, tokens) ->
                Text(
                    group.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ButlerTheme.colors.textLow,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                tokens.forEach { token ->
                    TokenRow(
                        token = token,
                        color = custom.colorOf(token),
                        setByHand = token.key in custom.overrides,
                        hardToRead = custom.hardToRead(token),
                        onClick = { editing = token },
                        onAuto = { viewModel.setCustom(custom.copy(overrides = custom.overrides - token.key)) },
                    )
                }
            }
            val anything = custom.overrides.isNotEmpty() || custom.corners != 1f
            Text(
                "Put everything back to Auto",
                style = MaterialTheme.typography.labelLarge,
                color = if (anything) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = anything) { viewModel.setCustom(custom.copy(overrides = emptyMap(), corners = 1f)) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
    editing?.let { token ->
        com.cherry.butler.ui.components.ColorPickerSheet(
            title = token.label,
            initial = custom.colorOf(token),
            recent = recent,
            onPick = { argb ->
                editing = null
                viewModel.addRecent(argb)
                viewModel.setCustom(custom.copy(overrides = custom.overrides + (token.key to argb)))
            },
            onDismiss = { editing = null },
        )
    }
}

/** One colour of the look: its swatch, what it colours, Auto or set by hand, and back to Auto. */
@Composable
private fun TokenRow(
    token: com.cherry.butler.core.design.CustomToken,
    color: androidx.compose.ui.graphics.Color,
    setByHand: Boolean,
    hardToRead: Boolean,
    onClick: () -> Unit,
    onAuto: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(color).rim(1.dp, ButlerTheme.colors.rule, CircleShape))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(token.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                (if (setByHand) "Set by you" else "Auto") + " \u00B7 " + token.hint,
                style = MaterialTheme.typography.labelSmall,
                color = ButlerTheme.colors.textLow,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (hardToRead) {
                Text("Hard to read on what it sits on", style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.warn)
            }
        }
        if (setByHand) {
            IconButton(onClick = onAuto) {
                Icon(Icons.Rounded.Replay, contentDescription = "Back to Auto", tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

/** A colour setting: its name and what it colours, and its swatch at the end. */
/**
 * A PC's light round an open chat while a reply is being written: whether it warms and
 * breathes at all, and the colour it warms to (the look's accent unless one is picked).
 */
@Composable
private fun ReplyGlowSection(viewModel: SettingsViewModel) {
    val on by viewModel.replyGlow.collectAsStateWithLifecycle()
    val picked by viewModel.replyGlowColor.collectAsStateWithLifecycle()
    val recent by viewModel.recentColors.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val shown = picked?.let { androidx.compose.ui.graphics.Color(it) } ?: accent
    SettingsSection(title = "Reply glow", icon = Icons.Rounded.LightMode) {
        SwitchRow("Glow while a reply is written", null, on, viewModel::setReplyGlow)
        com.cherry.butler.ui.components.Unfold(on) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { picking = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Colour", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(shown).rim(1.dp, ButlerTheme.colors.rule, CircleShape))
                }
                // Back to the look's own accent, whatever that is or becomes.
                if (picked != null) {
                    Text(
                        "Use the look" + "’" + "s accent",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { viewModel.setReplyGlowColor(null) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
    if (picking) {
        com.cherry.butler.ui.components.ColorPickerSheet(
            title = "Reply glow",
            initial = shown,
            recent = recent,
            onPick = { argb ->
                picking = false
                viewModel.addRecent(argb)
                viewModel.setReplyGlowColor(argb)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun ColorRow(title: String, subtitle: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(color).rim(1.dp, ButlerTheme.colors.rule, CircleShape))
    }
}

/**
 * Rich typing, on its own: keys for "speech", *action* and **bold** over the message box,
 * the marks drawn as they are typed, and what typing becomes by itself. Also switchable from
 * a chat's menu.
 */
@Composable
internal fun RichTypingSection(viewModel: SettingsViewModel) {
    val on by viewModel.richOn.collectAsStateWithLifecycle()
    val default by viewModel.richDefault.collectAsStateWithLifecycle()
    SettingsSection(
        title = "Rich typing",
        icon = Icons.Rounded.FormatQuote,
    ) {
        SwitchRow(
            "Rich typing",
            null,
            on,
            viewModel::setRich,
            icon = Icons.Rounded.FormatQuote,
        )
        Unfold(on) { Branch { Column {
            Text(
                "Typing starts as",
                style = MaterialTheme.typography.labelMedium,
                color = ButlerTheme.colors.textMed,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            )
            val keys = listOf(null, "action", "speech", "bold")
            com.cherry.butler.ui.components.SegmentedChoice(
                options = listOf("Plain", "Action", "Speech", "Bold"),
                selected = keys.indexOf(default).coerceAtLeast(0),
                onSelect = { i -> viewModel.setRichDefault(keys[i]) },
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
            )
        } } }
    }
}

/**
 * Butler's specials, which work by adding to what the user's own model is asked: Butter mode,
 * Highlights, and Compact mode. Each is switched on through a warning that says what it costs
 * (and, for the ones with tags, where its tags go).
 */
@Composable
private fun SpecialsSection(viewModel: SettingsViewModel) {
    val butter by viewModel.butter.collectAsStateWithLifecycle()
    val strip by viewModel.stripTags.collectAsStateWithLifecycle()
    val tint by viewModel.butterTint.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val moods by viewModel.moods.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf(false) }
    var askingHighlights by remember { mutableStateOf(false) }
    SettingsSection(
        title = "Butler specials",
        icon = Icons.Rounded.Star,
    ) {
        SwitchRow(
            "Butter mode",
            null,
            butter,
            { on -> if (on) asking = true else viewModel.setButter(false) },
            icon = Icons.Rounded.Star,
        )
        Unfold(butter) { Branch {
            SwitchRow(
                "Tint the butter",
                null,
                tint,
                viewModel::setButterTint,
            )
        } }
        SwitchRow(
            "Highlights",
            null,
            highlights,
            { on -> if (on) askingHighlights = true else viewModel.setHighlights(false) },
            icon = Icons.Rounded.Highlight,
        )
        Unfold(highlights) { Branch { Column {
            MoodChips(moods, viewModel::setMood)
            val custom by viewModel.highlightsPrompt.collectAsStateWithLifecycle()
            HighlightsPromptField(custom, viewModel::setHighlightsPrompt)
        } } }
        Unfold(butter || highlights) {
            DropRow(title = "Tags", value = if (strip) "Kept on this ${com.cherry.butler.core.platform.Device.NAME}" else "Saved to Janitor too", icon = Icons.Rounded.Label) { close ->
                DropItem(title = "Kept on this ${com.cherry.butler.core.platform.Device.NAME}", subtitle = null, selected = strip, onClick = { close(); viewModel.setStripTags(true) })
                DropItem(title = "Saved to Janitor too", subtitle = null, selected = !strip, onClick = { close(); viewModel.setStripTags(false) })
            }
        }
    }
    if (askingHighlights) {
        AddonWarning(
            name = "Highlights",
            tokens = com.cherry.butler.core.generation.PromptAddons.tokensOf(com.cherry.butler.core.generation.MoodTagger.question(moods.ifEmpty { com.cherry.butler.core.generation.Mood.entries.toSet() })),
            tag = "romantic",
            cost = "Your replies are written exactly as before. After each longer one, Butler asks your model one short " +
                "follow-up: which sentences carry a mood. On a proxy that sends the reply plus about " +
                "${com.cherry.butler.core.generation.PromptAddons.tokensOf(com.cherry.butler.core.generation.MoodTagger.question(moods.ifEmpty { com.cherry.butler.core.generation.Mood.entries.toSet() }))} " +
                "tokens and gets a few lines back. On JLLM it is a second short generation.",
            strip = strip,
            onTurnOn = { keep -> askingHighlights = false; viewModel.setStripTags(keep); viewModel.setHighlights(true) },
            onCancel = { askingHighlights = false },
        )
    }
    if (asking) {
        AddonWarning(
            name = "Butter mode",
            tokens = com.cherry.butler.core.generation.PromptAddons.tokensOf(com.cherry.butler.core.generation.PromptAddons.BUTTER),
            tag = "butter",
            strip = strip,
            onTurnOn = { keep -> asking = false; viewModel.setStripTags(keep); viewModel.setButter(true) },
            onCancel = { asking = false },
        )
    }
}

/**
 * Which moods Highlights asks for: a chip each, wearing its colour. Fewer moods, a shorter
 * instruction. At least one stays on.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MoodChips(on: Set<com.cherry.butler.core.generation.Mood>, onChange: (com.cherry.butler.core.generation.Mood, Boolean) -> Unit) {
    val colors = ButlerTheme.colors
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.cherry.butler.core.generation.Mood.entries.forEach { mood ->
            val lit = mood in on
            val wash = when (mood) {
                com.cherry.butler.core.generation.Mood.Romantic -> colors.romance
                com.cherry.butler.core.generation.Mood.Erotic -> colors.desire
                com.cherry.butler.core.generation.Mood.Dangerous -> colors.danger
                com.cherry.butler.core.generation.Mood.Sad -> colors.speech
                com.cherry.butler.core.generation.Mood.Funny -> colors.success
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (lit) wash.copy(alpha = 0.22f) else ButlerTheme.colors.surfaceHigh)
                    .rim(1.dp, if (lit) wash else ButlerTheme.colors.outlineFaint, MaterialTheme.shapes.small)
                    .clickable(enabled = !lit || on.size > 1) { onChange(mood, !lit) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(wash, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
                Text(
                    mood.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (lit) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.textLow,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Said before a special is switched on: it runs on the user's own model and costs tokens,
 * and its tags (when it has any, [tags]) either stay on this phone (clean on Janitor, gone if
 * Butler's storage is cleared) or go to Janitor too (kept with the account, shown raw on
 * Janitor's site).
 */
@Composable
private fun AddonWarning(
    name: String,
    tokens: Int,
    tag: String,
    strip: Boolean,
    onTurnOn: (strip: Boolean) -> Unit,
    onCancel: () -> Unit,
    cost: String = "This adds a short instruction to every reply, sent to your own model: about $tokens tokens of input " +
        "each time, and a little more output for the tags. It counts against your proxy or JLLM like any other text.",
    tags: Boolean = true,
    /** Instead of [cost]: what it does, as a few short lines, each led by a word. */
    points: List<Pair<String, String>>? = null,
) {
    var keepHere by remember { mutableStateOf(strip) }
    com.cherry.butler.ui.components.ButlerDialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp, bottom = 12.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 20.dp))
            if (points != null) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    points.forEach { (lead, rest) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(ButlerTheme.colors.warn),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(lead) }
                                    append(" ")
                                    append(rest)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = ButlerTheme.colors.textMed,
                            )
                        }
                    }
                }
            } else {
                Text(
                    cost,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                )
            }
            if (tags) {
                Text(
                    "Where the tags go",
                    style = MaterialTheme.typography.labelMedium,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                )
                ChoiceRow(
                    title = "Keep them on this ${com.cherry.butler.core.platform.Device.NAME}",
                    subtitle = null,
                    selected = keepHere,
                    onClick = { keepHere = true },
                )
                ChoiceRow(
                    title = "Save them to Janitor too",
                    subtitle = null,
                    selected = !keepHere,
                    onClick = { keepHere = false },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
                Box(
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onTurnOn(keepHere) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Turn on", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/**
 * One of the two fonts: the built-ins, the fonts added (each with its faces, and a way to
 * remove it), and "Add font files", which opens the system file picker for .ttf and .otf
 * files: up to four faces of one family at once. When there are added fonts already, the
 * picked faces can join one of them or start a family of their own. Under it, the chosen
 * font's letter spacing, kept with the font: pick another and it has its own.
 */
@Composable
private fun FontRow(title: String, forChat: Boolean, viewModel: SettingsViewModel) {
    val chosen by (if (forChat) viewModel.chatFont else viewModel.appFont).collectAsStateWithLifecycle()
    val added by viewModel.addedFonts.collectAsStateWithLifecycle()
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.pickFonts(uris, forChat) }
    fun choose(key: String) = if (forChat) viewModel.setChatFont(key) else viewModel.setAppFont(key)
    var removing by remember { mutableStateOf<com.cherry.butler.core.data.FontPrefs.Added?>(null) }
    removing?.let { font ->
        com.cherry.butler.feature.chat.DeleteConfirmDialog(
            count = 1,
            title = "Remove ${font.name}?",
            body = (if (font.faces.size == 1) "Its file is" else "Its ${font.faces.size} files are") +
                " deleted from Butler. Anything set to it goes back to the default.",
            confirmLabel = "Remove",
            onConfirm = { removing = null; viewModel.removeFont(font.key) },
            onDismiss = { removing = null },
        )
    }
    val fontPick by viewModel.fontPick.collectAsStateWithLifecycle()
    fontPick?.takeIf { it.forChat == forChat }?.let { p ->
        FontFamilyDialog(picked = p.picked, added = added, onPlace = viewModel::placeFonts, onDismiss = viewModel::cancelFontPick)
    }
    DropRow(title = title, value = com.cherry.butler.core.data.FontPrefs.nameOf(chosen)) { close ->
        com.cherry.butler.core.data.FontPrefs.BUILT_IN.forEach { (key, name) ->
            DropItem(
                title = name,
                subtitle = when {
                    forChat && key == com.cherry.butler.core.data.FontPrefs.DEFAULT_CHAT -> "Butler\u2019s reading font"
                    !forChat && key == com.cherry.butler.core.data.FontPrefs.DEFAULT_APP -> "The default"
                    else -> com.cherry.butler.core.data.FontPrefs.BLURB[key]
                },
                selected = chosen == key,
                onClick = { close(); choose(key) },
            )
        }
        if (added.isNotEmpty()) DropDivider()
        added.forEach { font ->
            DropItem(
                title = font.name,
                subtitle = font.faces.joinToString(", ") { it.label },
                selected = chosen == font.key,
                onClick = { close(); choose(font.key) },
                trailing = {
                    IconButton(onClick = { close(); removing = font }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove ${font.name}", tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
                    }
                },
            )
        }
        DropDivider()
        DropItem(
            title = "Add font files",
            subtitle = null,
            onClick = { close(); pick.launch(arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/vnd.ms-opentype", "application/octet-stream")) },
            trailing = {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.padding(12.dp).size(20.dp))
            },
        )
    }
    val spacing by viewModel.fontSpacing.collectAsStateWithLifecycle()
    Branch {
        SliderRow(
            title = "Letter spacing",
            value = (spacing[chosen] ?: 0).toFloat(),
            range = com.cherry.butler.core.data.FontPrefs.MIN_SPACING.toFloat()..com.cherry.butler.core.data.FontPrefs.MAX_SPACING.toFloat(),
            step = 1f,
            format = { v -> if (kotlin.math.round(v).toInt() == 0) "Normal" else "%+.2f em".format(java.util.Locale.ROOT, v / 100f) },
            onCommit = { v -> viewModel.setFontSpacing(chosen, kotlin.math.round(v).toInt()) },
        )
    }
}

/**
 * Where picked font files go: a family of their own, or one of the fonts added before. A font
 * with the same family name as the files comes first. A face the family has already is
 * replaced by the new file.
 */
@Composable
private fun FontFamilyDialog(
    picked: com.cherry.butler.core.data.FontPrefs.Picked,
    added: List<com.cherry.butler.core.data.FontPrefs.Added>,
    onPlace: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val faces = picked.faces.joinToString(", ") { it.label }
    val ordered = added.sortedByDescending { it.name.equals(picked.family, ignoreCase = true) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Add to a family", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "${picked.family}: $faces",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                )
                FamilyChoice(title = "A new family", subtitle = picked.family) { onPlace(null) }
                ordered.forEach { font ->
                    FamilyChoice(title = font.name, subtitle = font.faces.joinToString(", ") { it.label }) { onPlace(font.key) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, shape = com.cherry.butler.core.design.Pill) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
        },
    )
}

@Composable
private fun FamilyChoice(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textLow)
    }
}

/** The picture behind every chat: opens the backgrounds library. */
@Composable
private fun BackgroundRow() {
    var open by remember { mutableStateOf(false) }
    LinkRow(title = "Chat background", subtitle = null, onClick = { open = true }, icon = Icons.Rounded.Wallpaper)
    if (open) BackgroundsSheet(chatId = null, onDismiss = { open = false })
}

/** A thumbnail of the look (its ground, one card, a short red bar) with its name under it. */
@Composable
internal fun ThemeTile(theme: AppTheme, t: com.cherry.butler.core.design.Tones, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val frame by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else ButlerTheme.colors.rule,
        animationSpec = com.cherry.butler.core.design.Motion.enter(com.cherry.butler.core.design.Motion.SHORT),
        label = "theme-frame",
    )
    Column(
        modifier = modifier.clip(com.cherry.butler.core.design.liftedShape(12.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(com.cherry.butler.core.design.liftedShape(12.dp))
                .background(t.ground)
                .rim(if (selected) 2.dp else 1.dp, frame, com.cherry.butler.core.design.liftedShape(12.dp))
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
private fun GenerationSections(gen: JsonObject, saving: String?, viewModel: SettingsViewModel, jllm: Boolean) {
    fun num(key: String, default: Float) = gen[key]?.jsonPrimitive?.floatOrNull ?: default
    fun int(key: String, default: Int) = gen[key]?.jsonPrimitive?.intOrNull ?: gen[key]?.jsonPrimitive?.floatOrNull?.toInt() ?: default
    fun bool(key: String) = gen[key]?.jsonPrimitive?.booleanOrNull ?: false

    val length by viewModel.replyLength.collectAsStateWithLifecycle()
    SettingsSection(
        title = "Length",
        icon = Icons.Rounded.FormatSize,
    ) {
        // Butler's own, kept on this phone; the rest are Janitor's and saved there.
        DropRow(title = "Reply length", value = length.label) { close ->
            com.cherry.butler.core.generation.ReplyLength.entries.forEach { option ->
                DropItem(title = option.label, subtitle = null, selected = option == length, onClick = { close(); viewModel.setReplyLength(option) })
            }
        }
        // The defaults shown are the ones the website sends when nothing is saved.
        NumberRow("Hard limit (tokens)", null, int("max_new_token", 0), { viewModel.setGeneration("max_new_token", JsonPrimitive(it)) }, 0..65_536)
        NumberRow("Context", null, int("context_length", 50_000), { viewModel.setGeneration("context_length", JsonPrimitive(it)) }, 512..1_000_000)
    }
    SettingsSection(title = "Sampling", icon = Icons.Rounded.Tune) {
        Column(modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)) {
            SliderRow("Temperature", num("temperature", 1f), 0f..2f, 0.05f, { it.fixed(2) }, { viewModel.setGeneration("temperature", JsonPrimitive(it)) }, busy = saving == "gen:temperature", divider = false, mark = 1f)
            SliderRow("Top P", num("top_p", 1f), 0f..1f, 0.01f, { it.fixed(2) }, { viewModel.setGeneration("top_p", JsonPrimitive(it)) }, busy = saving == "gen:top_p", divider = false)
            SliderRow("Top K", int("top_k", 0).toFloat(), 0f..100f, 1f, { if (it.toInt() == 0) "Off" else it.toInt().toString() }, { viewModel.setGeneration("top_k", JsonPrimitive(it.toInt())) }, busy = saving == "gen:top_k", divider = false)
            HairlineRule(color = ButlerTheme.colors.outlineFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            SliderRow("Repetition penalty", num("repetition_penalty", 0f), 0f..2f, 0.01f, { if (it == 0f) "Off" else it.fixed(2) }, { viewModel.setGeneration("repetition_penalty", JsonPrimitive(it)) }, busy = saving == "gen:repetition_penalty", divider = false)
            SliderRow("Frequency penalty", num("frequency_penalty", 0f), 0f..2f, 0.01f, { if (it == 0f) "Off" else it.fixed(2) }, { viewModel.setGeneration("frequency_penalty", JsonPrimitive(it)) }, busy = saving == "gen:frequency_penalty", divider = false)
        }
    }
    // JLLM's own switches, in Janitor's words. They do nothing for a proxy (its thinking is the
    // proxy's Thinking level), so they show only while JLLM answers. Deep reasoning and
    // swipe reasoning are Janitor+ (they sit under its banner in the official app, and a
    // free account streams no reasoning either way: checked 2026-10-05).
    if (jllm) {
        val premium by viewModel.premium.collectAsStateWithLifecycle()
        val short = bool("enable_short_responses")
        SettingsSection(
            title = "JLLM",
        ) {
            SwitchRow(
                "Short responses",
                null,
                short,
                { viewModel.setGeneration("enable_short_responses", JsonPrimitive(it)) },
            )
            if (premium == true) {
                SwitchRow(
                    "Deep reasoning",
                    null,
                    bool("enable_reasoning_chat"),
                    { viewModel.setGeneration("enable_reasoning_chat", JsonPrimitive(it)) },
                )
                SwitchRow(
                    "Reasoning on enhanced swipes",
                    null,
                    bool("enable_reasoning"),
                    { viewModel.setGeneration("enable_reasoning", JsonPrimitive(it)) },
                    enabled = !short,
                )
            }
        }
    }
    SettingsSection(title = "Replies", icon = Icons.Rounded.Notes) {
        SwitchRow(
            "Prefill",
            null,
            bool("prefill_enabled"),
            { viewModel.setGeneration("prefill_enabled", JsonPrimitive(it)) },
        )
        if (bool("prefill_enabled")) Branch {
            PrefillField(gen["prefill_text"]?.jsonPrimitive?.contentOrNull.orEmpty()) { viewModel.setGeneration("prefill_text", JsonPrimitive(it)) }
        }
    }
}

/**
 * What Highlights looks for, in the user's words. The moods and the answer format are added
 * after it by Butler, so this can't break the marking; Reset goes back to Butler's wording.
 */
@Composable
private fun HighlightsPromptField(custom: String?, onCommit: (String?) -> Unit) {
    val shown = custom ?: com.cherry.butler.core.generation.MoodTagger.DEFAULT_GUIDANCE
    var text by rememberSynced(shown)
    Column {
        FieldBlock(label = "Highlights prompt", value = text, onChange = { text = it }, singleLine = false, minLines = 3)
        if (text != shown || custom != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (custom != null) KeyButton(label = "Reset", onClick = { onCommit(null) })
                if (text != shown) KeyButton(label = "Save", onClick = { onCommit(text) }, primary = true)
            }
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

    SettingsSection(title = "App", icon = Icons.Rounded.PhoneAndroid) {
        LinkRow(title = "Notifications", subtitle = null, onClick = onOpenNotifications, icon = Icons.Rounded.Notifications)
        LinkRow(title = "Blocked", subtitle = null, onClick = onOpenBlocked, icon = Icons.Rounded.Block)
        LinkRow(title = "Report a problem", subtitle = null, onClick = onOpenDiagnostics, icon = Icons.Rounded.BugReport)
        SwitchRow(
            title = "Finish replies in the background",
            icon = Icons.Rounded.Autorenew,
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
            icon = Icons.Rounded.Lock,
            subtitle = null,
            checked = locked,
            enabled = lock.canLock() || locked,
            onChange = { turnOn ->
                // Proving it works once before relying on it, both ways.
                activity?.let { a ->
                    lock.authenticate(a, title = if (turnOn) "Turn on the lock" else "Turn off the lock") { ok -> if (ok) lock.setEnabled(turnOn) }
                }
            },
        )
        Unfold(locked) { Branch {
            val graces = listOf(0L to "Right away", 60_000L to "After 1 minute away", 300_000L to "After 5 minutes away", 900_000L to "After 15 minutes away")
            DropRow(title = "Lock", value = graces.firstOrNull { it.first == grace }?.second ?: "Right away") { close ->
                graces.forEach { (ms, label) -> DropItem(title = label, selected = grace == ms, onClick = { close(); lock.setGrace(ms) }) }
            }
        } }
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

/**
 * Updates, optional: a check on a tap, or on every open when switched on. Both only ask GitHub
 * which release is the latest; a newer one leads to its release page, nothing is downloaded.
 */
@Composable
private fun UpdatesSection(viewModel: SettingsViewModel) {
    val auto by viewModel.updateAuto.collectAsStateWithLifecycle()
    val checking by viewModel.checkingUpdate.collectAsStateWithLifecycle()
    val result by viewModel.update.collectAsStateWithLifecycle()
    SettingsSection(title = "Updates", icon = Icons.Rounded.Update) {
        LinkRow(title = if (checking) "Checking…" else "Check for updates", subtitle = null, onClick = viewModel::checkForUpdate, icon = Icons.Rounded.Update)
        SwitchRow(title = "Check on every open", subtitle = null, checked = auto, onChange = viewModel::setUpdateAuto, icon = Icons.Rounded.Autorenew)
    }
    result?.let { UpdateResult(it, viewModel.currentVersion, onDone = viewModel::clearUpdate) }
}

/** Debug builds only: route Butler's traffic through Burp Suite or mitmproxy on a computer. */
@Composable
private fun DebugSection() {
    val proxy = com.cherry.butler.core.network.DebugProxy
    var on by remember { mutableStateOf(proxy.enabled) }
    var address by remember { mutableStateOf(proxy.address) }
    val valid = proxy.parse(address) != null
    SettingsSection(
        title = "Debug",
        icon = Icons.Rounded.Dns,
    ) {
        SwitchRow(
            title = "Send traffic through a proxy",
            icon = Icons.Rounded.Dns,
            subtitle = null,
            checked = on && valid,
            enabled = valid || on,
            onChange = { turnOn ->
                on = turnOn
                proxy.set(turnOn, address)
            },
        )
        androidx.compose.material3.OutlinedTextField(
            value = address,
            onValueChange = { text ->
                address = text
                proxy.set(on, text)
            },
            label = { Text("Proxy (host:port)") },
            placeholder = { Text("192.168.0.10:8080") },
            singleLine = true,
            isError = address.isNotBlank() && !valid,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var issuer by remember { mutableStateOf(proxy.certificate?.issuerX500Principal?.name) }
        var fetching by remember { mutableStateOf(false) }
        LinkRow(
            title = if (fetching) "Fetching certificate…" else "Fetch certificate",
            subtitle = issuer?.let { it.substringAfter("CN=").substringBefore(',') },
            onClick = {
                if (!fetching) {
                    fetching = true
                    scope.launch {
                        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { proxy.fetchCertificate() } }
                        fetching = false
                        result.onSuccess { issuer = it }
                        android.widget.Toast.makeText(
                            context,
                            result.fold({ "Certificate fetched and trusted" }, { it.message ?: "Couldn't fetch it" }),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
        if (issuer != null) {
            LinkRow(title = "Forget certificate", subtitle = null, onClick = { proxy.forgetCertificate(); issuer = null })
        }
        // Cloudflare's check, on demand: forget the pass so the next call meets the check the way
        // a device without one does (calls under a browser name of their own, no clearance).
        LinkRow(
            title = "Forget Cloudflare's pass",
            subtitle = null,
            onClick = {
                com.cherry.butler.core.network.JanitorCookies.clear()
                com.cherry.butler.core.network.ButlerUserAgent.forgetWebView()
                android.widget.Toast.makeText(context, "Forgotten. Go to a list and pull to refresh", android.widget.Toast.LENGTH_LONG).show()
            },
        )
        var previewUpdate by remember { mutableStateOf(false) }
        LinkRow(title = "Preview update pop-up", subtitle = null, onClick = { previewUpdate = true })
        if (previewUpdate) {
            UpdateSheet(
                version = "9.9.9",
                current = com.cherry.butler.BuildConfig.VERSION_NAME,
                url = com.cherry.butler.core.update.UpdateChecker.RELEASES,
                notes = listOf(
                    "Sending works again",
                    "Tags have their own row under the sort bar, in colour",
                    "JLLM has a custom prompt",
                ),
                onDismiss = { previewUpdate = false },
                onStopAsking = {},
            )
        }
    }
}
