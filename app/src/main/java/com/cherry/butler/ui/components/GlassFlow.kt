package com.cherry.butler.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Lift
import io.github.fletchmckee.liquid.LiquidState
import kotlin.math.roundToInt

/*
 * Glass that flows: a key or a menu that is one pane of glass which opens out of the thing
 * that was tapped and closes back into it.
 *
 * What makes it liquid is how it moves, not what colour it is (a coloured rim was tried, three
 * ways, and was a button with an outline each time).
 *
 *  - [GlassKey], the round key that makes something new: under a finger it gives like a drop,
 *    wider and flatter, with its lens pulling harder; tapped, the drop itself flows open into
 *    the pane its choices are on.
 *  - [GlassFlowMenu], a choice among a few (Browse's Popular and Trending): the key that was
 *    tapped stays where it is, showing what is picked, as the top row of a pane that opens
 *    down out of it with the other choices. (Opened over the key instead, the key was felt
 *    to be still there, only covered.)
 *
 * Both are one pane throughout, moved and resized: nothing more is drawn than the pane at rest.
 *
 * Neither holds the screen. A touch anywhere else shuts it and goes on to do what it was for
 * (a scroll scrolls, a tap taps): a menu that has to be tapped away first, and the thing
 * wanted tapped again after, was a complaint ([PressElsewhere]).
 */

/** Opening and shutting: quick, and a little past the mark before it settles. */
private val Flow = spring<Float>(dampingRatio = 0.72f, stiffness = 340f)
/** A finger coming down: firm. Lifting: loose, so it wobbles on its way back. */
private val Press = spring<Float>(dampingRatio = 0.7f, stiffness = 900f)
private val Release = spring<Float>(dampingRatio = 0.32f, stiffness = 420f)

private val KEY = 56.dp
private val ROW = 52.dp
private val EDGE = 6.dp
private val PANE_WIDTH = 212.dp
/** An opened pane's corner: its rows' own corners sit inside it. */
private val PANE_CORNER = 26.dp

// ------------------------------------------------------------------------------------------
// A touch elsewhere

/**
 * Shuts an open pane on a touch anywhere else, and lets that touch go on to the page: a
 * catcher laid across the screen under the pane, as a sibling over the page (not a parent of
 * it), which hears a finger come down and shares the touch with what lies under it.
 *
 * It stays for as long as the finger that touched it is down, open or not. Taken away the
 * moment it shut the pane (as it first was), the touch it was sharing was cancelled for the
 * page as well, so the tap that shut a menu never landed and a scroll never started: the
 * screen was held after all.
 */
@Composable
private fun PressElsewhere(active: Boolean, onPress: () -> Unit) {
    var held by remember { mutableStateOf(false) }
    val now by rememberUpdatedState(active)
    val press by rememberUpdatedState(onPress)
    if (active || held) {
        Box(Modifier.fillMaxSize() then remember { PressElsewhereElement(onPress = { if (now) press() }, onHeld = { held = it }) })
    }
}

private class PressElsewhereElement(val onPress: () -> Unit, val onHeld: (Boolean) -> Unit) : ModifierNodeElement<PressElsewhereNode>() {
    override fun create() = PressElsewhereNode(onPress, onHeld)
    override fun update(node: PressElsewhereNode) {
        node.onPress = onPress
        node.onHeld = onHeld
    }
    override fun hashCode() = onPress.hashCode()
    override fun equals(other: Any?) = other is PressElsewhereElement && other.onPress === onPress
}

private class PressElsewhereNode(var onPress: () -> Unit, var onHeld: (Boolean) -> Unit) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial) return
        if (pointerEvent.type == PointerEventType.Press) {
            onHeld(true)
            onPress()
        } else if (pointerEvent.changes.none { it.pressed }) {
            onHeld(false)
        }
    }
    override fun onCancelPointerInput() { onHeld(false) }
    // The point of it: the page under this node is touched too.
    override fun sharePointerInputWithSiblings() = true
}

// ------------------------------------------------------------------------------------------
// Where a pane that flows out of the page is drawn

/**
 * A place over the whole frame for a pane of glass that a screen opens out of itself. It is
 * drawn beside what the frame's glass looks through ([glass]: the page, as the nav island
 * sees it), not inside it, so the pane can look through the page it came from. One at a time.
 */
@Stable
class GlassOverlay(val glass: LiquidState) {
    internal var content: (@Composable BoxScope.() -> Unit)? by mutableStateOf(null)
    /** What a touch elsewhere does while a pane is open; null when none is. */
    internal var onPressElsewhere: (() -> Unit)? by mutableStateOf(null)
    /** Where the host lies in the window, and how big it is: panes are placed in its own measure. */
    internal var origin = Offset.Zero
    internal var size = IntSize.Zero
}

/** The frame's overlay for flowing glass; null where the frame has no glass (then menus are plain). */
val LocalGlassOverlay = compositionLocalOf<GlassOverlay?> { null }

