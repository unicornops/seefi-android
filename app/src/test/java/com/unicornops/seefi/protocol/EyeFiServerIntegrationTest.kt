package com.unicornops.seefi.protocol

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Runs the full EyeFi SOAP conversation against a live EyeFiServer on
 * an ephemeral port using HttpURLConnection, the same way a real card
 * would talk to the app.
 */
class EyeFiServerIntegrationTest {

    private lateinit var server: EyeFiServer
    private var uploads = ArrayList<Pair<ByteArray, String>>()
    private val mac = "0018562c1234"
    private val cnonce = "0123456789abcdef0123456789abcdef"
    private val uploadKey = "a1b2c3d4e5f60718293a4b5c6d7e8f90"

    @Before
    fun startServer() {
        uploads.clear()
        server = EyeFiServer(
            port = 0,
            uploadKeyForMac = { macAddress ->
                if (macAddress.equals(mac, ignoreCase = true)) uploadKey else CredentialCalculator.MOBI_KEY
            },
            onUpload = { tarData, soapEnvelope ->
                uploads.add(tarData to soapEnvelope)
                TarExtractor.extractImageEntries(tarData).size
            },
        )
        server.start()
    }

    @After
    fun stopServer() {
        server.stop()
    }

    private fun post(
        path: String,
        soapAction: String,
        contentType: String,
        body: ByteArray,
        chunked: Boolean = false,
    ): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:${server.boundPort}$path")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("SOAPAction", soapAction)
        connection.setRequestProperty("Content-Type", contentType)
        if (chunked) {
            connection.setChunkedStreamingMode(64)
        } else {
            connection.setFixedLengthStreamingMode(body.size.toLong())
        }
        connection.connect()
        connection.outputStream.use { output: OutputStream -> output.write(body) }
        val response = connection.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        return connection.responseCode to response
    }

    @Test
    fun `full card conversation StartSession GetPhotoStatus Upload`() {
        // 1. StartSession
        val startSessionBody = """
            < SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
            <SOAP-ENV:Body><StartSession xmlns="http://localhost/api/soap/eyefilm">
            <macaddress>$mac</macaddress>
            <cnonce>$cnonce</cnonce>
            <transfermode>2</transfermode>
            <transfermodetimestamp>1234567890</transfermodetimestamp>
            </StartSession></SOAP-ENV:Body></SOAP-ENV:Envelope>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val (startStatus, startResponse) =
            post("/api/soap/eyefilm/v1", "\"urn:StartSession\"", "text/xml", startSessionBody)
        assertEquals(200, startStatus)

        val expectedCredential =
            CredentialCalculator.credentialServerToClient(mac, cnonce, uploadKey)
        assertTrue(startResponse.contains("<credential>$expectedCredential</credential>"))
        val snonce = Regex("<snonce>([0-9a-f]+)</snonce>")
            .find(startResponse)!!.groupValues[1]

        // 2. GetPhotoStatus with the correct credential
        val cardCredential = CredentialCalculator.credentialClientToServer(mac, snonce, uploadKey)
        val photoStatusBody = """
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
            <SOAP-ENV:Body><GetPhotoStatus xmlns="http://localhost/api/soap/eyefilm">
            <macaddress>$mac</macaddress>
            <credential>$cardCredential</credential>
            </GetPhotoStatus></SOAP-ENV:Body></SOAP-ENV:Envelope>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val (photoStatus, photoStatusResponse) =
            post("/api/soap/eyefilm/v1", "\"urn:GetPhotoStatus\"", "text/xml", photoStatusBody)
        assertEquals(200, photoStatus)
        assertTrue(photoStatusResponse.contains("<fileid>1</fileid>"))
        assertTrue(photoStatusResponse.contains("<offset>0</offset>"))

        // 3. Upload a multipart archive with one photo
        val tar = testTar("IMG_0001.jpg")
        val uploadBody = buildMultipart(tar)
        val (uploadStatus, uploadResponse) = post(
            "/api/soap/eyefilm/v1/upload",
            "",
            "multipart/form-data; boundary=7d33a816d302b6",
            uploadBody,
        )
        assertEquals(200, uploadStatus)
        assertTrue(uploadResponse.contains("<success>true</success>"))
        assertEquals(1, uploads.size)
        assertTrue(uploads[0].second.contains("000exec.jpg"))

        // 4. MarkLastPhotoInRoll
        val (markStatus, markResponse) = post(
            "/api/soap/eyefilm/v1",
            "\"urn:MarkLastPhotoInRoll\"",
            "text/xml",
            "<x/>".toByteArray(StandardCharsets.UTF_8),
        )
        assertEquals(200, markStatus)
        assertTrue(markResponse.contains("MarkLastPhotoInRollResponse"))
    }

    @Test
    fun `GetPhotoStatus with bad credential is rejected`() {
        // Establish a session to create the snonce.
        post("/api/soap/eyefilm/v1", "\"urn:StartSession\"", "text/xml", startSessionXml())

        val badCredential = "deadbeef".padEnd(32, '0')
        val body = """
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
            <SOAP-ENV:Body><GetPhotoStatus>
            <macaddress>$mac</macaddress>
            <credential>$badCredential</credential>
            </GetPhotoStatus></SOAP-ENV:Body></SOAP-ENV:Envelope>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val (_, response) = post("/api/soap/eyefilm/v1", "\"urn:GetPhotoStatus\"", "text/xml", body)
        assertEquals(SoapMessages.NICE_TRY_RESPONSE, response)
    }

    @Test
    fun `upload via chunked transfer encoding is handled`() {
        val tar = testTar("IMG_0002.jpg")
        val body = buildMultipart(tar)
        val (status, response) = post(
            "/api/soap/eyefilm/v1/upload",
            "",
            "multipart/form-data; boundary=7d33a816d302b6",
            body,
            chunked = true,
        )
        assertEquals(200, status)
        assertTrue(response.contains("<success>true</success>"))
    }

    // ------------------------------------------------------------------

    private fun startSessionXml(): ByteArray = """
        <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
        <SOAP-ENV:Body><StartSession xmlns="http://localhost/api/soap/eyefilm">
        <macaddress>$mac</macaddress>
        <cnonce>$cnonce</cnonce>
        <transfermode>2</transfermode>
        <transfermodetimestamp>1234567890</transfermodetimestamp>
        </StartSession></SOAP-ENV:Body></SOAP-ENV:Envelope>
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

    private fun testTar(name: String): ByteArray {
        val content = ByteArray(1024) { (it % 251).toByte() }
        val header = ByteArray(512)
        name.toByteArray(StandardCharsets.UTF_8).copyInto(header)
        val sizeOctal = content.size.toString(8).padStart(11, '0') + "\u0000"
        sizeOctal.toByteArray(StandardCharsets.US_ASCII).copyInto(header, 124)
        header[156] = '0'.code.toByte()

        val out = java.io.ByteArrayOutputStream()
        out.write(header)
        out.write(content)
        repeat((512 - (content.size % 512)) % 512) { out.write(0) }
        out.write(header)
        out.write(ByteArray(700))
        return out.toByteArray()
    }

    private fun buildMultipart(tar: ByteArray): ByteArray {
        val boundary = "--7d33a816d302b6"
        val head = (
            "$boundary\r\n" +
                "Content-Disposition: form-data; name=\"SOAPENVELOPE\"\r\n" +
                "Content-Type: application/x-www-form-urlencoded\r\n" +
                "\r\n" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">\r\n" +
                "<SOAP-ENV:Body><UploadPhoto><filename>000exec.jpg</filename>" +
                "<macaddress>$mac</macaddress>" +
                "<fileid>1</fileid></UploadPhoto></SOAP-ENV:Body></SOAP-ENV:Envelope>\r\n" +
                "$boundary\r\n" +
                "Content-Disposition: form-data; name=\"FILENAME\"; filename=\"abc.tar\"\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "\r\n"
            ).toByteArray(StandardCharsets.UTF_8)
        val tail = "\r\n$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
        return head + tar + tail
    }
}
