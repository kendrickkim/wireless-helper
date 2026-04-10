package com.andrerinas.wirelesshelper.strategy

import android.content.Context
import android.util.Log
import com.andrerinas.wirelesshelper.connection.NearbySocket
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A connection strategy using Google Nearby Connections API.
 * The Phone (WirelessHelper) acts as an ADVERTISER only.
 * Uses Stream Tunneling (like Emil's implementation) for robust connections.
 */
class StrategyNearby(context: Context, scope: CoroutineScope) : BaseStrategy(context, scope) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.andrerinas.headunitrevived.NEARBY"
    private var activeNearbySocket: NearbySocket? = null

    override fun start() {
        Log.i(TAG, "NearbyStrategy: Starting Nearby Connections (Advertiser only)...")
        startAdvertising()
    }

    override fun stop() {
        Log.i(TAG, "NearbyStrategy: Stopping Nearby Connections...")
        connectionsClient.stopAdvertising()
        connectionsClient.stopAllEndpoints()
        cleanup()
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        connectionsClient.startAdvertising(
            android.os.Build.MODEL, // Use phone model as name
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        )
            .addOnSuccessListener { Log.d("NearbyStrategy", "Advertising started successfully") }
            .addOnFailureListener { e -> Log.e("NearbyStrategy", "Advertising failed: ${e.message}") }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.i(TAG, "NearbyStrategy: Connection initiated with $endpointId. Accepting and stopping advertising...")
            connectionsClient.stopAdvertising() // Stop immediately to free up radio bandwidth
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.i(TAG, "NearbyStrategy: Connected to $endpointId. Establishing Stream Tunnel...")
                    val socket = NearbySocket()
                    activeNearbySocket = socket
                    
                    val pipes = android.os.ParcelFileDescriptor.createPipe()
                    socket.outputStreamWrapper = android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])
                    val streamPayload = Payload.fromStream(android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0]))
                    connectionsClient.sendPayload(endpointId, streamPayload)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> Log.w("NearbyStrategy", "Connection rejected by $endpointId")
                ConnectionsStatusCodes.STATUS_ERROR -> Log.e("NearbyStrategy", "Connection error with $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "NearbyStrategy: Disconnected from $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.STREAM) {
                Log.i(TAG, "NearbyStrategy: Received STREAM payload from $endpointId. Launching Android Auto tunnel...")
                activeNearbySocket?.inputStreamWrapper = payload.asStream()?.asInputStream()
                
                // Pass the pre-connected socket to launchAndroidAuto.
                // The hostIp is ignored because the socket is already connected.
                launchAndroidAuto("127.0.0.1", preConnectedSocket = activeNearbySocket)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Monitor transfer progress
        }
    }
}
