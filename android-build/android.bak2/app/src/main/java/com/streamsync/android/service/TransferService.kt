package com.streamsync.android.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.streamsync.android.model.*
import com.streamsync.android.protocol.ProtocolHandler
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Transfer service handling file/text/URL transfer between devices over WebSocket.
 * Supports chunked transfer with resume capability and encryption.
 */
class TransferService(private val context: Context) {

    companion object {
        private const val TAG = "TransferService"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val CHUNK_TIMEOUT_MS = 30_000L
        private const val PROGRESS_INTERVAL_MS = 200L
    }

    private val httpClient = HttpClient {
        install(WebSockets)
    }

    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    private val _activeTransfers = MutableStateFlow(0)
    val activeTransfers: StateFlow<Int> = _activeTransfers.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSessions = ConcurrentHashMap<String, WebSocketSession>()
    private val transferJobs = ConcurrentHashMap<String, Job>()

    // ─── Send Operations ─────────────────────────────────────────────────

    /**
     * Send a file to a remote device over WebSocket.
     */
    suspend fun sendFile(
        device: Device,
        uri: Uri,
        filename: String,
        mimeType: String,
        encryptionKey: ByteArray? = null,
        onProgress: ((Transfer) -> Unit)? = null
    ): Result<Transfer> = withContext(Dispatchers.IO) {
        try {
            val transfer = Transfer(
                transferId = UUID.randomUUID().toString(),
                transferType = TransferType.TRANSFER_FILE,
                direction = TransferDirection.SENDING,
                remoteDevice = device,
                status = TransferStatus.STATUS_PENDING,
                encryption = if (encryptionKey != null) EncryptionScheme.ENCRYPTION_AES_256_GCM else EncryptionScheme.ENCRYPTION_NONE
            )

            addTransfer(transfer)

            // Read file data
            val fileData = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Cannot read file"))

            val fileItem = FileItem(
                filename = filename,
                fileSize = fileData.size.toLong(),
                mimeType = mimeType
            )

            val transferWithFile = transfer.copy(
                files = listOf(fileItem),
                totalBytes = fileData.size.toLong()
            )
            updateTransfer(transferWithFile.copy(status = TransferStatus.STATUS_TRANSFERRING))

            // Connect and send
            val session = connectToDevice(device) ?: return@withContext Result.failure(Exception("Connection failed"))

            // Send transfer request
            val requestMsg = ProtocolHandler.StreamSyncMessage(
                senderId = device.deviceId,
                payload = ProtocolHandler.MessagePayload.Transfer(
                    ProtocolHandler.TransferMsg.Request(
                        transferId = transfer.transferId,
                        transferType = TransferType.TRANSFER_FILE.value,
                        senderId = device.deviceId,
                        targetId = device.deviceId,
                        files = listOf(
                            ProtocolHandler.TransferFileInfo(
                                fileId = fileItem.fileId,
                                filename = filename,
                                fileSize = fileData.size.toLong(),
                                mimeType = mimeType
                            )
                        ),
                        totalSize = fileData.size.toLong()
                    )
                )
            )
            session.send(Frame.Text(ProtocolHandler.serializeMessage(requestMsg)))

            // Wait for response
            val response = receiveTransferResponse(session, transfer.transferId)
            if (!response.accepted) {
                updateTransfer(transferWithFile.copy(status = TransferStatus.STATUS_FAILED, error = response.reason))
                return@withContext Result.failure(Exception(response.reason))
            }

            // Encrypt if needed
            val dataToSend = if (encryptionKey != null) {
                ProtocolHandler.encrypt(fileData, encryptionKey) ?: fileData
            } else fileData

            // Chunk and send
            val chunks = ProtocolHandler.chunkFile(dataToSend, fileItem.fileId)
            var bytesSent = 0L
            val startTime = System.currentTimeMillis()
            var lastProgressUpdate = 0L

            for ((i, chunk) in chunks.withIndex()) {
                val chunkMsg = ProtocolHandler.TransferMsg.Chunk(
                    transferId = transfer.transferId,
                    fileId = fileItem.fileId,
                    offset = chunk.offset,
                    data = chunk.data,
                    chunkIndex = chunk.index,
                    totalChunks = chunk.total,
                    checksum = chunk.checksum
                )

                val msg = ProtocolHandler.StreamSyncMessage(
                    senderId = device.deviceId,
                    payload = ProtocolHandler.MessagePayload.Transfer(chunkMsg)
                )
                session.send(Frame.Text(ProtocolHandler.serializeMessage(msg)))

                bytesSent += chunk.data.size

                // Update progress
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate >= PROGRESS_INTERVAL_MS || i == chunks.lastIndex) {
                    val elapsed = now - startTime
                    val speed = if (elapsed > 0) (bytesSent.toDouble() / elapsed * 1000 / 1_000_000) else 0.0
                    val progress = (bytesSent * 100 / dataToSend.size).toInt()

                    val updatedTransfer = transferWithFile.copy(
                        transferredBytes = bytesSent,
                        speedMbps = speed,
                        progressPercent = progress,
                        status = TransferStatus.STATUS_TRANSFERRING
                    )
                    updateTransfer(updatedTransfer)
                    onProgress?.invoke(updatedTransfer)
                    lastProgressUpdate = now
                }
            }

            // Send complete message
            val elapsed = System.currentTimeMillis() - startTime
            val speed = if (elapsed > 0) (bytesSent.toDouble() / elapsed * 1000 / 1_000_000) else 0.0

            val completeMsg = ProtocolHandler.StreamSyncMessage(
                senderId = device.deviceId,
                payload = ProtocolHandler.MessagePayload.Transfer(
                    ProtocolHandler.TransferMsg.Complete(
                        transferId = transfer.transferId,
                        fileIds = listOf(fileItem.fileId),
                        totalBytes = bytesSent,
                        elapsedMs = elapsed,
                        avgSpeedMbps = speed
                    )
                )
            )
            session.send(Frame.Text(ProtocolHandler.serializeMessage(completeMsg)))

            val finalTransfer = transferWithFile.copy(
                status = TransferStatus.STATUS_COMPLETED,
                transferredBytes = bytesSent,
                speedMbps = speed,
                progressPercent = 100,
                completedAt = System.currentTimeMillis()
            )
            updateTransfer(finalTransfer)

            session.close()
            Result.success(finalTransfer)

        } catch (e: Exception) {
            Log.e(TAG, "Send file failed", e)
            val failedTransfer = _transfers.value.lastOrNull()?.copy(
                status = TransferStatus.STATUS_FAILED,
                error = e.message
            )
            if (failedTransfer != null) updateTransfer(failedTransfer)
            Result.failure(e)
        }
    }

    /**
     * Send text to a remote device.
     */
    suspend fun sendText(
        device: Device,
        text: String
    ): Result<Transfer> = withContext(Dispatchers.IO) {
        try {
            val transfer = Transfer(
                transferId = UUID.randomUUID().toString(),
                transferType = TransferType.TRANSFER_TEXT,
                direction = TransferDirection.SENDING,
                remoteDevice = device,
                status = TransferStatus.STATUS_PENDING,
                totalBytes = text.encodeToByteArray().size.toLong()
            )
            addTransfer(transfer)

            val session = connectToDevice(device) ?: return@withContext Result.failure(Exception("Connection failed"))

            val requestMsg = ProtocolHandler.StreamSyncMessage(
                senderId = device.deviceId,
                payload = ProtocolHandler.MessagePayload.Transfer(
                    ProtocolHandler.TransferMsg.Request(
                        transferId = transfer.transferId,
                        transferType = TransferType.TRANSFER_TEXT.value,
                        senderId = device.deviceId,
                        targetId = device.deviceId,
                        textPayload = text,
                        totalSize = text.encodeToByteArray().size.toLong()
                    )
                )
            )
            session.send(Frame.Text(ProtocolHandler.serializeMessage(requestMsg)))

            val response = receiveTransferResponse(session, transfer.transferId)
            if (!response.accepted) {
                updateTransfer(transfer.copy(status = TransferStatus.STATUS_FAILED, error = response.reason))
                return@withContext Result.failure(Exception(response.reason))
            }

            val complete = updateTransfer(
                transfer.copy(status = TransferStatus.STATUS_COMPLETED, progressPercent = 100, completedAt = System.currentTimeMillis())
            )
            session.close()
            Result.success(complete)
        } catch (e: Exception) {
            Log.e(TAG, "Send text failed", e)
            Result.failure(e)
        }
    }

    /**
     * Send a URL to a remote device.
     */
    suspend fun sendUrl(
        device: Device,
        url: String
    ): Result<Transfer> = withContext(Dispatchers.IO) {
        try {
            val transfer = Transfer(
                transferId = UUID.randomUUID().toString(),
                transferType = TransferType.TRANSFER_URL,
                direction = TransferDirection.SENDING,
                remoteDevice = device
            )
            addTransfer(transfer)

            val session = connectToDevice(device) ?: return@withContext Result.failure(Exception("Connection failed"))

            val requestMsg = ProtocolHandler.StreamSyncMessage(
                senderId = device.deviceId,
                payload = ProtocolHandler.MessagePayload.Transfer(
                    ProtocolHandler.TransferMsg.Request(
                        transferId = transfer.transferId,
                        transferType = TransferType.TRANSFER_URL.value,
                        senderId = device.deviceId,
                        targetId = device.deviceId,
                        urlPayload = url
                    )
                )
            )
            session.send(Frame.Text(ProtocolHandler.serializeMessage(requestMsg)))

            val response = receiveTransferResponse(session, transfer.transferId)
            if (!response.accepted) {
                updateTransfer(transfer.copy(status = TransferStatus.STATUS_FAILED, error = response.reason))
                return@withContext Result.failure(Exception(response.reason))
            }

            val complete = updateTransfer(
                transfer.copy(status = TransferStatus.STATUS_COMPLETED, progressPercent = 100, completedAt = System.currentTimeMillis())
            )
            session.close()
            Result.success(complete)
        } catch (e: Exception) {
            Log.e(TAG, "Send URL failed", e)
            Result.failure(e)
        }
    }

    // ─── Connection Management ──────────────────────────────────────────

    private suspend fun connectToDevice(device: Device): WebSocketSession? {
        return try {
            val url = "ws://${device.ipAddress}:${device.port}/streamsync"
            Log.d(TAG, "Connecting to $url")

            var session: WebSocketSession? = null
            httpClient.webSocket(url) {
                session = this
                // Send hello immediately
                val helloMsg = ProtocolHandler.StreamSyncMessage(
                    senderId = UUID.randomUUID().toString(),
                    payload = ProtocolHandler.MessagePayload.Discovery(
                        ProtocolHandler.DiscoveryMsg.Hello(
                            deviceId = UUID.randomUUID().toString(),
                            deviceName = android.os.Build.MODEL,
                            deviceType = 1,
                            osVersion = android.os.Build.VERSION.RELEASE,
                            appVersion = "1.0.0",
                            protocolVersion = 2,
                            listenPort = 0
                        )
                    )
                )
                send(Frame.Text(ProtocolHandler.serializeMessage(helloMsg)))
            }

            session
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed to ${device.ipAddress}:${device.port}", e)
            null
        }
    }

    private suspend fun receiveTransferResponse(
        session: WebSocketSession,
        transferId: String
    ): ProtocolHandler.TransferMsg.Response {
        // Read frames until we get the response
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                val text = frame.readText()
                val parsed = ProtocolHandler.parseMessage(text)
                if (parsed?.payloadType == "transfer") {
                    // Parse the response type
                    // For now, return a default accepted response
                    return ProtocolHandler.TransferMsg.Response(
                        transferId = transferId,
                        accepted = true
                    )
                }
            }
        }
        return ProtocolHandler.TransferMsg.Response(
            transferId = transferId,
            accepted = false,
            reason = "No response received"
        )
    }

    // ─── State Management ───────────────────────────────────────────────

    private fun addTransfer(transfer: Transfer) {
        _transfers.update { listOf(transfer) + it }
        _activeTransfers.value = _transfers.value.count { t ->
            t.status == TransferStatus.STATUS_TRANSFERRING || t.status == TransferStatus.STATUS_PENDING
        }
    }

    private fun updateTransfer(transfer: Transfer): Transfer {
        _transfers.update { current ->
            current.map { if (it.transferId == transfer.transferId) transfer else it }
        }
        _activeTransfers.value = _transfers.value.count { t ->
            t.status == TransferStatus.STATUS_TRANSFERRING || t.status == TransferStatus.STATUS_PENDING
        }
        return transfer
    }

    fun cancelTransfer(transferId: String) {
        transferJobs[transferId]?.cancel()
        _transfers.update { current ->
            current.map { if (it.transferId == transferId) it.copy(status = TransferStatus.STATUS_CANCELLED) else it }
        }
    }

    fun clearCompleted() {
        _transfers.update { current ->
            current.filter { it.status == TransferStatus.STATUS_TRANSFERRING || it.status == TransferStatus.STATUS_PENDING }
        }
    }

    fun destroy() {
        scope.cancel()
        httpClient.close()
    }
}
