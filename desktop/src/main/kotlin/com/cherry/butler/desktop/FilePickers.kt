package com.cherry.butler.desktop

import android.net.Uri
import com.sun.jna.Native
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

/**
 * Windows' own open and save dialogs, for the phone's pickers: the Explorer ones
 * ([ShellDialogs]), shown off the UI thread so Butler keeps drawing behind them, and
 * answered on it. Java's old FileDialog stays as the fallback.
 */
object FilePickers {
    @Volatile var owner: Frame? = null

    /**
     * Hands a picked file back once the mouse is let go. A double-click on a file closes the
     * dialog on its second press; the release (and the rest of that click) then lands on
     * Butler, and a confirmation opening right then took it for a click outside and closed.
     */
    private fun answer(block: () -> Unit) {
        Thread.sleep(SETTLE_MS)
        SwingUtilities.invokeLater(block)
    }

    private const val SETTLE_MS = 250L

    private fun ownerHandle(): Long = owner?.let { runCatching { Native.getWindowID(it) }.getOrNull() } ?: 0L

    fun open(title: String, extensions: List<String>?, done: (Uri?) -> Unit) {
        val hwnd = ownerHandle()
        Thread({
            val picked = ShellDialogs.open(hwnd, title, extensions, many = false)
            answer {
                if (picked != null) done(picked.firstOrNull()?.let { Uri.fromFile(it) })
                else done(legacyOpen(title, extensions, many = false).firstOrNull())
            }
        }, "butler-open").apply { isDaemon = true }.start()
    }

    /** The open dialog with several files picked at once; an empty list when it is closed. */
    fun openMany(title: String, extensions: List<String>?, done: (List<Uri>) -> Unit) {
        val hwnd = ownerHandle()
        Thread({
            val picked = ShellDialogs.open(hwnd, title, extensions, many = true)
            answer {
                if (picked != null) done(picked.map { Uri.fromFile(it) })
                else done(legacyOpen(title, extensions, many = true))
            }
        }, "butler-open").apply { isDaemon = true }.start()
    }

    fun save(title: String, suggested: String, done: (Uri?) -> Unit) {
        val hwnd = ownerHandle()
        Thread({
            val result = ShellDialogs.save(hwnd, title, suggested)
            answer {
                if (result != null) done(result.file?.let { Uri.fromFile(it) })
                else done(legacySave(title, suggested))
            }
        }, "butler-save").apply { isDaemon = true }.start()
    }

    private fun legacyOpen(title: String, extensions: List<String>?, many: Boolean): List<Uri> {
        val dialog = FileDialog(owner, title, FileDialog.LOAD)
        dialog.isMultipleMode = many
        if (extensions != null) {
            dialog.file = extensions.joinToString(";") { "*.$it" }
            dialog.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
        }
        dialog.isVisible = true
        return dialog.files.map { Uri.fromFile(it) }
    }

    private fun legacySave(title: String, suggested: String): Uri? {
        val dialog = FileDialog(owner, title, FileDialog.SAVE)
        dialog.file = suggested
        dialog.isVisible = true
        val name = dialog.file
        val dir = dialog.directory
        return if (name != null && dir != null) Uri.fromFile(File(dir, name)) else null
    }
}
