package com.cherry.butler.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.Depth
import com.cherry.butler.core.design.LocalDepth
import kotlinx.coroutines.launch

/*
 * Depth in motion (Depth Lifted): what is pressed answers like a thing, not a picture.
 * - [pressSpring]: the pressed surface sinks a little and springs back on release. Wired to
 *   the surfaces people tap most (Browse cards, keys), since a press can only move what comes
 *   after it in a modifier chain.
 * - [PressWash]: every other press, a soft wash over what is held instead of Android's ripple.
 * - [sheetPushBack]: while a sheet is up, the page under it steps back.
 */

private const val SINK = 0.965f

/** The pressed surface sinks to [SINK] at once and springs back, with a little give, on release. */
@Composable
fun Modifier.pressSpring(source: InteractionSource): Modifier {
    if (LocalDepth.current != Depth.Lifted) return this
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) SINK else 1f,
        animationSpec = if (pressed) spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)
        else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "press-spring",
    )
    // A layer only while it moves: at rest the surface draws as it always did.
    val moving by remember { derivedStateOf { scale.value != 1f } }
    return if (moving) graphicsLayer { scaleX = scale.value; scaleY = scale.value } else this
}

/** A press shown as a soft wash over what is held: in at once, out a touch slower. */
class PressWash(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource, color)
    override fun equals(other: Any?) = other is PressWash && other.color == color
    override fun hashCode() = color.hashCode()

    private class Node(private val source: InteractionSource, private val color: Color) : Modifier.Node(), DrawModifierNode {
        private val level = Animatable(0f)
        override fun onAttach() {
            coroutineScope.launch {
                var held = 0
                source.interactions.collect { i ->
                    when (i) {
                        is PressInteraction.Press -> held++
                        is PressInteraction.Release, is PressInteraction.Cancel -> held = (held - 1).coerceAtLeast(0)
                    }
                    val target = if (held > 0) PRESSED else 0f
                    launch { level.animateTo(target, tween(if (target > level.value) 50 else 220)) }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val a = level.value
            if (a > 0f) drawRect(color.copy(alpha = a))
        }
    }

    private companion object {
        const val PRESSED = 0.08f
    }
}

/** How many sheets are up right now; every sheet's grip (SheetHandle) counts itself in. */
object SheetStack {
    var open by mutableIntStateOf(0)
        internal set
}

/** While a sheet is up the page steps back: a little smaller, a little lower, its corners rounding. */
@Composable
fun Modifier.sheetPushBack(): Modifier {
    if (LocalDepth.current != Depth.Lifted) return this
    val p by animateFloatAsState(
        targetValue = if (SheetStack.open > 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "sheet-push-back",
    )
    return graphicsLayer {
        if (p > 0f) {
            val s = 1f - 0.06f * p
            scaleX = s
            scaleY = s
            translationY = 10.dp.toPx() * p
            shape = com.cherry.butler.core.design.SmoothCornerShape((28 * p).dp)
            clip = true
        }
    }
}
