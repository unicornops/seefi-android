package com.unicornops.seefi.protocol

import java.security.MessageDigest

/**
 * Computes credentials for Wi-Fi SD card authentication (EyeFi protocol).
 *
 * Mobi series: the key is 32 zero bytes ("0" * 32 as a hex string).
 * Pro/X2 series: the key is the card's upload_key from its Settings.xml.
 */
object CredentialCalculator {

    const val MOBI_KEY: String = "00000000000000000000000000000000"

    /**
     * Credential sent by the server in the StartSession response.
     * Formula: MD5(hexToBytes(mac + cnonce + key)) as lowercase hex.
     */
    fun credentialServerToClient(mac: String, cnonce: String, key: String = MOBI_KEY): String =
        md5Hex(mac + cnonce + key)

    /**
     * Credential expected from the card in the GetPhotoStatus request.
     * Formula: MD5(hexToBytes(mac + key + snonce)) as lowercase hex.
     */
    fun credentialClientToServer(mac: String, snonce: String, key: String = MOBI_KEY): String =
        md5Hex(mac + key + snonce)

    /**
     * Interprets [hexString] as a sequence of hexadecimal byte values
     * (two characters per byte, as in eyefi-mobi.py), hashes the bytes
     * with MD5 and returns the lowercase hex digest.
     */
    fun md5Hex(hexString: String): String {
        val bytes = hexStringToBytes(hexString)
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { String.format("%02x", it) }
    }

    private fun hexStringToBytes(hex: String): ByteArray {
        val bytes = ArrayList<Byte>(hex.length / 2)
        var i = 0
        while (i + 1 < hex.length) {
            val pair = hex.substring(i, i + 2)
            pair.toIntOrNull(16)?.let { value ->
                bytes.add(value.toByte())
            }
            i += 2
        }
        return bytes.toByteArray()
    }
}
