package com.unicornops.seefi.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoapMessagesTest {

    private val startSessionXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
        <SOAP-ENV:Body>
        <StartSession xmlns="http://localhost/api/soap/eyefilm">
        <macaddress>0018562c1234</macaddress>
        <cnonce>0123456789abcdef0123456789abcdef</cnonce>
        <transfermode>2</transfermode>
        <transfermodetimestamp>1234567890</transfermodetimestamp>
        </StartSession>
        </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
    """.trimIndent()

    private val getPhotoStatusXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
        <SOAP-ENV:Body>
        <GetPhotoStatus xmlns="http://localhost/api/soap/eyefilm">
        <macaddress>0018562c1234</macaddress>
        <credential>0123456789abcdef0123456789abcdef</credential>
        </GetPhotoStatus>
        </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
    """.trimIndent()

    @Test
    fun `parses StartSession request`() {
        val request = SoapMessages.parseStartSession(startSessionXml)!!
        assertEquals("0018562c1234", request.macaddress)
        assertEquals("0123456789abcdef0123456789abcdef", request.cnonce)
        assertEquals("2", request.transfermode)
        assertEquals("1234567890", request.transfermodetimestamp)
    }

    @Test
    fun `parses StartSession request with prefixed tags`() {
        val prefixed = startSessionXml
            .replace("<macaddress>", "<ns1:macaddress>")
            .replace("</macaddress>", "</ns1:macaddress>")
        val request = SoapMessages.parseStartSession(prefixed)!!
        assertEquals("0018562c1234", request.macaddress)
    }

    @Test
    fun `rejects incomplete StartSession request`() {
        assertNull(SoapMessages.parseStartSession("<nope>42</nope>"))
    }

    @Test
    fun `parses GetPhotoStatus request`() {
        val request = SoapMessages.parseGetPhotoStatus(getPhotoStatusXml)!!
        assertEquals("0018562c1234", request.macaddress)
        assertEquals("0123456789abcdef0123456789abcdef", request.credential)
    }

    @Test
    fun `StartSession response echoes transfer mode and carries credential and snonce`() {
        val response = SoapMessages.startSessionResponse(
            credential = "abcdef",
            snonce = "0123456789abcdef0123456789abcdef",
            transfermode = "2",
            transfermodetimestamp = "1234567890",
        )
        assertTrue(response.contains("<credential>abcdef</credential>"))
        assertTrue(response.contains("<snonce>0123456789abcdef0123456789abcdef</snonce>"))
        assertTrue(response.contains("<transfermode>2</transfermode>"))
        assertTrue(response.contains("<transfermodetimestamp>1234567890</transfermodetimestamp>"))
        assertTrue(response.contains("<upsyncallowed>false</upsyncallowed>"))
    }

    @Test
    fun `GetPhotoStatus response starts from the beginning of the file`() {
        val response = SoapMessages.getPhotoStatusResponse()
        assertTrue(response.contains("<fileid>1</fileid>"))
        assertTrue(response.contains("<offset>0</offset>"))
    }

    @Test
    fun `Upload response reports success`() {
        assertTrue(SoapMessages.uploadPhotoResponse(true).contains("<success>true</success>"))
        assertTrue(SoapMessages.uploadPhotoResponse(false).contains("<success>false</success>"))
    }

    @Test
    fun `extracts values ignoring surrounding whitespace`() {
        assertEquals("42", SoapMessages.extractXmlValue("<a> 42 </a>", "a"))
    }
}
