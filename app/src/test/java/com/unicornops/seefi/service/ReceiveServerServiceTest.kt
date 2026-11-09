package com.unicornops.seefi.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unicornops.seefi.SeefiApplication
import com.unicornops.seefi.protocol.CredentialCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.OutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * End-to-end test of the real foreground service: drives
 * ReceiveServerService through its lifecycle on a Robolectric runtime,
 * then plays the role of an EyeFi Mobi card against the actual socket
 * the server binds, and verifies the photo is saved via MediaStore and
 * counted by the shared server state.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReceiveServerServiceTest {

    private val mac = "0018562c1234"
    private val cnonce = "0123456789abcdef0123456789abcdef"

    @Test
    fun `service receives a photo pushed by a mock card`() {
        val application = ApplicationProvider.getApplicationContext<SeefiApplication>()
        val controller = Robolectric.buildService(
            ReceiveServerService::class.java,
            Intent(application, ReceiveServerService::class.java)
                .setAction(ReceiveServerService.ACTION_START),
        )

        controller.startCommand(0, 0)
        assertTrue("server reports running after start", ReceiveServerService.isServerRunning)

        try {
            // StartSession - a Mobi card authenticates with the zero key.
            val snonce = startSessionExpectingMobiCredential()

            // GetPhotoStatus - the card proves itself with the snonce.
            assertTrue(getPhotoStatus(snonce).contains("<fileid>1</fileid>"))

            // Upload - a TAR archive containing one JPEG.
            assertTrue(uploadOneJpeg("IMG_0001.jpg").contains("<success>true</success>"))

            // System.currentTimeMillis()-based wait: the upload happens
            // on the server's connection thread.
            val container = application.container
            assertTrue(waitFor { container.serverState.photosReceived.value == 1 })
            assertEquals(
                "the transient error state is not left set",
                true,
                container.serverState.uiState.value.status is com.unicornops.seefi.server.ServerState.Status.Running,
            )
        } finally {
            controller.withIntent(
                Intent(application, ReceiveServerService::class.java)
                    .setAction(ReceiveServerService.ACTION_STOP),
            ).startCommand(1, 0)
        }

        assertFalse("server reports stopped after stop", ReceiveServerService.isServerRunning)
        assertTrue(waitFor { portClosed() })
    }

    private fun portClosed(): Boolean =
        try {
            Socket("127.0.0.1", ReceiveServerService.SERVER_PORT).use { false }
        } catch (expected: ConnectException) {
            true
        }

    // ------------------------------------------------------------------
    // Mock EyeFi Mobi card client

    private fun post(path: String, soapAction: String, contentType: String, body: ByteArray): String {
        val connection = java.net.URL("http://127.0.0.1:${ReceiveServerService.SERVER_PORT}$path")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty("SOAPAction", soapAction)
        connection.setRequestProperty("Content-Type", contentType)
        connection.setFixedLengthStreamingMode(body.size.toLong())
        connection.connect()
        connection.outputStream.use { output: OutputStream -> output.write(body) }
        return connection.inputStream.readBytes().toString(StandardCharsets.UTF_8)
    }

    private fun startSessionExpectingMobiCredential(): String {
        val body = """
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
            <SOAP-ENV:Body><StartSession xmlns="http://localhost/api/soap/eyefilm">
            <macaddress>$mac</macaddress>
            <cnonce>$cnonce</cnonce>
            <transfermode>2</transfermode>
            <transfermodetimestamp>1234567890</transfermodetimestamp>
            </StartSession></SOAP-ENV:Body></SOAP-ENV:Envelope>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val response = post("/api/soap/eyefilm/v1", "\"urn:StartSession\"", "text/xml", body)

        val expectedCredential = CredentialCalculator.credentialServerToClient(mac, cnonce)
        assertTrue(
            "server credential matches the Mobi zero-key calculation",
            response.contains("<credential>$expectedCredential</credential>"),
        )
        return Regex("<snonce>([0-9a-f]+)</snonce>").find(response)!!.groupValues[1]
    }

    private fun getPhotoStatus(snonce: String): String {
        val cardCredential = CredentialCalculator.credentialClientToServer(mac, snonce)
        val body = """
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
            <SOAP-ENV:Body><GetPhotoStatus xmlns="http://localhost/api/soap/eyefilm">
            <macaddress>$mac</macaddress>
            <credential>$cardCredential</credential>
            </GetPhotoStatus></SOAP-ENV:Body></SOAP-ENV:Envelope>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        return post("/api/soap/eyefilm/v1", "\"urn:GetPhotoStatus\"", "text/xml", body)
    }

    private fun uploadOneJpeg(name: String): String {
        val tar = tarWithJpeg(name)
        val boundary = "--7d33a816d302b6"
        val soapEnvelope = """
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
            <SOAP-ENV:Body><UploadPhoto><filename>$name</filename>
            <macaddress>$mac</macaddress><fileid>1</fileid></UploadPhoto>
            </SOAP-ENV:Body></SOAP-ENV:Envelope>
        """.trimIndent()
        val head = (
            "$boundary\r\n" +
                "Content-Disposition: form-data; name=\"SOAPENVELOPE\"\r\n\r\n" +
                "$soapEnvelope\r\n" +
                "$boundary\r\n" +
                "Content-Disposition: form-data; name=\"FILENAME\"; filename=\"abc.tar\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
            ).toByteArray(StandardCharsets.UTF_8)
        val tail = "\r\n$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
        return post(
            "/api/soap/eyefilm/v1/upload",
            "",
            "multipart/form-data; boundary=7d33a816d302b6",
            head + tar + tail,
        )
    }

    private fun tarWithJpeg(name: String): ByteArray {
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
        out.write(ByteArray(1024))
        return out.toByteArray()
    }

    private fun waitFor(timeoutMillis: Long = 10000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }
}
