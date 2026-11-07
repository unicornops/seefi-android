package com.unicornops.seefi.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialCalculatorTest {

    private val mac = "0018562c1234"
    private val cnonce = "0123456789abcdef0123456789abcdef"
    private val snonce = "fedcba9876543210fedcba9876543210"
    private val proKey = "a1b2c3d4e5f60718293a4b5c6d7e8f90"

    @Test
    fun `mobi server to client credential`() {
        // Reference computed independently: MD5 over the bytes of
        // mac + cnonce + 32 zero bytes expressed as a hex string.
        assertEquals(
            "2dea7b766fe6824aae1f2a459c614332",
            CredentialCalculator.credentialServerToClient(mac, cnonce),
        )
    }

    @Test
    fun `mobi client to server credential`() {
        assertEquals(
            "9a509caf687daa993aab85d44ad6ca82",
            CredentialCalculator.credentialClientToServer(mac, cnonce),
        )
    }

    @Test
    fun `client to server uses snonce not cnonce`() {
        assertEquals(
            "911f8603f645b0f1ec73732504eadd1f",
            CredentialCalculator.credentialClientToServer(mac, snonce),
        )
    }

    @Test
    fun `pro key changes the credential`() {
        assertEquals(
            "069cd9cc3d7ec1d3ea58a07cff6fa105",
            CredentialCalculator.credentialServerToClient(mac, cnonce, proKey),
        )
    }

    @Test
    fun `odd length hex input drops the trailing character`() {
        // The reference implementation reads the input two characters
        // at a time; a trailing half-byte is ignored.
        val even = CredentialCalculator.md5Hex("ab")
        val odd = CredentialCalculator.md5Hex("abc")
        assertEquals(even, odd)
    }

    @Test
    fun `digest is lowercase hex`() {
        val digest = CredentialCalculator.credentialServerToClient(mac, cnonce)
        assert(digest.matches(Regex("[0-9a-f]{32}")))
    }
}
