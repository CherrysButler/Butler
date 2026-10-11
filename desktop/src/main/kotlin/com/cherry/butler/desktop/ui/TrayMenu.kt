package com.cherry.butler.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.ui.components.hairline
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

private val MenuWidth = 200.dp
private val MenuHeight = 104.dp

/**
 * The tray icon's menu, as Butler draws it: a small card at the pointer with Open and Quit,
 * in the look's own colours. Windows' own tray menu is the old system one and can't be
 * dressed. It goes as soon as anything else is clicked.
 */
@Composable
internal fun TrayMenu(at: Point, onOpen: () -> Unit, onQuit: () -> Unit, onDismiss: () -> Unit) {
    // Above and to the left of the pointer, as Windows puts a tray menu, kept on the screen.
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val x = (at.x - MenuWidth.value.toInt()).coerceIn(screen.x, screen.x + screen.width - MenuWidth.value.toInt())
    val y = (at.y - MenuHeight.value.toInt()).coerceIn(screen.y, screen.y + screen.height - MenuHeight.value.toInt())
    val state = rememberWindowState(position = WindowPosition(x.dp, y.dp), size = DpSize(MenuWidth, MenuHeight))
    Window(
        onCloseRequest = onDismiss,
        state = state,
        title = "Butler",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(Unit) {
            window.toFront()
            window.requestFocus()
            window.addWindowFocusListener(object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) = Unit
                override fun windowLostFocus(e: WindowEvent?) = onDismiss()
            })
        }
        val shape = RoundedCornerShape(10.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(hairline(), ButlerTheme.colors.rule, shape)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MenuRow(Icons.Rounded.OpenInNew, "Open Butler") { onDismiss(); onOpen() }
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).height(hairline()).background(ButlerTheme.colors.rule))
            MenuRow(Icons.Rounded.PowerSettingsNew, "Quit Butler") { onDismiss(); onQuit() }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
