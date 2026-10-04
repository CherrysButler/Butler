package com.cherry.butler.core.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the Janitor session usable, whatever the phone does to Butler in between.
 *
 * supabase-kt refreshes on a timer, and a timer is not enough on a phone: a frozen app
 * (Transsion's Hiber) or a sleeping CPU wakes long after the token died, and while a
 * refresh is failing for want of network supabase-kt reports no session at all, so every
 * call would go out unsigned and quietly come back as an anonymous browse. Here:
 *
 * - the last good session is kept through a failing refresh, so calls stay signed;
 * - a token that is about to expire is refreshed before it is sent;
 * - a 401 refreshes once and the call is repeated ([refreshAfter]);
 * - one refresh at a time. A refresh token used twice can make the auth server revoke the
 *   whole session, which looks to the user like being thrown out for nothing.
 *
 * Only a refresh token the server refuses ends the session; then [ended] is set so the
 * sign-in screen can say why it is there.
 */
@Singleton
class SessionKeeper @Inject constructor(private val supabase: SupabaseClient) {
    private val auth get() = supabase.auth
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var last: UserSession? = null
    @Volatile private var signingOut = false

    /** Called just before the user's own sign-out, so it isn't mistaken for a lost session. */
    fun markSigningOut() {
        signingOut = true
    }

    private val _ended = MutableStateFlow(false)
    /** The session was lost, not signed out of: the sign-in screen says so. */
    val ended: StateFlow<Boolean> = _ended.asStateFlow()

    init {
        scope.launch {
            auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> { last = status.session; _ended.value = false }
                    // supabase-kt reports every clear as a sign-out, so only Butler knows
                    // which ones the user asked for.
                    is SessionStatus.NotAuthenticated -> {
                        if (last != null && !signingOut) _ended.value = true
                        signingOut = false
                        last = null
                    }
                    else -> Unit
                }
            }
        }
    }

    /** The session to sign with: the live one, or the last good one while a refresh is failing. */
    fun session(): UserSession? = auth.currentSessionOrNull() ?: last

    /**
     * A token to send now, refreshed first if it has under [MARGIN] left. Blocks; for
     * OkHttp's threads. Never null just because a refresh failed: the old token is still
     * worth a try, and a 401 comes back to [refreshAfter].
     */
    fun tokenForRequest(): String? {
        val s = session() ?: return null
        if (s.expiresAt - Clock.System.now() > MARGIN) return s.accessToken
        return runBlocking { refreshAfter(s.accessToken) } ?: s.accessToken
    }

    /**
     * After [stale] was refused or ran out: refresh, unless another call already has, and
     * return the token to retry with. Null when there is nothing better to retry with.
     */
    suspend fun refreshAfter(stale: String?): String? = lock.withLock {
        val current = session() ?: return@withLock null
        val stillGood = current.expiresAt - Clock.System.now() > MARGIN
        if (current.accessToken != stale && stillGood) return@withLock current.accessToken
        try {
            val fresh = auth.refreshSession(current.refreshToken)
            auth.importSession(fresh, source = SessionSource.Refresh(current))
            last = fresh
            fresh.accessToken
        } catch (e: RestException) {
            android.util.Log.w(TAG, "refresh refused (${e.statusCode})")
            // 4xx: the refresh token is dead (revoked, used up, expired). Nothing to retry.
            if (e.statusCode in 400..499) auth.clearSession()
            null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w(TAG, "refresh failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /** On coming back to the screen: a session that ran out while away is renewed now. */
    fun onForeground() {
        scope.launch {
            val s = session() ?: return@launch
            if (s.expiresAt - Clock.System.now() <= MARGIN) refreshAfter(s.accessToken)
        }
    }

    fun acknowledgeEnded() {
        _ended.value = false
    }

    private companion object {
        const val TAG = "SessionKeeper"
        val MARGIN = 90.seconds
    }
}
