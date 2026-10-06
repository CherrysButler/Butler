package com.cherry.butler.core.config

/**
 * Janitor backend endpoints and the *public* client keys the app authenticates with.
 *
 * The Supabase anon key and Turnstile site key are client-side public values by
 * design (RLS/site-key protected) — the same values every official client ships.
 * There are no private secrets here, and Butler has no backend of its own: the
 * device talks directly to Janitor's servers.
 */
object JanitorConfig {
    // Supabase (auth, realtime, storage, PostgREST)
    const val SUPABASE_URL = "https://auth.janitorai.com"
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1jbXp4dHpvbW1wbnhreW5kZGJvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjgzNzA3NDAsImV4cCI6MjA0Mzk0Njc0MH0." +
            "UfRPni4ga9Lmin8j0JjV5ouuK9bXp8tsqPJ8pMTDDAI"

    // Backend REST + LLM services
    const val BACKEND_BASE = "https://janitorai.com/mb"
    const val LLM_BASE = "https://janitorai.com/mobile"
    /**
     * The website's generation route. Unlike [LLM_BASE] (where Janitor calls the proxy itself
     * and relays its stream), here Janitor only assembles the prompt and hands back the
     * OpenAI payload; the device then sends it to the user's proxy (captured 2026-09-23, six
     * of six calls; docs/JANITOR_API.md §18.1). That is what lets Butler add OpenRouter's `provider`
     * or a preset model before sending.
     */
    const val WEB_LLM_BASE = "https://janitorai.com"

    /** The website build's `x-app-version`, sent with generation calls as the website does. */
    const val WEB_APP_VERSION = "10.0.0.116"
    const val NOTIFS_BASE = "https://janitorai.com/notifs/mobile"

    // Media CDN (the only non-Janitor-first-party host we ever contact is Supabase above)
    const val MEDIA_BASE = "https://ella.janitorai.com"
    const val BOT_AVATARS = "$MEDIA_BASE/bot-avatars"
    const val MEDIA_APPROVED = "$MEDIA_BASE/media-approved"

    /**
     * Character avatars arrive as a **bare filename** (`85f5b296-....jpeg`), not a URL —
     * verified 2026-09-23. Rendering the raw value loads nothing and fails silently as a
     * blank card, so always resolve through here.
     */
    fun avatarUrl(fileName: String?): String? =
        fileName?.takeIf { it.isNotBlank() }?.let { "$BOT_AVATARS/$it" }

    /** Persona avatars are bare file names under `/avatars` (verified 2026-10-03). */
    fun personaAvatarUrl(fileName: String?): String? =
        fileName?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else "$MEDIA_BASE/avatars/$it" }

    // Cloudflare Turnstile site key for the auth challenge (public site key).
    const val TURNSTILE_SITE_KEY = "0x4AAAAAAAMttfE31t8DPXZ8"

    // OAuth deep-link scheme/host. Must match the intent-filter in AndroidManifest.xml
    // and the Auth plugin config in SupabaseModule — all three read from here.
    const val OAUTH_SCHEME = "janitor"
    const val OAUTH_HOST = "auth"

    /**
     * The redirect every OAuth provider comes back through.
     *
     * Butler doesn't own Janitor's Supabase project, so GoTrue only honours a
     * `redirect_to` that is on *their* allow-list; anything else is discarded and the
     * user lands on janitorai.com logged into the website instead of back in the app.
     *
     * Verified against the official app: it declares exactly two deep links —
     * `janitor://age-verification-complete` and `janitor://auth/discord`. There is no
     * `janitor://auth/google`, and the allow-list is evidently exact URLs rather than a
     * scheme-wide wildcard, which is why the Google-shaped guess silently failed.
     *
     * GoTrue validates the redirect URL on its own, without tying it to the provider,
     * so every provider is routed through the one allow-listed URL. The `/discord` path
     * is therefore load-bearing and not a copy-paste slip — the manifest's intent-filter
     * is scheme+host only, so any path lands back in Butler regardless.
     */
    const val OAUTH_REDIRECT = "$OAUTH_SCHEME://$OAUTH_HOST/discord"

    /**
     * The `clientPlatform` Butler declares on `POST /mobile/generateAlpha`.
     *
     * Verified 2026-09-23 (docs/JANITOR_API.md section 24): with "mobile", Janitor makes the
     * upstream call to the user's proxy itself and streams the result; with "web", it
     * hands back the assembled OpenAI payload for the client to send. The decision on
     * record (docs/ROADMAP.md) is to prefer the direct route - the user's key is then used
     * only from the device - and to accept whichever shape Janitor actually returns, so
     * the transport reads the response's content type rather than trusting this value.
     */
    const val GENERATION_CLIENT_PLATFORM = "web"
}
