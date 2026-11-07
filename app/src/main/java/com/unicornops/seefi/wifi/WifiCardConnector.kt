package com.unicornops.seefi.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion

/**
 * Joins the phone to the card's own Wi-Fi network.
 *
 * On Android 10+ the WifiNetworkSuggestion API is used: the OS keeps
 * the credentials and connects automatically when the card's network is
 * in range. The user sees the OS dialog the first time and can accept
 * or decline.
 */
class WifiCardConnector(private val context: Context) {

    sealed interface Result {
        data object Suggested : Result
        data object Success : Result
        data class Failure(val reason: Int) : Result
    }

    private val wifiManager: WifiManager?
        get() = context.getSystemService(WifiManager::class.java)

    /**
     * Registers a network suggestion for the card's network. Returns
     * whether the suggestion was accepted.
     */
    fun suggestNetwork(ssid: String, password: String): Boolean {
        val manager = wifiManager ?: return false
        if (ssid.isBlank()) return false

        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .setIsAppInteractionRequired(false)
        if (password.isNotEmpty()) {
            builder.setWpa2Passphrase(password)
        }
        val suggestion = builder.build()

        val result = manager.addNetworkSuggestions(listOf(suggestion))
        return result == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
    }

    /** Whether the phone is currently connected to the card's network. */
    fun isCurrentlyConnected(ssid: String): Boolean {
        val manager = wifiManager ?: return false
        @Suppress("DEPRECATION")
        val info = manager.connectionInfo ?: return false
        //noinspection missing permission: SSID only needs location for third-party apps pre-Q.
        val currentSsid = info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: return false
        return currentSsid.equals(ssid, ignoreCase = true)
    }
}
