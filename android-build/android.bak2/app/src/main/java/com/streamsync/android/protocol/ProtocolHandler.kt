package com.streamsync.android.protocol

import com.streamsync.android.model.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32C
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * StreamSync Protocol Handler for Android.
 * Handles message construction, serialization, encryption, and chunking.
 */
object ProtocolHandler {

    const val PROTOCOL_VERSION = 2
    const val DEFAULT_CHUNK_SIZE = 64 * 1024 // 64KB chunks
    const val MAX_MESSAGE_SIZE = 10 * 1024 * 1024 // 10MB per message
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val GCM_IV_LENGTH = 12 // bytes

    // ─── Message Construction ────────────────────────────────────────────

    data class StreamSyncMessage(
        val protocolVersion: Int = PROTOCOL_VERSION,
        val messageId: Long = UUID.randomUUID().mostSignificantBits,
        val timestampMs: Long = System.currentTimeMillis(),
        val senderId: String,
        val payload: MessagePayload
    )

    sealed class MessagePayload {
        data class Discovery(val msg: DiscoveryMsg) : MessagePayload()
        data class Transfer(val msg: TransferMsg) : MessagePayload()
        data class Stream(val msg: StreamMsg) : MessagePayload()
        data class Control(val msg: ControlMsg) : MessagePayload()
        data class Clipboard(val msg: ClipboardData) : MessagePayload()
    }

    sealed class DiscoveryMsg {
        data class Hello(
            val deviceId: String,
            val deviceName: String,
            val deviceType: Int,
            val osVersion: String,
            val appVersion: String,
            val protocolVersion: Int,
            val listenPort: Int,
            val capabilities: List<String>,
            val publicKey: ByteArray? = null
        ) : DiscoveryMsg()

        data class HelloAck(
            val deviceId: String,
            val deviceName: String,
            val deviceType: Int,
            val accept: Boolean,
            val reason: String = ""
        ) : DiscoveryMsg()

        data class Goodbye(val reason: String = "") : DiscoveryMsg()
    }

    sealed class TransferMsg {
        data class Request(
            val transferId: String,
            val transferType: Int,
            val senderId: String,
            val targetId: String,
            val files: List<TransferFileInfo> = emptyList(),
            val textPayload: String = "",
            val urlPayload: String = "",
            val totalSize: Long = 0,
            val streamType: Int = 0
        ) : TransferMsg()

        data class Response(
            val transferId: String,
            val accepted: Boolean,
            val reason: String = "",
            val maxChunkSize: Int = DEFAULT_CHUNK_SIZE,
            val offset: Long = 0
        ) : TransferMsg()

        data class Chunk(
            val transferId: String,
            val fileId: String,
            val offset: Long,
            val data: ByteArray,
            val chunkIndex: Int,
            val totalChunks: Int,
            val checksum: ByteArray? = null
        ) : TransferMsg()

        data class Complete(
            val transferId: String,
            val fileIds: List<String>,
            val totalBytes: Long,
            val elapsedMs: Long,
            val avgSpeedMbps: Double
        ) : TransferMsg()

        data class Error(
            val transferId: String,
            val errorCode: Int,
            val errorMessage: String,
            val retryable: Boolean = false
        ) : TransferMsg()

        data class Pause(val transferId: String, val reason: String = "") : TransferMsg()
        data class Resume(val transferId: String) : TransferMsg()
        data class Cancel(val transferId: String, val reason: String = "") : TransferMsg()
        data class Progress(
            val transferId: String,
            val fileId: String,
            val bytesTransferred: Long,
            val totalBytes: Long,
            val speedMbps: Double
        ) : TransferMsg()
    }

    data class TransferFileInfo(
        val fileId: String = UUID.randomUUID().toString(),
        val filename: String,
        val fileSize: Long,
        val mimeType: String = "application/octet-stream",
        val fileHash: ByteArray? = null
    )

