package com.cherry.butler.core.di

import android.content.Context
import com.cherry.butler.core.auth.DevSessionSink
import com.cherry.butler.core.auth.SecureSessionStore
import com.cherry.butler.core.config.JanitorConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context,
        json: Json,
        devSessionSink: DevSessionSink,
    ): SessionManager = SecureSessionStore(context, json, devSessionSink)

    @Provides
    @Singleton
    fun provideSupabaseClient(sessionManager: SessionManager): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = JanitorConfig.SUPABASE_URL,
            supabaseKey = JanitorConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                this.sessionManager = sessionManager
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                // Off: on leaving the screen supabase-kt resets the session to "Initializing",
                // so currentSessionOrNull() is null and a reply finishing in the background
                // goes out unsigned (401). Butler keeps the session, and its refresh, running.
                enableLifecycleCallbacks = false
                // Reuse Janitor's allow-listed deep-link scheme for the OAuth redirect.
                scheme = JanitorConfig.OAUTH_SCHEME
                host = JanitorConfig.OAUTH_HOST
                // PKCE is the correct mobile flow (no tokens in the redirect URL).
                flowType = FlowType.PKCE
            }
        }

    @Provides
    @Singleton
    fun provideAuth(client: SupabaseClient): Auth = client.auth
}
