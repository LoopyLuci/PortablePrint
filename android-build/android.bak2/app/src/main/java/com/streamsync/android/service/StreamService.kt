package com.streamsync.android.service

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.streamsync.android.model.*
import com.streamsync.android.protocol.ProtocolHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Content streaming service supporting video, audio, and screen mirroring.
 * Handles both sending (broadcasting) and receiving (playing) streams.
 */
class StreamService(private val context: Context) {

    companion object {
        private const val TAG = "StreamService"
        private const val KEEPALIVE_INTERVAL_MS = 5_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeStreams = MutableStateFlow<List<StreamSession>>(emptyList())
    val activeStreams: StateFlow<List<StreamSession>> = _activeStreams.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private var exoPlayer: ExoPlayer? = null

    /**
     * Start playing a stream from a remote device.
     */
    suspend fun startPlayback(streamSession: StreamSession): Result<ExoPlayer> = withContext(Dispatchers.Main) {
        try {
            val player = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(streamSession.streamUrl)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(streamSession.title)
                            .build()
                    )
                    .build()
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }

            exoPlayer?.release()
            exoPlayer = player

            val active = streamSession.copy(isActive = true, isPlaying = true)
            addOrUpdateStream(active)
            _isStreaming.value = true

            Result.success(player)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            Result.failure(e)
        }
    }

    /**
     * Start broadcasting local content to remote devices.
     */
    suspend fun startBroadcast(
        streamType: StreamType,
        port: Int,
        onPacketReady: (ByteArray) -> Unit
    ): Result<StreamSession> = withContext(Dispatchers.IO) {
        try {
            val session = StreamSession(
                streamType = streamType,
                isActive = true,
                streamUrl = "ws://0.0.0.0:$port/stream",
                title = when (streamType) {
                    StreamType.STREAM_VIDEO -> "Video Stream"
                    StreamType.STREAM_AUDIO -> "Audio Stream"
                    StreamType.STREAM_SCREEN -> "Screen Mirror"
                    StreamType.STREAM_CAMERA -> "Camera Feed"
                    StreamType.STREAM_MICROPHONE -> "Microphone Feed"
                    StreamType.STREAM_FILE -> "File Stream"
                }
            )

            addOrUpdateStream(session)
            _isStreaming.value = true

            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start broadcast", e)
            Result.failure(e)
        }
    }

    /**
     * Stop a specific stream.
     */
    fun stopStream(streamId: String) {
        _activeStreams.update { streams ->
            streams.map {
                if (it.streamId == streamId) it.copy(isActive = false, isPlaying = false)
                else it
            }
        }

        if (streamId == exoPlayer?.let { _activeStreams.value.find { s -> s.streamUrl.isNotEmpty() }?.streamId }) {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        }

        _isStreaming.value = _activeStreams.value.any { it.isActive }
    }

    /**
     * Stop all active streams.
     */
    fun stopAllStreams() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        _activeStreams.value = emptyList()
        _isStreaming.value = false
    }

    /**
     * Control playback of an active stream.
     */
    fun controlPlayback(streamId: String, play: Boolean) {
        _activeStreams.update { streams ->
            streams.map {
                if (it.streamId == streamId) it.copy(isPlaying = play)
                else it
            }
        }
        if (play) exoPlayer?.play() else exoPlayer?.pause()
    }

    /**
     * Seek to a position in the stream.
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    /**
     * Set volume (0.0 - 1.0).
     */
    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
    }

    /**
     * Get current playback position.
     */
    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L

    /**
     * Get total duration.
     */
    fun getDuration(): Long = exoPlayer?.duration ?: 0L

    /**
     * Update stream quality.
     */
    fun updateQuality(streamId: String, quality: StreamingQuality) {
        _activeStreams.update { streams ->
            streams.map {
                if (it.streamId == streamId) it.copy(
                    width = quality.width,
                    height = quality.height,
                    bitrateKbps = quality.bitrateKbps
                )
                else it
            }
        }
    }

    fun destroy() {
        scope.cancel()
        stopAllStreams()
    }

    private fun addOrUpdateStream(session: StreamSession) {
        _activeStreams.update { current ->
            val existing = current.indexOfFirst { it.streamId == session.streamId }
            if (existing >= 0) {
                current.toMutableList().apply { set(existing, session) }
            } else {
                current + session
            }
        }
    }
}
