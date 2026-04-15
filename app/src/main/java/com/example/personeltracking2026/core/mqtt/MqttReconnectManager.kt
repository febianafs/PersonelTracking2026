package com.example.personeltracking2026.core.mqtt

import android.content.Context
import android.net.*
import android.util.Log
import kotlinx.coroutines.*

class MqttReconnectManager(
    private val context: Context,
    private val mqttManager: MqttManager
) {

    private val TAG = "MQTT_RECONNECT"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var reconnectJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isReconnecting = false

    // ─────────────────────────────
    // START SYSTEM
    // ─────────────────────────────
    fun start() {
        startNetworkMonitor()
        startWatchdog()
    }

    // ─────────────────────────────
    // STOP SYSTEM
    // ─────────────────────────────
    fun stop() {
        reconnectJob?.cancel()
        unregisterNetwork()
    }

    // ─────────────────────────────
    // NETWORK MONITOR (WiFi ON/OFF)
    // ─────────────────────────────
    private fun startNetworkMonitor() {

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {

                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(network)

                val isReady = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

                if (isReady) {
                    Log.d(TAG, "Network validated → reconnect MQTT")

                    reconnectNow()
                } else {
                    Log.d(TAG, "Network not ready → skip")
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
            }
        }

        cm.registerNetworkCallback(request, networkCallback!!)
    }

    private fun unregisterNetwork() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
    }

    // ─────────────────────────────
    // WATCHDOG (CHECK TIAP 5 DETIK)
    // ─────────────────────────────
    private fun startWatchdog() {

        reconnectJob = scope.launch {

            while (isActive) {

                if (!mqttManager.isConnected()) {
                    Log.d(TAG, "MQTT not connected → reconnect")
                    reconnectNow()
                }

                delay(5000)
            }
        }
    }

    // ─────────────────────────────
    // RECONNECT
    // ─────────────────────────────
    private fun reconnectNow() {

        if (isReconnecting) return
        isReconnecting = true

        scope.launch {

            delay(2000)

            try {
                mqttManager.disconnect()
            } catch (_: Exception) {}

            try {
                mqttManager.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Reconnect error: ${e.message}")
            }

            isReconnecting = false
        }
    }
}