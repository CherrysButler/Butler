package com.cherry.butler.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a screen with unsaved changes stop the user leaving it by the bottom bar. The bar
 * asks [leave]; a screen that has registered with [GuardLeaving] gets the move instead and
 * runs it once the user has saved or discarded. Back and the back arrow are guarded where
 * they are handled, with a BackHandler.
 */
class LeaveGuard {
    private var check: ((proceed: () -> Unit) -> Unit)? = null

    /** Runs [go] now, or hands it to the screen that has changes waiting. */
    fun leave(go: () -> Unit) {
        check?.invoke(go) ?: go()
    }

    internal fun register(block: (proceed: () -> Unit) -> Unit): () -> Unit {
        check = block
        return { if (check === block) check = null }
    }
}

val LocalLeaveGuard = staticCompositionLocalOf { LeaveGuard() }

/** While [active], leaving through the bottom bar calls [onAttempt] with the move to make. */
@Composable
fun GuardLeaving(active: Boolean, onAttempt: (proceed: () -> Unit) -> Unit) {
    val guard = LocalLeaveGuard.current
    val attempt = rememberUpdatedState(onAttempt)
    DisposableEffect(active, guard) {
        val release = if (active) guard.register { proceed -> attempt.value(proceed) } else ({})
        onDispose { release() }
    }
}
