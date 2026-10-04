package com.cherry.butler.core.auth

import io.github.jan.supabase.auth.user.UserSession

/**
 * A seam that exists purely so debug builds can mirror the session to a plain file
 * that `adb` can read — see the debug implementation for why that's worth doing.
 *
 * The real implementation lives **only** in `src/debug/`. The release variant
 * compiles [NoOpDevSessionSink] from `src/release/` instead, so the file-writing
 * code is not merely disabled in a release APK, it is never compiled into one.
 * Keep it that way: this is the one place Butler deliberately handles a token in
 * plaintext, and the guarantee in TRUST.md ("credentials encrypted at rest") holds
 * for shipped builds precisely because this seam is variant-scoped.
 */
interface DevSessionSink {

    /** Called after a session is persisted to encrypted storage. */
    suspend fun onSaved(session: UserSession)

    /** Called after the session is cleared (sign-out). */
    suspend fun onCleared()

    /**
     * Last-resort restore when encrypted storage has no session — lets a session
     * pushed back via `adb` survive a reinstall. Returns null in release builds.
     */
    suspend fun seed(): UserSession?
}
