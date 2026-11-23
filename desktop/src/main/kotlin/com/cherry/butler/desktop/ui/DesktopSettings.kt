package com.cherry.butler.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Lift
import com.cherry.butler.core.design.LocalDepthLook
import com.cherry.butler.feature.settings.SettingsPage
import com.cherry.butler.ui.components.hairline
import com.cherry.butler.ui.components.liftShadow
import com.cherry.butler.ui.components.rim
import com.cherry.butler.ui.components.rimBrush

/**
 * The settings window's state: out or away, and the pages opened in it. The first page is the
 * one picked in the list; a page it opens (the thinking line, a look's profiles) stacks on it
 * and steps back to it.
 */
@Stable
internal class SettingsWindowState {
    var open by mutableStateOf(false)
    var stack by mutableStateOf(listOf(SettingsPage.Model))

    val page: SettingsPage get() = stack.last()
    val root: SettingsPage get() = stack.first()

    fun pick(page: SettingsPage) { stack = listOf(page) }
    fun push(page: SettingsPage) { if (page != stack.last()) stack = stack + page }

    /** One step back inside the window; false when there is none (the window closes instead). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }
}

/** As wide and as tall as the window gets; it gives way to a smaller app window. */
private val WindowWidest = 1020.dp
private val WindowTallest = 800.dp
/** The room it always leaves around itself. */
private val WindowMargin = 24.dp
private val ListWidth = 220.dp
/** Under this the list is its marks alone, so the page keeps its room. */
private val ListNamedFrom = 720.dp
private val ListCompact = 56.dp
private val BarHeight = 48.dp

/**
 * Settings on a PC: a window floating over the app, which stays where it was behind a scrim.
 * A bar with its name and a close key; the pages down the left, the open one at the right.
 * [content] draws a page in the room it is given.
 */
@Composable
internal fun SettingsWindow(
    state: SettingsWindowState,
    onClose: () -> Unit,
    /** Local Lorebooks: its own screen, opened from the list's foot. */
    onLorebooks: () -> Unit,
    content: @Composable (page: SettingsPage, onBack: (() -> Unit)?, onOpenPage: (SettingsPage) -> Unit) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        AnimatedVisibility(state.open, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        val width = minOf(WindowWidest, maxWidth - WindowMargin * 2)
        val height = minOf(WindowTallest, maxHeight - WindowMargin * 2)
        AnimatedVisibility(
            visible = state.open,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(180)) + scaleIn(tween(240, easing = com.cherry.butler.core.design.Motion.Glide), initialScale = 0.97f, transformOrigin = TransformOrigin.Center),
            exit = fadeOut(tween(140)) + scaleOut(tween(160, easing = com.cherry.butler.core.design.Motion.Glide), targetScale = 0.98f, transformOrigin = TransformOrigin.Center),
        ) {
            val shape = MaterialTheme.shapes.large
            val look = LocalDepthLook.current
            Column(
                Modifier
                    .size(width, height)
                    .liftShadow(shape, look, Lift.Float)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.background)
                    .rim(1.dp, rimBrush(ButlerTheme.colors.cardOutline, look, Lift.Float), shape)
                    // A click in the window is the window's, never the scrim's under it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .semantics { paneTitle = "Settings" },
            ) {
                Row(Modifier.fillMaxWidth().height(BarHeight).padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(36.dp).clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClickLabel = "Close settings", onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close settings", tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(hairline()).background(ButlerTheme.colors.rule))
                Row(Modifier.fillMaxSize()) {
                    val named = width >= ListNamedFrom
                    val listWidth = if (named) ListWidth else ListCompact
                    PageList(state = state, named = named, width = listWidth, onLorebooks = onLorebooks)
                    Box(Modifier.width(hairline()).fillMaxHeight().background(ButlerTheme.colors.rule))
                    val pageWidth = width - listWidth
                    PaneSize(pageWidth, height - BarHeight, Modifier.weight(1f)) {
                        // A page keeps its own scroll and state; another page starts its own.
                        androidx.compose.runtime.key(state.page) {
                            content(
                                state.page,
                                if (state.stack.size > 1) ({ state.back(); Unit }) else null,
                                { state.push(it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The pages down the window's left; the account and the way to the lorebooks at its foot. */
@Composable
private fun PageList(state: SettingsWindowState, named: Boolean, width: Dp, onLorebooks: () -> Unit) {
    val pages = SettingsPage.entries.filter { it.listed && (it != SettingsPage.Debug || com.cherry.butler.BuildConfig.DEBUG) }
    Column(Modifier.width(width).fillMaxHeight().background(ButlerTheme.colors.chrome).padding(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            pages.forEach { p ->
                ListRow(
                    label = p.title,
                    selected = state.root == p,
                    named = named,
                    onClick = { state.pick(p) },
                ) {
                    Icon(androidx.compose.ui.res.painterResource(p.icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(hairline()).background(ButlerTheme.colors.rule))
        ListRow(label = "Local Lorebooks", selected = false, named = named, onClick = onLorebooks, trailing = Icons.Rounded.NorthEast) {
            Icon(androidx.compose.ui.res.painterResource(com.cherry.butler.R.drawable.ic_set_lorebooks), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
        }
        ListRow(label = "Account", selected = state.root == SettingsPage.Account, named = named, onClick = { state.pick(SettingsPage.Account) }) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ListRow(
    label: String,
    selected: Boolean,
    named: Boolean,
    onClick: () -> Unit,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    mark: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) ButlerTheme.colors.surfaceHigh else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label }
            .heightIn(min = 40.dp)
            .padding(horizontal = if (named) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (named) androidx.compose.foundation.layout.Arrangement.Start else androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { mark() }
        if (named) {
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.textMed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) Icon(trailing, contentDescription = null, tint = ButlerTheme.colors.textLow, modifier = Modifier.size(16.dp))
        }
    }
}
