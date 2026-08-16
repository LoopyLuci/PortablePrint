package com.streamsync.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streamsync.android.StreamSyncApp
import com.streamsync.android.model.*
import com.streamsync.android.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(navController: NavController) {
    val app = StreamSyncApp.instance
    val transfers by app.serviceManager.transferService.transfers.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Transfers", fontWeight = FontWeight.Bold) },
            actions = {
                if (transfers.any { it.status == TransferStatus.STATUS_COMPLETED }) {
                    IconButton(onClick = { app.serviceManager.transferService.clearCompleted() }) {
                        Icon(Icons.Filled.ClearAll, contentDescription = "Clear completed")
                    }
                }
            }
        )

        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("All (${transfers.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Active") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Completed") }
            )
        }

        val filteredTransfers = when (selectedTab) {
            1 -> transfers.filter { it.status == TransferStatus.STATUS_TRANSFERRING || it.status == TransferStatus.STATUS_PENDING }
            2 -> transfers.filter { it.status == TransferStatus.STATUS_COMPLETED }
            else -> transfers
        }

        if (filteredTransfers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No transfers",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Select a device and send files to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTransfers, key = { it.transferId }) { transfer ->
                    TransferDetailCard(
                        transfer = transfer,
                        onCancel = { app.serviceManager.transferService.cancelTransfer(transfer.transferId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferDetailCard(
    transfer: Transfer,
    onCancel: () -> Unit
) {
    val statusColor = when (transfer.status) {
        TransferStatus.STATUS_COMPLETED -> SyncGreen
        TransferStatus.STATUS_FAILED -> SyncRed
        TransferStatus.STATUS_TRANSFERRING -> SyncBlue
        TransferStatus.STATUS_PAUSED -> SyncOrange
        TransferStatus.STATUS_CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        TransferStatus.STATUS_PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (transfer.transferType) {
                        TransferType.TRANSFER_FILE -> Icons.Filled.InsertDriveFile
                        TransferType.TRANSFER_TEXT -> Icons.Filled.TextFields
                        TransferType.TRANSFER_URL -> Icons.Filled.Link
                        TransferType.TRANSFER_CLIPBOARD -> Icons.Filled.ContentPaste
                        else -> Icons.Filled.SwapHoriz
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transfer.files.firstOrNull()?.filename ?: when (transfer.transferType) {
                            TransferType.TRANSFER_TEXT -> "Text"
                            TransferType.TRANSFER_URL -> "URL"
                            else -> "Transfer"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${transfer.direction.name} to ${transfer.remoteDevice?.deviceName ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        transfer.status.name.replace("STATUS_", "").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                    if (transfer.status == TransferStatus.STATUS_TRANSFERRING) {
                        Text(
                            "${transfer.progressPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Progress bar
            if (transfer.totalBytes > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (transfer.totalBytes > 0) transfer.transferredBytes.toFloat() / transfer.totalBytes else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (transfer.speedMbps > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatBytes(transfer.transferredBytes) + " / " + formatBytes(transfer.totalBytes),
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

            // Error message
            if (transfer.error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    transfer.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = SyncRed
                )
            }

            // Actions
            if (transfer.status == TransferStatus.STATUS_TRANSFERRING || transfer.status == TransferStatus.STATUS_PENDING) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SyncRed)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
