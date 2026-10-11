package com.cherry.butler.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.cherry.butler.core.data.LocalLastPlace
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.isLight
import com.cherry.butler.ui.AuthGate
import com.cherry.butler.ui.LockScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth
import okio.Path.Companion.toOkioPath
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

/**
 * Butler's window: what MainActivity does on the phone. The graph is built, the outbox picks
 * up where it stopped, and the same AuthGate and ButlerRoot draw inside.
 */
fun main(args: Array<String>) {
    FrameLog.startIfOn()
    linkLog("start", args.toList())
    // A second launch (a janitor: sign-in link from the browser) goes to the Butler already open.
    if (!DesktopInstance.claim(args) { handed -> takeLinks(handed); if ("--quit" !in handed) focusWindow?.invoke() }) return
    // Nothing to quit: this was the only Butler.
    if ("--quit" in args) return
    com.cherry.butler.core.diagnostics.Diagnostics.installCrashKeeper(DesktopContext)
    val graph = DesktopGraph.component
    installImageLoader { graph.okHttpClient() }
    graph.lastPlace().markColdStart(true)
    // The rest of getting ready runs beside the window's own start (Skia, the GPU, Compose:
    // most of a second) instead of before it: the janitor: link registration, the database,
    // the Supabase client and the chat store are built here, so the first screen finds them
    // made (Dagger hands every caller the same one, whoever asks first), and the outbox
    // picks up whatever it was doing when Butler last closed.
    Thread({
        runCatching { DesktopInstance.registerLinks() }
        runCatching { graph.database(); graph.okHttpClient(); graph.supabase(); graph.chatRepository() }
        graph.pipeline().resumeAll()
    }, "butler-warm").start()
    // Cold start through the OAuth redirect.
    takeLinks(args.toList())

    application {
        val state = rememberWindowState(
            placement = WindowMemory.placement(),
            size = WindowMemory.size() ?: DpSize(1180.dp, 820.dp),
            position = WindowMemory.position() ?: WindowPosition(Alignment.Center),
        )
        WindowMemory.Keep(state)
        // Closing the window puts Butler in the tray (DesktopTray): replies keep going.
        var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
        var trayMenuAt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<java.awt.Point?>(null) }
        val mark = androidx.compose.ui.graphics.vector.rememberVectorPainter(com.cherry.butler.desktop.ui.ButlerIcon)
        val trayDensity = LocalDensity.current
        LaunchedEffect(Unit) {
            // The tray icon keeps Java's window system alive: it goes first, or the process stays.
            quitButler = { java.awt.EventQueue.invokeLater { DesktopTray.remove(); exitApplication() } }
            val image = mark.toAwtImage(trayDensity, androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.geometry.Size(64f, 64f))
            DesktopTray.install(
                image,
                onOpen = { java.awt.EventQueue.invokeLater { shown = true; focusWindow?.invoke() } },
                onMenu = { at -> java.awt.EventQueue.invokeLater { trayMenuAt = at } },
            )
        }
        trayMenuAt?.let { at ->
            ButlerThemed(graph) {
                com.cherry.butler.desktop.ui.TrayMenu(
                    at = at,
                    onOpen = { shown = true; focusWindow?.invoke() },
                    onQuit = { quitButler?.invoke() },
                    onDismiss = { trayMenuAt = null },
                )
            }
        }
        Window(
            onCloseRequest = {
                if (DesktopTray.available) {
                    shown = false
                    DesktopTray.windowFocused = false
                    DesktopTray.hidden()
                } else {
                    exitApplication()
                }
            },
            visible = shown,
            state = state,
            title = if (com.cherry.butler.BuildConfig.DEBUG) "Butler (dev)" else "Butler",
            icon = mark,
            onPreviewKeyEvent = { e ->
                when {
                    e.type != KeyEventType.KeyDown -> false
                    e.key == Key.Escape -> DesktopBack.press()
                    e.key == Key.F && e.isCtrlPressed && e.isShiftPressed -> { FrameLog.toggle(); true }
                    // Ctrl+R or F5: refresh what's on show, as a pull does on the phone.
                    (e.key == Key.R && e.isCtrlPressed && !e.isShiftPressed) || e.key == Key.F5 ->
                        DesktopKeys.refresh?.invoke() ?: false
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) {
                focusWindow = { java.awt.EventQueue.invokeLater { shown = true; window.isMinimized = false; window.toFront(); window.requestFocus() } }
                FilePickers.owner = window
                WindowFrame.install(window)
                val refresh = window.graphicsConfiguration.device.displayMode.refreshRate
                FramePacer.install(window, refresh)
                WindowFrame.groundUnderFrames(window)
                FrameLog.attach(window, refresh)
                window.minimumSize = java.awt.Dimension(420, 560)
                window.addWindowFocusListener(object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        DesktopTray.windowFocused = true
                        graph.appLock().onReturn()
                        graph.sessionKeeper().onForeground()
                        graph.pipeline().resumeStalled()
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        DesktopTray.windowFocused = false
                        graph.appLock().onLeave()
                    }
                })
            }
            val density = LocalDensity.current
            val config = remember(state.size) {
                android.content.res.Configuration(state.size.width.value.toInt(), state.size.height.value.toInt())
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                ButlerWindow(graph, onGround = { argb -> WindowFrame.paintBehind(window, argb) })
            }
        }
    }
}

