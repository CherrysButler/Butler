package com.cherry.butler.core.network

import com.cherry.butler.core.auth.AuthRepository
import com.cherry.butler.core.config.JanitorConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Attaches the two headers every Janitor backend call needs: the user's bearer token
 * and the public Supabase anon key.
 *
 * [AuthRepository] arrives as a [Provider] to break the dependency cycle — the Supabase
 * client owns the session, and the OkHttp client that Supabase's Ktor engine sits on
 * would otherwise need the session to be constructed first.
 *
 * Anonymous calls are legal on several endpoints (`GET /characters` works with no token
 * at all), so a missing token is not an error here — we simply omit the header and let
 * the server decide. That keeps the character browser usable before sign-in.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authRepository: Provider<AuthRepository>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Janitor's own API only. Image CDNs and other people's hosts (Google avatars among
        // them) share this client through Coil and must never see the user's token.
        if (!isJanitorApi(request.url.host)) return chain.proceed(request)
        val builder = request.newBuilder()
            .header("apikey", JanitorConfig.SUPABASE_ANON_KEY)
            .header("Accept", "application/json")

        authRepository.get().currentAccessToken?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }

    companion object {
        fun isJanitorApi(host: String): Boolean = host == JANITOR_HOST

        private const val JANITOR_HOST = "janitorai.com"
    }
}
