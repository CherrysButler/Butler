package com.cherry.butler.desktop

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File

/**
 * Windows' own Explorer open and save dialogs (IFileOpenDialog, IFileSaveDialog), the ones
 * every other app shows. Java's FileDialog opens the old common dialog from before Vista,
 * which looked out of place. Called through COM's function tables with JNA, on a thread of
 * its own; null from [open] or [save] means the dialog couldn't be made, and the caller falls
 * back to Java's.
 */
internal object ShellDialogs {
    /** Files picked (empty when closed), or null when the dialog couldn't be shown at all. */
    fun open(owner: Long, title: String, extensions: List<String>?, many: Boolean): List<File>? =
        onComThread { show(owner, title, extensions, save = false, many = many, suggested = null) }

    /** The file to write (null inside when closed), or null when the dialog couldn't be shown. */
    fun save(owner: Long, title: String, suggested: String): Result? =
        onComThread {
            val ext = suggested.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 8 }
            show(owner, title, ext?.let { listOf(it) }, save = true, many = false, suggested = suggested)
        }?.let { Result(it.firstOrNull()) }

    class Result(val file: File?)

    private fun <T> onComThread(block: () -> T?): T? {
        var out: T? = null
        var failed = false
        val t = Thread({
            val init = Ole32.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED)
            try {
                out = block()
            } catch (e: Throwable) {
                FrameLog.line("[picker] dialog failed: $e")
                failed = true
            } finally {
                if (init.toInt() >= 0) Ole32.INSTANCE.CoUninitialize()
            }
        }, "butler-file-dialog")
        t.isDaemon = true
        t.start()
        t.join()
        return if (failed) null else out
    }

    /** The picked files, or null when anything went wrong before the dialog showed. */
    private fun show(owner: Long, title: String, extensions: List<String>?, save: Boolean, many: Boolean, suggested: String?): List<File>? {
        val ppv = PointerByReference()
        val clsid = Guid.CLSID(if (save) CLSID_SAVE else CLSID_OPEN)
        val iid = Guid.IID(if (save) IID_SAVE else IID_OPEN)
        val made = Ole32.INSTANCE.CoCreateInstance(clsid, null, CLSCTX_INPROC_SERVER, iid, ppv).toInt()
        if (made < 0) { FrameLog.line("[picker] CoCreateInstance 0x%08X".format(made)); return null }
        val dialog = ppv.value ?: return null
        val keep = ArrayList<Memory>()
        fun wide(s: String) = Memory((s.length + 1) * 2L).also { it.setWideString(0, s); keep += it }
        try {
            val options = IntByReference()
            call(dialog, GET_OPTIONS, options.pointer)
            var fos = options.value or FOS_FORCEFILESYSTEM
            fos = if (save) fos or FOS_OVERWRITEPROMPT or FOS_PATHMUSTEXIST else fos or FOS_FILEMUSTEXIST
            if (many) fos = fos or FOS_ALLOWMULTISELECT
            call(dialog, SET_OPTIONS, fos)
            call(dialog, SET_TITLE, wide(title))
            if (!extensions.isNullOrEmpty()) {
                // One filter for the kinds asked for, then everything.
                val specs = listOf(
                    extensions.joinToString(", ") { it.uppercase() } to extensions.joinToString(";") { "*.$it" },
                    "All files" to "*.*",
                )
                val array = Memory(specs.size * 2L * Native.POINTER_SIZE).also { keep += it }
                specs.forEachIndexed { i, (name, spec) ->
                    array.setPointer((i * 2L) * Native.POINTER_SIZE, wide(name))
                    array.setPointer((i * 2L + 1) * Native.POINTER_SIZE, wide(spec))
                }
                call(dialog, SET_FILE_TYPES, specs.size, array)
                if (save) call(dialog, SET_DEFAULT_EXTENSION, wide(extensions.first()))
            }
            if (suggested != null) call(dialog, SET_FILE_NAME, wide(suggested))

            val shown = call(dialog, SHOW, Pointer.createConstant(owner))
            if (shown == ERROR_CANCELLED) return emptyList()
            if (shown < 0) { FrameLog.line("[picker] Show 0x%08X".format(shown)); return null }

            if (!save && many) {
                val items = PointerByReference()
                if (call(dialog, GET_RESULTS, items.pointer) < 0) return null
                val array = items.value ?: return emptyList()
                try {
                    val count = IntByReference()
                    call(array, ARRAY_GET_COUNT, count.pointer)
                    return (0 until count.value).mapNotNull { i ->
                        val item = PointerByReference()
                        if (call(array, ARRAY_GET_ITEM_AT, i, item.pointer) < 0) null else pathOf(item.value)
                    }
                } finally {
                    release(array)
                }
            }
            val item = PointerByReference()
            val got = call(dialog, GET_RESULT, item.pointer)
            if (got < 0) { FrameLog.line("[picker] GetResult 0x%08X".format(got)); return null }
            return listOfNotNull(pathOf(item.value))
        } finally {
            release(dialog)
        }
    }

    /** A shell item's file path, releasing the item. */
    private fun pathOf(item: Pointer?): File? {
        item ?: return null
        try {
            val name = PointerByReference()
            val got = call(item, ITEM_GET_DISPLAY_NAME, SIGDN_FILESYSPATH, name.pointer)
            if (got < 0) { FrameLog.line("[picker] GetDisplayName 0x%08X".format(got)); return null }
            val p = name.value ?: return null
            return try { File(p.getWideString(0)) } finally { Ole32.INSTANCE.CoTaskMemFree(p) }
        } finally {
            release(item)
        }
    }

    private fun release(obj: Pointer) { call(obj, RELEASE) }

    /** Calls method [index] of a COM object's function table, with the object itself first. */
    private fun call(obj: Pointer, index: Int, vararg args: Any): Int {
        val table = obj.getPointer(0)
        val fn = Function.getFunction(table.getPointer(index.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return fn.invokeInt(arrayOf(obj, *args))
    }

    private const val CLSID_OPEN = "{DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7}"
    private const val CLSID_SAVE = "{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}"
    private const val IID_OPEN = "{d57c7288-d4ad-4768-be02-9d969532d960}"
    private const val IID_SAVE = "{84bccd23-5fde-4cdb-aea4-af64b83d78ab}"
    private const val CLSCTX_INPROC_SERVER = 1
    private const val COINIT_APARTMENTTHREADED = 2

    // IUnknown 0-2, IModalWindow 3, IFileDialog 4-26, IFileOpenDialog 27-28.
    private const val RELEASE = 2
    private const val SHOW = 3
    private const val SET_FILE_TYPES = 4
    private const val SET_OPTIONS = 9
    private const val GET_OPTIONS = 10
    private const val SET_FILE_NAME = 15
    private const val SET_TITLE = 17
    private const val GET_RESULT = 20
    private const val SET_DEFAULT_EXTENSION = 22
    private const val GET_RESULTS = 27
    // IShellItem and IShellItemArray.
    private const val ITEM_GET_DISPLAY_NAME = 5
    private const val ARRAY_GET_COUNT = 7
    private const val ARRAY_GET_ITEM_AT = 8

    private const val SIGDN_FILESYSPATH = 0x80058000.toInt()
    private const val FOS_OVERWRITEPROMPT = 0x2
    private const val FOS_FORCEFILESYSTEM = 0x40
    private const val FOS_ALLOWMULTISELECT = 0x200
    private const val FOS_PATHMUSTEXIST = 0x800
    private const val FOS_FILEMUSTEXIST = 0x1000
    private const val ERROR_CANCELLED = 0x800704C7.toInt()
}
