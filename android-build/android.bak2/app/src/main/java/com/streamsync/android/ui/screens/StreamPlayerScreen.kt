package com.streamsync.android.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streamsync.android.StreamSyncApp
import com.streamsync.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamPlayerScreen(
    streamId: String,
    navController: NavController
) {
    val app = StreamSyncApp.instance
    val activeStreams by app.serviceManager.streamService.activeStreams.collectAsState()

    val stream = activeStreams.find { it.streamId == streamId }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("Auto") }

    if (stream == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Cast,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Stream not found", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text(stream.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        stream.device?.deviceName ?: "Stream",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showVolumeSlider = !showVolumeSlider }) {
                        Icon(
                            when {
                                volume <= 0f -> Icons.Filled.VolumeOff
                                volume < 0.5f -> Icons.Filled.VolumeDown
                                else -> Icons.Filled.VolumeUp
                            },
                            contentDescription = "Volume"
                        )
                    }
                }
                IconButton(onClick = { /* Stream settings */ }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Stream settings")
                }
            }
        )

        // Video area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.CastConnected,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stream.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${stream.width}x${stream.height} · ${stream.fps}fps",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Loading indicator overlay
            if (!isPlaying) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PauseCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Paused", color = Color.White)
                    }
                }
            }
        }

        // Stream info
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StreamInfoItem("Quality", "${stream.width}x${stream.height}")
                    StreamInfoItem("Bitrate", "${stream.bitrateKbps} kbps")
                    StreamInfoItem("FPS", "${stream.fps}")
                    StreamInfoItem("Audio", if (stream.hasAudio) "Yes" else "No")
                }
            }
        }

        // Playback controls
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Seek bar
                Slider(
                    value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                    onValueChange = { /* Seek */ },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatDuration(totalDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Control buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Previous */ }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                    }

                    FilledIconButton(
                        onClick = {
                            isPlaying = !isPlaying
                            app.serviceManager.streamService.controlPlayback(streamId, isPlaying)
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = { /* Next */ }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { /* Picture in picture */ }) {
                        Icon(Icons.Filled.PictureInPicture, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PiP", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { /* Download */ }) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { /* Stop */ }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp), tint = SyncRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", style = MaterialTheme.typography.labelMedium, color = SyncRed)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Volume slider (conditional)
        if (showVolumeSlider) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Volume", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            app.serviceManager.streamService.setVolume(it)
                        },
                        valueRange = 0f..1f
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
