package com.example.phototransfer.ui.transfer

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phototransfer.service.ConnectionManager
import com.example.phototransfer.service.ConnectionState
import com.example.phototransfer.service.DiscoveryResult
import com.example.phototransfer.service.TransferManager
import com.example.phototransfer.service.TransferProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val transferManager: TransferManager
) : ViewModel() {
    
    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val transferProgress: StateFlow<TransferProgress> = transferManager.transferProgress
    
    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
    
    // Track discovered devices
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // Track if we're already connecting to avoid duplicate connection attempts
    private var isConnecting = false

    // Track connected device name for transfer
    private var connectedDeviceName: String = ""

    fun startAdvertising() {
        viewModelScope.launch {
            connectionManager.startAdvertising(deviceName).collect { result ->
                // Handle advertising results if needed
            }
        }
    }
    
    fun startDiscovery() {
        viewModelScope.launch {
            connectionManager.startDiscovery().collect { result ->
                when (result) {
                    is DiscoveryResult.DeviceFound -> {
                        // Add to discovered devices list
                        val device = DiscoveredDevice(result.endpointId, result.deviceName)
                        _discoveredDevices.value = _discoveredDevices.value + device
                    }
                    is DiscoveryResult.DeviceLost -> {
                        // Remove from discovered devices list
                        _discoveredDevices.value = _discoveredDevices.value.filter {
                            it.endpointId != result.endpointId
                        }
                    }
                    else -> { /* Started or Failed handled elsewhere */ }
                }
            }
        }
    }

    fun connectToDevice(endpointId: String) {
        if (isConnecting) return
        isConnecting = true

        // Find and store the device name
        connectedDeviceName = _discoveredDevices.value.find { it.endpointId == endpointId }?.deviceName ?: "Unknown Device"

        viewModelScope.launch {
            connectionManager.requestConnection(endpointId, deviceName).collect { result ->
                when (result) {
                    is com.example.phototransfer.service.ConnectionResult.Connected -> {
                        isConnecting = false
                    }
                    is com.example.phototransfer.service.ConnectionResult.Failed -> {
                        isConnecting = false
                    }
                    is com.example.phototransfer.service.ConnectionResult.Disconnected -> {
                        isConnecting = false
                    }
                    else -> { /* Requesting or Initiated */ }
                }
            }
        }
    }
    
    fun disconnect() {
        isConnecting = false
        connectedDeviceName = ""
        connectionManager.disconnect()
    }
    
    fun sendPhoto(photoUri: Uri, fileName: String) {
        viewModelScope.launch {
            transferManager.sendPhoto(photoUri, fileName, connectedDeviceName)
        }
    }

    fun getCurrentEndpointId(): String? {
        return connectionManager.getCurrentEndpointId()
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

data class DiscoveredDevice(
    val endpointId: String,
    val deviceName: String
)

