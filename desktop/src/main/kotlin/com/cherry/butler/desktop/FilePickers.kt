package com.cherry.butler.desktop

import android.net.Uri
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

/** Windows' own open and save dialogs, for the phone's pickers. */
object FilePickers {
    @Volatile var owner: Frame? = null

    fun open(title: String, extensions: List<String>?, done: (Uri?) -> Unit) = SwingUtilities.invokeLater {
        val dialog = FileDialog(owner, title, FileDialog.LOAD)
        if (extensions != null) {
            dialog.file = extensions.joinToString(";") { "*.$it" }
            dialog.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
        }
        dialog.isVisible = true
        val picked = dialog.files.firstOrNull()
        done(picked?.let { Uri.fromFile(it) })
    }

    /** The open dialog with several files picked at once; an empty list when it is closed. */
    fun openMany(title: String, extensions: List<String>?, done: (List<Uri>) -> Unit) = SwingUtilities.invokeLater {
        val dialog = FileDialog(owner, title, FileDialog.LOAD)
        dialog.isMultipleMode = true
        if (extensions != null) {
            dialog.file = extensions.joinToString(";") { "*.$it" }
            dialog.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
        }
        dialog.isVisible = true
        done(dialog.files.map { Uri.fromFile(it) })
    }

    fun save(title: String, suggested: String, done: (Uri?) -> Unit) = SwingUtilities.invokeLater {
        val dialog = FileDialog(owner, title, FileDialog.SAVE)
        dialog.file = suggested
        dialog.isVisible = true
        val name = dialog.file
        val dir = dialog.directory
        done(if (name != null && dir != null) Uri.fromFile(File(dir, name)) else null)
    }
}
