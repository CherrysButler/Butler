package android.net

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** A URI as text, the way Butler uses one: parse, read its parts, encode a path piece. */
class Uri private constructor(private val text: String) {
    private val parsed: URI? = runCatching { URI(text) }.getOrNull()

    val scheme: String? get() = parsed?.scheme
    val host: String? get() = parsed?.host
    val path: String? get() = if (scheme == "file") parsed?.let { File(it).path } else parsed?.path
    val query: String? get() = parsed?.query
    val fragment: String? get() = parsed?.fragment
    val lastPathSegment: String? get() = pathSegments.lastOrNull()
    val pathSegments: List<String> get() = parsed?.path?.split('/')?.filter { it.isNotEmpty() } ?: emptyList()

    fun getQueryParameter(key: String): String? =
        parsed?.rawQuery?.split('&')?.map { it.split('=', limit = 2) }
            ?.firstOrNull { URLDecoder.decode(it[0], Charsets.UTF_8) == key }
            ?.let { URLDecoder.decode(it.getOrElse(1) { "" }, Charsets.UTF_8) }

    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is Uri && other.text == text
    override fun hashCode(): Int = text.hashCode()

    companion object {
        @JvmStatic fun parse(text: String): Uri = Uri(text)
        @JvmStatic fun fromFile(file: File): Uri = Uri(file.toURI().toString())
        /** Android's Uri.encode: everything but unreserved characters, spaces as %20. */
        @JvmStatic fun encode(s: String?): String? = s?.let { URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20").replace("%7E", "~") }
        @JvmStatic fun decode(s: String?): String? = s?.let { URLDecoder.decode(it, Charsets.UTF_8) }
    }
}
