package com.unicornops.seefi.protocol

/**
 * Minimal TAR (ustar) reader for the archives uploaded by Wi-Fi SD cards.
 *
 * The cards wrap each photo batch in an uncompressed TAR archive: a
 * sequence of 512-byte headers followed by the file content padded to
 * 512-byte blocks.
 */
object TarExtractor {

    private val IMAGE_EXTENSIONS =
        setOf("jpg", "jpeg", "png", "raw", "cr2", "nef", "arw", "dng")

    data class Entry(val name: String, val data: ByteArray)

    /**
     * Extracts the entries of a TAR archive and returns image files only.
     * Directory entries and non-image files are skipped, mirroring the
     * behaviour of the iOS app this project is ported from.
     */
    fun extractImageEntries(tarData: ByteArray): List<Entry> {
        val entries = ArrayList<Entry>()
        var offset = 0

        while (offset + 512 <= tarData.size) {
            val header = tarData.copyOfRange(offset, offset + 512)
            offset += 512

            val filename = readNullTerminatedString(header, 0, 100)
            if (filename.isEmpty()) break

            val sizeString = readNullTerminatedString(header, 124, 136).trim(' ', '\u0000')
            val size = sizeString.toIntOrNull(8) ?: 0
            val typeflag = header[156]

            if (typeflag == '5'.code.toByte()) {
                // Directory entry - nothing to do.
            } else if (typeflag == '0'.code.toByte() || typeflag.toInt() == 0) {
                val extension = filename.substringAfterLast('.', "").lowercase()
                if (extension in IMAGE_EXTENSIONS && size > 0 && offset + size <= tarData.size) {
                    val data = tarData.copyOfRange(offset, offset + size)
                    val baseName = filename.substringAfterLast('/')
                    entries.add(Entry(baseName, data))
                }
            }

            offset += size
            val padding = size % 512
            if (padding != 0) {
                offset += 512 - padding
            }
        }

        return entries
    }

    private fun readNullTerminatedString(bytes: ByteArray, start: Int, end: Int): String {
        val lastIndex = if (start < end) minOf(end, bytes.size) else start
        val slice = bytes.copyOfRange(start, lastIndex)
        val zeroIndex = slice.indexOfFirst { it.toInt() == 0 }
        val terminated =
            if (zeroIndex >= 0) slice.copyOfRange(0, zeroIndex) else slice
        return String(terminated, Charsets.UTF_8)
    }
}
