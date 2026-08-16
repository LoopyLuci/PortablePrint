package com.streamsync.android.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.streamsync.android.model.*
import com.streamsync.android.protocol.ProtocolHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Device discovery service using Android NSD (Network Service Discovery).
 * Discovers StreamSync peers on the local network using mDNS/DNS-SD.
 */
class DiscoveryService(private val context: Context) {

    companion object {
        private const val TAG = "DiscoveryService"
        const val SERVICE_TYPE = "_streamsync._tcp"
        const val SERVICE_NAME = "StreamSync"

        private const val DISCOVERY_INTERVAL_MS = 30_000L
        private const val STALE_DEVICE_TIMEOUT_MS = 120_000L
    }

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var resolveListeners = mutableMapOf<String, NsdManager.ResolveListener>()

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _myDeviceInfo = MutableStateFlow<Device?>(null)
    val myDeviceInfo: StateFlow<Device?> = _myDeviceInfo.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var staleCleanupJob: Job? = null

    /**
     * Register this device as a discoverable StreamSync service.
     */
    fun registerService(port: Int, device: Device) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        _myDeviceInfo.value = device

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "${SERVICE_NAME}-${device.deviceName}"
            serviceType = SERVICE_TYPE
            this.port = port
            // Store metadata as TXT records
            setAttribute("device_id", device.deviceId)
            setAttribute("device_type", device.deviceType.value.toString())
            setAttribute("device_name", device.deviceName)
            setAttribute("protocol_version", device.protocolVersion.toString())
            setAttribute("os_version", device.osVersion)
            setAttribute("app_version", device.appVersion)
            setAttribute("capabilities", device.capabilities.joinToString(",") { it.feature })
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                Log.i(TAG, "Service registered: ${info?.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Registration failed: errorCode=$errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo?) {
                Log.d(TAG, "Service unregistered: ${info?.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: errorCode=$errorCode")
            }
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            Log.i(TAG, "NSD service registered on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NSD service", e)
        }
    }

    /**
     * Start discovering StreamSync peers on the network.
     */
    fun startDiscovery() {
        if (_isDiscovering.value) return

        nsdManager = nsdManager ?: context.getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                Log.i(TAG, "Discovery started: $regType")
                _isDiscovering.value = true
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "Discovery stopped: $serviceType")
                _isDiscovering.value = false
            }

            override fun onServiceFound(info: NsdServiceInfo?) {
                info ?: return
                Log.d(TAG, "Service found: ${info.serviceName}")
                // Skip our own service
                if (info.serviceName.startsWith("$SERVICE_NAME-${_myDeviceInfo.value?.deviceName}")) return
                resolveService(info)
            }

            override fun onServiceLost(info: NsdServiceInfo?) {
                info ?: return
                Log.d(TAG, "Service lost: ${info.serviceName}")
                removeDevice(info)
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: errorCode=$errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: errorCode=$errorCode")
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            startStaleCleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start NSD discovery", e)
        }
    }

    /**
     * Stop discovering peers.
     */
    fun stopDiscovery() {
        staleCleanupJob?.cancel()
        try {
            nsdManager?.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping discovery", e)
        }
        _isDiscovering.value = false
    }

    /**
     * Unregister this device's service.
     */
    fun unregisterService() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering service", e)
        }
    }

    /**
     * Clean up all resources.
     */
    fun destroy() {
        scope.cancel()
        stopDiscovery()
        unregisterService()
    }

    // ─── Private ─────────────────────────────────────────────────────────

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${info?.serviceName}: errorCode=$errorCode")
                resolveListeners.remove(info?.serviceName)
            }

            override fun onServiceResolved(info: NsdServiceInfo?) {
                info ?: return
                Log.d(TAG, "Service resolved: ${info.serviceName} at ${info.host?.hostAddress}:${info.port}")

                val device = parseNsdServiceInfo(info)
                if (device != null) {
                    addOrUpdateDevice(device)
                }
                resolveListeners.remove(info.serviceName)
            }
        }

        resolveListeners[serviceInfo.serviceName] = resolveListener
        try {
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving service", e)
        }
    }

    private fun parseNsdServiceInfo(info: NsdServiceInfo): Device? {
        return try {
            val attrs = info.attributes?.mapValues { it.value?.toString(Charsets.UTF_8) ?: "" } ?: emptyMap()
            val capabilities = (attrs["capabilities"] ?: "")
                .split(",")
                .filter { it.isNotBlank() }
                .map { Capability(feature = it.trim()) }

            Device(
                deviceId = attrs["device_id"] ?: info.serviceName,
                deviceName = attrs["device_name"] ?: info.serviceName.removePrefix("$SERVICE_NAME-"),
                deviceType = DeviceType.fromValue(attrs["device_type"]?.toIntOrNull() ?: 0),
                osVersion = attrs["os_version"] ?: "",
                appVersion = attrs["app_version"] ?: "",
                protocolVersion = attrs["protocol_version"]?.toIntOrNull() ?: 1,
                capabilities = capabilities,
                ipAddress = info.host?.hostAddress ?: "",
                port = info.port,
                isActive = true,
                lastSeen = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse NsdServiceInfo", e)
            null
        }
    }

    private fun addOrUpdateDevice(device: Device) {
        _discoveredDevices.update { current ->
            val existing = current.indexOfFirst { it.deviceId == device.deviceId }
            if (existing >= 0) {
                current.toMutableList().apply {
                    set(existing, device.copy(lastSeen = System.currentTimeMillis()))
                }
            } else {
                current + device
            }
        }
    }

    private fun removeDevice(serviceInfo: NsdServiceInfo) {
        _discoveredDevices.update { current ->
            current.filterNot { it.deviceName == serviceInfo.serviceName.removePrefix("$SERVICE_NAME-") }
        }
    }

    private fun startStaleCleanup() {
        staleCleanupJob = scope.launch {
            while (isActive) {
                delay(DISCOVERY_INTERVAL_MS)
                val now = System.currentTimeMillis()
                _discoveredDevices.update { devices ->
                    devices.filter { now - it.lastSeen < STALE_DEVICE_TIMEOUT_MS }
                }
            }
        }
    }
}
