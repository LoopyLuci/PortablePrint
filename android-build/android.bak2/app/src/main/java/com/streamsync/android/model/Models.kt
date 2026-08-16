package com.streamsync.android.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Core data models used across the StreamSync Android app.
 */

@Serializable
data class Device(
    val deviceId: String = UUID.randomUUID().toString(),
    val deviceName: String = "Unknown",
    val deviceType: DeviceType = DeviceType.DEVICE_UNKNOWN,
    val osVersion: String = "",
    val appVersion: String = "",
    val protocolVersion: Int = 2,
    val capabilities: List<Capability> = emptyList(),
    val ipAddress: String = "",
    val port: Int = 0,
    val isActive: Boolean = true,
    val isPaired: Boolean = false,
    val signalStrength: Int = 0,
    val connectionType: ConnectionType = ConnectionType.WIFI,
    val lastSeen: Long = System.currentTimeMillis(),
    val publicKey: ByteArray? = null
)

@Serializable
enum class DeviceType(val value: Int) {
    DEVICE_UNKNOWN(0),
    DEVICE_ANDROID(1),
    DEVICE_IOS(2),
    DEVICE_DESKTOP_WINDOWS(3),
    DEVICE_DESKTOP_MACOS(4),
    DEVICE_DESKTOP_LINUX(5),
    DEVICE_WEB(6),
    DEVICE_TV(7);

    companion object {
        fun fromValue(value: Int): DeviceType = entries.firstOrNull { it.value == value } ?: DEVICE_UNKNOWN
    }
}

@Serializable
data class Capability(
    val feature: String,
    val version: Int = 1,
    val params: Map<String, String> = emptyMap()
)

@Serializable
enum class ConnectionType {
    WIFI, CELLULAR, ETHERNET, BLUETOOTH, USB, HOTSPOT
}

// Transfer Models

@Serializable
data class Transfer(
    val transferId: String = UUID.randomUUID().toString(),
    val transferType: TransferType = TransferType.TRANSFER_FILE,
    val direction: TransferDirection = TransferDirection.SENDING,
    val files: List<FileItem> = emptyList(),
    val remoteDevice: Device? = null,
    val status: TransferStatus = TransferStatus.STATUS_PENDING,
    val totalBytes: Long = 0,
    val transferredBytes: Long = 0,
    val speedMbps: Double = 0.0,
    val progressPercent: Int = 0,
    val estimatedRemainingMs: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
    val error: String? = null,
    val encryption: EncryptionScheme = EncryptionScheme.ENCRYPTION_AES_256_GCM
)

@Serializable
enum class TransferType(val value: Int) {
    TRANSFER_FILE(0),
    TRANSFER_STREAM(1),
    TRANSFER_CLIPBOARD(2),
    TRANSFER_URL(3),
    TRANSFER_TEXT(4),
    TRANSFER_CONTACT(5),
    TRANSFER_SCREEN_MIRROR(6)
}

@Serializable
enum class TransferDirection {
    SENDING, RECEIVING
}

@Serializable
enum class TransferStatus(val value: Int) {
    STATUS_PENDING(0),
    STATUS_TRANSFERRING(1),
    STATUS_PAUSED(2),
    STATUS_COMPLETED(3),
    STATUS_FAILED(4),
    STATUS_CANCELLED(5)
}

@Serializable
enum class EncryptionScheme(val value: Int) {
    ENCRYPTION_NONE(0),
    ENCRYPTION_AES_256_GCM(1),
    ENCRYPTION_CHACHA20_POLY1305(2),
    ENCRYPTION_TLS_1_3(3)
}

@Serializable
data class FileItem(
    val fileId: String = UUID.randomUUID().toString(),
    val filename: String,
    val fileSize: Long,
    val mimeType: String = "application/octet-stream",
    val fileHash: ByteArray? = null,
    val relativePath: String = "",
    val contentUri: String? = null,
    val localPath: String? = null
)

// Streaming Models

@Serializable
data class StreamSession(
    val streamId: String = UUID.randomUUID().toString(),
    val streamType: StreamType = StreamType.STREAM_VIDEO,
    val device: Device? = null,
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val bitrateKbps: Int = 0,
    val streamUrl: String = "",
    val hasAudio: Boolean = false
)

@Serializable
enum class StreamType(val value: Int) {
    STREAM_VIDEO(0),
    STREAM_AUDIO(1),
    STREAM_SCREEN(2),
    STREAM_CAMERA(3),
    STREAM_MICROPHONE(4),
    STREAM_FILE(5)
}

// Clipboard

@Serializable
data class ClipboardEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String? = null,
    val sourceDevice: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

// App Settings

data class AppSettings(
    val deviceName: String = android.os.Build.MODEL,
    val autoAcceptTransfers: Boolean = false,
    val defaultDownloadPath: String = "Downloads/StreamSync",
    val enableClipboardSync: Boolean = true,
    val encryptionEnabled: Boolean = true,
    val maxConcurrentTransfers: Int = 3,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val enableBackgroundDiscovery: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notificationEnabled: Boolean = true
)

enum class StreamingQuality(val label: String, val width: Int, val height: Int, val bitrateKbps: Int) {
    LOW("480p", 854, 480, 800),
    MEDIUM("720p", 1280, 720, 2500),
    HIGH("1080p", 1920, 1080, 5000),
    ULTRA("4K", 3840, 2160, 16000),
    AUTO("Auto", 0, 0, 0)
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }
