package com.cherry.butler.core.auth

import com.cherry.butler.core.data.LastPlace
import com.cherry.butler.core.data.PersonaRepository
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.SettingsRepository
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.generation.SendPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signing out, done completely. The session goes (and with it the debug build's session
 * mirror), and so does everything this phone kept for the account: the chat mirror, the
 * outbox, the profile and settings held in memory, and the persona choice. Another
 * account signing in next starts from nothing that belonged to this one.
 */
@Singleton
class AccountService @Inject constructor(
    private val auth: AuthRepository,
    private val db: ButlerDatabase,
    private val pipeline: SendPipeline,
    private val profile: ProfileRepository,
    private val settings: SettingsRepository,
    private val personas: PersonaRepository,
    private val lastPlace: LastPlace,
    private val gauge: com.cherry.butler.core.generation.ContextGauge,
    private val openRouter: com.cherry.butler.core.data.OpenRouterOptionsStore,
) {
    /** Lines the user wrote that Janitor has not accepted yet; signing out loses them. */
    suspend fun unsentCount(): Int = db.messageDao().countUnsent()

    suspend fun signOut() {
        pipeline.abandonAll()
        auth.signOut()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        profile.clear()
        settings.clear()
        personas.select(null)
        personas.setDefaultOrigin(null)
        lastPlace.clear()
        gauge.clear()
        openRouter.clear()
    }
}
