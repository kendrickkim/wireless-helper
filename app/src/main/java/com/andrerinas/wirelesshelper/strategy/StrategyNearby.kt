package com.andrerinas.wirelesshelper.strategy

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope

/**
 * A connection strategy using Google Nearby Connections API.
 * Inspired by WiFi-Launcher 4.0.
 */
class StrategyNearby(context: Context, scope: CoroutineScope) : BaseStrategy(context, scope) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.andrerinas.headunitrevived.NEARBY"

    override fun start() {
        Log.i("NearbyStrategy", "Starting Nearby Connections discovery...")
        startDiscovery()
    }

    override fun stop() {
        Log.i("NearbyStrategy", "Stopping Nearby Connections...")
        connectionsClient.stopDiscovery()
        connectionsClient.stopAdvertising()
        cleanup()
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { Log.d("NearbyStrategy", "Discovery started successfully") }
            .addOnFailureListener { e -> Log.e("NearbyStrategy", "Discovery failed: ${e.message}") }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.i("NearbyStrategy", "Endpoint found: ${info.endpointName} ($endpointId)")
            // Future: Handle connection and IP exchange here
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i("NearbyStrategy", "Endpoint lost: $endpointId")
        }
    }
}
