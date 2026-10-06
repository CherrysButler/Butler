package com.cherry.butler.core.network

import android.content.Context
import com.cherry.butler.BuildConfig
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Debug builds only: sends every call Butler makes (Janitor, the JLLM socket, the user's own
 * proxy) through an inspecting proxy on a computer, such as Burp Suite or mitmproxy, so a
 * request can be read and compared without tapping around the app. Release builds never
 * consult it: [select] answers "direct" unless [BuildConfig.DEBUG].
 *
 * To read HTTPS the proxy's own CA has to be trusted. Each Burp install makes its own, so
 * none can ship in the app: [fetchCertificate] downloads it through the proxy (Burp serves
 * it at http://burp/cert, mitmproxy at http://mitm.it) and [trustManager] trusts it on top of
 * the system's, in this app only. A CA installed on the phone works too (src/debug/res/xml).
 * The release build sets none of this up and trusts the system's authorities only.
 */
object DebugProxy : ProxySelector() {
    private const val PREFS = "butler_prefs"
    private const val KEY_ON = "debug_proxy_on"
    private const val KEY_ADDRESS = "debug_proxy_address"

    @Volatile var enabled: Boolean = false
        private set

    /** `host:port`, as typed. */
    @Volatile var address: String = ""
        private set

    private const val CA_FILE = "debug_proxy_ca.crt"

    private var context: Context? = null

    /** The proxy's CA, once fetched. */
    @Volatile var certificate: X509Certificate? = null
        private set
    private val pools = mutableListOf<ConnectionPool>()

    fun load(context: Context) {
        if (!BuildConfig.DEBUG) return
        this.context = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ON, false)
        address = prefs.getString(KEY_ADDRESS, "").orEmpty()
        certificate = runCatching { File(context.filesDir, CA_FILE).takeIf { it.isFile }?.let { readCertificate(it.readBytes()) } }.getOrNull()
    }

    /** The client's pool, so connections opened the other way are dropped on a change. */
    fun track(pool: ConnectionPool) {
        synchronized(pools) { pools += pool }
    }

    fun set(on: Boolean, address: String) {
        if (!BuildConfig.DEBUG) return
        enabled = on
        this.address = address.trim()
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putBoolean(KEY_ON, on)?.putString(KEY_ADDRESS, this.address)?.apply()
        // A kept-alive connection would carry on the old way; the next call opens a new one.
        dropConnections()
    }

    /**
     * Closes kept-alive connections so the next call opens one the new way. Off the calling
     * thread: closing a TLS connection writes to it, and the settings screen calls from main.
     */
    private fun dropConnections() {
        Thread { synchronized(pools) { pools.forEach { it.evictAll() } } }.start()
    }

    /** `host:port` as a socket address, or null when it isn't one. */
    fun parse(text: String): InetSocketAddress? {
        val host = text.substringBeforeLast(':', "").trim()
        val port = text.substringAfterLast(':', "").trim().toIntOrNull()
        if (host.isEmpty() || port == null || port !in 1..65535) return null
        return InetSocketAddress.createUnresolved(host, port)
    }

    /**
     * Downloads the proxy's CA through the proxy and keeps it. Returns who issued it, for the
     * settings row. Blocking; call off the main thread.
     */
    fun fetchCertificate(): String {
        check(BuildConfig.DEBUG)
        val to = parse(address) ?: throw IOException("Enter the proxy as host:port first")
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, to))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        var last: Exception? = null
        for (url in listOf("http://burp/cert", "http://mitm.it/cert/pem")) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val bytes = r.body?.bytes() ?: throw IOException("empty answer")
                    val cert = readCertificate(bytes)
                    File(context!!.filesDir, CA_FILE).writeBytes(bytes)
                    certificate = cert
                    dropConnections()
                    return cert.issuerX500Principal.name
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException("The proxy didn't hand out a certificate (${last?.message})", last)
    }

    fun forgetCertificate() {
        context?.let { File(it.filesDir, CA_FILE).delete() }
        certificate = null
        dropConnections()
    }

    private fun readCertificate(bytes: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(bytes.inputStream()) as X509Certificate

    /**
     * The system's trust (as the network security config sets it), plus the fetched CA.
     * Extended, so the system's checker is told the host it is checking: the debug config's
     * per-domain rule (for http://burp) makes it refuse the host-less check, which turned
     * every HTTPS call away.
     */
    val trustManager: X509TrustManager by lazy {
        val system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }.trustManagers.filterIsInstance<X509ExtendedTrustManager>().first()

        fun proxyCa(chain: Array<out X509Certificate>?, authType: String?, refused: CertificateException) {
            val ca = certificate ?: throw refused
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setCertificateEntry("proxy", ca) }
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
                .trustManagers.filterIsInstance<X509TrustManager>().first()
                .checkServerTrusted(chain, authType)
        }

        object : X509ExtendedTrustManager() {
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
                try { system.checkServerTrusted(chain, authType, socket) } catch (e: CertificateException) { proxyCa(chain, authType, e) }
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
                try { system.checkServerTrusted(chain, authType, engine) } catch (e: CertificateException) { proxyCa(chain, authType, e) }
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try { system.checkServerTrusted(chain, authType) } catch (e: CertificateException) { proxyCa(chain, authType, e) }
            }

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
                system.checkClientTrusted(chain, authType, socket)

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
                system.checkClientTrusted(chain, authType, engine)

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                system.checkClientTrusted(chain, authType)

            override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers + listOfNotNull(certificate)
        }
    }

    val socketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory
    }

    override fun select(uri: URI?): List<Proxy> {
        if (!BuildConfig.DEBUG || !enabled) return listOf(Proxy.NO_PROXY)
        val to = parse(address) ?: return listOf(Proxy.NO_PROXY)
        return listOf(Proxy(Proxy.Type.HTTP, to))
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}
