package androidx.paging

/**
 * Paging's logger, silent. On the JVM paging 3.3 logs everything (isLoggable is always true)
 * to stdout: a line for every item a list touches and whole page dumps, built on the UI
 * thread while a list scrolls. This class takes the library's place in the desktop build.
 */
object PagingLogger {
    fun isLoggable(level: Int): Boolean = false

    fun log(level: Int, message: String, tr: Throwable? = null) = Unit
}