/** Marks the box the overlay's panes are placed in: the one [GlassOverlayHost] is called in. */
fun Modifier.glassOverlayFrame(overlay: GlassOverlay): Modifier = onGloballyPositioned {
    overlay.origin = it.positionInWindow()
    overlay.size = it.size
}

/**
 * Draws the overlay's pane, as the last children of the frame's own box (straight into it,
 * with no box of its own between: the touch that shuts a pane goes on to the page only as a
 * sibling of the page).
 */
@Composable
fun BoxScope.GlassOverlayHost(overlay: GlassOverlay) {
    PressElsewhere(active = overlay.onPressElsewhere != null) { overlay.onPressElsewhere?.invoke() }
    overlay.content?.invoke(this)
}

// ------------------------------------------------------------------------------------------
// The menu that flows out of a key

/**
 * The other choices, opened downward out of the key at [anchor] (its bounds in the window, in
 * pixels). The key is not covered and does not move: it is the pane's top row, drawn by [key]
 * as the key itself, showing what is picked now. Under it come [count] rows drawn by [row]:
 * the choices that are not picked, and only those. Every row is the key's size.
 *
 * It opens downward and nowhere else. (It was first opened round the key, so that the picked
 * choice kept its place in the list's order: the list then opened upward out of reach for a
 * choice low in it, and jumped as it re-ordered itself round a new pick while it shut.)
 *
 * Where the frame has no glass it is the plain menu ([ButlerMenu]) with [plain] in it.
 */
@Composable
fun GlassFlowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: () -> Rect,
    count: Int,
    plain: @Composable () -> Unit,
    key: @Composable () -> Unit,
    row: @Composable (Int) -> Unit,
) {
    val overlay = LocalGlassOverlay.current
    if (overlay == null) {
        ButlerMenu(expanded = expanded, onDismissRequest = onDismissRequest) { plain() }
        return
    }
    val flow = remember { Animatable(0f) }
    LaunchedEffect(expanded) { flow.animateTo(if (expanded) 1f else 0f, Flow) }
    val open by rememberUpdatedState(expanded)
    val rows by rememberUpdatedState(count)
    val drawRow by rememberUpdatedState(row)
    val drawKey by rememberUpdatedState(key)
    val dismiss by rememberUpdatedState(onDismissRequest)
    // On the frame for as long as any of it shows: while open, and while it flows shut.
    val showing by remember { derivedStateOf { open || flow.value > 0.004f } }
    if (showing) DisposableEffect(overlay) {
        val pane: @Composable BoxScope.() -> Unit = {
            FlowMenuPane(overlay, flow, anchor, count = { rows }, key = { drawKey() }, row = { drawRow(it) })
        }
        overlay.content = pane
        onDispose { if (overlay.content === pane) overlay.content = null }
    }
    if (expanded) DisposableEffect(overlay) {
        val shut = { dismiss() }
        overlay.onPressElsewhere = shut
        onDispose { if (overlay.onPressElsewhere === shut) overlay.onPressElsewhere = null }
    }
    androidx.activity.compose.BackHandler(enabled = expanded) { onDismissRequest() }
}

/** Between a key and the edge of the pane it is a row of, and between rows: the sort bar's own. */
private val ROW_GAP = 3.dp

