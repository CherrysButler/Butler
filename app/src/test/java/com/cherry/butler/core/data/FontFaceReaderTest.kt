package com.cherry.butler.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class FontFaceReaderTest {

    @Test
    fun readsWeightAndItalicFromOs2() {
        val font = sfnt(0x00010000, "OS/2" to os2(weight = 700, fsSelection = 1), "head" to head(macStyle = 0))
        assertEquals(FontFaceInfo(weight = 700, italic = true, family = null), FontFaceReader.read(font))
    }

    @Test
    fun obliqueCountsAsItalic() {
        val font = sfnt(0x00010000, "OS/2" to os2(weight = 400, fsSelection = 1 shl 9))
        assertEquals(true, FontFaceReader.read(font)?.italic)
    }

    @Test
    fun anOpenTypeCffFontReadsTheSame() {
        val font = sfnt(0x4F54544F, "OS/2" to os2(weight = 300, fsSelection = 0))
        assertEquals(FontFaceInfo(weight = 300, italic = false, family = null), FontFaceReader.read(font))
    }

    @Test
    fun withoutOs2TheHeadTableDecides() {
        assertEquals(FontFaceInfo(700, true, null), FontFaceReader.read(sfnt(0x00010000, "head" to head(macStyle = 3))))
        assertEquals(FontFaceInfo(400, false, null), FontFaceReader.read(sfnt(0x00010000, "head" to head(macStyle = 0))))
    }

    @Test
    fun weightsInHundredsAreScaled() {
        assertEquals(600, FontFaceReader.read(sfnt(0x00010000, "OS/2" to os2(weight = 6, fsSelection = 0)))?.weight)
    }

    @Test
    fun typographicFamilyWinsOverTheFamily() {
        val names = name(1 to "Inter Bold", 16 to "Inter")
        val font = sfnt(0x00010000, "OS/2" to os2(weight = 700, fsSelection = 0), "name" to names)
        assertEquals("Inter", FontFaceReader.read(font)?.family)
        val plain = sfnt(0x00010000, "OS/2" to os2(weight = 400, fsSelection = 0), "name" to name(1 to "Lora"))
        assertEquals("Lora", FontFaceReader.read(plain)?.family)
    }

    @Test
    fun aCollectionIsReadByItsFirstFont() {
        val inner = sfnt(0x00010000, "OS/2" to os2(weight = 500, fsSelection = 0))
        val header = ByteArrayOutputStream().apply {
            write("ttcf".toByteArray())
            u32(0x00010000)
            u32(1)
            u32(16)
        }.toByteArray()
        // The inner font's table offsets count from the start of the file, as in a real collection.
        val shifted = sfnt(0x00010000, "OS/2" to os2(weight = 500, fsSelection = 0), base = header.size)
        assertEquals(500, FontFaceReader.read(header + shifted)?.weight)
        assertEquals(500, FontFaceReader.read(inner)?.weight)
    }

    @Test
    fun anythingElseIsNull() {
        assertNull(FontFaceReader.read(ByteArray(0)))
        assertNull(FontFaceReader.read("{\"not\": \"a font\"}".toByteArray()))
        // A font with neither OS/2 nor head says nothing about its face.
        assertNull(FontFaceReader.read(sfnt(0x00010000, "cmap" to ByteArray(8))))
        // Cut short: the table directory points past the end.
        assertNull(FontFaceReader.read(sfnt(0x00010000, "OS/2" to os2(700, 0)).copyOf(30)))
    }

    /** A minimal sfnt: the header, the table directory, then each table, from [base] in the file. */
    private fun sfnt(version: Int, vararg tables: Pair<String, ByteArray>, base: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.u32(version)
        out.u16(tables.size)
        out.u16(0); out.u16(0); out.u16(0)
        var offset = base + 12 + tables.size * 16
        for ((tag, data) in tables) {
            out.write(tag.toByteArray(Charsets.ISO_8859_1))
            out.u32(0)
            out.u32(offset)
            out.u32(data.size)
            offset += data.size
        }
        tables.forEach { out.write(it.second) }
        return out.toByteArray()
    }

    private fun os2(weight: Int, fsSelection: Int): ByteArray = ByteArray(78).also {
        it[4] = (weight shr 8).toByte(); it[5] = weight.toByte()
        it[62] = (fsSelection shr 8).toByte(); it[63] = fsSelection.toByte()
    }

    private fun head(macStyle: Int): ByteArray = ByteArray(54).also {
        it[44] = (macStyle shr 8).toByte(); it[45] = macStyle.toByte()
    }

    /** A name table of Windows English records, UTF-16BE. */
    private fun name(vararg records: Pair<Int, String>): ByteArray {
        val strings = records.map { it.second.toByteArray(Charsets.UTF_16BE) }
        val out = ByteArrayOutputStream()
        out.u16(0)
        out.u16(records.size)
        out.u16(6 + records.size * 12)
        var at = 0
        records.forEachIndexed { i, (id, _) ->
            out.u16(3); out.u16(1); out.u16(0x409); out.u16(id)
            out.u16(strings[i].size); out.u16(at)
            at += strings[i].size
        }
        strings.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.u16(v: Int) { write(v shr 8 and 0xFF); write(v and 0xFF) }
    private fun ByteArrayOutputStream.u32(v: Int) { u16(v ushr 16); u16(v and 0xFFFF) }
}
