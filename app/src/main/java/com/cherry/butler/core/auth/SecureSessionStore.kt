package com.cherry.butler.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cherry.butler.core.config.JanitorEndpoint
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    devSessionSink: DevSessionSink,
    endpoint: JanitorEndpoint,
) : SessionManager, CodeVerifierCache {

    // Existing adb session backups belong to the original endpoint.
    private val legacySink = devSessionSink.takeIf { endpoint == JanitorEndpoint.JanitorAI }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            endpoint.scopedName(PREFS_FILE),
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun saveSession(session: UserSession) {
        persist { putString(KEY_SESSION, json.encodeToString(session)) }
        legacySink?.onSaved(session)
    }

    /**
     * Encrypted storage is the source of truth. Only when it holds nothing do we
     * fall back to [DevSessionSink.seed] — which is a no-op in release builds, and
     * in debug lets a session pushed back over `adb` survive a reinstall. A seeded
     * session is written straight back so the normal path owns it from then on.
     */
    override suspend fun loadSession(): UserSession? {
        val raw = prefs.getString(KEY_SESSION, null)
            ?: return legacySink?.seed()?.also { saveSession(it) }
        return runCatching { json.decodeFromString<UserSession>(raw) }.getOrNull()
    }

    override suspend fun deleteSession() {
        persist { remove(KEY_SESSION).remove(KEY_CODE_VERIFIER) }
        legacySink?.onCleared()
    }

    override suspend fun saveCodeVerifier(codeVerifier: String) {
        persist { putString(KEY_CODE_VERIFIER, codeVerifier) }
    }

    override suspend fun loadCodeVerifier(): String? = prefs.getString(KEY_CODE_VERIFIER, null)

    override suspend fun deleteCodeVerifier() {
        persist { remove(KEY_CODE_VERIFIER) }
    }

    private suspend fun persist(change: SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) {
            check(prefs.edit().apply(change).commit())
        }
    }

    private companion object {
        const val KEY_CODE_VERIFIER = "pkce_code_verifier"
        const val PREFS_FILE = "butler_session"
        const val KEY_SESSION = "supabase_session"
    }
}
