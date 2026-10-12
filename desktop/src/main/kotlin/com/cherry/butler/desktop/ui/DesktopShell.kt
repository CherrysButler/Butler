package com.cherry.butler.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cherry.butler.core.data.LocalLastPlace
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion
import com.cherry.butler.desktop.DesktopBack
import com.cherry.butler.desktop.DesktopTray
import com.cherry.butler.feature.character.CharacterDetailScreen
import com.cherry.butler.feature.chat.ChatScreen
import com.cherry.butler.feature.chats.ChatsScreen
import com.cherry.butler.feature.community.CommentsScreen
import com.cherry.butler.feature.community.PublishedChatScreen
import com.cherry.butler.feature.community.PublishedListScreen
import com.cherry.butler.feature.notifications.NotificationsScreen
import com.cherry.butler.feature.profile.ProfileScreen
import com.cherry.butler.feature.settings.CustomizeScreen
import com.cherry.butler.feature.settings.PromptEditorScreen
import com.cherry.butler.feature.settings.PromptsScreen
import com.cherry.butler.feature.settings.ProxyEditorScreen
import com.cherry.butler.feature.settings.SettingsPage
import com.cherry.butler.feature.settings.SettingsScreen
import com.cherry.butler.ui.ShellViewModel
import com.cherry.butler.ui.components.hairline
import com.cherry.butler.ui.navigation.LeaveGuard
import com.cherry.butler.ui.navigation.LocalLeaveGuard
import com.cherry.butler.ui.navigation.Routes
import com.cherry.butler.ui.navigation.TabHost
import com.cherry.butler.ui.navigation.TopLevelDestination

/** A character's page sits beside the content from this pane width; below it, it slides over. */
private val SideBySideFrom = 1040.dp
private val PanelWidth = 380.dp
private val PanelNarrowest = 320.dp
private val PanelWidest = 900.dp
/** What the panel always leaves the pane beside it. */
private val PaneAtLeast = 560.dp

/**
 * The line between the pane and the character panel, which takes hold: a few dp either side
 * of the hairline answer the pointer with a resize cursor, light up, and drag the panel wider
 * or narrower. Double-click puts it back to its usual width.
 */
@Composable
private fun PanelEdge(onDrag: (Dp) -> Unit, onDone: () -> Unit, onReset: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val dragged by source.collectIsDraggedAsState()
    val lit = hovered || dragged
    val line by androidx.compose.animation.animateColorAsState(
        if (lit) ButlerTheme.colors.textLow else ButlerTheme.colors.rule,
        tween(Motion.SHORT),
        label = "panel-edge",
    )
    Box(
        Modifier
            .width(hairline())
            .fillMaxHeight()
            .zIndex(1f)
            .wrapContentWidth(unbounded = true)
            .width(EdgeGrip)
            .hoverable(source)
            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
            .draggable(
                state = androidx.compose.foundation.gestures.rememberDraggableState { px -> onDrag(with(density) { px.toDp() }) },
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                interactionSource = source,
                onDragStopped = { onDone() },
            )
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(if (lit) 2.dp else hairline()).fillMaxHeight().background(line))
    }
}

/** How wide the edge's grip is, centred on the hairline. */
private val EdgeGrip = 9.dp

/** Settings shows its list and the open page side by side from this pane width. */
private val SettingsSplitFrom = 860.dp
private val SettingsListWidth = 360.dp

/**
 * Butler's window on the desktop: the rail of sections and recent faces at the left, one
 * ground pane for whatever is open, and a character's page in a panel at the right. The
 * screens are the phone's own; only where they sit changes.
 */
