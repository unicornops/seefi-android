package com.unicornops.seefi.protocol

/**
 * Parsing and rendering for the EyeFi SOAP protocol on
 * /api/soap/eyefilm/v1.
 *
 * The cards do not speak strict SOAP; the payloads are simple XML
 * documents whose values are extracted by tag name (with or without a
 * namespace prefix), mirroring the reference implementation.
 */
object SoapMessages {

    /** Extracts the value of [tag], tolerating namespace prefixes on the tag. */
    fun extractXmlValue(xml: String, tag: String): String? {
        val plain = Regex("<$tag>([^<]*)</$tag>").find(xml)?.groupValues?.get(1)
        if (plain != null) return plain.trim()

        val prefixed = Regex("<[^:]+:$tag>([^<]*)</[^:]+:$tag>").find(xml)?.groupValues?.get(1)
        if (prefixed != null) return prefixed.trim()

        return null
    }

    fun parseStartSession(xml: String): StartSessionRequest? {
        val mac = extractXmlValue(xml, "macaddress") ?: return null
        val cnonce = extractXmlValue(xml, "cnonce") ?: return null
        val transferMode = extractXmlValue(xml, "transfermode") ?: return null
        val transferModeTimestamp = extractXmlValue(xml, "transfermodetimestamp") ?: return null
        return StartSessionRequest(mac, cnonce, transferMode, transferModeTimestamp)
    }

    fun parseGetPhotoStatus(xml: String): GetPhotoStatusRequest? {
        val mac = extractXmlValue(xml, "macaddress") ?: return null
        val credential = extractXmlValue(xml, "credential") ?: return null
        return GetPhotoStatusRequest(mac, credential)
    }

    data class StartSessionRequest(
        val macaddress: String,
        val cnonce: String,
        val transfermode: String,
        val transfermodetimestamp: String,
    )

    data class GetPhotoStatusRequest(
        val macaddress: String,
        val credential: String,
    )

    private fun envelope(body: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
        <SOAP-ENV:Body>
        $body
        </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
    """.trimIndent()

    fun startSessionResponse(
        credential: String,
        snonce: String,
        transfermode: String,
        transfermodetimestamp: String,
    ): String = envelope(
        """
        <StartSessionResponse xmlns="http://localhost/api/soap/eyefilm">
        <credential>$credential</credential>
        <snonce>$snonce</snonce>
        <transfermode>$transfermode</transfermode>
        <transfermodetimestamp>$transfermodetimestamp</transfermodetimestamp>
        <upsyncallowed>false</upsyncallowed>
        </StartSessionResponse>
        """.trimIndent(),
    )

    fun getPhotoStatusResponse(fileid: String = "1", offset: String = "0"): String = envelope(
        """
        <GetPhotoStatusResponse xmlns="http://localhost/api/soap/eyefilm">
        <fileid>$fileid</fileid>
        <offset>$offset</offset>
        </GetPhotoStatusResponse>
        """.trimIndent(),
    )

    fun markLastPhotoInRollResponse(): String = envelope(
        """
        <MarkLastPhotoInRollResponse xmlns="http://localhost/api/soap/eyefilm"/>
        """.trimIndent(),
    )

    fun uploadPhotoResponse(success: Boolean): String = envelope(
        """
        <UploadPhotoResponse xmlns="http://localhost/api/soap/eyefilm">
        <success>${if (success) "true" else "false"}</success>
        </UploadPhotoResponse>
        """.trimIndent(),
    )

    /** Classic EyeFiServer reply for a failed credential check. */
    const val NICE_TRY_RESPONSE = "Nice try!"
}
