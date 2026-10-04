package com.cherry.butler.core.auth

import io.github.jan.supabase.auth.user.UserSession
import javax.inject.Singleton

/**
 * Release builds never mirror the session anywhere. This is the only
 * [DevSessionSink] on the release compile path — the debug file-writing
 * implementation does not exist in this variant's source set.
 */
@Singleton
class NoOpDevSessionSink : DevSessionSink {
    override suspend fun onSaved(session: UserSession) = Unit
    override suspend fun onCleared() = Unit
    override suspend fun seed(): UserSession? = null
}