@Composable
fun DesktopShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val openChatId = if (route == Routes.CHAT) backStackEntry?.arguments?.getLong(Routes.ARG_CHAT_ID) else null
    var tab by rememberSaveable { mutableStateOf(TopLevelDestination.Browse) }
    var panelCharacter by rememberSaveable { mutableStateOf<String?>(null) }
    val leaveGuard = remember { LeaveGuard() }

    fun openChat(id: Long) {
        navController.navigate(Routes.chat(id)) { popUpTo(Routes.TABS) }
    }

    fun goTo(dest: TopLevelDestination) = leaveGuard.leave {
        tab = dest
        navController.popBackStack(Routes.TABS, inclusive = false)
    }

    var panelDrag by remember { mutableStateOf(com.cherry.butler.desktop.WindowMemory.panelWidth(PanelWidth)) }

    RememberPlace(navController, tab, panelCharacter, onTab = { tab = it }, onPanel = { panelCharacter = it })

    // A clicked reply notification names the chat to open.
    val shell: ShellViewModel = hiltViewModel()
    val notified by shell.openChat.collectAsStateWithLifecycle()
    LaunchedEffect(notified) {
        val id = notified ?: return@LaunchedEffect
        if (openChatId != id) openChat(id)
        shell.openedChat(id)
    }
    DisposableEffect(Unit) {
        DesktopTray.openChat = { id -> navController.navigate(Routes.chat(id)) { popUpTo(Routes.TABS) } }
        onDispose {
            DesktopTray.openChat = null
        }
    }

    // Esc, after any open sheet or dialog: the panel closes first, then the pane steps back.
    DisposableEffect(navController) {
        DesktopBack.fallback = {
            when {
                panelCharacter != null -> { panelCharacter = null; true }
                navController.previousBackStackEntry != null -> navController.popBackStack()
                else -> false
            }
        }
        onDispose { DesktopBack.fallback = null }
    }

    // Ctrl+R / F5 refresh whatever is on show: each screen offers its own refresh while it is
    // (RegisterRefresh), the newest on top, so a page or the character panel over a list wins.
    val showing = androidx.compose.runtime.rememberUpdatedState(route to tab)
    DisposableEffect(Unit) {
        com.cherry.butler.desktop.DesktopKeys.refresh = {
            val action = com.cherry.butler.desktop.DesktopKeys.screen.action
            if (action != null) { action(); true } else false
        }
        onDispose { com.cherry.butler.desktop.DesktopKeys.refresh = null }
    }
    // Ctrl+H: the archive, from wherever the window is; again, back to the list.
    DisposableEffect(Unit) {
        com.cherry.butler.desktop.DesktopKeys.archive = {
            val (r, t) = showing.value
            if (r != Routes.TABS || t != TopLevelDestination.Chats) goTo(TopLevelDestination.Chats)
            com.cherry.butler.desktop.DesktopKeys.chatsArchive.action?.invoke()
            true
        }
        onDispose { com.cherry.butler.desktop.DesktopKeys.archive = null }
    }

    ChatListWatch(section = tab.takeIf { route == Routes.TABS })

    val chatLight = remember { ChatLight() }
    CompositionLocalProvider(
        LocalLeaveGuard provides leaveGuard,
        com.cherry.butler.ui.components.LocalPointerMotion provides true,
        // Every scrollbar in Butler's colours: a hairline track's worth of thumb, lit on hover.
        androidx.compose.foundation.LocalScrollbarStyle provides androidx.compose.foundation.ScrollbarStyle(
            minimalHeight = 32.dp,
            thickness = 6.dp,
            shape = RoundedCornerShape(3.dp),
            hoverDurationMillis = Motion.SHORT,
            unhoverColor = ButlerTheme.colors.textLow.copy(alpha = 0.35f),
            hoverColor = ButlerTheme.colors.textMed,
        ),
        LocalChatLight provides chatLight,
        com.cherry.butler.ui.components.LocalBackdropHost provides remember { com.cherry.butler.ui.components.BackdropHost() },
        com.cherry.butler.ui.components.LocalRefreshHandle provides com.cherry.butler.desktop.DesktopKeys.screen,
    ) {
        Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().background(ButlerTheme.colors.chrome)) {
            DesktopRail(
                lit = tab.takeIf { route != Routes.CHAT },
                openChatId = openChatId,
                onSection = ::goTo,
                onOpenChat = ::openChat,
            )
            // The ground rests on the rail's chrome with rounded inner corners, as the phone's
            // ground rests on its tab bar.
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sideBySide = maxWidth >= SideBySideFrom
                    val panelOpen = panelCharacter != null
                    // The panel is as wide as it was last dragged, within what leaves the pane room.
                    val widest = (maxWidth - PaneAtLeast).coerceAtLeast(PanelNarrowest)
                    val panelWidth = panelDrag.coerceIn(PanelNarrowest, minOf(PanelWidest, widest))
                    val paneWidth = if (panelOpen && sideBySide) maxWidth - panelWidth else maxWidth
                    val paneHeight = maxHeight
                    Row(Modifier.fillMaxSize()) {
                        PaneSize(paneWidth, paneHeight, Modifier.weight(1f)) {
                            PaneNav(
                                navController = navController,
                                tab = tab,
                                paneWidth = paneWidth,
                                onTab = { tab = it },
                                onCharacter = { panelCharacter = it },
                                onOpenChat = ::openChat,
                            )
                        }
                        if (panelOpen && sideBySide) {
                            PanelEdge(
                                onDrag = { dx -> panelDrag = (panelDrag.coerceIn(PanelNarrowest, minOf(PanelWidest, widest)) - dx).coerceIn(PanelNarrowest, minOf(PanelWidest, widest)) },
                                onDone = { com.cherry.butler.desktop.WindowMemory.keepPanelWidth(panelDrag) },
                                onReset = { panelDrag = PanelWidth; com.cherry.butler.desktop.WindowMemory.keepPanelWidth(PanelWidth) },
                            )
                            PaneSize(panelWidth, paneHeight, Modifier.width(panelWidth)) {
                                CharacterPanel(
                                    characterId = panelCharacter!!,
                                    onClose = { panelCharacter = null },
                                    onOpenChat = ::openChat,
                                    onNavigate = { navController.navigate(it) },
                                    onCharacter = { panelCharacter = it },
                                )
                            }
                        }
                    }
                    // A narrow window: the page slides in over the content, behind a scrim.
                    if (!sideBySide) {
                        PanelOverlay(
                            characterId = panelCharacter,
                            width = minOf(maxWidth, 440.dp),
                            height = paneHeight,
                            onClose = { panelCharacter = null },
                            onOpenChat = { panelCharacter = null; openChat(it) },
                            onNavigate = { panelCharacter = null; navController.navigate(it) },
                            onCharacter = { panelCharacter = it },
                        )
                    }
                }
            }
        }
        // An open chat's light, over the rail and the panes alike.
        ChatLightOverlay(chatLight, Modifier.matchParentSize())
        }
    }
}

