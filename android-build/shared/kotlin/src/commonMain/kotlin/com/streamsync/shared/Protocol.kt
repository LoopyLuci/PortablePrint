package com.streamsync.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * StreamSync Shared Protocol — Kotlin Multiplatform
 * Shared message models and protocol handling used across Android & iOS.
 */

// ─── Message Models ────────────────────────────────────────────────────

@Serializable
data class StreamSyncEnvelope(
    val pv: Int = 2,                           // protocol version
    val id: Long,                               // message id
    val ts: Long = currentTimeMillis(),         // timestamp
    val s: String,                              // sender id
    val p: Payload                              // payload
)

@Serializable
sealed class Payload {
    @Serializable
    data class Discovery(
        val type: String,                       // hello, hello_ack, goodbye
        val did: String = "",                   // device id
        val dn: String = "",                    // device name
        val dt: Int = 0,                        // device type
        val os: String = "",
        val av: String = "",
        val pv: Int = 2,
        val port: Int = 0,
        val caps: List<String> = emptyList(),
        val ok: Boolean = true,                 // accepted
        val reason: String = ""
    ) : Payload()

    @Serializable
    data class Transfer(
        val type: String,                       // request, response, chunk, complete, error
        val tid: String = "",                   // transfer id
        val fid: String = "",                   // file id
        val tt: Int = 0,                        // transfer type
        val sid: String = "",
        val tg: String = "",
        val fn: String = "",                    // filename
        val fs: Long = 0,                       // file size
        val mt: String = "application/octet-stream",
        val total: Long = 0,
        val off: Long = 0,                      // offset
        val len: Int = 0,                       // chunk length
        val ci: Int = 0,                        // chunk index
        val tc: Int = 0,                        // total chunks
        val data: String = "",                  // base64 chunk data
        val ok: Boolean = true,
        val reason: String = "",
        val speed: Double = 0.0,
        val tb: Long = 0,                       // total bytes
        val elapsed: Long = 0
    ) : Payload()

    @Serializable
    data class Stream(
        val type: String,                       // start, stop, packet, control
        val sid: String = "",
        val w: Int = 0,
        val h: Int = 0,
        val fps: Int = 0,
        val br: Int = 0,                        // bitrate
        val seq: Long = 0,
        val data: String = "",                  // base64 frame data
        val audio: Boolean = false,
        val video: Boolean = true,
        val cmd: Int = 0,
        val param: Long = 0
    ) : Payload()

    @Serializable
    data class Control(
        val type: String,                       // ping, pong, pair_req, pair_res, heartbeat
        val ts: Long = 0,
        val ok: Boolean = true,
        val dn: String = "",
        val did: String = "",
        val batt: Double = 0.0,
        val chg: Boolean = false,
        val tx: Int = 0
    ) : Payload()

    @Serializable
    data class Clipboard(
        val text: String = "",
        val src: String = ""
    ) : Payload()
}

// ─── Device Identity ───────────────────────────────────────────────────

@Serializable
data class DeviceIdentity(
    val deviceId: String,
    val deviceName: String,
    val deviceType: Int,
    val osVersion: String,
    val appVersion: String,
    val protocolVersion: Int = 2,
    val capabilities: List<String> = emptyList(),
    val ipAddress: String = "",
    val port: Int = 0
) {
    fun isCompatible() = protocolVersion >= 1

    fun displayType(): String = when (deviceType) {
        1 -> "Android"
        2 -> "iOS"
        3 -> "Windows"
        4 -> "macOS"
        5 -> "Linux"
        6 -> "Web"
        7 -> "TV"
        else -> "Unknown"
    }
}

// ─── Protocol Constants ────────────────────────────────────────────────

object ProtocolConstants {
    const val PROTOCOL_VERSION = 2
    const val SERVICE_TYPE = "_streamsync._tcp"
    const val DEFAULT_PORT = 9876
    const val DEFAULT_CHUNK_SIZE = 64 * 1024  // 64KB
    const val MAX_MESSAGE_SIZE = 10 * 1024 * 1024  // 10MB
    const val HEARTBEAT_INTERVAL_MS = 5_000L
    const val DISCOVERY_TIMEOUT_MS = 30_000L
    const val TRANSFER_TIMEOUT_MS = 60_000L
    const val MAX_CONCURRENT_TRANSFERS = 3

    val DEVICE_TYPES = mapOf(
        1 to "Android",
        2 to "iOS",
        3 to "Windows",
        4 to "macOS",
        5 to "Linux",
        6 to "Web",
        7 to "TV"
    )
}

// ─── Protocol Handler ─────────────────────────────────────────────────

object SharedProtocolHandler {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Serialize a message to JSON string for WebSocket transmission.
     */
    fun serialize(envelope: StreamSyncEnvelope): String {
        return json.encodeToString(envelope)
    }

    /**
     * Deserialize a JSON string into a message envelope.
     */
    fun deserialize(jsonString: String): StreamSyncEnvelope? {
        return try {
            json.decodeFromString<StreamSyncEnvelope>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create a Hello discovery message for this device.
     */
    fun createHello(identity: DeviceIdentity): String {
        return serialize(StreamSyncEnvelope(
            id = generateMessageId(),
            s = identity.deviceId,
            p = Payload.Discovery(
                type = "hello",
                did = identity.deviceId,
                dn = identity.deviceName,
                dt = identity.deviceType,
                os = identity.osVersion,
                av = identity.appVersion,
                pv = identity.protocolVersion,
                port = identity.port,
                caps = identity.capabilities
            )
        ))
    }

    /**
     * Create a transfer request message.
     */
    fun createTransferRequest(
        transferId: String,
        senderId: String,
        targetId: String,
        filename: String,
        fileSize: Long,
        mimeType: String
    ): String {
        return serialize(StreamSyncEnvelope(
            id = generateMessageId(),
            s = senderId,
            p = Payload.Transfer(
                type = "request",
                tid = transferId,
                tt = 0,
                sid = senderId,
                tg = targetId,
                fn = filename,
                fs = fileSize,
                mt = mimeType,
                total = fileSize
            )
        ))
    }

    /**
     * Create a transfer response message.
     */
    fun createTransferResponse(
        transferId: String,
        accepted: Boolean,
        reason: String = ""
    ): String {
        return serialize(StreamSyncEnvelope(
            id = generateMessageId(),
            s = "",
            p = Payload.Transfer(
                type = "response",
                tid = transferId,
                ok = accepted,
                reason = reason
            )
        ))
    }

    /**
     * Parse the payload type from a JSON message string.
     */
    fun getPayloadType(jsonString: String): String? {
        return try {
            val obj = json.decodeFromString<Map<String, Any?>>(jsonString)
            val p = obj["p"] as? Map<*, *>
            p?.get("type") as? String
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate a unique message ID.
     */
    fun generateMessageId(): Long {
        return (System.nanoTime() and 0x7FFFFFFFFFFFFFFF)
    }
}

// ─── Utility ──────────────────────────────────────────────────────────

/**
 * Cross-platform current time in milliseconds.
 * Uses System.currentTimeMillis() on JVM, expects a KMP-appropriate alternative on native.
 */
internal expect fun currentTimeMillis(): Long

// On JVM/Android this uses System.currentTimeMillis()
// On iOS, expect fun maps to NSDate().timeIntervalSince1970
