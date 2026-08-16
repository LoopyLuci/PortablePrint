package com.streamsync.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streamsync.android.StreamSyncApp
import com.streamsync.android.model.*
import com.streamsync.android.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    navController: NavController
) {
    val app = StreamSyncApp.instance
    val discoveredDevices by app.serviceManager.discoveryService.discoveredDevices.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val device = discoveredDevices.find { it.deviceId == deviceId }

    var showFilePicker by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var urlToSend by remember { mutableStateOf("") }
    var textToSend by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val filename = uri.lastPathSegment ?: "file"
                app.serviceManager.transferService.sendFile(
                    device = device ?: return@launch,
                    uri = it,
                    filename = filename,
                    mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                )
            }
        }
    }

    if (device == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Device not found")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Device header
        TopAppBar(
            title = { Text(device.deviceName) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // Device info card
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (device.deviceType == DeviceType.DEVICE_ANDROID) SyncGreen.copy(alpha = 0.15f)
                        else SyncBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (device.deviceType) {
                            DeviceType.DEVICE_ANDROID -> Icons.Filled.PhoneAndroid
                            DeviceType.DEVICE_IOS -> Icons.Filled.PhoneIphone
                            DeviceType.DEVICE_DESKTOP_WINDOWS -> Icons.Filled.Computer
                            DeviceType.DEVICE_DESKTOP_MACOS -> Icons.Filled.Computer
                            DeviceType.DEVICE_DESKTOP_LINUX -> Icons.Filled.Computer
                            else -> Icons.Filled.DevicesOther
                        },
                        contentDescription = null,
                        tint = if (device.deviceType == DeviceType.DEVICE_ANDROID) SyncGreen else SyncBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    device.deviceName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${device.deviceType.name.replace("DEVICE_", "")} · ${device.ipAddress}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Protocol",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "v${device.protocolVersion}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Signal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${device.signalStrength}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Capabilities",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${device.capabilities.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Capabilities chips
                if (device.capabilities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        device.capabilities.forEach { cap ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        cap.feature.replace("_", " "),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Actions
        Text(
            "Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                DeviceAction(
                    icon = Icons.Filled.FileUpload,
                    title = "Send Files",
                    subtitle = "Transfer files to ${device.deviceName}",
                    onClick = { filePickerLauncher.launch("*/*") }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                DeviceAction(
                    icon = Icons.Filled.Link,
                    title = "Send URL",
                    subtitle = "Share a link with ${device.deviceName}",
                    onClick = { showUrlDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                DeviceAction(
                    icon = Icons.Filled.TextFields,
                    title = "Send Text",
                    subtitle = "Send a text snippet",
                    onClick = { showTextDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                DeviceAction(
                    icon = Icons.Filled.Cast,
                    title = "Stream Content",
                    subtitle = "Stream media to ${device.deviceName}",
                    onClick = { navController.navigate(Screen.Stream.route) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                DeviceAction(
                    icon = Icons.Filled.Link,
                    title = if (device.isPaired) "Unpair Device" else "Pair Device",
                    subtitle = if (device.isPaired) "Remove pairing" else "Establish trusted connection",
                    onClick = { /* Pair/unpair logic */ }
                )
            }
        }
    }

    // URL Dialog
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Send URL") },
            text = {
                OutlinedTextField(
                    value = urlToSend,
                    onValueChange = { urlToSend = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.serviceManager.transferService.sendUrl(device, urlToSend)
                    }
                    showUrlDialog = false
                    urlToSend = ""
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Text Dialog
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Send Text") },
            text = {
                OutlinedTextField(
                    value = textToSend,
                    onValueChange = { textToSend = it },
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.serviceManager.transferService.sendText(device, textToSend)
                    }
                    showTextDialog = false
                    textToSend = ""
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DeviceAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}