/** The character panel sliding in from the right over a scrim, for a window too narrow for both. */
@Composable
private fun PanelOverlay(
    characterId: String?,
    width: Dp,
    height: Dp,
    onClose: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    onCharacter: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(characterId != null, enter = fadeIn(tween(Motion.SHORT)), exit = fadeOut(tween(Motion.SHORT))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        // Kept while sliding out, so the page leaves with its content.
        var shown by remember { mutableStateOf(characterId) }
        if (characterId != null) shown = characterId
        AnimatedVisibility(
            visible = characterId != null,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.EaseOut)) { it },
            exit = slideOutHorizontally(tween(Motion.SHORT, easing = Motion.EaseOut)) { it },
        ) {
            shown?.let { id ->
                PaneSize(width, height, Modifier.width(width)) {
                    CharacterPanel(id, onClose, onOpenChat, onNavigate, onCharacter)
                }
            }
        }
    }
}

/** The phone screens size themselves from "the screen"; on the desktop that is their pane. */
@Composable
internal fun PaneSize(width: Dp, height: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalConfiguration provides android.content.res.Configuration(width.value.toInt(), height.value.toInt()),
    ) {
        Box(modifier.fillMaxHeight()) { content() }
    }
}

/** The pane's screens: the five sections, then everything they open, as on the phone. */
@Composable
private fun PaneNav(
    navController: NavHostController,
    tab: TopLevelDestination,
    paneWidth: Dp,
    onTab: (TopLevelDestination) -> Unit,
    onCharacter: (String) -> Unit,
    onOpenChat: (Long) -> Unit,
) {
    val none = PaddingValues()
    // Every screen change is a cut, as on the phone: a fade drew both screens at once and
    // left the one being left on show while the next was built.
    NavHost(
        navController = navController,
        startDestination = Routes.TABS,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Routes.TABS) {
            TabHost(selected = tab, modifier = Modifier.fillMaxSize()) { dest ->
                when (dest) {
                    TopLevelDestination.Browse -> DesktopBrowse(onCharacterClick = onCharacter)
                    TopLevelDestination.Chats -> CompositionLocalProvider(
                        com.cherry.butler.ui.components.LocalArchiveHandle provides com.cherry.butler.desktop.DesktopKeys.chatsArchive,
                    ) {
                        ChatsScreen(
                            contentPadding = none,
                            onChatClick = onOpenChat,
                            onCharacterClick = onCharacter,
                            onBrowse = { onTab(TopLevelDestination.Browse) },
                        )
                    }
                    TopLevelDestination.Notifications -> NotificationsScreen(
                        contentPadding = none,
                        onOpenPublished = { navController.navigate(Routes.published(it)) },
                        onOpenCharacter = onCharacter,
                    )
                    TopLevelDestination.Profile -> ProfileScreen(
                        contentPadding = none,
                        onEditPersona = { navController.navigate(Routes.persona(it)) },
                        onOpenMyCharacters = { navController.navigate(Routes.MY_CHARACTERS) },
                    )
                    TopLevelDestination.Settings -> SettingsSection(navController, paneWidth)
                }
            }
        }
        composable(Routes.SETTINGS_PAGE, arguments = listOf(navArgument("page") { type = NavType.StringType })) { entry ->
            val page = entry.arguments?.getString("page")?.let { name -> SettingsPage.entries.firstOrNull { it.name == name } } ?: SettingsPage.Main
            settingsScreen(navController, page = page, onBack = { navController.popBackStack() })
        }
        composable(Routes.PERSONA, arguments = listOf(navArgument("personaId") { type = NavType.StringType })) {
            com.cherry.butler.feature.profile.PersonaEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BLOCKED) { com.cherry.butler.feature.settings.BlockedScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.DIAGNOSTICS) { com.cherry.butler.feature.settings.DiagnosticsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ROUTER) { com.cherry.butler.feature.settings.RouterScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NOTIFICATION_PREFS) {
            com.cherry.butler.feature.notifications.NotificationPrefsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.GENERATION) { settingsScreen(navController, page = SettingsPage.Generation, onBack = { navController.popBackStack() }) }
        composable(Routes.MODEL_SETTINGS) { settingsScreen(navController, page = SettingsPage.Model, onBack = { navController.popBackStack() }) }
        composable(Routes.CUSTOMIZE) { CustomizeScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS_PROXY, arguments = listOf(navArgument("proxyId") { type = NavType.StringType })) {
            ProxyEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_PROMPTS) {
            PromptsScreen(onBack = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.prompt(it)) })
        }
        composable(Routes.SETTINGS_PROMPT, arguments = listOf(navArgument("promptId") { type = NavType.StringType })) {
            PromptEditorScreen(onBack = { navController.popBackStack() })
        }
        // A character opened from anywhere the panel can't reach (a link inside a page).
        composable(Routes.CHARACTER, arguments = listOf(navArgument(Routes.ARG_CHARACTER_ID) { type = NavType.StringType })) {
            CharacterDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = onOpenChat,
                onOpenComments = { id, name -> navController.navigate(Routes.comments(id, name)) },
                onOpenPublished = { navController.navigate(Routes.published(it)) },
                onOpenCharacter = onCharacter,
                onSeeAllPublished = { id, name -> navController.navigate(Routes.publishedList(id, name)) },
                onOpenCreator = { navController.navigate(Routes.creator(it)) },
                onEditCharacter = { navController.navigate(Routes.characterEditor(it)) },
            )
        }
        composable(Routes.MY_CHARACTERS) { entry ->
            // The editor leaves a mark here when it saved, so the list asks again.
            val saved by entry.savedStateHandle.getStateFlow(STUDIO_SAVED, 0).collectAsState()
            com.cherry.butler.feature.studio.MyCharactersScreen(
                onBack = { navController.popBackStack() },
                onOpenCharacter = onCharacter,
                onEdit = { navController.navigate(Routes.characterEditor(it)) },
                refreshKey = saved,
            )
        }
        composable(Routes.CHARACTER_EDITOR, arguments = listOf(navArgument("characterId") { type = NavType.StringType })) {
            // The chat's light, here as well, warm and breathing the whole time a character is
            // being written: the chapter being written is the column, the rail and the card
            // side are the room either side where its pools drift.
            val light = LocalChatLight.current
            DisposableEffect(light) {
                light?.on = true
                light?.warm = true
                onDispose { light?.on = false; light?.warm = false }
            }
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                Modifier.fillMaxSize().onGloballyPositioned { c ->
                    val l = light ?: return@onGloballyPositioned
                    val x = c.positionInRoot().x
                    val w = c.size.width.toFloat()
                    // The editor's own measures (CharacterEditorScreen): a 220 dp rail and a 276 dp
                    // card column beside the chapter from 840 dp; narrower, the chapter is everything.
                    val wide = with(density) { w.toDp() } >= 840.dp
                    l.left = x + if (wide) with(density) { 221.dp.toPx() } else 0f
                    l.right = x + w - if (wide) with(density) { 277.dp.toPx() } else 0f
                },
            ) {
                com.cherry.butler.feature.studio.CharacterEditorScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.previousBackStackEntry?.savedStateHandle?.let { h -> h[STUDIO_SAVED] = (h.get<Int>(STUDIO_SAVED) ?: 0) + 1 }
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(Routes.CREATOR, arguments = listOf(navArgument(Routes.ARG_USER_ID) { type = NavType.StringType })) {
            com.cherry.butler.feature.creator.CreatorScreen(onBack = { navController.popBackStack() }, onOpenCharacter = onCharacter)
        }
        composable(
            Routes.PUBLISHED_LIST,
            arguments = listOf(
                navArgument("characterId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            PublishedListScreen(onBack = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.published(it)) })
        }
        composable(
            Routes.COMMENTS,
            arguments = listOf(
                navArgument("characterId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            CommentsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PUBLISHED, arguments = listOf(navArgument("slug") { type = NavType.StringType })) {
            PublishedChatScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CHAT, arguments = listOf(navArgument(Routes.ARG_CHAT_ID) { type = NavType.LongType })) {
            DesktopChat {
                ChatScreen(
                    onBack = { navController.popBackStack() },
                    onOpenModelSettings = { navController.navigate(Routes.MODEL_SETTINGS) },
                    onOpenCustomize = { navController.navigate(Routes.CUSTOMIZE) },
                    onOpenCharacter = onCharacter,
                    onOpenChat = { id -> navController.navigate(Routes.chat(id)) { popUpTo(Routes.CHAT) { inclusive = true } } },
                    onEditPersona = { navController.navigate(Routes.persona(it)) },
                )
            }
        }
    }
}

/** SettingsScreen with every destination it can open wired to the pane. */
@Composable
private fun settingsScreen(
    navController: NavHostController,
    page: SettingsPage,
    onBack: (() -> Unit)?,
    onOpenPage: (SettingsPage) -> Unit = { navController.navigate(Routes.settingsPage(it.name)) },
) = SettingsScreen(
    contentPadding = PaddingValues(),
    onEditProxy = { navController.navigate(Routes.proxy(it)) },
    onOpenPrompts = { navController.navigate(Routes.SETTINGS_PROMPTS) },
    onOpenCustomize = { navController.navigate(Routes.CUSTOMIZE) },
    onOpenGeneration = { navController.navigate(Routes.GENERATION) },
    onOpenNotifications = { navController.navigate(Routes.NOTIFICATION_PREFS) },
    onOpenBlocked = { navController.navigate(Routes.BLOCKED) },
    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
    onOpenRouter = { navController.navigate(Routes.ROUTER) },
    onOpenPage = onOpenPage,
    page = page,
    onBack = onBack,
)

/**
 * Settings: on a wide pane the list stays at the left and the chosen page opens beside it;
 * narrower, it is the phone's list that opens pages over itself.
 */
@Composable
private fun SettingsSection(navController: NavHostController, paneWidth: Dp) {
    if (paneWidth < SettingsSplitFrom) {
        settingsScreen(navController, page = SettingsPage.Main, onBack = null)
        return
    }
    var open by rememberSaveable { mutableStateOf(SettingsPage.entries.first { it != SettingsPage.Main }) }
    Row(Modifier.fillMaxSize()) {
        PaneSize(SettingsListWidth, LocalConfiguration.current.screenHeightDp.dp, Modifier.width(SettingsListWidth)) {
            settingsScreen(navController, page = SettingsPage.Main, onBack = null, onOpenPage = { open = it })
        }
        Box(Modifier.width(hairline()).fillMaxHeight().background(ButlerTheme.colors.rule))
        PaneSize(paneWidth - SettingsListWidth, LocalConfiguration.current.screenHeightDp.dp, Modifier.weight(1f)) {
            androidx.compose.runtime.key(open) {
                settingsScreen(navController, page = open, onBack = null, onOpenPage = { open = it })
            }
        }
    }
}

/** A character's page in the side panel, with its own steps (a related character opens here too). */
@Composable
private fun CharacterPanel(
    characterId: String,
    onClose: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    onCharacter: (String) -> Unit,
) {
    androidx.compose.runtime.key(characterId) {
        val panelNav = rememberNavController()
        NavHost(
            navController = panelNav,
            startDestination = Routes.CHARACTER,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(
                Routes.CHARACTER,
                arguments = listOf(navArgument(Routes.ARG_CHARACTER_ID) { type = NavType.StringType; defaultValue = characterId }),
            ) {
                CharacterDetailScreen(
                    onBack = onClose,
                    onOpenChat = onOpenChat,
                    onOpenComments = { id, name -> onNavigate(Routes.comments(id, name)) },
                    onOpenPublished = { onNavigate(Routes.published(it)) },
                    onOpenCharacter = onCharacter,
                    onSeeAllPublished = { id, name -> onNavigate(Routes.publishedList(id, name)) },
                    onOpenCreator = { onNavigate(Routes.creator(it)) },
                    onEditCharacter = { onNavigate(Routes.characterEditor(it)) },
                )
            }
        }
    }
}

/** LastPlace, as on the phone: reopen the section, chat or character the user left. */
@Composable
private fun RememberPlace(
    navController: NavHostController,
    tab: TopLevelDestination,
    panelCharacter: String?,
    onTab: (TopLevelDestination) -> Unit,
    onPanel: (String?) -> Unit,
) {
    val lastPlace = LocalLastPlace.current ?: return
    LaunchedEffect(navController) {
        lastPlace.takeRestore()?.let { place ->
            TopLevelDestination.entries.firstOrNull { it.route == place.tab }?.let(onTab)
            val detail = place.detail
            when {
                detail == null -> Unit
                detail.startsWith("character/") -> onPanel(detail.removePrefix("character/"))
                else -> runCatching { navController.navigate(detail) { popUpTo(Routes.TABS) } }
            }
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    LaunchedEffect(entry, tab, panelCharacter) {
        val e = entry ?: return@LaunchedEffect
        val detail = when (e.destination.route) {
            Routes.CHAT -> e.arguments?.getLong(Routes.ARG_CHAT_ID)?.let { Routes.chat(it) }
            else -> panelCharacter?.let { Routes.character(it) }
        }
        lastPlace.record(tab.route, detail)
    }
}

/** The mark the character editor leaves on Your characters after a save. */
private const val STUDIO_SAVED = "studio_saved"

/**
 * Keeps the chat list (the Chats pane and the rail's faces) up with chats started elsewhere.
 * The list asks Janitor once, at start; a desktop window stays open for days. So while the
 * window has focus, the newest page is fetched again: on coming back to the window, on
 * changing section, and every [CHAT_WATCH_EVERY_MS], never closer than [CHAT_WATCH_GAP_MS].
 */
@Composable
private fun ChatListWatch(section: TopLevelDestination?) {
    val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    val chats = remember { com.cherry.butler.desktop.DesktopGraph.component.chatRepository() }
    val last = remember { longArrayOf(0L) }
    LaunchedEffect(focused, section) {
        if (!focused) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            if (now - last[0] >= CHAT_WATCH_GAP_MS) {
                last[0] = now
                runCatching { chats.syncLatest() }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
            kotlinx.coroutines.delay(CHAT_WATCH_EVERY_MS)
        }
    }
}

private const val CHAT_WATCH_EVERY_MS = 2 * 60 * 1000L
private const val CHAT_WATCH_GAP_MS = 20 * 1000L
