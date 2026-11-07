package com.unicornops.seefi.protocol

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * HTTP server implementing the Wi-Fi SD card (EyeFi) SOAP protocol.
 *
 * The card connects to the phone over the card's own Wi-Fi network and
 * pushes photos:
 *
 *  - StartSession         authentication handshake
 *  - GetPhotoStatus       card verification before upload
 *  - Upload (multipart)   TAR file upload containing the photos
 *  - MarkLastPhotoInRoll  end of a photo roll
 *
 * The wire protocol is plain HTTP/1.1 on port 59278, so this class
 * depends only on the JDK and is fully unit-testable on the JVM.
 */
class EyeFiServer(
    private val port: Int,
    /**
     * Returns the upload key configured for a card MAC address. Cards
     * without an entry authenticate with the zero "Mobi" key.
     */
    private val uploadKeyForMac: (mac: String) -> String = { CredentialCalculator.MOBI_KEY },
    /**
     * Handles one uploaded TAR archive. Returns the number of photos
     * saved, or 0 when the archive could not be processed.
     */
    private val onUpload: (tarData: ByteArray, soapEnvelope: String) -> Int,
) {

    private val isRunning = AtomicBoolean(false)
    private val currentSnonce = AtomicReference<String?>(null)
    private val random = SecureRandom()

    @Volatile private var serverSocket: ServerSocket? = null
    private val acceptExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "eyefi-server-accept").apply { isDaemon = true }
    }
    private val connectionExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "eyefi-server-connection").apply { isDaemon = true }
    }

    /** The local port the server bound to (useful when [port] is 0 for tests). */
    val boundPort: Int get() = serverSocket?.localPort ?: -1

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        val socket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
        serverSocket = socket
        acceptExecutor.execute {
            try {
                while (isRunning.get()) {
                    val client = socket.accept()
                    connectionExecutor.execute { handleConnection(client) }
                }
            } catch (expected: SocketException) {
                // Socket closed by stop().
            } catch (unexpected: IOException) {
                if (isRunning.get()) unexpected.printStackTrace()
            }
        }
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        currentSnonce.set(null)
    }

    val running: Boolean get() = isRunning.get()

    // ------------------------------------------------------------------
    // HTTP plumbing

    private class Request(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        val keepAlive: Boolean
            get() {
                val connection = headers["connection"]?.lowercase()
                return when {
                    connection == "close" -> false
                    connection == "keep-alive" -> true
                    else -> true
                }
            }
    }

    private class Response(
        val status: Int,
        val reason: String,
        val body: ByteArray,
        val contentType: String,
    ) {
        companion object {
            fun xml(status: Int = 200, reason: String = "OK", xml: String) =
                Response(status, reason, xml.toByteArray(Charsets.UTF_8), "text/xml; charset=\"utf-8\"")

            fun error() = Response(200, "OK", ByteArray(0), "text/xml")
        }
    }

    private fun handleConnection(socket: Socket) {
        socket.use { client ->
            try {
                val input = BufferedInputStream(client.getInputStream())
                val output = BufferedOutputStream(client.getOutputStream())
                while (isRunning.get() && !client.isClosed) {
                    val request = readRequest(input) ?: break
                    val keepAlive = request.keepAlive
                    val response = dispatch(request)
                    writeResponse(output, response, keepAlive)
                    output.flush()
                    if (!keepAlive) break
                }
            } catch (expected: SocketException) {
                // Client dropped the connection.
            } catch (unexpected: IOException) {
                // Connection-level failures end the conversation quietly.
            }
        }
    }

    private fun readRequest(input: BufferedInputStream): Request? {
        val requestLine = readLine(input) ?: return null
        if (requestLine.isBlank()) return null
        val parts = requestLine.split(" ")
        if (parts.size < 3) return null
        val method = parts[0]
        val path = parts[1]

        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
        }

        val body = when {
            headers["transfer-encoding"]?.lowercase()?.contains("chunked") == true ->
                readChunkedBody(input)
            headers["content-length"]?.toLongOrNull()?.let { it > 0 } == true ->
                readBytes(input, headers["content-length"]!!.toInt())
            else -> ByteArray(0)
        }

        return Request(method, path, headers, body)
    }

    private fun readLine(input: BufferedInputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (builder.isEmpty()) null else builder.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) builder.append(b.toChar())
        }
        return builder.toString()
    }

    private fun readBytes(input: BufferedInputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n == -1) break
            read += n
        }
        return if (read == length) buffer else buffer.copyOf(read)
    }

    private fun readChunkedBody(input: BufferedInputStream): ByteArray {
        val chunks = ArrayList<ByteArray>()
        while (true) {
            val sizeLine = readLine(input) ?: break
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) break
            chunks.add(readBytes(input, size))
            readLine(input) // trailing CRLF after each chunk
        }
        val total = chunks.sumOf { it.size }
        val body = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(body, offset)
            offset += chunk.size
        }
        return body
    }

    private fun writeResponse(output: BufferedOutputStream, response: Response, keepAlive: Boolean) {
        val head = buildString {
            append("HTTP/1.1 ").append(response.status).append(' ').append(response.reason).append("\r\n")
            append("Content-Type: ").append(response.contentType).append("\r\n")
            append("Content-Length: ").append(response.body.size).append("\r\n")
            append("Connection: ").append(if (keepAlive) "keep-alive" else "close").append("\r\n")
            append("\r\n")
        }
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.write(response.body)
    }

    // ------------------------------------------------------------------
    // Protocol dispatch

    private fun dispatch(request: Request): Response {
        if (request.method != "POST") return Response.error()

        val soapAction = request.headers["soapaction"] ?: ""
        val bodyText = request.body.toString(Charsets.UTF_8)

        if (!request.path.endsWith("/api/soap/eyefilm/v1/upload")) {
            when {
                soapAction.contains("StartSession") -> return handleStartSession(bodyText)
                soapAction.contains("GetPhotoStatus") -> return handleGetPhotoStatus(bodyText)
                soapAction.contains("MarkLastPhotoInRoll") -> {
                    return Response.xml(xml = SoapMessages.markLastPhotoInRollResponse())
                }
            }
            return Response.error() // unknown SOAP action
        }

        val contentType = request.headers["content-type"]
            ?: return Response.error()
        val boundary = MultipartParser.boundaryOf(contentType)
            ?: return Response.error()
        val parts = MultipartParser.parse(request.body, boundary)

        val soapPart = MultipartParser.soapEnvelope(parts)
        val tarPart = MultipartParser.tarPart(parts)
        if (soapPart == null || tarPart == null) return Response.error()

        val saved = onUpload(tarPart.body, soapPart.bodyText())
        return Response.xml(xml = SoapMessages.uploadPhotoResponse(saved > 0))
    }

    private fun handleStartSession(bodyText: String): Response {
        val request = SoapMessages.parseStartSession(bodyText) ?: return Response.error()

        val key = uploadKeyForMac(request.macaddress)
        val credential = CredentialCalculator.credentialServerToClient(request.macaddress, request.cnonce, key)
        val snonce = randomHexString(32)
        currentSnonce.set(snonce)

        return Response.xml(
            xml = SoapMessages.startSessionResponse(
                credential = credential,
                snonce = snonce,
                transfermode = request.transfermode,
                transfermodetimestamp = request.transfermodetimestamp,
            ),
        )
    }

    private fun handleGetPhotoStatus(bodyText: String): Response {
        val request = SoapMessages.parseGetPhotoStatus(bodyText) ?: return Response.error()
        val snonce = currentSnonce.get() ?: return Response.error()

        val key = uploadKeyForMac(request.macaddress)
        val expected = CredentialCalculator.credentialClientToServer(request.macaddress, snonce, key)

        if (!expected.equals(request.credential, ignoreCase = true)) {
            return Response(200, "OK", SoapMessages.NICE_TRY_RESPONSE.toByteArray(Charsets.UTF_8), "text/plain")
        }

        return Response.xml(xml = SoapMessages.getPhotoStatusResponse())
    }

    private fun randomHexString(length: Int): String {
        val chars = "0123456789abcdef"
        return buildString {
            repeat(length) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }
}
