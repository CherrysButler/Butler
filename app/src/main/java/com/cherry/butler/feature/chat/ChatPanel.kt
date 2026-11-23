package com.cherry.butler.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.ui.components.LocalSheetInline

/** The PC chat panel's pages, in the order of its tabs. */
enum class ChatPanelTab(val label: String, val icon: ImageVector) {
    Character("Character", Icons.Rounded.Badge),
    Persona("Persona", Icons.Rounded.Person),
    Model("Model", Icons.Rounded.AutoAwesome),
    Memory("Memory", Icons.Rounded.AutoStories),
    Lore("Lore", Icons.Rounded.MenuBook),
    Look("Look", Icons.Rounded.Palette),
    Context("Context", Icons.Rounded.DataObject),
}

/**
 * The chat panel of a PC window: everything about the open chat (who it is with, who you are,
 * the model, its memory, its lore, how it looks, what goes to the model) beside the story
 * instead of in sheets over it. The shell owns one and shows it at the window's right; the open
 * chat hands it its pages. Not on the phone, where it would crowd the chat.
 */
class ChatPanelHost {
    /** Whether the panel is out. */
    var open by mutableStateOf(false)
    var tab by mutableStateOf(ChatPanelTab.Character)
    /** The open chat's character, for the Character page (the shell shows the character page). */
    var characterId by mutableStateOf<String?>(null)
    /** The open chat's pages; null while no chat is open, which hides the panel. */
    var pages by mutableStateOf<(@Composable (ChatPanelTab) -> Unit)?>(null)
    /** What the chat's menu held beyond its pages: a new chat, the export, the delete. */
    var actions by mutableStateOf<ChatPanelActions?>(null)

    fun show(tab: ChatPanelTab) {
        this.tab = tab
        open = true
    }

    fun toggle() { open = !open }
}

/** The chat's own actions, along the panel's foot whichever page is open. */
class ChatPanelActions(val onNewChat: () -> Unit, val onExport: () -> Unit, val onDelete: () -> Unit)

val LocalChatPanelHost = staticCompositionLocalOf<ChatPanelHost?> { null }

/**
 * The panel: a row of tabs (an icon over its name, the open one on a pill that springs across
 * to it) over the open page, which slides in from the side it was asked from. [character] draws
 * the Character page; the others are the chat's [pages], laid out in place of their sheets.
 */
@Composable
fun ChatPanel(
    host: ChatPanelHost,
    character: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = host.pages
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        PanelTabs(selected = host.tab, onSelect = { host.tab = it })
        // The chat has left: the panel slides out as itself, its page already put away.
        if (pages == null) return@Column
        AnimatedContent(
            targetState = host.tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val slide = tween<androidx.compose.ui.unit.IntOffset>(260, easing = com.cherry.butler.core.design.Motion.Glide)
                (slideInHorizontally(slide) { w -> if (forward) w / 8 else -w / 8 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(slide) { w -> if (forward) -w / 8 else w / 8 } + fadeOut(tween(160)))
            },
            label = "chat panel page",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { tab ->
            if (tab == ChatPanelTab.Character) {
                character()
            } else {
                CompositionLocalProvider(LocalSheetInline provides true) {
                    Column(Modifier.fillMaxSize().padding(top = 4.dp)) { pages(tab) }
                }
            }
        }
        host.actions?.let { PanelFoot(it) }
    }
}

/** The chat's own actions under every page: a new chat, the export, and the delete in red. */
@Composable
private fun PanelFoot(actions: ChatPanelActions) {
    com.cherry.butler.ui.components.HairlineRule(color = ButlerTheme.colors.outlineFaint)
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
        FootKey(Icons.AutoMirrored.Rounded.Chat, "New chat", MaterialTheme.colorScheme.onSurface, actions.onNewChat, Modifier.weight(1f))
        FootKey(Icons.Rounded.FileDownload, "Export", MaterialTheme.colorScheme.onSurface, actions.onExport, Modifier.weight(1f))
        FootKey(Icons.Rounded.Delete, "Delete", ButlerTheme.colors.danger, actions.onDelete, Modifier.weight(1f))
    }
}

@Composable
private fun FootKey(icon: ImageVector, label: String, ink: androidx.compose.ui.graphics.Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
    }
}

/** The tabs: equal cells across the panel, the open one on a pill that springs to it. */
@Composable
private fun PanelTabs(selected: ChatPanelTab, onSelect: (ChatPanelTab) -> Unit) {
    val tabs = PANEL_TABS
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
        val cell = maxWidth / tabs.size
        val pillAt by animateDpAsState(
            targetValue = cell * tabs.indexOf(selected).coerceAtLeast(0),
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow * 2.5f),
            label = "chat panel pill",
        )
        Box(
            Modifier
                .offset(x = pillAt)
                .width(cell)
                .height(TAB_HEIGHT)
                .padding(horizontal = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                val on = tab == selected
                Column(
                    modifier = Modifier
                        .width(cell)
                        .height(TAB_HEIGHT)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Tab) { onSelect(tab) }
                        .semantics { contentDescription = tab.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (on) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    // Seven names across a narrow panel: set a little smaller there, so none is cut.
                    val small = MaterialTheme.typography.labelSmall
                    Text(
                        tab.label,
                        style = if (cell < 64.dp) small.copy(fontSize = small.fontSize * (cell / 64.dp).coerceIn(0.74f, 1f), letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified) else small,
                        color = if (on) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.textLow,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}

private val TAB_HEIGHT = 48.dp

/** The tabs on show. */
val PANEL_TABS: List<ChatPanelTab> = ChatPanelTab.entries

/** A page of the panel that is not a sheet: scrolls in place, with the sheets' side margins. */
@Composable
internal fun PanelPage(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) { content() }
}
