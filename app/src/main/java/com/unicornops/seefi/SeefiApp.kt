package com.unicornops.seefi

import android.app.Application
import com.unicornops.seefi.data.PhotoStore
import com.unicornops.seefi.data.SettingsRepository
import com.unicornops.seefi.server.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple constructor-based dependency container. */
class AppContainer(application: Application) {
    val settingsRepository = SettingsRepository(application)
    val photoStore = PhotoStore(application)
    val serverState = ServerState(
        scope = applicationScope,
        settings = settingsRepository,
    )
    val wifiCardConnector = com.unicornops.seefi.wifi.WifiCardConnector(application)

    companion object {
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

class SeefiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
