package com.unicornops.seefi.ui.receive

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unicornops.seefi.server.ServerState
import com.unicornops.seefi.service.ReceiveServerService

@Composable
fun ReceiveScreen(viewModel: ReceiveViewModel = viewModel(factory = ReceiveViewModel.Factory)) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val wifiConfig by viewModel.wifiConfig.collectAsStateWithLifecycle()
    var missingWifiDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    fun startReceiving() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        notificationPermissionLauncher.launch(permissions.toTypedArray())
        if (!wifiConfig.isValid) {
            missingWifiDialog = true
            return
        }
        context.startService(
            Intent(context, ReceiveServerService::class.java)
                .setAction(ReceiveServerService.ACTION_START),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Receive photos", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val status = uiState.status
                when (status) {
                    is ServerState.Status.Running -> Text(
                        "Server running on port ${status.port}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is ServerState.Status.Error -> Text(
                        "Error: ${status.message}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text(
                        "Server stopped",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text("Photos received this session: ${uiState.photosReceived}")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.status is ServerState.Status.Running) {
                        OutlinedButton(onClick = {
                            context.startService(
                                Intent(context, ReceiveServerService::class.java)
                                    .setAction(ReceiveServerService.ACTION_STOP),
                            )
                        }) {
                            Text("Stop receiving")
                        }
                    } else {
                        Button(onClick = ::startReceiving) {
                            Text("Start receiving")
                        }
                    }
                    if (wifiConfig.isValid) {
                        OutlinedButton(onClick = { viewModel.connectToCard(context) }) {
                            Text("Join card Wi-Fi")
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("How it works", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "1. Put the SD card in your camera and enable its Wi-Fi mode.\n" +
                        "2. Start receiving here.\n" +
                        "3. Join the card's Wi-Fi network (configure it in Settings).\n" +
                        "4. Take photos - they are transferred automatically and saved " +
                        "to Pictures/Seefi.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Text("Activity", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (uiState.events.isEmpty()) {
                item {
                    Text(
                        "Nothing yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(uiState.events) { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        event.timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(event.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (missingWifiDialog) {
        AlertDialog(
            onDismissRequest = { missingWifiDialog = false },
            title = { Text("No card Wi-Fi configured") },
            text = {
                Text(
                    "Set the SSID (and password) of your card's network in Settings " +
                        "so Seefi can join it automatically.",
                )
            },
            confirmButton = {
                TextButton(onClick = { missingWifiDialog = false }) { Text("OK") }
            },
        )
    }
}
