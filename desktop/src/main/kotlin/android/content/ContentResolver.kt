package android.content

import android.database.Cursor
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** URIs on the desktop are files (file:...), from a picker or a drop. */
object ContentResolver {
    fun openInputStream(uri: Uri): InputStream? = uri.file()?.takeIf { it.isFile }?.inputStream()
    fun openOutputStream(uri: Uri): OutputStream? = uri.file()?.also { it.parentFile?.mkdirs() }?.outputStream()
    fun getType(uri: Uri): String? = uri.file()?.let { java.nio.file.Files.probeContentType(it.toPath()) }

    /** Answers OpenableColumns.DISPLAY_NAME and SIZE, the only columns Butler asks for. */
    fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val file = uri.file() ?: return null
        val cols = projection ?: arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE)
        val row = cols.map { if (it == android.provider.OpenableColumns.SIZE) file.length().toString() else file.name }
        return Cursor(cols.toList(), listOf(row))
    }

    private fun Uri.file(): File? = when (scheme) {
        "file", null -> path?.let(::File)
        else -> null
    }
}
