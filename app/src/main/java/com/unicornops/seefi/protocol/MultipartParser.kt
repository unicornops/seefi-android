package com.unicornops.seefi.protocol

/**
 * Lenient multipart/form-data parser for the card upload requests.
 *
 * The card posts two parts: a SOAPENVELOPE form field (XML) and a
 * FILENAME part carrying the binary TAR payload. The parser splits the
 * body on the boundary from the Content-Type header and tolerates
 * headers in any order/case.
 */
object MultipartParser {

    data class Part(val name: String, val filename: String?, val headers: Map<String, String>, val body: ByteArray) {
        fun bodyText(): String = String(body, Charsets.UTF_8)
    }

    fun boundaryOf(contentType: String): String? {
        val parameter = contentType.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("boundary=", ignoreCase = true) }
            ?: return null
        return parameter.substringAfter("=").trim('"')
    }

    fun parse(body: ByteArray, boundary: String): List<Part> {
        val delimiter = "--$boundary".toByteArray(Charsets.US_ASCII)
        val parts = ArrayList<Part>()
        var index = indexOf(body, delimiter, 0)

        while (index >= 0) {
            var cursor = index + delimiter.size
            if (cursor + 2 <= body.size && body[cursor] == '-'.code.toByte() && body[cursor + 1] == '-'.code.toByte()) {
                break // closing delimiter
            }
            // Skip the CRLF after the boundary.
            if (cursor + 2 <= body.size && body[cursor] == '\r'.code.toByte() && body[cursor + 1] == '\n'.code.toByte()) {
                cursor += 2
            }
            val headerEnd = indexOf(body, "\r\n\r\n".toByteArray(), cursor)
            if (headerEnd < 0) break
            val headerText = String(body, cursor, headerEnd - cursor, Charsets.UTF_8)

            val contentStart = headerEnd + 4
            val nextBoundary = indexOf(body, delimiter, contentStart)
            if (nextBoundary < 0) break
            // Content is followed by CRLF before the next boundary.
            val contentEnd = if (nextBoundary >= contentStart + 2 &&
                body[nextBoundary - 2] == '\r'.code.toByte() && body[nextBoundary - 1] == '\n'.code.toByte()
            ) {
                nextBoundary - 2
            } else {
                nextBoundary
            }

            val headers = parseHeaders(headerText)
            val contentDisposition = headers["content-disposition"]
            val name = extractDispositionParameter(contentDisposition, "name")
            val filename = extractDispositionParameter(contentDisposition, "filename")

            parts.add(
                Part(
                    name = name ?: "",
                    filename = filename,
                    headers = headers,
                    body = body.copyOfRange(contentStart, contentEnd),
                ),
            )
            index = nextBoundary
        }

        return parts
    }

    fun soapEnvelope(parts: List<Part>): Part? =
        parts.firstOrNull { it.name.equals("SOAPENVELOPE", ignoreCase = true) }

    fun tarPart(parts: List<Part>): Part? =
        parts.firstOrNull { it.name.equals("FILENAME", ignoreCase = true) }

    private fun parseHeaders(headerText: String): Map<String, String> {
        val headers = HashMap<String, String>()
        for (line in headerText.split("\r\n")) {
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
        }
        return headers
    }

    private fun extractDispositionParameter(disposition: String?, parameter: String): String? {
        disposition ?: return null
        val parameterName = "$parameter="
        for (segment in disposition.split(';')) {
            val trimmed = segment.trim()
            if (trimmed.startsWith(parameterName, ignoreCase = true)) {
                return trimmed.substring(parameterName.length).trim('"')
            }
        }
        return null
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty()) return -1
        var i = maxOf(from, 0)
        val limit = haystack.size - needle.size
        outer@ while (i <= limit) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    i++
                    continue@outer
                }
            }
            return i
        }
        return -1
    }
}
