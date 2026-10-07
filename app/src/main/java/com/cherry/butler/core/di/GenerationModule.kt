package com.cherry.butler.core.di

import com.cherry.butler.core.auth.AuthRepository
import com.cherry.butler.core.generation.GenerationTransport
import com.cherry.butler.core.generation.HttpGenerationTransport
import com.cherry.butler.core.generation.JllmTransport
import com.cherry.butler.core.generation.RoutingTransport
import com.cherry.butler.core.network.ApiCall
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GenerationModule {

    /** Proxy generations over HTTP, JLLM over its WebSocket; the envelope picks per send. */
    @Provides
    @Singleton
    fun provideGenerationTransport(
        client: OkHttpClient,
        apiCall: ApiCall,
        json: Json,
        auth: Provider<AuthRepository>,
        agent: com.cherry.butler.core.generation.AgentLoop,
    ): GenerationTransport = RoutingTransport(
        proxyPath = HttpGenerationTransport(client, apiCall, json, agent),
        jllm = JllmTransport(client, apiCall, json) { auth.get().currentAccessToken },
    )
}
