@file:JvmName("PrefetchExecutor_skikoKt")
@file:OptIn(ExperimentalFoundationApi::class)

package androidx.compose.foundation.lazy.layout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.cherry.butler.desktop.FramePacer
import java.awt.EventQueue
import javax.swing.Timer

/**
 * Compose 1.7's desktop prefetch scheduler is empty (schedulePrefetch does nothing), so a
 * lazy list builds each item only in the frame it scrolls into view: a new row of Browse
 * cards cost one frame 10-17 ms at 180 Hz. This one replaces it (same class, Butler's jar is
 * first on the classpath) and works as Android's does: queued items are composed and
 * measured on the UI thread in the time a frame leaves over, stopping short of the next
 * frame, so the next frame is never pushed back.
 */
@Composable
internal fun rememberDefaultPrefetchScheduler(): PrefetchScheduler = remember { IdlePrefetchScheduler() }

private class IdlePrefetchScheduler : PrefetchScheduler, PrefetchRequestScope {
    private val queue = ArrayDeque<PrefetchRequest>()
    private var posted = false
    private var deadline = 0L
    /** Waits a moment instead of spinning on the event queue while a frame is due. */
    private val later = Timer(1) { run() }.apply { isRepeats = false }
    /** The request at the head and how many gaps it has waited without finishing. */
    private var waiting: PrefetchRequest? = null
    private var waitedGaps = 0

    override fun availableTimeNanos(): Long = (deadline - System.nanoTime()).coerceAtLeast(0L)

    override fun schedulePrefetch(prefetchRequest: PrefetchRequest) {
        queue.addLast(prefetchRequest)
        post()
    }

    private fun post() {
        if (posted) return
        posted = true
        EventQueue.invokeLater(::run)
    }

    private fun run() {
        posted = false
        if (queue.isEmpty()) return
        deadline = FramePacer.idleUntil()
        if (queue.first() === waiting && waitedGaps >= STARVED_GAPS) {
            // A card heavier than any gap between frames would never be built ahead: give it
            // one slice of its own. It costs what building it in the frame it appears would.
            deadline = System.nanoTime() + STARVED_SLICE_NANOS
            waitedGaps = 0
        } else if (availableTimeNanos() < MIN_SLICE_NANOS) {
            // Too close to the next frame: try again once it has gone.
            later.restart()
            return
        }
        while (queue.isNotEmpty() && availableTimeNanos() > 0) {
            val request = queue.first()
            // "More" means the request wants more time than is left. It may have done nothing
            // at all, so asking again in the same gap only spins: wait for the next one.
            if (with(request) { this@IdlePrefetchScheduler.execute() }) break
            queue.removeFirst()
        }
        val head = queue.firstOrNull()
        if (head != null && head === waiting) waitedGaps++ else { waiting = head; waitedGaps = 0 }
        if (head != null) later.restart()
    }

    private companion object {
        const val MIN_SLICE_NANOS = 400_000L
        const val STARVED_GAPS = 3
        const val STARVED_SLICE_NANOS = 8_000_000L
    }
}