    sealed class StreamMsg {
        data class Start(
            val streamId: String,
            val streamType: Int,
            val width: Int = 0,
            val height: Int = 0,
            val fps: Int = 0,
            val bitrateKbps: Int = 0,
            val codec: String = "",
            val hasAudio: Boolean = false,
            val params: Map<String, String> = emptyMap()
        ) : StreamMsg()

        data class Stop(val streamId: String, val reason: String = "") : StreamMsg()

        data class Packet(
            val streamId: String,
            val sequence: Long,
            val timestampMs: Long,
            val keyframe: Boolean = false,
            val data: ByteArray,
            val isVideo: Boolean = true,
            val isAudio: Boolean = false
        ) : StreamMsg()

        data class Control(
            val streamId: String,
            val command: Int,
            val param: Long = 0
        ) : StreamMsg()

        data class Keepalive(val streamId: String) : StreamMsg()
    }

    sealed class ControlMsg {
        data class Ping(val timestampMs: Long, val nonce: ByteArray) : ControlMsg()
        data class Pong(val timestampMs: Long, val nonce: ByteArray, val serverTs: Long) : ControlMsg()
        data class PairRequest(val deviceId: String, val deviceName: String) : ControlMsg()
        data class PairResponse(val accepted: Boolean, val deviceName: String, val reason: String = "") : ControlMsg()
        data class Heartbeat(val timestampMs: Long, val batteryLevel: Double, val isCharging: Boolean, val activeTransfers: Int) : ControlMsg()
    }

    data class ClipboardData(
        val text: String? = null,
        val imageData: ByteArray? = null,
        val imageMime: String? = null,
        val sourceDevice: String = ""
    )

    // ─── JSON-based serialization (lightweight, no protobuf dependency at runtime) ──
    // We use JSON for simplicity and cross-platform compatibility
    // In production, protobuf would provide better performance

