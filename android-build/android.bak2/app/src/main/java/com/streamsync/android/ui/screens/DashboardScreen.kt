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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streamsync.android.StreamSyncApp
import com.streamsync.android.model.TransferStatus
import com.streamsync.android.model.TransferType
import com.streamsync.android.ui.navigation.Screen
import com.streamsync.android.ui.theme.SyncBlue
import com.streamsync.android.ui.theme.SyncGreen
import com.streamsync.android.ui.theme.SyncOrange
import com.streamsync.android.ui.theme.SyncPurple
import com.streamsync.android.ui.theme.SyncRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val app = StreamSyncApp.instance
    val discoveredDevices by app.serviceManager.discoveryService.discoveredDevices.collectAsState()
    val transfers by app.serviceManager.transferService.transfers.collectAsState()
    val activeStreams by app.serviceManager.streamService.activeStreams.collectAsState()
    val activeTransfers by app.serviceManager.transferService.activeTransfers.collectAsState()
    val isDiscovering by app.serviceManager.discoveryService.isDiscovering.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "StreamSync",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Instant file transfer & content streaming",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Quick stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon = Icons.Outlined.Devices,
                label = "Devices",
                value = "${discoveredDevices.size}",
                color = SyncBlue,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Outlined.SwapHoriz,
                label = "Active",
                value = "$activeTransfers",
                color = SyncGreen,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Outlined.Cast,
                label = "Streams",
                value = "${activeStreams.size}",
                color = SyncPurple,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick actions
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                icon = Icons.Filled.Send,
                label = "Send Files",
                description = "Transfer to any device",
                onClick = { navController.navigate(Screen.Transfers.route) },
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                icon = Icons.Filled.Cast,
                label = "Cast Content",
                description = "Stream to devices",
                onClick = { navController.navigate(Screen.Stream.route) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                icon = Icons.Filled.ContentPaste,
                label = "Clipboard",
                description = "Sync across devices",
                onClick = { navController.navigate(Screen.Clipboard.route) },
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                icon = Icons.Filled.DevicesOther,
                label = "Discover",
                description = if (isDiscovering) "Scanning..." else "Find devices",
                onClick = { navController.navigate(Screen.Devices.route) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent transfers
        Text(
            text = "Recent Transfers",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val recentTransfers = transfers.take(3)
        if (recentTransfers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No transfers yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Send your first file to a nearby device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            recentTransfers.forEach { transfer ->
                TransferListItem(transfer = transfer)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Discovered devices
        Text(
            text = "Nearby Devices",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (discoveredDevices.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (isDiscovering) "Scanning network..." else "No devices found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isDiscovering) {
                            Text(
                                "Make sure other devices have StreamSync open",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        } else {
            discoveredDevices.take(5).forEach { device ->
                DeviceListItem(
                    deviceName = device.deviceName,
                    deviceType = device.deviceType.name,
                    signalStrength = device.signalStrength,
                    isActive = device.isActive,
                    onClick = { navController.navigate(Screen.DeviceDetail.createRoute(device.deviceId)) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TransferListItem(transfer: com.streamsync.android.model.Transfer) {
    val statusColor = when (transfer.status) {
        TransferStatus.STATUS_COMPLETED -> SyncGreen
        TransferStatus.STATUS_FAILED -> SyncRed
        TransferStatus.STATUS_TRANSFERRING -> SyncBlue
        TransferStatus.STATUS_PAUSED -> SyncOrange
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusIcon = when (transfer.status) {
        TransferStatus.STATUS_COMPLETED -> Icons.Filled.CheckCircle
        TransferStatus.STATUS_FAILED -> Icons.Filled.Error
        TransferStatus.STATUS_TRANSFERRING -> Icons.Filled.CloudUpload
        TransferStatus.STATUS_PAUSED -> Icons.Filled.PauseCircle
        TransferStatus.STATUS_PENDING -> Icons.Outlined.Schedule
        TransferStatus.STATUS_CANCELLED -> Icons.Filled.Cancel
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.files.firstOrNull()?.filename ?: "Transfer",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${transfer.direction.name}: ${transfer.remoteDevice?.deviceName ?: "Unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transfer.status == TransferStatus.STATUS_TRANSFERRING && transfer.totalBytes > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { transfer.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${transfer.progressPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "%.1f Mbps".format(transfer.speedMbps),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (transfer.status) {
                    TransferStatus.STATUS_COMPLETED -> "Done"
                    TransferStatus.STATUS_FAILED -> "Failed"
                    TransferStatus.STATUS_TRANSFERRING -> "Sending"
                    TransferStatus.STATUS_PAUSED -> "Paused"
                    TransferStatus.STATUS_PENDING -> "Pending"
                    TransferStatus.STATUS_CANCELLED -> "Cancelled"
                },
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
        }
    }
}

@Composable
private fun DeviceListItem(
    deviceName: String,
    deviceType: String,
    signalStrength: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    deviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    deviceType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) SyncGreen else SyncRed)
            )
        }
    }
}
