package com.streamsync.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streamsync.android.StreamSyncApp
import com.streamsync.android.model.DeviceType
import com.streamsync.android.model.Device
import com.streamsync.android.ui.navigation.Screen
import com.streamsync.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(navController: NavController) {
    val app = StreamSyncApp.instance
    val discoveredDevices by app.serviceManager.discoveryService.discoveredDevices.collectAsState()
    val isDiscovering by app.serviceManager.discoveryService.isDiscovering.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        TopAppBar(
            title = {
                Text("Devices", fontWeight = FontWeight.Bold)
            },
            actions = {
                IconButton(onClick = {
                    if (isDiscovering) {
                        app.serviceManager.discoveryService.stopDiscovery()
                    } else {
                        app.serviceManager.discoveryService.startDiscovery()
                    }
                }) {
                    Icon(
                        if (isDiscovering) Icons.Filled.SearchOff else Icons.Filled.Search,
                        contentDescription = if (isDiscovering) "Stop scanning" else "Scan"
                    )
                }
            }
        )

        if (discoveredDevices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isDiscovering) Icons.Outlined.NetworkWifi else Icons.Outlined.DevicesOther,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (isDiscovering) "Scanning your network..."
                        else "No devices found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isDiscovering) "Keep StreamSync open on other devices"
                        else "Tap the search icon to scan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (isDiscovering) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    if (isDiscovering) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Scanning... ${discoveredDevices.size} devices found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(discoveredDevices, key = { it.deviceId }) { device ->
                    DeviceCard(
                        device = device,
                        onClick = {
                            navController.navigate(Screen.DeviceDetail.createRoute(device.deviceId))
                        },
                        onSendFile = {
                            navController.navigate(Screen.DeviceDetail.createRoute(device.deviceId))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: Device,
    onClick: () -> Unit,
    onSendFile: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(getDeviceColor(device.deviceType).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    getDeviceIcon(device.deviceType),
                    contentDescription = null,
                    tint = getDeviceColor(device.deviceType),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    buildDeviceSubtitle(device),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.capabilities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        device.capabilities.take(3).forEach { cap ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(cap.feature.replace("_", " "), fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Signal indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    when {
                        device.signalStrength > 70 -> Icons.Filled.NetworkWifi
                        device.signalStrength > 30 -> Icons.Filled.NetworkWifi
                        else -> Icons.Filled.NetworkWifi
                    },
                    contentDescription = null,
                    tint = when {
                        device.signalStrength > 70 -> SyncGreen
                        device.signalStrength > 30 -> SyncOrange
                        else -> SyncRed
                    },
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "${device.signalStrength}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Divider and action buttons
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onSendFile) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Send", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { /* Stream */ }) {
                Icon(Icons.Filled.Cast, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stream", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { /* Pair */ }) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pair", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun getDeviceIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.DEVICE_ANDROID -> Icons.Filled.PhoneAndroid
    DeviceType.DEVICE_IOS -> Icons.Filled.PhoneIphone
    DeviceType.DEVICE_DESKTOP_WINDOWS, DeviceType.DEVICE_DESKTOP_MACOS, DeviceType.DEVICE_DESKTOP_LINUX -> Icons.Filled.Computer
    DeviceType.DEVICE_WEB -> Icons.Filled.Language
    DeviceType.DEVICE_TV -> Icons.Filled.Tv
    DeviceType.DEVICE_UNKNOWN -> Icons.Filled.DevicesOther
}

private fun getDeviceColor(type: DeviceType): Color = when (type) {
    DeviceType.DEVICE_ANDROID -> SyncGreen
    DeviceType.DEVICE_IOS -> Color(0xFF007AFF)
    DeviceType.DEVICE_DESKTOP_WINDOWS -> SyncBlue
    DeviceType.DEVICE_DESKTOP_MACOS -> Color(0xFFA2AAAD)
    DeviceType.DEVICE_DESKTOP_LINUX -> SyncOrange
    DeviceType.DEVICE_WEB -> SyncPurple
    DeviceType.DEVICE_TV -> Color(0xFFFF3B30)
    DeviceType.DEVICE_UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun buildDeviceSubtitle(device: Device): String {
    val os = device.osVersion.takeIf { it.isNotBlank() }?.let { "OS $it" } ?: ""
    val type = device.deviceType.name.replace("DEVICE_", "").lowercase().replaceFirstChar { it.uppercase() }
    return listOfNotNull(type, os, device.ipAddress.takeIf { it.isNotBlank() }).joinToString(" · ")
}
