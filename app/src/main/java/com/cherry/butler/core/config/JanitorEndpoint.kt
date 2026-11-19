package com.cherry.butler.core.config

enum class JanitorEndpoint(val host: String, val authUrl: String, val mediaHost: String) {
    JanitorAI("janitorai.com", "https://auth.janitorai.com", "ella.janitorai.com"),
    Jani("jani.com", "https://mcmzxtzommpnxkynddbo.supabase.co", "janitorsunite.b-cdn.net");

    val origin: String get() = "https://$host"
    val mediaUrl: String get() = "https://$mediaHost"

    /** Keep files written by earlier versions under the original endpoint. */
    fun scopedName(legacy: String): String = if (this == JanitorAI) legacy else "$legacy.$host"

    companion object {
        fun fromHost(host: String?): JanitorEndpoint = entries.firstOrNull { it.host == host } ?: JanitorAI

        fun isSiteHost(host: String): Boolean = entries.any { host == it.host || host.endsWith(".${it.host}") }
    }
}
