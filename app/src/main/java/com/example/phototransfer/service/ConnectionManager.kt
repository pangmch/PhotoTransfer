package com.example.phototransfer.service

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val serviceId = "com.example.phototransfer"
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _connectedDevices = MutableStateFlow<Set<String>>(emptySet())
    val connectedDevices: StateFlow<Set<String>> = _connectedDevices
    
    private var currentEndpointId: String? = null
    
    fun startAdvertising(deviceName: String): Flow<AdvertisingResult> = callbackFlow {
        val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                // Auto-accept the connection for bidirectional mode
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                trySend(AdvertisingResult.ConnectionRequested(endpointId, connectionInfo.endpointName))
            }
            
            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        currentEndpointId = endpointId
                        _connectedDevices.value = _connectedDevices.value + endpointId
                        _connectionState.value = ConnectionState.Connected(endpointId)
                        trySend(AdvertisingResult.Connected(endpointId))
                    }
                    else -> {
                        _connectionState.value = ConnectionState.Error("Connection failed")
                        trySend(AdvertisingResult.Failed("Connection failed"))
                    }
                }
            }
            
            override fun onDisconnected(endpointId: String) {
                _connectedDevices.value = _connectedDevices.value - endpointId
                if (_connectedDevices.value.isEmpty()) {
                    _connectionState.value = ConnectionState.Idle
                }
                trySend(AdvertisingResult.Disconnected(endpointId))
            }
        }
        
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        
        connectionsClient.startAdvertising(
            deviceName,
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            // Update state based on current state (support bidirectional mode)
            _connectionState.value = when (_connectionState.value) {
                is ConnectionState.Discovering -> ConnectionState.AdvertisingAndDiscovering
                else -> ConnectionState.Advertising
            }
            trySend(AdvertisingResult.Started)
        }.addOnFailureListener { e ->
            _connectionState.value = ConnectionState.Error(e.message ?: "Advertising failed")
            trySend(AdvertisingResult.Failed(e.message ?: "Advertising failed"))
        }
        
        awaitClose {
            connectionsClient.stopAdvertising()
        }
    }
    
    fun startDiscovery(): Flow<DiscoveryResult> = callbackFlow {
        val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                trySend(DiscoveryResult.DeviceFound(endpointId, info.endpointName))
            }
            
            override fun onEndpointLost(endpointId: String) {
                trySend(DiscoveryResult.DeviceLost(endpointId))
            }
        }
        
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        
        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            // Update state based on current state (support bidirectional mode)
            _connectionState.value = when (_connectionState.value) {
                is ConnectionState.Advertising -> ConnectionState.AdvertisingAndDiscovering
                else -> ConnectionState.Discovering
            }
            trySend(DiscoveryResult.Started)
        }.addOnFailureListener { e ->
            _connectionState.value = ConnectionState.Error(e.message ?: "Discovery failed")
            trySend(DiscoveryResult.Failed(e.message ?: "Discovery failed"))
        }
        
        awaitClose {
            connectionsClient.stopDiscovery()
        }
    }
    
    fun requestConnection(endpointId: String, deviceName: String): Flow<ConnectionResult> = callbackFlow {
        val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                // Auto-accept the connection
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                trySend(ConnectionResult.Initiated(endpointId))
            }
            
            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        currentEndpointId = endpointId
                        _connectedDevices.value = _connectedDevices.value + endpointId
                        _connectionState.value = ConnectionState.Connected(endpointId)
                        trySend(ConnectionResult.Connected(endpointId))
                    }
                    else -> {
                        _connectionState.value = ConnectionState.Error("Connection failed")
                        trySend(ConnectionResult.Failed("Connection failed"))
                    }
                }
            }
            
            override fun onDisconnected(endpointId: String) {
                _connectedDevices.value = _connectedDevices.value - endpointId
                if (_connectedDevices.value.isEmpty()) {
                    _connectionState.value = ConnectionState.Idle
                }
                trySend(ConnectionResult.Disconnected(endpointId))
            }
        }
        
        connectionsClient.requestConnection(
            deviceName,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            trySend(ConnectionResult.Requesting)
        }.addOnFailureListener { e ->
            _connectionState.value = ConnectionState.Error(e.message ?: "Request failed")
            trySend(ConnectionResult.Failed(e.message ?: "Request failed"))
        }
        
        awaitClose {
            // Cleanup if needed
        }
    }
    
    fun acceptConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
    }
    
    fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
    }
    
    fun disconnect() {
        currentEndpointId?.let { endpointId ->
            connectionsClient.disconnectFromEndpoint(endpointId)
        }
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        _connectionState.value = ConnectionState.Idle
        _connectedDevices.value = emptySet()
        currentEndpointId = null
    }
    
    fun getCurrentEndpointId(): String? = currentEndpointId
    
    // This will be set by TransferManager
    var payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // Default empty implementation
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Default empty implementation
        }
    }
}

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Advertising : ConnectionState()
    object Discovering : ConnectionState()
    object AdvertisingAndDiscovering : ConnectionState()
    data class Connected(val endpointId: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

sealed class AdvertisingResult {
    object Started : AdvertisingResult()
    data class ConnectionRequested(val endpointId: String, val deviceName: String) : AdvertisingResult()
    data class Connected(val endpointId: String) : AdvertisingResult()
    data class Disconnected(val endpointId: String) : AdvertisingResult()
    data class Failed(val error: String) : AdvertisingResult()
}

sealed class DiscoveryResult {
    object Started : DiscoveryResult()
    data class DeviceFound(val endpointId: String, val deviceName: String) : DiscoveryResult()
    data class DeviceLost(val endpointId: String) : DiscoveryResult()
    data class Failed(val error: String) : DiscoveryResult()
}

sealed class ConnectionResult {
    object Requesting : ConnectionResult()
    data class Initiated(val endpointId: String) : ConnectionResult()
    data class Connected(val endpointId: String) : ConnectionResult()
    data class Disconnected(val endpointId: String) : ConnectionResult()
    data class Failed(val error: String) : ConnectionResult()
}
