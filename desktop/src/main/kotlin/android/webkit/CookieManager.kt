package android.webkit

/**
 * The desktop's sign-in check keeps the cookies it saw here, so JanitorCookies reads them the
 * way it reads Android's WebView cookie store.
 */
class CookieManager private constructor() {
    private val jar = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getCookie(url: String): String? {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return null
        return jar[host]
    }

    fun setCookie(url: String, value: String) {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return
        jar[host] = value
    }

    fun removeAllCookies(callback: ((Boolean) -> Unit)?) {
        jar.clear()
        callback?.invoke(true)
    }

    fun flush() = Unit

    companion object {
        private val instance = CookieManager()
        @JvmStatic fun getInstance(): CookieManager = instance
    }
}
