package com.cherry.butler.core.config

import android.content.Context

class JanitorEndpointPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("butler_endpoint", Context.MODE_PRIVATE)

    fun selected(): JanitorEndpoint = JanitorEndpoint.fromHost(prefs.getString("host", null))

    fun save(endpoint: JanitorEndpoint): Boolean = prefs.edit().putString("host", endpoint.host).commit()
}
