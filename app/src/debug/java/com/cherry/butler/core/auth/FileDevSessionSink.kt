package com.cherry.butler.core.auth

import com.cherry.butler.BuildConfig
import android.content.Context
import android.util.Log
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Singleton

/**
 * Debug-only: mirrors the Supabase session to a plaintext file so the host machine
 * can read it over `adb`.
 *
 * Why this exists — it removes the slowest loop in developing Butler. Without it,
 * confirming an API shape means edit → build → install → tap through the UI → read
 * logcat. With it, the session is one `adb` command away, so authenticated endpoints
 * can be probed directly with curl from the dev machine (the same way
 * `GET /characters` was recovered) and the app only gets rebuilt once the shape is
 * already known. It also lets a session survive a reinstall: pull the file before,
 * push it back after, and [seed] restores it on next launch.
 *
 * Read it with:
 * ```
 * adb exec-out run-as com.cherry.butler.debug cat files/dev-session.json
 * ```
 *
 * **This writes an unencrypted bearer token to disk.** That is acceptable only
 * because this class is in `src/debug/` and is never compiled into a release APK
 * (release gets [NoOpDevSessionSink]), and because even in a debug build it does
 * nothing unless `butler.devMirror=true` is set in the dev machine's local.properties
 * ([BuildConfig.DEV_MIRROR]). The debug APKs CI publishes are built without it. Do not move it to `src/main/`, do not
 * "temporarily" reference it from shared code, and note that the access token it
 * holds is good for ~1 hour.
 *
 * Caveat when using the token from the host: GoTrue **rotates refresh tokens**.
 * Calling `grant_type=refresh_token` off-device invalidates the copy the app holds
 * and silently signs the phone out. Use the `access_token` and re-pull when it
 * expires; leave refreshing to the app.
 */
@Singleton
class FileDevSessionSink(
    private val context: Context,
    private val json: Json,
) : DevSessionSink {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override suspend fun onSaved(session: UserSession) = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEV_MIRROR) return@withContext
        runCatching { file.writeText(json.encodeToString(session)) }
            .onSuccess { Log.i(TAG, "session mirrored -> ${file.absolutePath}") }
            .onFailure { Log.w(TAG, "failed to mirror session", it) }
        Unit
    }

    override suspend fun onCleared() = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) file.delete() }
            .onFailure { Log.w(TAG, "failed to clear mirrored session", it) }
        Unit
    }

    override suspend fun seed(): UserSession? = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEV_MIRROR || !file.exists()) return@withContext null
        runCatching { json.decodeFromString<UserSession>(file.readText()) }
            .onSuccess { Log.i(TAG, "seeded session from ${file.absolutePath}") }
            .onFailure { Log.w(TAG, "mirrored session unreadable; ignoring", it) }
            .getOrNull()
    }

    private companion object {
        const val FILE_NAME = "dev-session.json"
        const val TAG = "ButlerDevSession"
    }
}
