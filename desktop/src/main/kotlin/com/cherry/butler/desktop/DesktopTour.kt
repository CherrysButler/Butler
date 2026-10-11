package com.cherry.butler.desktop

import androidx.room.useReaderConnection
import com.cherry.butler.ui.navigation.TopLevelDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO

/**
 * Development only (desktop-dev; removed before release): `janitor://butler-tour` walks the
 * window through its screens and saves a capture of each to files/tour, so the layout can be
 * reviewed without anyone clicking through it. Nothing leaves the PC.
 */
object DesktopTour {
    /** What the shell lets the tour do; set while the shell is on screen. */
    class Controls(
        val section: (TopLevelDestination) -> Unit,
        val character: (String?) -> Unit,
        val chat: (Long) -> Unit,
        val back: () -> Unit,
    )

    @Volatile var controls: Controls? = null
    @Volatile var window: java.awt.Window? = null
    @Volatile private var running = false

    fun isTour(link: String) = link.startsWith("janitor://butler-tour")

    suspend fun run() {
        if (running) return
        running = true
        try {
            val win = window ?: return
            val c = controls ?: return DesktopToasts.show("Sign in first")
            val dir = File(DesktopContext.filesDir, "tour").apply { deleteRecursively(); mkdirs() }
            val (character, chat) = withContext(Dispatchers.IO) { ids() }
            val restore = win.bounds
            val wasMax = (win as? java.awt.Frame)?.extendedState ?: 0

            suspend fun size(w: Int, h: Int) = onUi {
                (win as? java.awt.Frame)?.extendedState = java.awt.Frame.NORMAL
                win.setBounds(restore.x.coerceAtLeast(0), restore.y.coerceAtLeast(0), w, h)
                win.toFront()
            }
            suspend fun shot(name: String, settle: Long = 2200) {
                delay(settle)
                onUi { win.toFront() }
                delay(250)
                val b = onUi { Rectangle(win.locationOnScreen, win.size) }
                val scale = win.graphicsConfiguration.defaultTransform.scaleX
                val image = Robot().createScreenCapture(
                    Rectangle((b.x * scale).toInt(), (b.y * scale).toInt(), (b.width * scale).toInt(), (b.height * scale).toInt()),
                )
                withContext(Dispatchers.IO) { ImageIO.write(image, "png", File(dir, "$name.png")) }
            }

            for ((w, h, tag) in listOf(Triple(1440, 900, "wide"), Triple(900, 820, "narrow"))) {
                size(w, h)
                onUi { c.character(null); c.section(TopLevelDestination.Browse) }
                shot("$tag-01-browse", 3500)
                character?.let { onUi { c.character(it) }; shot("$tag-02-character") }
                onUi { c.character(null); c.section(TopLevelDestination.Chats) }
                shot("$tag-03-chats")
                chat?.let { (id, who) ->
                    onUi { c.chat(id) }
                    shot("$tag-04-chat", 3000)
                    onUi { c.character(who) }
                    shot("$tag-05-chat-character")
                    onUi { c.character(null); c.back() }
                }
                for ((i, dest) in listOf(TopLevelDestination.Notifications, TopLevelDestination.Profile, TopLevelDestination.Settings).withIndex()) {
                    onUi { c.section(dest) }
                    shot("$tag-0${6 + i}-${dest.name.lowercase()}")
                }
            }
            onUi {
                win.bounds = restore
                (win as? java.awt.Frame)?.extendedState = wasMax
                c.section(TopLevelDestination.Browse)
            }
            DesktopToasts.show("Tour saved")
        } finally {
            running = false
        }
    }

    private suspend fun <T> onUi(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    /** A character from Browse and the latest chat with its character, from the local mirror. */
    internal suspend fun ids(): Pair<String?, Pair<Long, String>?> {
        val db = DesktopGraph.component.database()
        return db.useReaderConnection { tx ->
            val character = tx.usePrepared("SELECT id FROM characters ORDER BY cachedAt DESC, position ASC LIMIT 1") { st ->
                if (st.step()) st.getText(0) else null
            }
            val chat = tx.usePrepared("SELECT id, characterId FROM chats ORDER BY lastMessageAt DESC LIMIT 1") { st ->
                if (st.step()) st.getLong(0) to st.getText(1) else null
            }
            character to chat
        }
    }
}
