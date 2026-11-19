package com.cherry.butler.core.config

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class JanitorEndpointPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("butler_endpoint", Context.MODE_PRIVATE)

    fun selected(): JanitorEndpoint = JanitorEndpoint.fromHost(prefs.getString("host", null))

    suspend fun switchTo(endpoint: JanitorEndpoint, restart: () -> Boolean): Boolean =
        // Once saved, finish the switch even if the calling screen is disposed.
        withContext(NonCancellable) {
            if (save(endpoint) && restart()) return@withContext true
            save(JanitorConfig.endpoint)
            false
        }

    private suspend fun save(endpoint: JanitorEndpoint): Boolean = withContext(Dispatchers.IO) {
        prefs.edit().putString("host", endpoint.host).commit()
    }
}
