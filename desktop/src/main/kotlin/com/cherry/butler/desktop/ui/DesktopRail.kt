package com.cherry.butler.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion
import com.cherry.butler.core.model.ChatGroup
import com.cherry.butler.desktop.DesktopGraph
import com.cherry.butler.ui.ShellViewModel
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.hairline
import com.cherry.butler.ui.navigation.TopLevelDestination
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val RailWidth = 72.dp
private val KeySize = 48.dp
private val FaceSize = 44.dp

/** How many recent chats the rail keeps within reach. */
private const val RECENT = 12

/**
 * The rail: Butler's five sections, then a hairline, then the faces of your recent chats.
 * A lit section is red; the open chat's face gets a red tab at the rail's edge; a face whose
 * reply is still being written wears a breathing red ring.
 */
@Composable
fun DesktopRail(
    lit: TopLevelDestination?,
    openChatId: Long?,
    onSection: (TopLevelDestination) -> Unit,
    onOpenChat: (Long) -> Unit,
) {
    val graph = DesktopGraph.component
    val shell: ShellViewModel = hiltViewModel()
    val unread by shell.unread.collectAsStateWithLifecycle()
    LaunchedEffect(lit) { shell.refreshUnread() }

    val recent = remember { graph.chatRepository().chatGroups() }.collectAsLazyPagingItems()
    val working by graph.backgroundReplies().working.collectAsState()
    val openCharacter by remember(openChatId) {
        openChatId?.let { id -> graph.chatRepository().observeChat(id).map { it?.characterId } } ?: flowOf(null)
    }.collectAsState(null)

    Column(
        modifier = Modifier.width(RailWidth).fillMaxHeight().background(ButlerTheme.colors.chrome).padding(top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TopLevelDestination.entries.forEach { dest ->
                SectionKey(
                    dest = dest,
                    lit = dest == lit,
                    dot = dest == TopLevelDestination.Notifications && unread > 0,
                    onClick = { onSection(dest) },
                )
            }
        }
        Box(
            Modifier.padding(vertical = 14.dp).width(28.dp).height(hairline())
                .background(ButlerTheme.colors.rule),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 14.dp),
        ) {
            items(
                count = minOf(recent.itemCount, RECENT),
                key = { i -> recent.peek(i)?.characterId ?: i },
            ) { i ->
                recent[i]?.let { group ->
                    RecentFace(
                        group = group,
                        open = group.characterId == openCharacter,
                        replying = group.latestChatId in working.keys,
                        onClick = { onOpenChat(group.latestChatId) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionKey(dest: TopLevelDestination, lit: Boolean, dot: Boolean, onClick: () -> Unit) {
    val label = stringResource(dest.labelRes)
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val tint by animateColorAsState(
        when {
            lit -> MaterialTheme.colorScheme.primary
            hovered -> ButlerTheme.colors.textMed
            else -> ButlerTheme.colors.textLow
        },
        animationSpec = Motion.enter(Motion.SHORT),
        label = "section-tint",
    )
    RailTooltip(label) {
        Box(
            modifier = Modifier
                .size(KeySize)
                .background(if (hovered && !lit) ButlerTheme.colors.surfaceElevated else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                .hoverable(hover)
                .pointerHoverIcon(PointerIcon.Hand)
                .selectable(selected = lit, onClick = onClick, role = Role.Tab, interactionSource = hover, indication = null)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Box {
                Icon(dest.icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                if (dot) {
                    Box(Modifier.align(Alignment.TopEnd).size(9.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentFace(group: ChatGroup, open: Boolean, replying: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    // The open chat's tab grows from the rail's edge; hover shows a short one, as a preview.
    val tabHeight by animateDpAsState(
        when {
            open -> 28.dp
            hovered -> 12.dp
            else -> 0.dp
        },
        animationSpec = tween(Motion.SHORT, easing = Motion.EaseOut),
        label = "face-tab",
    )
    RailTooltip(if (replying) "${group.characterName} · replying" else group.characterName) {
        Box(Modifier.width(RailWidth), contentAlignment = Alignment.Center) {
            Box(
                Modifier.align(Alignment.CenterStart).width(4.dp).height(tabHeight)
                    .background(
                        if (open) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
                        RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                    ),
            )
            val ringShape = RoundedCornerShape((FaceSize + 6.dp) * 0.2f)
            Box(
                modifier = Modifier
                    .size(FaceSize + 6.dp)
                    .then(if (replying) Modifier.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = breathing()), ringShape) else Modifier)
                    .hoverable(hover)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .selectable(selected = open, onClick = onClick, role = Role.Button, interactionSource = hover, indication = null)
                    .semantics { contentDescription = "Open your chat with ${group.characterName}" },
                contentAlignment = Alignment.Center,
            ) {
                Avatar(
                    url = group.avatarUrl,
                    name = group.characterName,
                    size = FaceSize,
                    modifier = Modifier.alpha(if (open || hovered) 1f else 0.82f),
                )
            }
        }
    }
}

/** The ring's breath while a reply is written: the phone's 900 ms skeleton breath, in red. */
@Composable
private fun breathing(): Float {
    val t = rememberInfiniteTransition(label = "reply-ring")
    val a by t.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = Motion.EaseOut), RepeatMode.Reverse),
        label = "reply-ring-alpha",
    )
    return a
}

/** A name beside the rail on hover: a raised slate tag with the card's hairline edge. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailTooltip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Box(
                Modifier
                    .background(ButlerTheme.colors.surfaceHigh, RoundedCornerShape(8.dp))
                    .border(hairline(), ButlerTheme.colors.rule, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        delayMillis = 250,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.CenterEnd,
            alignment = Alignment.CenterEnd,
            offset = DpOffset(6.dp, 0.dp),
        ),
        content = content,
    )
}
