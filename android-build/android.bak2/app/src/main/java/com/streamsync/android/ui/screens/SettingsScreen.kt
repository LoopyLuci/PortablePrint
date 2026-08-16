package com.streamsync.android.ui.screens

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streamsync.android.StreamSyncApp
import com.streamsync.android.model.StreamingQuality
import com.streamsync.android.model.ThemeMode
import com.streamsync.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val app = StreamSyncApp.instance

    var deviceName by remember { mutableStateOf(Build.MODEL) }
    var autoAccept by remember { mutableStateOf(false) }
    var clipboardSync by remember { mutableStateOf(true) }
    var encryptionEnabled by remember { mutableStateOf(true) }
    var backgroundDiscovery by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var selectedQuality by remember { mutableStateOf(StreamingQuality.AUTO) }
    var qualityExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Device Identity
            Text(
                "Device Identity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Device Name") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This name is visible to other devices on the network",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transfers
            Text(
                "Transfers",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingsSwitch(
                        icon = Icons.Filled.CheckCircle,
                        title = "Auto-accept transfers",
                        subtitle = "Automatically accept incoming files",
                        checked = autoAccept,
                        onCheckedChange = { autoAccept = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitch(
                        icon = Icons.Filled.Lock,
                        title = "Encryption",
                        subtitle = "AES-256-GCM encryption for all transfers",
                        checked = encryptionEnabled,
                        onCheckedChange = { encryptionEnabled = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Streaming
            Text(
                "Streaming Quality",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = qualityExpanded,
                        onExpandedChange = { qualityExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedQuality.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Stream Quality") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = qualityExpanded,
                            onDismissRequest = { qualityExpanded = false }
                        ) {
                            StreamingQuality.entries.forEach { quality ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(quality.label)
                                            Text(
                                                "${quality.width}x${quality.height} · ${quality.bitrateKbps}kbps",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedQuality = quality
                                        qualityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sync
            Text(
                "Sync & Discovery",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingsSwitch(
                        icon = Icons.Filled.ContentPaste,
                        title = "Clipboard Sync",
                        subtitle = "Sync clipboard across all devices",
                        checked = clipboardSync,
                        onCheckedChange = {
                            clipboardSync = it
                            app.serviceManager.clipboardService.setEnabled(it)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitch(
                        icon = Icons.Filled.Search,
                        title = "Background Discovery",
                        subtitle = "Discover devices even when app is in background",
                        checked = backgroundDiscovery,
                        onCheckedChange = { backgroundDiscovery = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Notifications
            Text(
                "Notifications",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingsSwitch(
                        icon = Icons.Filled.Notifications,
                        title = "Transfer Notifications",
                        subtitle = "Show notification for transfers",
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About
            Text(
                "About",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AboutRow("App Name", "StreamSync")
                    AboutRow("Version", "1.0.0")
                    AboutRow("Protocol", "StreamSync v2")
                    AboutRow("Android", "API ${Build.VERSION.SDK_INT}")
                    AboutRow("Device", Build.MODEL)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
