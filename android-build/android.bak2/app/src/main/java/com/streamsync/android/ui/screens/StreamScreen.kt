package com.streamsync.android.ui.screens

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
import com.streamsync.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(navController: NavController) {
    val app = StreamSyncApp.instance
    val activeStreams by app.serviceManager.streamService.activeStreams.collectAsState()
    val isStreaming by app.serviceManager.streamService.isStreaming.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Streaming", fontWeight = FontWeight.Bold) }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active streams section
            if (activeStreams.isNotEmpty()) {
                item {
                    Text(
                        "Active Streams",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(activeStreams, key = { it.streamId }) { stream ->
                    ActiveStreamCard(
                        stream = stream,
                        onStop = { app.serviceManager.streamService.stopStream(stream.streamId) },
                        onPlayPause = {
                            app.serviceManager.streamService.controlPlayback(stream.streamId, !stream.isPlaying)
                        },
                        onClick = { navController.navigate("stream_player/${stream.streamId}") }
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // Stream to device section
            item {
                Text(
                    "Stream to Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                StreamTargetCard(
                    icon = Icons.Filled.Videocam,
                    title = "Stream Video",
                    description = "Cast video files to any device",
                    color = SyncBlue,
                    onClick = { /* File picker for video */ }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                StreamTargetCard(
                    icon = Icons.Filled.MusicNote,
                    title = "Stream Audio",
                    description = "Play music on remote speakers",
                    color = SyncPurple,
                    onClick = { /* File picker for audio */ }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                StreamTargetCard(
                    icon = Icons.Filled.Monitor,
                    title = "Screen Mirror",
                    description = "Mirror your screen to another device",
                    color = SyncGreen,
                    onClick = { /* Start screen mirror */ }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                StreamTargetCard(
                    icon = Icons.Filled.CameraAlt,
                    title = "Camera Feed",
                    description = "Share your camera in real-time",
                    color = SyncOrange,
                    onClick = { /* Start camera stream */ }
                )
            }

            // Receive section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Or receive a stream",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    onClick = { /* Show QR code or code */ }
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.CastConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Waiting for incoming stream...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Keep this screen open to receive streams",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveStreamCard(
    stream: com.streamsync.android.model.StreamSession,
    onStop: () -> Unit,
    onPlayPause: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(SyncGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stream.title.ifEmpty { stream.streamType.name },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stream.device?.deviceName ?: "Local stream",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (stream.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (stream.isPlaying) "Pause" else "Play"
                        )
                    }
                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "Stop",
                            tint = SyncRed
                        )
                    }
                }
            }

            if (stream.width > 0 && stream.height > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${stream.width}x${stream.height} @ ${stream.fps}fps · ${stream.bitrateKbps}kbps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StreamTargetCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
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
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
}
