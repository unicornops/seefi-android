package com.unicornops.seefi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "seefi_settings")

/**
 * Settings store for card configurations and the card Wi-Fi network.
 * Flows update automatically; values are persisted as JSON.
 */
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cardsKey = stringPreferencesKey("cardConfigurations")
    private val wifiKey = stringPreferencesKey("wifiConfig")

    val cardConfigurations: Flow<List<CardConfiguration>> =
        context.settingsDataStore.data.map { preferences ->
            preferences[cardsKey]?.let(::decodeCards) ?: emptyList()
        }

    val wifiConfig: Flow<WifiConfig> =
        context.settingsDataStore.data.map { preferences ->
            preferences[wifiKey]?.let(::decodeWifi) ?: WifiConfig.EMPTY
        }

    suspend fun saveCardConfiguration(card: CardConfiguration) {
        val current = cardConfigurations.first().toMutableList()
        val index = current.indexOfFirst { it.id == card.id }
        if (index >= 0) current[index] = card else current.add(card)
        writeCards(current)
    }

    suspend fun deleteCardConfiguration(cardId: String) {
        writeCards(cardConfigurations.first().filterNot { it.id == cardId })
    }

    suspend fun saveWifiConfig(config: WifiConfig) {
        context.settingsDataStore.edit { preferences ->
            preferences[wifiKey] = json.encodeToString(WifiConfig.serializer(), config)
        }
    }

    private suspend fun writeCards(cards: List<CardConfiguration>) {
        context.settingsDataStore.edit { preferences ->
            preferences[cardsKey] =
                json.encodeToString(ListSerializer(CardConfiguration.serializer()), cards)
        }
    }

    private fun decodeCards(value: String): List<CardConfiguration> =
        runCatching {
            json.decodeFromString(ListSerializer(CardConfiguration.serializer()), value)
        }.getOrDefault(emptyList())

    private fun decodeWifi(value: String): WifiConfig =
        runCatching {
            json.decodeFromString(WifiConfig.serializer(), value)
        }.getOrDefault(WifiConfig.EMPTY)
}
