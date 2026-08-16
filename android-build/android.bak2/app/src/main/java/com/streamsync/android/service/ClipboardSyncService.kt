package com.streamsync.android.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.streamsync.android.model.ClipboardEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Clipboard synchronization service.
 * Monitors local clipboard changes and broadcasts them to paired devices.
 * Also listens for remote clipboard updates and applies locally.
 */
class ClipboardSyncService(private val context: Context) {

    companion object {
        private const val TAG = "ClipboardSync"
        private const val POLL_INTERVAL_MS = 1_000L
    }

    private val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _clipboardHistory = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val clipboardHistory: StateFlow<List<ClipboardEntry>> = _clipboardHistory.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private var lastClipboardContent: String? = null
    private var isSyncing = false
    private var externalUpdate = false

    /**
     * Start monitoring clipboard changes.
     */
    fun start() {
        if (isSyncing) return
        isSyncing = true

        clipboardManager.addPrimaryClipChangedListener(clipListener)
        Log.i(TAG, "Clipboard sync started")
    }

    /**
     * Stop monitoring clipboard.
     */
    fun stop() {
        isSyncing = false
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        Log.i(TAG, "Clipboard sync stopped")
    }

    /**
     * Enable or disable clipboard sync.
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (enabled) start() else stop()
    }

    /**
     * Called when a remote clipboard update is received.
     * Sets the local clipboard content and marks it as an external update
     * to avoid re-broadcasting.
     */
    fun receiveRemoteClipboard(text: String, sourceDevice: String) {
        if (!_isEnabled.value) return

        externalUpdate = true
        val clipData = ClipData.newPlainText("streamsync_clipboard", text)
        clipboardManager.setPrimaryClip(clipData)
        lastClipboardContent = text

        val entry = ClipboardEntry(
            text = text,
            sourceDevice = sourceDevice,
            isSynced = true
        )
        addToHistory(entry)
    }

    /**
     * Get callbacks for clipboard changes to broadcast.
     */
    fun getClipboardBroadcastFlow(): Flow<String> = callbackFlow {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            if (!_isEnabled.value || externalUpdate) {
                externalUpdate = false
                return@OnPrimaryClipChangedListener
            }

            val clip = clipboardManager.primaryClip
            val text = clip?.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener

            if (text != lastClipboardContent) {
                lastClipboardContent = text
                trySend(text)
            }
        }

        clipboardManager.addPrimaryClipChangedListener(listener)
        awaitClose { clipboardManager.removePrimaryClipChangedListener(listener) }
    }

    fun destroy() {
        scope.cancel()
        stop()
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!_isEnabled.value || externalUpdate) {
            externalUpdate = false
            return@OnPrimaryClipChangedListener
        }

        val clip = clipboardManager.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener

        if (text != lastClipboardContent) {
            lastClipboardContent = text
            val entry = ClipboardEntry(
                text = text,
                sourceDevice = "local",
                isSynced = false
            )
            addToHistory(entry)
        }
    }

    private fun addToHistory(entry: ClipboardEntry) {
        _clipboardHistory.update { listOf(entry) + it.take(49) }
    }
}
