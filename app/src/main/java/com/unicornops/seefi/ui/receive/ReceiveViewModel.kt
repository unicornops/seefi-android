package com.unicornops.seefi.ui.receive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unicornops.seefi.AppContainer
import com.unicornops.seefi.SeefiApplication
import com.unicornops.seefi.data.WifiConfig
import com.unicornops.seefi.server.ServerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ReceiveViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<ServerState.UiState> = container.serverState.uiState

    val wifiConfig: StateFlow<WifiConfig> = container.settingsRepository.wifiConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, WifiConfig.EMPTY)

    /** Registers a Wi-Fi suggestion for the card network (best effort). */
    fun connectToCard(context: Context) {
        val config = wifiConfig.value
        if (!config.isValid) return
        val suggested = container.wifiCardConnector.suggestNetwork(config.ssid, config.password)
        container.serverState.addEvent(
            if (suggested) {
                "Wi-Fi suggestion registered for \"${config.ssid}\" - connect via the system dialog"
            } else {
                "Could not register Wi-Fi suggestion - join \"${config.ssid}\" from system settings"
            },
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SeefiApplication
                ReceiveViewModel(app.container)
            }
        }
    }
}
