package com.cherry.butler.core.network

import com.cherry.butler.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Every call says it is Butler: `Butler/<version>`. Janitor's firewall turned away the website's
 * generation route with OkHttp's own `okhttp/4.12.0` and let the same request through as
 * `Butler/0.2.3` (2026-10-07). Clients that drop Butler's interceptors on purpose (the user's
 * proxy, storage uploads) add this one back, so no call goes out as OkHttp.
 */
object ButlerUserAgent : Interceptor {
    const val VALUE = "Butler/" + BuildConfig.VERSION_NAME

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", VALUE).build())
}
