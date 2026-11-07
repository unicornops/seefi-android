package com.unicornops.seefi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.unicornops.seefi.ui.gallery.GalleryScreen
import com.unicornops.seefi.ui.receive.ReceiveScreen
import com.unicornops.seefi.ui.settings.SettingsScreen
import com.unicornops.seefi.ui.theme.SeefiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeefiTheme {
                SeefiHome()
            }
        }
    }
}

private enum class AppTab(val label: String) {
    RECEIVE("Receive"),
    GALLERY("Gallery"),
    SETTINGS("Settings"),
}

@Composable
fun SeefiHome() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.RECEIVE) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.RECEIVE,
                    onClick = { selectedTab = AppTab.RECEIVE },
                    icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                    label = { Text(AppTab.RECEIVE.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.GALLERY,
                    onClick = { selectedTab = AppTab.GALLERY },
                    icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                    label = { Text(AppTab.GALLERY.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.SETTINGS,
                    onClick = { selectedTab = AppTab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(AppTab.SETTINGS.label) },
                )
            }
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(innerPadding),
        ) {
            when (selectedTab) {
                AppTab.RECEIVE -> ReceiveScreen()
                AppTab.GALLERY -> GalleryScreen(refreshKey = selectedTab)
                AppTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
