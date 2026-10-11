package com.cherry.butler.desktop

import com.cherry.butler.ui.navigation.TopLevelDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Development only (desktop-dev; removed before release): `janitor://butler-motion` steps the
 * window through its screen changes (sections, a chat and back, the character panel) and logs
 * every frame of each to files/frames.log: how soon the first frame came, how many frames the
 * display was kept waiting, and the slowest frame.
 */
object DesktopMotionBench {
    @Volatile private var running = false

    fun isMotion(link: String) = link.startsWith("janitor://butler-motion")

    suspend fun run() {
        if (running) return
        running = true
        try {
            val win = DesktopTour.window ?: return
            val c = DesktopTour.controls ?: return DesktopToasts.show("Sign in first")
            val refresh = win.graphicsConfiguration.device.displayMode.refreshRate.coerceAtLeast(30)
            val budget = 1_000_000_000L / refresh
            FrameLog.attach(win, refresh, force = true)
            val (character, chat) = withContext(Dispatchers.IO) { DesktopTour.ids() }
            onUi { c.character(null); c.section(TopLevelDestination.Browse); win.toFront() }
            delay(3_000)

            val starts = LongArray(2048)
            val builds = LongArray(2048)
            var n = 0
            FrameLog.probe = { t0, b -> if (n < starts.size) { starts[n] = t0; builds[n] = b; n++ } }

            suspend fun step(name: String, action: () -> Unit) {
                n = 0
                val at = System.nanoTime()
                onUi(action)
                delay(1_200)
                val count = n
                if (count == 0) { FrameLog.line("[motion] %-22s no frames".format(name)); return }
                var dropped = 0L
                var worstGap = starts[0] - at
                for (i in 1 until count) {
                    val gap = starts[i] - starts[i - 1]
                    if (gap < 150_000_000L) {
                        if (gap > budget * 3 / 2) dropped += gap / budget - 1
                        worstGap = maxOf(worstGap, gap)
                    }
                }
                val worstBuild = builds.copyOf(count).max()
                val firstBuild = builds[0]
                fun ms(ns: Long) = "%5.1f".format(ns / 1e6)
                FrameLog.line(
                    "[motion] %-22s frames %3d  first after %s  first build %s  worst build %s  worst gap %s  dropped %3d".format(
                        name, count, ms(starts[0] - at), ms(firstBuild), ms(worstBuild), ms(worstGap), dropped,
                    ),
                )
            }

            FrameLog.line("--- motion ${java.time.LocalDateTime.now()} ${refresh} Hz, window ${win.width}x${win.height}")
            repeat(2) { round ->
                FrameLog.line("--- round ${round + 1}")
                for (dest in listOf(TopLevelDestination.Chats, TopLevelDestination.Notifications, TopLevelDestination.Profile, TopLevelDestination.Settings, TopLevelDestination.Browse)) {
                    step("section ${dest.name.lowercase()}") { c.section(dest) }
                }
                character?.let { id ->
                    step("panel open") { c.character(id) }
                    step("panel close") { c.character(null) }
                }
                chat?.let { (id, _) ->
                    step("chat open") { c.chat(id) }
                    step("chat back") { c.back() }
                }
            }
            FrameLog.line("--- motion end")
            DesktopToasts.show("Motion probe saved")
        } finally {
            FrameLog.probe = null
            running = false
        }
    }

    private suspend fun <T> onUi(block: () -> T): T = withContext(Dispatchers.Main) { block() }
}
