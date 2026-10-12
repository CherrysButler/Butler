package com.cherry.butler.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.network.JanitorCookies
import com.cherry.butler.desktop.FilePickers
import com.cherry.butler.ui.components.card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Turnstile check on Windows, inside Butler's window. The panel draws here; the check
 * itself runs in WebView2 (butler-turnstile.exe, Edge's engine), as a borderless panel Butler
 * owns, laid exactly over the empty slot below. The page is the phone's, served as
 * janitorai.com so the site key accepts it; like Android's WebView it keeps its own
 * User-Agent, and the clearance it earns comes over through [JanitorCookies].
 */
@Composable
fun TurnstileSheet(onToken: (String) -> Unit, onDismiss: () -> Unit, check: TurnstileCheck = TurnstileCheck.SignIn) {
    val dark = !MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue > 1.5f }
    val panel = ButlerTheme.colors.surfaceElevated
    var failure by remember { mutableStateOf<String?>(null) }
    val deliver by rememberUpdatedState(onToken)
    var process by remember { mutableStateOf<Process?>(null) }
    var slot by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(slot != null) {
        val bounds = slot ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { runCheck(dark, panel, bounds, check) { process = it } }
        when {
            result.startsWith("token ") -> deliver(result.removePrefix("token "))
            else -> failure = result.removePrefix("error ")
        }
    }
    DisposableEffect(Unit) { onDispose { process?.destroy() } }

    // Drawn by the door over the whole window, not inside the form's column.
    com.cherry.butler.desktop.ui.DoorOverlay {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(360.dp).card().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Text("One moment", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = check.body,
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textMed,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            // Where the check appears.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(panel)
                    .onGloballyPositioned { slot = it.boundsInWindow() },
            )
            failure?.let {
                Text(
                    text = "The check didn't go through ($it). Close this and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ButlerTheme.colors.danger,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
        }
    }
    }
}

/** Runs the helper over [slot]; returns "token …" or "error …". Cookies and the agent are taken on the way. */
private fun runCheck(dark: Boolean, panel: Color, slot: Rect, check: TurnstileCheck, started: (Process) -> Unit): String {
    val exe = helper() ?: return "error helper-missing"
    val page = File.createTempFile("butler-turnstile", ".html").apply {
        writeText(turnstilePage(dark, check))
        deleteOnExit()
    }
    val placement = placement(slot)
    val ground = "%06X".format(panel.toArgb() and 0xFFFFFF)
    val args = listOf(exe.path, page.path, "One moment · Butler", if (dark) "dark" else "light") +
        (placement?.let { listOf(it.owner.toString(), it.x.toString(), it.y.toString(), it.w.toString(), it.h.toString(), ground) } ?: emptyList())
    val process = runCatching { ProcessBuilder(args).redirectErrorStream(true).start() }.getOrElse { return "error helper-start" }
    started(process)
    var agent: String? = null
    var result = "error closed"
    val cookies = mutableListOf<String>()
    process.inputStream.bufferedReader().useLines { lines ->
        for (line in lines) {
            when {
                line.startsWith("agent ") -> agent = line.removePrefix("agent ")
                line.startsWith("token ") -> result = line
                line.startsWith("error ") -> if (!result.startsWith("token ")) result = line
                line.startsWith("cookie ") -> cookies += line.removePrefix("cookie ")
                line == "done" -> break
            }
        }
    }
    if (cookies.isNotEmpty()) {
        android.webkit.CookieManager.getInstance().setCookie("https://janitorai.com/", cookies.joinToString("; "))
    }
    JanitorCookies.importFromWebView(agent)
    page.delete()
    return result
}

private class Placement(val owner: Long, val x: Int, val y: Int, val w: Int, val h: Int)

/** The slot in screen pixels, and Butler's window handle to own the helper's panel. */
private fun placement(slot: Rect): Placement? = runCatching {
    val window = FilePickers.owner ?: return null
    val owner = com.sun.jna.Pointer.nativeValue(com.sun.jna.Native.getWindowPointer(window))
    val scale = window.graphicsConfiguration.defaultTransform.scaleX
    val origin = (window as? javax.swing.JFrame)?.contentPane?.locationOnScreen ?: window.locationOnScreen
    Placement(
        owner = owner,
        x = (origin.x * scale + slot.left).toInt(),
        y = (origin.y * scale + slot.top).toInt(),
        w = slot.width.toInt(),
        h = slot.height.toInt(),
    )
}.getOrNull()

private fun helper(): File? {
    val dirs = listOfNotNull(
        System.getProperty("compose.application.resources.dir"),
        System.getProperty("butler.resources"),
    )
    return dirs.map { File(it, "butler-turnstile.exe") }.firstOrNull { it.isFile }
}
