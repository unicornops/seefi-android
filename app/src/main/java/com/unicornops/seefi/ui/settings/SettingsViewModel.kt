package com.unicornops.seefi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unicornops.seefi.AppContainer
import com.unicornops.seefi.SeefiApplication
import com.unicornops.seefi.data.CardConfiguration
import com.unicornops.seefi.data.WifiConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val cards: StateFlow<List<CardConfiguration>> = container.settingsRepository.cardConfigurations
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val wifiConfig: StateFlow<WifiConfig> = container.settingsRepository.wifiConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, WifiConfig.EMPTY)

    fun saveWifiConfig(ssid: String, password: String) {
        viewModelScope.launch {
            container.settingsRepository.saveWifiConfig(WifiConfig(ssid.trim(), password))
        }
    }

    fun saveCard(cardId: String?, macAddress: String, uploadKey: String, name: String) {
        if (macAddress.isBlank()) return
        viewModelScope.launch {
            val existing = cards.value.firstOrNull { it.id == cardId }
            val card = if (existing != null) {
                existing.copy(
                    macAddress = macAddress.trim(),
                    uploadKey = uploadKey.trim(),
                    name = name.ifBlank { existing.name },
                )
            } else {
                CardConfiguration.new(macAddress, uploadKey, name)
            }
            container.settingsRepository.saveCardConfiguration(card)
        }
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            container.settingsRepository.deleteCardConfiguration(cardId)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SeefiApplication
                SettingsViewModel(app.container)
            }
        }
    }
}
