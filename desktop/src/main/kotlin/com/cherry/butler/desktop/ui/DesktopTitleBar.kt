package com.cherry.butler.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion
import com.cherry.butler.desktop.WindowFrame

private val BarHeight = 32.dp
private val KeyWidth = 46.dp

/** Windows' own close red, so the one key that ends things looks as it does everywhere else. */
private val CloseRed = Color(0xFFC42B1C)

/**
 * The window's title bar, in the rail's chrome so the two read as one frame around the ground.
 * Butler's mark sits over the rail; the rest of the strip moves the window; the three keys
 * at the right are drawn as Windows 11 draws them, a hairline each.
 */
@Composable
internal fun DesktopTitleBar() {
    if (!WindowFrame.custom) return
    val focused = LocalWindowInfo.current.isWindowFocused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .background(ButlerTheme.colors.chrome)
            .onGloballyPositioned { WindowFrame.barHeight = it.size.height },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Over the rail's middle, as the rail's keys are.
        Box(Modifier.width(72.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Image(
                painter = rememberVectorPainter(ButlerMark),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                alpha = if (focused) 1f else 0.6f,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxHeight().onGloballyPositioned { WindowFrame.buttonsFrom = it.positionInWindow().x.toInt() },
        ) {
            val glyph = if (focused) ButlerTheme.colors.textMed else ButlerTheme.colors.textLow
            WindowKey("Minimize", glyph, onClick = WindowFrame::minimize) { c, s -> minimizeGlyph(c, s) }
            val max = WindowFrame.maximized
            WindowKey(if (max) "Restore" else "Maximize", glyph, onClick = WindowFrame::toggleMaximized) { c, s ->
                if (max) restoreGlyph(c, s) else maximizeGlyph(c, s)
            }
            WindowKey("Close", glyph, close = true, onClick = WindowFrame::close) { c, s -> closeGlyph(c, s) }
        }
    }
}

@Composable
private fun WindowKey(
    label: String,
    glyph: Color,
    close: Boolean = false,
    onClick: () -> Unit,
    draw: DrawScope.(Color, Stroke) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val wash = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val fill by animateColorAsState(
        when {
            close && pressed -> CloseRed.copy(alpha = 0.8f)
            close && hovered -> CloseRed
            pressed -> wash.copy(alpha = 0.06f)
            hovered -> wash.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        tween(Motion.SHORT),
        label = "window-key",
    )
    val ink = if (close && (hovered || pressed)) Color.White else glyph
    Box(
        modifier = Modifier
            .width(KeyWidth)
            .fillMaxHeight()
            .background(fill)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) { draw(ink, Stroke(width = 1.dp.toPx())) }
    }
}

private fun DrawScope.minimizeGlyph(c: Color, s: Stroke) {
    val y = size.height / 2f
    drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = s.width)
}

private fun DrawScope.maximizeGlyph(c: Color, s: Stroke) {
    val inset = s.width / 2f
    drawRoundRect(c, Offset(inset, inset), Size(size.width - s.width, size.height - s.width), CornerRadius(1.5.dp.toPx()), style = s)
}

/** The restored window's two squares: the front one whole, the back one's corner behind it. */
private fun DrawScope.restoreGlyph(c: Color, s: Stroke) {
    val inset = s.width / 2f
    val step = 2.dp.toPx()
    val front = size.width - step
    drawRoundRect(c, Offset(inset, step + inset), Size(front - s.width, front - s.width), CornerRadius(1.dp.toPx()), style = s)
    val back = Path().apply {
        moveTo(step + inset, step)
        lineTo(step + inset, inset)
        lineTo(size.width - inset, inset)
        lineTo(size.width - inset, front - inset)
        lineTo(front, front - inset)
    }
    drawPath(back, c, style = s)
}

private fun DrawScope.closeGlyph(c: Color, s: Stroke) {
    drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = s.width)
    drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = s.width)
}
