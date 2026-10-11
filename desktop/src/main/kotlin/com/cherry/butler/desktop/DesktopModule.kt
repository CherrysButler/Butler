package com.cherry.butler.desktop

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.cherry.butler.core.auth.AuthRepository
import com.cherry.butler.core.auth.DevSessionSink
import com.cherry.butler.core.auth.NoOpDevSessionSink
import com.cherry.butler.core.auth.SecureSessionStore
import com.cherry.butler.core.auth.SessionKeeper
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.generation.AgentLoop
import com.cherry.butler.core.generation.GenerationTransport
import com.cherry.butler.core.generation.HttpGenerationTransport
import com.cherry.butler.core.generation.JllmTransport
import com.cherry.butler.core.generation.RoutingTransport
import com.cherry.butler.core.network.ApiCall
import com.cherry.butler.core.network.AuthInterceptor
import com.cherry.butler.core.network.ButlerUserAgent
import com.cherry.butler.core.network.CloudflareGate
import com.cherry.butler.core.network.DebugProxy
import com.cherry.butler.core.network.JanitorCookies
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Android's DataModule, GenerationModule and SupabaseModule in one, provided the same way.
 * Keep each provider in step with its Android twin in app/…/core/di.
 */
@Module
object DesktopModule {

    private const val RETRIED = "X-Butler-Retried"

    @Provides
    @ApplicationContext
    fun provideContext(): Context = DesktopContext

    @Provides
    fun provideDevSessionSink(): DevSessionSink = NoOpDevSessionSink()

    // DataModule

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
        keeper: SessionKeeper,
    ): OkHttpClient {
        DebugProxy.load(context)
        ButlerUserAgent.load(context)
        CloudflareGate.init(context)
        JanitorCookies.init(context)
        return OkHttpClient.Builder()
            .proxySelector(DebugProxy)
            .apply { if (com.cherry.butler.BuildConfig.DEBUG) sslSocketFactory(DebugProxy.socketFactory, DebugProxy.trustManager) }
            .addInterceptor(ButlerUserAgent)
            .cookieJar(JanitorCookies)
            .addInterceptor(authInterceptor)
            .authenticator { _, response ->
                val request = response.request
                if (!AuthInterceptor.isJanitorApi(request.url.host)) return@authenticator null
                if (request.header(RETRIED) != null) return@authenticator null
                val stale = request.header("Authorization")?.removePrefix("Bearer ")
                val fresh = runBlocking { keeper.refreshAfter(stale) } ?: return@authenticator null
                request.newBuilder().header("Authorization", "Bearer $fresh").header(RETRIED, "1").build()
            }
            .retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
            .also { DebugProxy.track(it.connectionPool) }
    }

    @Provides
    @Singleton
    fun provideApiCall(client: OkHttpClient, json: Json): ApiCall = ApiCall(client, json)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ButlerDatabase =
        Room.databaseBuilder<ButlerDatabase>(name = File(context.filesDir, ButlerDatabase.NAME).path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    // GenerationModule

    @Provides
    @Singleton
    fun provideGenerationTransport(
        client: OkHttpClient,
        apiCall: ApiCall,
        json: Json,
        auth: Provider<AuthRepository>,
        agent: AgentLoop,
    ): GenerationTransport = RoutingTransport(
        proxyPath = HttpGenerationTransport(client, apiCall, json, agent),
        jllm = JllmTransport(client, apiCall, json) { auth.get().currentAccessToken },
    )

    // SupabaseModule

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
                enableLifecycleCallbacks = false
                scheme = JanitorConfig.OAUTH_SCHEME
                host = JanitorConfig.OAUTH_HOST
                flowType = FlowType.PKCE
            }
        }

    @Provides
    @Singleton
    fun provideAuth(client: SupabaseClient): Auth = client.auth
}
