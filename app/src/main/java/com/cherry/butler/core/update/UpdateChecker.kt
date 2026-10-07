package com.cherry.butler.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.cherry.butler.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a newer Butler is out, asked of GitHub's public API for the latest release. Nothing
 * is downloaded or installed: a newer version is offered as a link to its release page.
 *
 * Butler otherwise talks only to Janitor (and the user's proxy), so it's optional: GitHub is
 * asked when the user taps "Check for updates", or each time the app opens if they turn the
 * automatic check on (off by default). A copy not signed with the GitHub release key (F-Droid's, signed by F-Droid) is
 * told to update where it came from: a GitHub APK can't install over it.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient,
    private val json: Json,
) {
    sealed interface Result {
        /** [notes]: the first lines of the release's own notes, plain text. */
        data class Available(val version: String, val url: String, val notes: List<String> = emptyList()) : Result
        data class UpToDate(val version: String) : Result
        /** Installed from elsewhere (F-Droid): updates come from there. */
        data object OtherSource : Result
        data class Failed(val message: String) : Result
    }

    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)
    // GitHub is not Janitor: none of Butler's Janitor interceptors or cookies, only its name.
    private val http = client.newBuilder()
        .apply { interceptors().clear(); networkInterceptors().clear() }
        .addInterceptor(com.cherry.butler.core.network.ButlerUserAgent)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()

    private val _auto = MutableStateFlow(prefs.getBoolean(KEY_AUTO, false))
    val auto: StateFlow<Boolean> = _auto.asStateFlow()

    fun setAuto(on: Boolean) {
        _auto.value = on
        prefs.edit().putBoolean(KEY_AUTO, on).apply()
    }

    val current: String get() = BuildConfig.VERSION_NAME

    /** Asks GitHub now. */
    suspend fun check(): Result = withContext(Dispatchers.IO) {
        if (!fromGitHub()) return@withContext Result.OtherSource
        runCatching {
            val request = Request.Builder()
                .url(LATEST)
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext Result.Failed("GitHub answered ${r.code}")
                val release = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
                if (release["draft"]?.jsonPrimitive?.booleanOrNull == true) return@withContext Result.UpToDate(current)
                val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@withContext Result.Failed("No version on the latest release")
                val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: RELEASES
                val latest = tag.removePrefix("v")
                val notes = summarize(release["body"]?.jsonPrimitive?.contentOrNull.orEmpty())
                if (isNewer(latest, current)) Result.Available(latest, url, notes) else Result.UpToDate(current)
            }
        }.getOrElse { Result.Failed("Couldn't reach GitHub") }
    }

    /** The automatic check as the app opens, when switched on: a newer version, or null. */
    suspend fun checkOnOpen(): Result.Available? {
        if (!_auto.value) return null
        return check() as? Result.Available
    }

    /** Signed with the GitHub release key (debug builds count, so the checker can be tried). */
    private fun fromGitHub(): Boolean {
        if (BuildConfig.DEBUG) return true
        return runCatching { RELEASE_CERT_SHA256 in signingDigests() }.getOrDefault(true)
    }

    @Suppress("DEPRECATION")
    private fun signingDigests(): List<String> {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }.orEmpty()
        val sha = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig -> sha.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }
    }

    companion object {
        const val RELEASES = "https://github.com/CherrysButler/Butler/releases"
        private const val LATEST = "https://api.github.com/repos/CherrysButler/Butler/releases/latest"

        /** The GitHub release key's certificate (CN=Cherry cigs, OU=Butler), read from 0.2.3's APK. */
        private const val RELEASE_CERT_SHA256 = "664e07a5af6b9069f6a785f4c74d4ca2c067b526a994faf97dffb61cb5c1562d"

        private const val KEY_AUTO = "update_check_auto"

        /**
         * The release notes' bullets as short plain lines: each bullet's first sentence, without
         * markdown, at most [max]. The Install section and the like have no bullets, so they drop out.
         */
        fun summarize(body: String, max: Int = 4): List<String> = body.lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { line ->
                line.drop(2)
                    .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
                    .replace("**", "").replace("`", "").replace("__", "")
                    .let { t -> t.substringBefore(". ").removeSuffix(".").trim() }
            }
            .filter { it.isNotEmpty() }
            .take(max)

        /** Whether [latest] is a higher version than [current] ("0.2.10" > "0.2.9"). */
        fun isNewer(latest: String, current: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(latest)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
