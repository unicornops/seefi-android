package com.unicornops.seefi.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultipartParserTest {

    private fun multipartBody(vararg parts: String, boundary: String): ByteArray {
        val out = StringBuilder()
        for (part in parts) {
            out.append("--").append(boundary).append("\r\n")
            out.append(part).append("\r\n")
        }
        out.append("--").append(boundary).append("--\r\n")
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    @Test
    fun `parses soap envelope and file part`() {
        val body = multipartBody(
            "Content-Disposition: form-data; name=\"SOAPENVELOPE\"\r\n" +
                "Content-Type: application/x-www-form-urlencoded\r\n" +
                "\r\n" +
                "<UploadPhoto><filename>abc_000.jpg</filename></UploadPhoto>",
            "Content-Disposition: form-data; name=\"FILENAME\"; filename=\"aabbcc.tar\"\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "\r\n" +
                "BINARYTARDATA",
            boundary = "AaB03x",
        )

        val parts = MultipartParser.parse(body, "AaB03x")
        assertEquals(2, parts.size)

        val soap = MultipartParser.soapEnvelope(parts)!!
        assertEquals("SOAPENVELOPE", soap.name)
        assertTrue(soap.bodyText().contains("abc_000.jpg"))

        val tar = MultipartParser.tarPart(parts)!!
        assertEquals("FILENAME", tar.name)
        assertEquals("aabbcc.tar", tar.filename)
        assertEquals("BINARYTARDATA", String(tar.body, Charsets.UTF_8))
    }

    @Test
    fun `returns empty list for garbage body`() {
        assertTrue(MultipartParser.parse(ByteArray(0), "AaB03x").isEmpty())
    }

    @Test
    fun `extracts boundary parameter`() {
        assertEquals("AaB03x", MultipartParser.boundaryOf("multipart/form-data; boundary=AaB03x"))
        assertEquals("AaB03x", MultipartParser.boundaryOf("multipart/form-data; boundary=\"AaB03x\""))
        assertNull(MultipartParser.boundaryOf("text/xml"))
    }

    @Test
    fun `parses all soap tags without ordering constraints`() {
        // Current eye-fi firmware posts parts in SOAPENVELOPE, FILENAME
        // order; the parser must not depend on it.
        val soapPart = "Content-Disposition: form-data; name=\"SOAPENVELOPE\"\r\n\r\n<x/>"
        val filePart = "Content-Disposition: form-data; name=\"FILENAME\"\r\n\r\nDATA"
        val body = multipartBody(filePart, soapPart, boundary = "xyz")
        val parts = MultipartParser.parse(body, "xyz")
        assertEquals("SOAPENVELOPE", MultipartParser.soapEnvelope(parts)!!.name)
        assertEquals("FILENAME", MultipartParser.tarPart(parts)!!.name)
    }
}
