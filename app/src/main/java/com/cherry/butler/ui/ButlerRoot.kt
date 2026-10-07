package com.cherry.butler.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.cherry.butler.ui.navigation.TabHost
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.cherry.butler.core.data.LocalLastPlace
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion
import androidx.compose.animation.animateColorAsState
import com.cherry.butler.feature.browse.BrowseScreen
import com.cherry.butler.feature.character.CharacterDetailScreen
import com.cherry.butler.feature.chat.ChatScreen
import com.cherry.butler.feature.chats.ChatsScreen
import com.cherry.butler.feature.community.CommentsScreen
import com.cherry.butler.feature.community.PublishedChatScreen
import com.cherry.butler.feature.community.PublishedListScreen
import com.cherry.butler.feature.notifications.NotificationsScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.feature.profile.ProfileScreen
import com.cherry.butler.feature.settings.PromptEditorScreen
import com.cherry.butler.feature.settings.PromptsScreen
import com.cherry.butler.feature.settings.ProxyEditorScreen
import com.cherry.butler.feature.settings.SettingsScreen
import com.cherry.butler.feature.settings.CustomizeScreen
import com.cherry.butler.ui.navigation.Routes
import com.cherry.butler.ui.navigation.TopLevelDestination

@Composable
fun ButlerRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBar = Routes.isTopLevel(currentDestination?.route)
    // Which tab shows. All five stay composed (TabHost); this only picks the one measured.
    var tab by rememberSaveable { mutableStateOf(TopLevelDestination.Browse) }

    // Reopen where the user was after a cold start, then keep recording where they are.
    val lastPlace = LocalLastPlace.current
    LaunchedEffect(navController, lastPlace) {
        val place = lastPlace ?: return@LaunchedEffect
        // Only over a fresh back stack: if the navigator restored itself, leave it be.
        val fresh = navController.previousBackStackEntry == null &&
            navController.currentDestination?.route == Routes.TABS
        place.takeRestore()?.takeIf { fresh }?.let { (saved, detail) ->
            TopLevelDestination.entries.firstOrNull { it.route == saved }?.let { tab = it }
            detail?.let { runCatching { navController.navigate(it) } }
        }
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route
            val detail = when (route) {
                Routes.CHAT -> entry.arguments?.getLong(Routes.ARG_CHAT_ID)?.let { Routes.chat(it) }
                Routes.CHARACTER -> entry.arguments?.getString(Routes.ARG_CHARACTER_ID)?.let { Routes.character(it) }
                else -> null
            }
            place.record(tab.route, detail)
        }
    }
    // The tab changes without the back stack moving: record it on its own.
    LaunchedEffect(tab, lastPlace) {
        val place = lastPlace ?: return@LaunchedEffect
        if (navController.currentDestination?.route == Routes.TABS) place.record(tab.route, null)
    }

    // A tapped reply notification: open that chat, over the Chats tab.
    val shell: ShellViewModel = hiltViewModel()
    val openChat by shell.openChat.collectAsStateWithLifecycle()
    LaunchedEffect(openChat) {
        val id = openChat ?: return@LaunchedEffect
        val top = navController.currentBackStackEntry
        val already = top?.destination?.route == Routes.CHAT && top.arguments?.getLong(Routes.ARG_CHAT_ID) == id
        if (!already) {
            tab = TopLevelDestination.Chats
            navController.popBackStack(Routes.TABS, inclusive = false)
            navController.navigate(Routes.chat(id))
        }
        shell.openedChat(id)
    }

    // A tab with unsaved changes (Settings) gets to ask before the bar takes the user away.
    val leaveGuard = androidx.compose.runtime.remember { com.cherry.butler.ui.navigation.LeaveGuard() }
    androidx.compose.runtime.CompositionLocalProvider(com.cherry.butler.ui.navigation.LocalLeaveGuard provides leaveGuard) {
    Scaffold(
        containerColor = if (showBar) ButlerTheme.colors.chrome else MaterialTheme.colorScheme.background,
        bottomBar = { if (showBar) BottomBar(selected = tab, onSelect = { dest -> if (dest != tab) leaveGuard.leave { tab = dest } }) },
    ) { scaffoldPadding ->
        // On a tab, the ground is a sheet with rounded lower corners resting on the dark
        // nav bar, as in Janitor. Screens get only the top inset; the sheet ends above the bar.
        val barPadding = if (showBar) scaffoldPadding.calculateBottomPadding() else 0.dp
        val innerPadding = PaddingValues(top = scaffoldPadding.calculateTopPadding())
        Surface(
            modifier = Modifier.fillMaxSize().padding(bottom = barPadding),
            color = MaterialTheme.colorScheme.background,
            shape = if (showBar) RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp) else RectangleShape,
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.TABS,
                // Every screen change is a cut, as Telegram and Instagram cut: no fade, no
                // slide. A transition only read as lag on top of the screen's own build time.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable(Routes.TABS) {
                    TabHost(selected = tab, modifier = Modifier.fillMaxSize()) { dest ->
                        when (dest) {
                            TopLevelDestination.Browse -> BrowseScreen(
                                contentPadding = innerPadding,
                                onCharacterClick = { navController.navigate(Routes.character(it)) },
                            )
                            TopLevelDestination.Chats -> ChatsScreen(
                                contentPadding = innerPadding,
                                onChatClick = { navController.navigate(Routes.chat(it)) },
                                onCharacterClick = { navController.navigate(Routes.character(it)) },
                                onBrowse = { tab = TopLevelDestination.Browse },
                            )
                            TopLevelDestination.Notifications -> NotificationsScreen(
                                contentPadding = innerPadding,
                                onOpenPublished = { navController.navigate(Routes.published(it)) },
                                onOpenCharacter = { navController.navigate(Routes.character(it)) },
                            )
                            TopLevelDestination.Profile -> ProfileScreen(contentPadding = innerPadding, onEditPersona = { navController.navigate(Routes.persona(it)) })
                            TopLevelDestination.Settings -> SettingsScreen(
                                contentPadding = innerPadding,
                                onEditProxy = { navController.navigate(Routes.proxy(it)) },
                                onOpenPrompts = { navController.navigate(Routes.SETTINGS_PROMPTS) },
                                onOpenCustomize = { navController.navigate(Routes.CUSTOMIZE) },
                                onOpenGeneration = { navController.navigate(Routes.GENERATION) },
                                onOpenNotifications = { navController.navigate(Routes.NOTIFICATION_PREFS) },
                                onOpenBlocked = { navController.navigate(Routes.BLOCKED) },
                                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                                onOpenRouter = { navController.navigate(Routes.ROUTER) },
                            )
                        }
                    }
                }
                composable(
                    route = Routes.PERSONA,
                    arguments = listOf(navArgument("personaId") { type = NavType.StringType }),
                ) {
                    com.cherry.butler.feature.profile.PersonaEditorScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.BLOCKED,
                ) {
                    com.cherry.butler.feature.settings.BlockedScreen(onBack = { navController.popBackStack() })
                }
                composable(route = Routes.DIAGNOSTICS) {
                    com.cherry.butler.feature.settings.DiagnosticsScreen(onBack = { navController.popBackStack() })
                }
                composable(route = Routes.ROUTER) {
                    com.cherry.butler.feature.settings.RouterScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.NOTIFICATION_PREFS,
                ) {
                    com.cherry.butler.feature.notifications.NotificationPrefsScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.GENERATION,
                ) {
                    SettingsScreen(
                        contentPadding = PaddingValues(),
                        onEditProxy = { navController.navigate(Routes.proxy(it)) },
                        onOpenPrompts = { navController.navigate(Routes.SETTINGS_PROMPTS) },
                        page = com.cherry.butler.feature.settings.SettingsPage.Generation,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.MODEL_SETTINGS,
                ) {
                    SettingsScreen(
                        contentPadding = PaddingValues(),
                        onEditProxy = { navController.navigate(Routes.proxy(it)) },
                        onOpenPrompts = { navController.navigate(Routes.SETTINGS_PROMPTS) },
                        page = com.cherry.butler.feature.settings.SettingsPage.Model,
                        onBack = { navController.popBackStack() },
                        onOpenRouter = { navController.navigate(Routes.ROUTER) },
                    )
                }
                composable(
                    route = Routes.CUSTOMIZE,
                ) {
                    CustomizeScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.SETTINGS_PROXY,
                    arguments = listOf(navArgument("proxyId") { type = NavType.StringType }),
                ) {
                    ProxyEditorScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.SETTINGS_PROMPTS,
                ) {
                    PromptsScreen(onBack = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.prompt(it)) })
                }
                composable(
                    route = Routes.SETTINGS_PROMPT,
                    arguments = listOf(navArgument("promptId") { type = NavType.StringType }),
                ) {
                    PromptEditorScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Routes.CHARACTER,
                    arguments = listOf(navArgument(Routes.ARG_CHARACTER_ID) { type = NavType.StringType }),
                ) {
                    CharacterDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenChat = { navController.navigate(Routes.chat(it)) },
                        onOpenComments = { id, name -> navController.navigate(Routes.comments(id, name)) },
                        onOpenPublished = { navController.navigate(Routes.published(it)) },
                        onOpenCharacter = { navController.navigate(Routes.character(it)) },
                        onSeeAllPublished = { id, name -> navController.navigate(Routes.publishedList(id, name)) },
                        onOpenCreator = { navController.navigate(Routes.creator(it)) },
                    )
                }
                composable(
                    route = Routes.CREATOR,
                    arguments = listOf(navArgument(Routes.ARG_USER_ID) { type = NavType.StringType }),
                ) {
                    com.cherry.butler.feature.creator.CreatorScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCharacter = { navController.navigate(Routes.character(it)) },
                    )
                }
                composable(
                    route = Routes.PUBLISHED_LIST,
                    arguments = listOf(
                        navArgument("characterId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    PublishedListScreen(onBack = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.published(it)) })
                }
                composable(
                    route = Routes.COMMENTS,
                    arguments = listOf(
                        navArgument("characterId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    CommentsScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.PUBLISHED,
                    arguments = listOf(navArgument("slug") { type = NavType.StringType }),
                ) {
                    PublishedChatScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.CHAT,
                    arguments = listOf(navArgument(Routes.ARG_CHAT_ID) { type = NavType.LongType }),
                ) {
                    ChatScreen(
                        onBack = { navController.popBackStack() },
                        onOpenModelSettings = { navController.navigate(Routes.MODEL_SETTINGS) },
                        onOpenCustomize = { navController.navigate(Routes.CUSTOMIZE) },
                        onOpenCharacter = { navController.navigate(Routes.character(it)) },
                        onOpenChat = { id ->
                            navController.navigate(Routes.chat(id)) { popUpTo(Routes.CHAT) { inclusive = true } }
                        },
                    )
                }
            }
        }
    }
    }
}

/** Five icons on the dark bar, as in Janitor; the current tab is lit red. */
@Composable
private fun BottomBar(selected: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit) {
    val shell: ShellViewModel = hiltViewModel()
    val unread by shell.unread.collectAsStateWithLifecycle()
    LaunchedEffect(selected) { shell.refreshUnread() }
    val outline = ButlerTheme.colors.cardOutline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ButlerTheme.colors.chrome)
            .then(if (outline.isSpecified) Modifier.drawBehind { drawRect(outline, size = Size(size.width, 1.dp.toPx())) } else Modifier)
            .navigationBarsPadding()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopLevelDestination.entries.forEach { dest ->
            val lit = dest == selected
            val label = stringResource(dest.labelRes)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = lit,
                        onClick = { onSelect(dest) },
                        role = Role.Tab,
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = 28.dp),
                    )
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                val tint by animateColorAsState(
                    if (lit) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                    animationSpec = Motion.enter(Motion.SHORT),
                    label = "tab-tint",
                )
                Box {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(28.dp),
                    )
                    if (dest == TopLevelDestination.Notifications && unread > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(9.dp)
                                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }
            }
        }
    }
}
