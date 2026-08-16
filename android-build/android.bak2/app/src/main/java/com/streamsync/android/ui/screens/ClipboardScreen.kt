package com.streamsync.android.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streamsync.android.StreamSyncApp
import com.streamsync.android.model.ClipboardEntry
import com.streamsync.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(navController: NavController) {
    val app = StreamSyncApp.instance
    val clipboardHistory by app.serviceManager.clipboardService.clipboardHistory.collectAsState()
    val clipboardSyncEnabled by app.serviceManager.clipboardService.isEnabled.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Clipboard Sync", fontWeight = FontWeight.Bold) },
            actions = {
                Switch(
                    checked = clipboardSyncEnabled,
                    onCheckedChange = { app.serviceManager.clipboardService.setEnabled(it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        )

        if (clipboardHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Clipboard Sync",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (clipboardSyncEnabled) "Copy text on any device, paste on another"
                        else "Enable clipboard sync to start sharing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Recent clipboard entries sync across all devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(clipboardHistory, key = { it.id }) { entry ->
                    ClipboardEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun ClipboardEntryCard(entry: ClipboardEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (entry.isSynced) Icons.Filled.CloudDone else Icons.Filled.ContentPaste,
                    contentDescription = null,
                    tint = if (entry.isSynced) SyncGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (entry.sourceDevice == "local") "This device" else entry.sourceDevice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                entry.text ?: "No content",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = { /* Copy to local clipboard */ },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Copy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}
