package com.unicornops.seefi.data

import kotlinx.serialization.Serializable

/** A configured Pro/X2 card whose upload key is used for authentication. */
@Serializable
data class CardConfiguration(
    val id: String,
    val macAddress: String,
    val uploadKey: String,
    val name: String,
) {
    companion object {
        fun new(macAddress: String, uploadKey: String, name: String): CardConfiguration =
            CardConfiguration(
                id = java.util.UUID.randomUUID().toString(),
                macAddress = macAddress.trim(),
                uploadKey = uploadKey.trim(),
                name = name.ifBlank { "Pro Card" },
            )
    }
}

/** The card's own Wi-Fi network the phone should join. */
@Serializable
data class WifiConfig(
    val ssid: String,
    val password: String,
) {
    val isValid: Boolean get() = ssid.isNotBlank()

    companion object {
        val EMPTY = WifiConfig("", "")
    }
}
