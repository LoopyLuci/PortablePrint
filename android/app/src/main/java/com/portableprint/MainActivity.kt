package com.portableprint

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.portableprint.databinding.ActivityMainBinding
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private val devices = mutableListOf<BluetoothDevice>()
    private val rfcommUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) scanDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scanButton.setOnClickListener {
            ensurePermissionsAndScan()
        }

        binding.printButton.setOnClickListener {
            printLabel()
        }
    }

    private fun ensurePermissionsAndScan() {
        val permissions = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissions.isEmpty()) {
            scanDevices()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun scanDevices() {
        devices.clear()
        val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
        devices.addAll(bonded)
        val names = devices.map { "${it.name} (${it.address})" }
        binding.deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        if (names.isNotEmpty()) binding.deviceSpinner.setSelection(0)
    }

    private fun selectedDevice(): BluetoothDevice? {
        val idx = binding.deviceSpinner.selectedItemPosition
        return if (idx in devices.indices) devices[idx] else null
    }

    private fun printLabel() {
        val device = selectedDevice()
        val text = binding.labelText.text?.toString().orEmpty()
        if (device == null) {
            Toast.makeText(this, "Select a device first", Toast.LENGTH_SHORT).show()
            return
        }
        if (text.isBlank()) {
            Toast.makeText(this, "Enter label text first", Toast.LENGTH_SHORT).show()
            return
        }
        val channel = binding.channelSpinner.selectedItem.toString().toIntOrNull() ?: 1
        Thread {
            try {
                socket = device.createRfcommSocketToServiceRecord(rfcommUuid)
                socket?.connect()
                val out: OutputStream = socket?.outputStream ?: return@Thread
                val payload = com.portableprint.shared.PrinterPayloadBuilder.buildText(text)
                out.write(payload)
                out.flush()
                runOnUiThread {
                    Toast.makeText(this, "Printed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Print error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { socket?.close() } catch (_: Exception) {}
    }
}
