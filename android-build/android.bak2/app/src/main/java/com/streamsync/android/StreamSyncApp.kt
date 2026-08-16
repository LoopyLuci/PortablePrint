package com.streamsync.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.streamsync.android.service.ServiceManager

class StreamSyncApp : Application() {

    lateinit var serviceManager: ServiceManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        serviceManager = ServiceManager(this)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val transferChannel = NotificationChannel(
            CHANNEL_TRANSFER,
            "File Transfers",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing file transfer notifications"
            setShowBadge(false)
        }

        val discoveryChannel = NotificationChannel(
            CHANNEL_DISCOVERY,
            "Device Discovery",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Device discovery service"
            setShowBadge(false)
        }

        val streamingChannel = NotificationChannel(
            CHANNEL_STREAMING,
            "Content Streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active streaming sessions"
            setShowBadge(false)
        }

        val generalChannel = NotificationChannel(
            CHANNEL_GENERAL,
            "StreamSync",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General notifications"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannels(
                listOf(transferChannel, discoveryChannel, streamingChannel, generalChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_TRANSFER = "streamsync_transfer"
        const val CHANNEL_DISCOVERY = "streamsync_discovery"
        const val CHANNEL_STREAMING = "streamsync_streaming"
        const val CHANNEL_GENERAL = "streamsync_general"

        lateinit var instance: StreamSyncApp
            private set
    }
}
