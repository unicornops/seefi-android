package com.unicornops.seefi.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unicornops.seefi.data.CardConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val wifiConfig by viewModel.wifiConfig.collectAsStateWithLifecycle()

    var wifiSsid by remember(wifiConfig.ssid) { mutableStateOf(wifiConfig.ssid) }
    var wifiPassword by remember(wifiConfig.password) { mutableStateOf(wifiConfig.password) }
    var editingCard by remember { mutableStateOf<CardConfiguration?>(null) }
    var showCardDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editingCard = null; showCardDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add card")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Card Wi-Fi network", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = { wifiSsid = it },
                            label = { Text("SSID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = wifiPassword,
                            onValueChange = { wifiPassword = it },
                            label = { Text("Password (leave empty for open networks)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        TextButton(onClick = { viewModel.saveWifiConfig(wifiSsid, wifiPassword) }) {
                            Text("Save Wi-Fi settings")
                        }
                    }
                }
            }

            item {
                Text("Pro/X2 cards", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Mobi cards need no configuration. For Pro/X2 cards add the MAC " +
                        "address and upload key from the card's Settings.xml.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(cards, key = { it.id }) { card ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "MAC ${card.macAddress}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editingCard = card; showCardDialog = true }) {
                            Text("Edit", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { viewModel.deleteCard(card.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete card")
                        }
                    }
                }
            }
        }
    }

    if (showCardDialog) {
        CardDialog(
            initial = editingCard,
            onDismiss = { showCardDialog = false },
            onSave = { mac, key, name ->
                viewModel.saveCard(editingCard?.id, mac, key, name)
                showCardDialog = false
            },
        )
    }
}

@Composable
private fun CardDialog(
    initial: CardConfiguration?,
    onDismiss: () -> Unit,
    onSave: (mac: String, uploadKey: String, name: String) -> Unit,
) {
    var mac by remember { mutableStateOf(initial?.macAddress ?: "") }
    var uploadKey by remember { mutableStateOf(initial?.uploadKey ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add card" else "Edit card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("MAC address") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uploadKey,
                    onValueChange = { uploadKey = it },
                    label = { Text("Upload key") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(mac, uploadKey, name) },
                enabled = mac.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