/** Brings the window forward, for a link handed over by a second launch. */
@Volatile private var focusWindow: (() -> Unit)? = null

/** Quits for real (the window's X only goes to the tray): the tray's Quit, or `Butler.exe --quit`. */
@Volatile private var quitButler: (() -> Unit)? = null

/**
 * supabase.handleDeeplinks on the phone: a `janitor://auth/…?code=…` redirect finishes the
 * Google or Discord sign-in (PKCE) by exchanging its code for a session.
 */
/** A line per launch and per link taken, codes hidden: how a sign-in link travelled. */
internal fun linkLog(event: String, args: List<String>) = runCatching {
    val shown = args.joinToString(" ") { it.replace(Regex("""code=[^&\s]+"""), "code=…") }
    java.io.File(DesktopContext.filesDir, "links.log").appendText("${java.time.LocalDateTime.now()} $event [$shown]\n")
}

private fun takeLinks(args: List<String>) {
    linkLog("take", args)
    if ("--quit" in args) {
        quitButler?.invoke()
        return
    }
    val link = args.firstOrNull { it.startsWith("${com.cherry.butler.core.config.JanitorConfig.OAUTH_SCHEME}://") } ?: return
    val code = android.net.Uri.parse(link).getQueryParameter("code") ?: return
    val auth = DesktopGraph.component.supabase().auth
    DesktopToasts.show("Finishing sign-in…")
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        runCatching { auth.exchangeCodeForSession(code) }
            .onFailure { DesktopToasts.show("Sign-in didn't finish. Try again.") }
    }
}

/** Butler's look for a window of its own (the tray menu): the theme and app font the main window wears. */
@androidx.compose.runtime.Composable
private fun ButlerThemed(graph: DesktopComponent, content: @androidx.compose.runtime.Composable () -> Unit) {
    val themePrefs = graph.themePrefs()
    val fontPrefs = graph.fontPrefs()
    val theme by themePrefs.theme.collectAsState()
    val custom by themePrefs.custom.collectAsState()
    val appFontKey by fontPrefs.app.collectAsState()
    val addedFonts by fontPrefs.added.collectAsState()
    val appFont = remember(appFontKey, addedFonts) { com.cherry.butler.core.design.fontFamilyOf(fontPrefs, appFontKey) }
    val scale by fontPrefs.appScale.collectAsState()
    ButlerTheme(theme = theme, custom = custom, appFont = appFont, appTextScale = scale) {
        CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides com.cherry.butler.desktop.ui.HoverWash(MaterialTheme.colorScheme.onSurface),
        ) { content() }
    }
}

