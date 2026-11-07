package com.unicornops.seefi.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TarExtractorTest {

    /** Builds a minimal ustar archive the same way the cards do. */
    private fun tar(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for ((name, data) in entries) {
            val header = ByteArray(512)
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            nameBytes.copyInto(header, 0, 0, minOf(nameBytes.size, 100))
            val sizeOctal = data.size.toString(8).padStart(11, '0') + "\u0000"
            sizeOctal.toByteArray(Charsets.US_ASCII).copyInto(header, 124)
            header[156] = '0'.code.toByte()
            out.write(header)
            out.write(data)
            val padding = (512 - (data.size % 512)) % 512
            repeat(padding) { out.write(0) }
        }
        out.write(ByteArray(1024)) // two zero blocks terminate the archive
        return out.toByteArray()
    }

    @Test
    fun `extracts image files`() {
        val tar = tar(
            listOf(
                "photo_1.jpg" to ByteArray(700) { it.toByte() },
                "photo_2.cr2" to ByteArray(10) { (it * 3).toByte() },
            ),
        )
        val entries = TarExtractor.extractImageEntries(tar)
        assertEquals(2, entries.size)
        assertEquals("photo_1.jpg", entries[0].name)
        assertEquals(700, entries[0].data.size)
        assertEquals("photo_2.cr2", entries[1].name)
        assertEquals(10, entries[1].data.size)
        // photo_2 content is (index * 3)
        assertEquals(3, entries[1].data[1].toInt())
    }

    @Test
    fun `skips non-image files`() {
        val tar = tar(
            listOf(
                "eyeFi.jpg" to ByteArray(20),
                "readme.txt" to ByteArray(20),
                "log.bin" to ByteArray(20),
            ),
        )
        val entries = TarExtractor.extractImageEntries(tar)
        assertEquals(listOf("eyeFi.jpg"), entries.map { it.name })
    }

    @Test
    fun `handles empty archive`() {
        val entries = TarExtractor.extractImageEntries(ByteArray(1024))
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `handles truncated archive gracefully`() {
        val tar = tar(listOf("photo.jpg" to ByteArray(2048)))
        val truncated = tar.copyOf(tar.size / 2)
        // Either returns the (complete-only) entries or nothing; must
        // not throw.
        TarExtractor.extractImageEntries(truncated)
    }
}
