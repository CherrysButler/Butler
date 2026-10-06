package com.cherry.butler.core.di

import kotlinx.coroutines.runBlocking
import com.cherry.butler.core.auth.SessionKeeper
import android.content.Context
import androidx.room.Room
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.network.ApiCall
import com.cherry.butler.core.network.AuthInterceptor
import com.cherry.butler.core.network.ButlerUserAgent
import com.cherry.butler.core.network.DebugProxy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** Marks a call already repeated after a 401; not sent anywhere that reads it. */
    private const val RETRIED = "X-Butler-Retried"

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
        keeper: SessionKeeper,
    ): OkHttpClient {
        DebugProxy.load(context)
        return OkHttpClient.Builder()
            // Debug builds only: Settings › Debug can route everything through Burp or mitmproxy.
            .proxySelector(DebugProxy)
            .apply { if (com.cherry.butler.BuildConfig.DEBUG) sslSocketFactory(DebugProxy.socketFactory, DebugProxy.trustManager) }
            .addInterceptor(ButlerUserAgent)
            .addInterceptor(authInterceptor)
            // A 401 from Janitor: renew the session once and send the call again. Never for
            // another host (the user's proxy shares this builder), and never twice.
            .authenticator { _, response ->
                val request = response.request
                if (!AuthInterceptor.isJanitorApi(request.url.host)) return@authenticator null
                if (request.header(RETRIED) != null) return@authenticator null
                val stale = request.header("Authorization")?.removePrefix("Bearer ")
                val fresh = runBlocking { keeper.refreshAfter(stale) } ?: return@authenticator null
                request.newBuilder().header("Authorization", "Bearer $fresh").header(RETRIED, "1").build()
            }
            // Retries live in ApiCall, which knows which failures are worth repeating.
            // OkHttp's own retry is blind to that, so it stays off.
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
        // No destructive fallback: from schema v2 the database holds the outbox, which is
        // user-authored text. Every version bump ships a migration (see ButlerDatabase).
        Room.databaseBuilder(context, ButlerDatabase::class.java, ButlerDatabase.NAME)
            .build()
}
