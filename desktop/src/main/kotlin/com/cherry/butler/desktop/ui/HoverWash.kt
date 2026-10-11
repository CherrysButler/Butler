package com.cherry.butler.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

/**
 * What a click looks like on the desktop: a faint wash over the thing under the pointer, a
 * little stronger while it is held, as a desktop app answers. The phone's ripple spread from
 * the click across a whole row, which on a wide settings row read as a stretched smear.
 */
internal class HoverWash(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource, color)

    override fun equals(other: Any?) = other is HoverWash && other.color == color
    override fun hashCode() = color.hashCode()

    private class Node(private val source: InteractionSource, private val color: Color) : Modifier.Node(), DrawModifierNode {
        private val level = Animatable(0f)
        private var hovered = false
        private var pressed = false

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { i ->
                    when (i) {
                        is HoverInteraction.Enter -> hovered = true
                        is HoverInteraction.Exit -> hovered = false
                        is PressInteraction.Press -> pressed = true
                        is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                    }
                    val target = when {
                        pressed -> PRESSED
                        hovered -> HOVERED
                        else -> 0f
                    }
                    // In at once, out a touch slower, so a quick click still shows.
                    launch { level.animateTo(target, tween(if (target > level.value) 60 else 160)) }
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
        const val HOVERED = 0.05f
        const val PRESSED = 0.10f
    }
}
