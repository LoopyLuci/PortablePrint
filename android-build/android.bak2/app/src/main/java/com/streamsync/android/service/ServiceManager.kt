package com.streamsync.android.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.streamsync.android.model.Device
import com.streamsync.android.model.DeviceType
import com.streamsync.android.model.Capability

/**
 * Central service manager that coordinates all StreamSync services:
 * discovery, transfer, streaming, and clipboard sync.
 */
class ServiceManager(private val context: Context) {

    val discoveryService = DiscoveryService(context)
    val transferService = TransferService(context)
    val streamService = StreamService(context)
    val clipboardService = ClipboardSyncService(context)

    private var isInitialized = false
    private var serverPort = 0

    /**
     * Initialize all services and start background discovery.
     */
    fun initialize(port: Int = 0) {
        if (isInitialized) return
        serverPort = port

        // Register this device so others can discover us
        val localDevice = buildLocalDevice()
        discoveryService.registerService(serverPort, localDevice)

        // Start discovering other devices
        discoveryService.startDiscovery()

        // Start clipboard sync if enabled
        clipboardService.start()

        isInitialized = true
    }

    /**
     * Start foreground services for persistent background operation.
     */
    fun startForegroundServices() {
        val transferIntent = Intent(context, TransferService::class.java)
        val discoveryIntent = Intent(context, DiscoveryService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, transferIntent)
            ContextCompat.startForegroundService(context, discoveryIntent)
        } else {
            context.startService(transferIntent)
            context.startService(discoveryIntent)
        }
    }

    /**
     * Build this device's identity for discovery/advertising.
     */
    private fun buildLocalDevice(): Device {
        return Device(
            deviceName = Build.MODEL,
            deviceType = DeviceType.DEVICE_ANDROID,
            osVersion = Build.VERSION.RELEASE,
            appVersion = "1.0.0",
            protocolVersion = 2,
            capabilities = listOf(
                Capability("file_transfer", 2),
                Capability("streaming", 2),
                Capability("clipboard_sync", 1),
                Capability("screen_mirror", 1)
            ),
            ipAddress = "",  // Will be filled by NSD
            port = serverPort
        )
    }

    /**
     * Gracefully shut down all services.
     */
    fun shutdown() {
        discoveryService.destroy()
        transferService.destroy()
        streamService.destroy()
        clipboardService.destroy()
        isInitialized = false
    }
}
