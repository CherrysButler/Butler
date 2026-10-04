package com.cherry.butler.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the Supabase session encrypted at rest via the Android Keystore
 * (AES-256-GCM). Tokens never touch plaintext storage and never leave the
 * device except back to Janitor's own auth endpoint.
 *
 * Implements supabase-kt's [SessionManager] so the Auth plugin loads/refreshes
 * transparently on top of it.
 */
class SecureSessionStore(
    context: Context,
    private val json: Json,
    private val devSessionSink: DevSessionSink,
) : SessionManager {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun saveSession(session: UserSession) {
        prefs.edit().putString(KEY_SESSION, json.encodeToString(session)).apply()
        devSessionSink.onSaved(session)
    }

    /**
     * Encrypted storage is the source of truth. Only when it holds nothing do we
     * fall back to [DevSessionSink.seed] — which is a no-op in release builds, and
     * in debug lets a session pushed back over `adb` survive a reinstall. A seeded
     * session is written straight back so the normal path owns it from then on.
     */
    override suspend fun loadSession(): UserSession? {
        val raw = prefs.getString(KEY_SESSION, null)
            ?: return devSessionSink.seed()?.also { saveSession(it) }
        return runCatching { json.decodeFromString<UserSession>(raw) }.getOrNull()
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(KEY_SESSION).apply()
        devSessionSink.onCleared()
    }

    private companion object {
        const val PREFS_FILE = "butler_session"
        const val KEY_SESSION = "supabase_session"
    }
}