@androidx.compose.runtime.Composable
private fun ButlerWindow(graph: DesktopComponent, onGround: (Int) -> Unit) {
    val themePrefs = graph.themePrefs()
    val fontPrefs = graph.fontPrefs()
    val textLook = graph.textLook()
    val appLock = graph.appLock()
    val theme by themePrefs.theme.collectAsState()
    val chatStyle by themePrefs.chatStyle.collectAsState()
    val thinkingLook by themePrefs.thinkingLook.collectAsState()
    val thoughtLook by themePrefs.thoughtLook.collectAsState()
    val look by textLook.look.collectAsState()
    val custom by themePrefs.custom.collectAsState()
    val chatFontKey by fontPrefs.chat.collectAsState()
    val appFontKey by fontPrefs.app.collectAsState()
    val appTextScale by fontPrefs.appScale.collectAsState()
    // Keyed on the added fonts too: a face added to the family in use joins it at once.
    val addedFonts by fontPrefs.added.collectAsState()
    val chatFont = remember(chatFontKey, addedFonts) { com.cherry.butler.core.design.fontFamilyOf(fontPrefs, chatFontKey) }
    val appFont = remember(appFontKey, addedFonts) { com.cherry.butler.core.design.fontFamilyOf(fontPrefs, appFontKey) }
    val locked by appLock.locked.collectAsState()
    @Suppress("UNUSED_VARIABLE") val light = theme.isLight(custom)
    ButlerTheme(theme = theme, chatStyle = chatStyle, look = look, custom = custom, appFont = appFont, chatFont = chatFont, appTextScale = appTextScale, thinkingLook = thinkingLook, thoughtLook = thoughtLook) {
        val ground = MaterialTheme.colorScheme.background
        androidx.compose.runtime.LaunchedEffect(ground) { onGround(ground.toArgb()) }
        Box(Modifier.fillMaxSize()) {
            // Butler's own title bar over everything, the sign-in and the lock screen included.
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                com.cherry.butler.desktop.ui.DesktopTitleBar()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = if (locked) Modifier.clearAndSetSemantics { } else Modifier) {
                        CompositionLocalProvider(
                            // Clicks answer with a hover wash, not the phone's ripple.
                            androidx.compose.foundation.LocalIndication provides com.cherry.butler.desktop.ui.HoverWash(MaterialTheme.colorScheme.onSurface),
                            // A mouse and a wide window everywhere, the first start's guide included.
                            com.cherry.butler.ui.components.LocalPointerMotion provides true,
                            LocalLastPlace provides graph.lastPlace(),
                            com.cherry.butler.ui.LocalAppShell provides { com.cherry.butler.desktop.ui.DesktopShell() },
                            com.cherry.butler.feature.chat.LocalEnterSends provides true,
                            com.cherry.butler.ui.LocalDoorFrame provides { form -> com.cherry.butler.desktop.ui.DesktopDoor(form) },
                        ) {
                            AuthGate()
                        }
                    }
                    if (locked) LockScreen(appLock)
                    else com.cherry.butler.feature.settings.UpdatePrompt(graph.updates())
                    // The splash until there is something to show (DesktopStartup), then the hand-over.
                    val session by graph.supabase().auth.sessionStatus.collectAsState()
                    val contentReady by com.cherry.butler.desktop.ui.DesktopStartup.ready.collectAsState()
                    var waited by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    LaunchedEffect(Unit) { delay(4_000); waited = true }
                    val signedOut = session is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated
                    com.cherry.butler.desktop.ui.StartupSplash(ready = contentReady || signedOut || waited)
                }
            }
            ToastLine(Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** The phone's toasts, at the foot of the window for a couple of seconds. */
@androidx.compose.runtime.Composable
private fun ToastLine(modifier: Modifier) {
    val shown by DesktopToasts.current.collectAsState()
    val now = shown ?: return
    LaunchedEffect(now) {
        delay(2_500)
        DesktopToasts.clear(now)
    }
    Surface(
        modifier = modifier.padding(bottom = 28.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(now.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * ButlerApp's image loader: Butler's own client, a real disk cache, a decoder that shrinks
 * smoothly, and a memory cache sized in bytes. Decoded pixels live outside the Java heap, so a
 * share of the heap (25 MB) was the wrong measure: a full-screen grid outran it and scrolling
 * back decoded everything again. 20 MB holds about four screens of four-across cards; past
 * that a picture comes back from the disk cache in about a millisecond, off the UI thread.
 */
private fun installImageLoader(client: () -> okhttp3.OkHttpClient) {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client().newBuilder().build() }))
                add(SmoothDecoder.Factory())
            }
            // Two decodes at a time, six downloads: a card's picture decodes in about a
            // millisecond, so a burst spread over Kotlin's IO pool (up to 64 threads) only
            // competed with the frame being drawn and held more pictures half-made at once.
            .decoderCoroutineContext(kotlinx.coroutines.Dispatchers.Default.limitedParallelism(2))
            .fetcherCoroutineContext(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(6))
            .memoryCache { MemoryCache.Builder().maxSizeBytes(20 * 1024L * 1024).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(DesktopContext.cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
