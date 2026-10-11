package com.cherry.butler.core.data

/**
 * What a font file says about itself: its weight (1 to 1000, 400 regular, 700 bold), whether it
 * is italic, and the name of the family it belongs to, when it gives one.
 */
data class FontFaceInfo(val weight: Int, val italic: Boolean, val family: String?)

/**
 * Reads [FontFaceInfo] straight from a .ttf or .otf (both are sfnt files: a table directory,
 * then the tables). Plain Kotlin over the bytes, so the phone and the desktop read a font the
 * same way. The weight and slant come from the OS/2 table (usWeightClass, fsSelection); a font
 * without one falls back to the head table's macStyle. The family name is the name table's
 * typographic family (ID 16), else its family (ID 1): a bold face's ID 1 is often "Inter
 * Bold" while its ID 16 is "Inter".
 */
object FontFaceReader {
    /** null for anything that isn't an sfnt font, or is cut short. */
    fun read(bytes: ByteArray): FontFaceInfo? = runCatching { parse(Bytes(bytes)) }.getOrNull()

    private fun parse(b: Bytes): FontFaceInfo? {
        // A collection (.ttc) holds several fonts; the first one speaks for the file.
        val start = if (b.tag(0) == "ttcf") b.u32(12).toInt() else 0
        val version = b.u32(start)
        if (version != 0x00010000L && b.tag(start) != "OTTO" && b.tag(start) != "true") return null
        val tables = HashMap<String, Int>()
        val count = b.u16(start + 4)
        for (i in 0 until count) {
            val record = start + 12 + i * 16
            tables[b.tag(record)] = b.u32(record + 8).toInt()
        }
        val os2 = tables["OS/2"]
        val head = tables["head"]
        if (os2 == null && head == null) return null
        val macStyle = head?.let { b.u16(it + 44) } ?: 0
        val weightClass = os2?.let { b.u16(it + 4) } ?: 0
        val fsSelection = os2?.let { b.u16(it + 62) } ?: 0
        val weight = when {
            // Some old fonts give the weight in hundreds (1 to 9).
            weightClass in 1..9 -> weightClass * 100
            weightClass in 10..1000 -> weightClass
            fsSelection and FS_BOLD != 0 || macStyle and MAC_BOLD != 0 -> 700
            else -> 400
        }
        val italic = fsSelection and (FS_ITALIC or FS_OBLIQUE) != 0 || macStyle and MAC_ITALIC != 0
        return FontFaceInfo(weight = weight, italic = italic, family = tables["name"]?.let { familyName(b, it) })
    }

    private fun familyName(b: Bytes, table: Int): String? {
        val count = b.u16(table + 2)
        val strings = table + b.u16(table + 4)
        data class Entry(val platform: Int, val language: Int, val id: Int, val text: String)
        val entries = (0 until count).mapNotNull { i ->
            val r = table + 6 + i * 12
            val platform = b.u16(r)
            val encoding = b.u16(r + 2)
            val id = b.u16(r + 6)
            if (id != NAME_FAMILY && id != NAME_TYPOGRAPHIC_FAMILY) return@mapNotNull null
            val raw = b.slice(strings + b.u16(r + 10), b.u16(r + 8))
            val text = when {
                platform == 0 || platform == 3 && encoding in setOf(0, 1, 10) -> String(raw, Charsets.UTF_16BE)
                platform == 1 && encoding == 0 -> String(raw, Charsets.ISO_8859_1)
                else -> return@mapNotNull null
            }.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Entry(platform, b.u16(r + 4), id, text)
        }
        // English from Windows first, then any Unicode entry, then the Mac's.
        fun rank(e: Entry) = when {
            e.platform == 3 && e.language == 0x409 -> 0
            e.platform == 3 || e.platform == 0 -> 1
            else -> 2
        }
        return (entries.filter { it.id == NAME_TYPOGRAPHIC_FAMILY }.minByOrNull(::rank)
            ?: entries.filter { it.id == NAME_FAMILY }.minByOrNull(::rank))?.text
    }

    /** Big-endian reads that throw past the end, which [read] turns into null. */
    private class Bytes(private val data: ByteArray) {
        fun u8(at: Int): Int {
            if (at < 0 || at >= data.size) throw IndexOutOfBoundsException()
            return data[at].toInt() and 0xFF
        }

        fun u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)
        fun u32(at: Int): Long = (u16(at).toLong() shl 16) or u16(at + 2).toLong()
        fun tag(at: Int): String = String(CharArray(4) { u8(at + it).toChar() })
        fun slice(at: Int, length: Int): ByteArray {
            if (at < 0 || length < 0 || at + length > data.size) throw IndexOutOfBoundsException()
            return data.copyOfRange(at, at + length)
        }
    }

    private const val FS_ITALIC = 1
    private const val FS_BOLD = 1 shl 5
    private const val FS_OBLIQUE = 1 shl 9
    private const val MAC_BOLD = 1
    private const val MAC_ITALIC = 2
    private const val NAME_FAMILY = 1
    private const val NAME_TYPOGRAPHIC_FAMILY = 16
}