@Composable
private fun BoxScope.FlowMenuPane(
    overlay: GlassOverlay,
    flow: Animatable<Float, AnimationVector1D>,
    anchor: () -> Rect,
    count: () -> Int,
    key: @Composable () -> Unit,
    row: @Composable (Int) -> Unit,
) {
    val density = LocalDensity.current
    val place = anchor()
    if (place.isEmpty) return
    val gap = with(density) { ROW_GAP.toPx() }
    val rows = 1 + count()
    // Shut, the pane is the key and the gap round it; open, the key and every row under it.
    // Its top never moves.
    val top = place.top - gap
    val shutHeight = place.height + gap * 2
    val fullHeight = rows * place.height + (rows - 1) * gap + gap * 2
    val height = (shutHeight + (fullHeight - shutHeight) * flow.value).coerceAtLeast(1f)
    val left = place.left - gap
    val width = place.width + gap * 2
    val opened = remember(flow) { { flow.value.coerceIn(0f, 1f) } }
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset((left - overlay.origin.x).roundToInt(), (top - overlay.origin.y).roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            // It comes out of the key: clear while it is the key's size, there a fifth of the way on.
            .graphicsLayer { alpha = (flow.value / 0.2f).coerceIn(0f, 1f) }
            .glassSurface(overlay.glass, MaterialTheme.shapes.medium, still = true, opened = opened),
        contentAlignment = Alignment.TopStart,
    ) {
        // Laid out at the pane's full size from the first frame, so the rows do not squeeze
        // as it opens down over them.
        Column(
            modifier = Modifier
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                .requiredSize(with(density) { width.toDp() }, with(density) { fullHeight.toDp() })
                .padding(ROW_GAP),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ROW_GAP),
        ) {
            val rowSize = Modifier.size(with(density) { place.width.toDp() }, with(density) { place.height.toDp() })
            // The key, where the key is.
            Box(rowSize) { key() }
            // The other choices, coming as the glass reaches them.
            for (i in 0 until rows - 1) {
                Box(rowSize.graphicsLayer { alpha = ((flow.value - 0.2f) / 0.55f).coerceIn(0f, 1f) }) { row(i) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// The round key that makes something new

/** One thing the key makes. */
class GlassKeyChoice(val label: String, val icon: ImageVector, val onPick: () -> Unit)

/**
 * The key, placed by [modifier] in a box that fills the screen (while open it lays a catcher
 * across that box, as a sibling of the page, so a touch anywhere else shuts it and still
 * lands). [glass] is what it looks through; without it (Depth Flat, battery saver) it is the
 * accent's own round key with a plain menu. With one choice a tap makes it at once.
 */
@Composable
fun GlassKey(
    glass: LiquidState?,
    choices: List<GlassKeyChoice>,
    label: String,
    modifier: Modifier = Modifier,
    /** Open from the start: for a render of it. */
    startOpen: Boolean = false,
) {
    var open by remember { mutableStateOf(startOpen) }
    val press = remember { MutableInteractionSource() }
    val tap = { if (choices.size == 1) choices.first().onPick() else open = true }

    if (glass == null) {
        Box(modifier) {
            Box(
                modifier = Modifier
                    .size(KEY)
                    .card(shape = CircleShape, color = MaterialTheme.colorScheme.primary, lift = Lift.Float)
                    .clickable(interactionSource = press, indication = LocalIndication.current, onClickLabel = label, role = Role.Button, onClick = tap),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp)) }
            ButlerMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.label, style = MaterialTheme.typography.titleMedium) },
                        leadingIcon = { Icon(choice.icon, contentDescription = null, tint = ButlerTheme.colors.textMed) },
                        onClick = { open = false; choice.onPick() },
                    )
                }
            }
        }
        return
    }

    // How far it has flowed open: 0 the drop, 1 the pane, and a little either side on the way.
    val flow = remember { Animatable(if (startOpen) 1f else 0f) }
    LaunchedEffect(open) { flow.animateTo(if (open) 1f else 0f, Flow) }
    // How far it is pressed in: 1 under a finger, and through 0 to the other side as it lets go.
    val give = remember { Animatable(0f) }
    val pressed by press.collectIsPressedAsState()
    LaunchedEffect(pressed) { give.animateTo(if (pressed) 1f else 0f, if (pressed) Press else Release) }

    androidx.activity.compose.BackHandler(enabled = open) { open = false }
    // A touch anywhere else shuts it, and still lands on the page.
    PressElsewhere(active = open) { open = false }

    val paneHeight = ROW * choices.size + EDGE * 2
    val at = flow.value
    val width = (KEY + (PANE_WIDTH - KEY) * at).coerceAtLeast(KEY * 0.8f)
    val height = (KEY + (paneHeight - KEY) * at).coerceAtLeast(KEY * 0.8f)
    // A drop is round as long as it is a drop; its corner eases to the pane's as it opens.
    val corner = minOf(width, height) / 2 + (PANE_CORNER - minOf(width, height) / 2) * at.coerceIn(0f, 1f)
    val opened = remember { { flow.value.coerceIn(0f, 1f) } }
    val swell = remember { { give.value * 16f } }
    Box(
        modifier = modifier
            // The drop gives under a finger: wider and flatter, from its middle.
            .graphicsLayer {
                scaleX = 1f + 0.10f * give.value
                scaleY = 1f - 0.09f * give.value
            }
            .size(width, height)
            // Words lie on it once open: the blur behind rises with it, as the message box's does.
            .glassSurface(glass, RoundedCornerShape(corner), still = true, opened = opened, swell = swell)
            .then(
                if (open) Modifier
                else Modifier.clickable(interactionSource = press, indication = null, onClickLabel = label, role = Role.Button, onClick = tap),
            ),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // The plus, in the corner the drop sits in: it turns and fades as the pane opens over it.
        if (at < 0.6f) Box(Modifier.size(KEY), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp).graphicsLayer {
                    val gone = (flow.value / 0.6f).coerceIn(0f, 1f)
                    rotationZ = 90f * gone
                    alpha = 1f - gone
                },
            )
        }
        // The choices, laid out at the pane's full size from the first frame (so they do not
        // squeeze as it grows) and uncovered as the glass reaches them.
        if (at > 0.25f) Column(
            modifier = Modifier
                .wrapContentSize(align = Alignment.BottomEnd, unbounded = true)
                .requiredSize(PANE_WIDTH, paneHeight)
                .graphicsLayer { alpha = ((flow.value - 0.25f) / 0.6f).coerceIn(0f, 1f) }
                .padding(EDGE),
        ) {
            choices.forEach { choice ->
                Row(
                    modifier = Modifier
                        .height(ROW)
                        .width(PANE_WIDTH - EDGE * 2)
                        .clickable(enabled = open, onClickLabel = choice.label, role = Role.Button) { open = false; choice.onPick() }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(choice.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(choice.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }
        }
    }
}