    fun serializeMessage(msg: StreamSyncMessage): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"pv\":${msg.protocolVersion},")
        sb.append("\"id\":${msg.messageId},")
        sb.append("\"ts\":${msg.timestampMs},")
        sb.append("\"s\":\"${escapeJson(msg.senderId)}\",")
        sb.append("\"p\":")
        serializePayload(msg.payload, sb)
        sb.append("}")
        return sb.toString()
    }

    private fun serializePayload(payload: MessagePayload, sb: StringBuilder) {
        when (payload) {
            is MessagePayload.Discovery -> {
                sb.append("{\"t\":\"discovery\",\"d\":")
                serializeDiscovery(payload.msg, sb)
                sb.append("}")
            }
            is MessagePayload.Transfer -> {
                sb.append("{\"t\":\"transfer\",\"d\":")
                serializeTransfer(payload.msg, sb)
                sb.append("}")
            }
            is MessagePayload.Stream -> {
                sb.append("{\"t\":\"stream\",\"d\":")
                serializeStream(payload.msg, sb)
                sb.append("}")
            }
            is MessagePayload.Control -> {
                sb.append("{\"t\":\"control\",\"d\":")
                serializeControl(payload.msg, sb)
                sb.append("}")
            }
            is MessagePayload.Clipboard -> {
                sb.append("{\"t\":\"clipboard\",\"d\":")
                serializeClipboard(payload.msg, sb)
                sb.append("}")
            }
        }
    }

    private fun serializeDiscovery(msg: DiscoveryMsg, sb: StringBuilder) {
        when (msg) {
            is DiscoveryMsg.Hello -> {
                sb.append("{\"type\":\"hello\",\"did\":\"${escapeJson(msg.deviceId)}\",")
                sb.append("\"dn\":\"${escapeJson(msg.deviceName)}\",")
                sb.append("\"dt\":${msg.deviceType},")
                sb.append("\"os\":\"${escapeJson(msg.osVersion)}\",")
                sb.append("\"av\":\"${escapeJson(msg.appVersion)}\",")
                sb.append("\"pv\":${msg.protocolVersion},")
                sb.append("\"port\":${msg.listenPort},")
                sb.append("\"caps\":[${msg.capabilities.joinToString(",") { "\"${escapeJson(it)}\"" }}]}")
            }
            is DiscoveryMsg.HelloAck -> {
                sb.append("{\"type\":\"hello_ack\",\"did\":\"${escapeJson(msg.deviceId)}\",")
                sb.append("\"dn\":\"${escapeJson(msg.deviceName)}\",")
                sb.append("\"dt\":${msg.deviceType},")
                sb.append("\"ok\":${msg.accept},")
                sb.append("\"reason\":\"${escapeJson(msg.reason)}\"}")
            }
            is DiscoveryMsg.Goodbye -> {
                sb.append("{\"type\":\"goodbye\",\"reason\":\"${escapeJson(msg.reason)}\"}")
            }
        }
    }

    private fun serializeTransfer(msg: TransferMsg, sb: StringBuilder) {
        when (msg) {
            is TransferMsg.Request -> {
                sb.append("{\"type\":\"request\",\"tid\":\"${escapeJson(msg.transferId)}\",")
                sb.append("\"tt\":${msg.transferType},")
                sb.append("\"sid\":\"${escapeJson(msg.senderId)}\",")
                sb.append("\"tg\":\"${escapeJson(msg.targetId)}\",")
                sb.append("\"total\":${msg.totalSize},")
                sb.append("\"files\":[")
                msg.files.forEachIndexed { i, f ->
                    if (i > 0) sb.append(",")
                    sb.append("{\"fid\":\"${escapeJson(f.fileId)}\",\"fn\":\"${escapeJson(f.filename)}\",")
                    sb.append("\"fs\":${f.fileSize},\"mt\":\"${escapeJson(f.mimeType)}\"}")
                }
                sb.append("]}")
            }
            is TransferMsg.Response -> {
                sb.append("{\"type\":\"response\",\"tid\":\"${escapeJson(msg.transferId)}\",")
                sb.append("\"ok\":${msg.accepted},")
                sb.append("\"reason\":\"${escapeJson(msg.reason)}\",")
                sb.append("\"mcs\":${msg.maxChunkSize},")
                sb.append("\"off\":${msg.offset}}")
            }
            is TransferMsg.Chunk -> {
                sb.append("{\"type\":\"chunk\",\"tid\":\"${escapeJson(msg.transferId)}\",")
                sb.append("\"fid\":\"${escapeJson(msg.fileId)}\",")
                sb.append("\"off\":${msg.offset},")
                sb.append("\"len\":${msg.data.size},")
                sb.append("\"ci\":${msg.chunkIndex},")
                sb.append("\"tc\":${msg.totalChunks},")
                sb.append("\"data\":\"${bytesToBase64(msg.data)}\"}")
            }
            is TransferMsg.Complete -> {
                sb.append("{\"type\":\"complete\",\"tid\":\"${escapeJson(msg.transferId)}\",")
                sb.append("\"tb\":${msg.totalBytes},")
                sb.append("\"elapsed\":${msg.elapsedMs},")
                sb.append("\"speed\":${msg.avgSpeedMbps}}")
            }
            is TransferMsg.Error -> {
                sb.append("{\"type\":\"error\",\"tid\":\"${escapeJson(msg.transferId)}\",")
                sb.append("\"code\":${msg.errorCode},")
                sb.append("\"msg\":\"${escapeJson(msg.errorMessage)}\",")
                sb.append("\"retry\":${msg.retryable}}")
            }
            is TransferMsg.Pause -> {
                sb.append("{\"type\":\"pause\",\"tid\":\"${escapeJson(msg.transferId)}\",\"reason\":\"${escapeJson(msg.reason)}\"}")
            }
            is TransferMsg.Resume -> {
                sb.append("{\"type\":\"resume\",\"tid\":\"${escapeJson(msg.transferId)}\"}")
            }
            is TransferMsg.Cancel -> {
                sb.append("{\"type\":\"cancel\",\"tid\":\"${escapeJson(msg.transferId)}\",\"reason\":\"${escapeJson(msg.reason)}\"}")
            }
            is TransferMsg.Progress -> {
                sb.append("{\"type\":\"progress\",\"tid\":\"${escapeJson(msg.transferId)}\",")
                sb.append("\"fid\":\"${escapeJson(msg.fileId)}\",")
                sb.append("\"bt\":${msg.bytesTransferred},")
                sb.append("\"tt\":${msg.totalBytes},")
                sb.append("\"speed\":${msg.speedMbps}}")
            }
        }
    }

    private fun serializeStream(msg: StreamMsg, sb: StringBuilder) {
        when (msg) {
            is StreamMsg.Start -> {
                sb.append("{\"type\":\"start\",\"sid\":\"${escapeJson(msg.streamId)}\",")
                sb.append("\"st\":${msg.streamType},")
                sb.append("\"w\":${msg.width},\"h\":${msg.height},")
                sb.append("\"fps\":${msg.fps},\"br\":${msg.bitrateKbps},")
                sb.append("\"codec\":\"${escapeJson(msg.codec)}\",")
                sb.append("\"audio\":${msg.hasAudio}}")
            }
            is StreamMsg.Stop -> {
                sb.append("{\"type\":\"stop\",\"sid\":\"${escapeJson(msg.streamId)}\",\"reason\":\"${escapeJson(msg.reason)}\"}")
            }
            is StreamMsg.Packet -> {
                sb.append("{\"type\":\"packet\",\"sid\":\"${escapeJson(msg.streamId)}\",")
                sb.append("\"seq\":${msg.sequence},\"ts\":${msg.timestampMs},")
                sb.append("\"kf\":${msg.keyframe},")
                sb.append("\"len\":${msg.data.size},")
                sb.append("\"video\":${msg.isVideo},\"audio\":${msg.isAudio},")
                sb.append("\"data\":\"${bytesToBase64(msg.data)}\"}")
            }
            is StreamMsg.Control -> {
                sb.append("{\"type\":\"control\",\"sid\":\"${escapeJson(msg.streamId)}\",")
                sb.append("\"cmd\":${msg.command},\"param\":${msg.param}}")
            }
            is StreamMsg.Keepalive -> {
                sb.append("{\"type\":\"keepalive\",\"sid\":\"${escapeJson(msg.streamId)}\"}")
            }
        }
    }

    private fun serializeControl(msg: ControlMsg, sb: StringBuilder) {
        when (msg) {
            is ControlMsg.Ping -> {
                sb.append("{\"type\":\"ping\",\"ts\":${msg.timestampMs}}")
            }
            is ControlMsg.Pong -> {
                sb.append("{\"type\":\"pong\",\"ts\":${msg.timestampMs},\"st\":${msg.serverTs}}")
            }
            is ControlMsg.PairRequest -> {
                sb.append("{\"type\":\"pair_req\",\"did\":\"${escapeJson(msg.deviceId)}\",\"dn\":\"${escapeJson(msg.deviceName)}\"}")
            }
            is ControlMsg.PairResponse -> {
                sb.append("{\"type\":\"pair_res\",\"ok\":${msg.accepted},\"dn\":\"${escapeJson(msg.deviceName)}\",\"reason\":\"${escapeJson(msg.reason)}\"}")
            }
            is ControlMsg.Heartbeat -> {
                sb.append("{\"type\":\"heartbeat\",\"ts\":${msg.timestampMs},")
                sb.append("\"batt\":${msg.batteryLevel},\"chg\":${msg.isCharging},")
                sb.append("\"tx\":${msg.activeTransfers}}")
            }
        }
    }

    private fun serializeClipboard(msg: ClipboardData, sb: StringBuilder) {
        sb.append("{\"text\":\"${escapeJson(msg.text ?: "")}\",")
        sb.append("\"src\":\"${escapeJson(msg.sourceDevice)}\"}")
    }

    // ─── JSON parsing ────────────────────────────────────────────────────

    data class ParsedMessage(
        val protocolVersion: Int,
        val messageId: Long,
        val timestampMs: Long,
        val senderId: String,
        val payloadType: String,
        val payloadJson: String
    )

    fun parseMessage(json: String): ParsedMessage? {
        return try {
            // Simple JSON parsing without external dependencies
            val msg = parseJsonObject(json) ?: return null
            val pv = (msg["pv"] as? Number)?.toInt() ?: 1
            val id = (msg["id"] as? Number)?.toLong() ?: 0L
            val ts = (msg["ts"] as? Number)?.toLong() ?: 0L
            val sender = msg["s"] as? String ?: ""
            val payload = msg["p"] as? Map<*, *> ?: return null
            val payloadType = payload["t"] as? String ?: ""
            val payloadData = payload["d"] as? Map<*, *>
            ParsedMessage(
                protocolVersion = pv,
                messageId = id,
                timestampMs = ts,
                senderId = sender,
                payloadType = payloadType,
                payloadJson = payloadData?.toString() ?: "{}"
            )
        } catch (e: Exception) {
            null
        }
    }

    // ─── Encryption / Decryption ─────────────────────────────────────────

    fun encrypt(data: ByteArray, key: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandomProvider.nextBytes(it) }
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            val secretKey = SecretKeySpec(key, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val encrypted = cipher.doFinal(data)
            // Prepend IV to ciphertext
            ByteArray(iv.size + encrypted.size).apply {
                iv.copyInto(this, 0)
                encrypted.copyInto(this, iv.size)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun decrypt(encrypted: ByteArray, key: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            val secretKey = SecretKeySpec(key, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }

    // ─── Chunking ────────────────────────────────────────────────────────

    data class FileChunk(
        val fileId: String,
        val data: ByteArray,
        val offset: Long,
        val index: Int,
        val total: Int,
        val checksum: ByteArray
    )

    fun chunkFile(
        fileData: ByteArray,
        fileId: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE
    ): List<FileChunk> {
        val totalChunks = (fileData.size + chunkSize - 1) / chunkSize
        val chunks = mutableListOf<FileChunk>()

        for (i in 0 until totalChunks) {
            val start = i * chunkSize.toLong()
            val end = minOf(start + chunkSize, fileData.size.toLong())
            val chunkData = fileData.copyOfRange(start.toInt(), end.toInt())
            val crc = CRC32C().apply { update(chunkData, 0, chunkData.size) }
            chunks.add(
                FileChunk(
                    fileId = fileId,
                    data = chunkData,
                    offset = start,
                    index = i,
                    total = totalChunks,
                    checksum = ByteBuffer.allocate(4).putInt(crc.value.toInt()).array()
                )
            )
        }
        return chunks
    }

    fun reassembleFile(chunks: List<FileChunk>): ByteArray {
        val sorted = chunks.sortedBy { it.index }
        val output = ByteArrayOutputStream()
        for (chunk in sorted) {
            output.write(chunk.data)
        }
        return output.toByteArray()
    }

    // ─── Hashing ─────────────────────────────────────────────────────────

    fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    fun sha256Hex(data: ByteArray): String {
        return sha256(data).joinToString("") { "%02x".format(it) }
    }

    // ─── Utility ─────────────────────────────────────────────────────────

    private fun escapeJson(s: String): String {
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun bytesToBase64(data: ByteArray): String {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
    }

    private fun base64ToBytes(s: String): ByteArray {
        return android.util.Base64.decode(s, android.util.Base64.DEFAULT)
    }

    private fun parseJsonObject(json: String): Map<String, Any?>? {
        return try {
            // Minimal JSON parser for our message format
            // In production, use kotlinx.serialization
            org.json.JSONObject(json).let { obj ->
                obj.keys().asSequence().associateWith { key ->
                    val value = obj.get(key)
                    when (value) {
                        is org.json.JSONObject -> parseJsonObject(value.toString())
                        is org.json.JSONArray -> (0 until value.length()).map { value.get(it) }
                        else -> value
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * SecureRandom provider that works across Android versions.
 */
internal object SecureRandomProvider {
    private val random = java.security.SecureRandom()
    fun nextBytes(array: ByteArray) = random.nextBytes(array)
    fun nextLong(): Long = random.nextLong()
    fun generateKey(length: Int = 32): ByteArray = ByteArray(length).also { random.nextBytes(it) }
}
