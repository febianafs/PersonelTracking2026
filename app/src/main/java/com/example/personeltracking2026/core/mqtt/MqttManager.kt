package com.example.personeltracking2026.core.mqtt

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.personeltracking2026.core.mqtt.queue.MqttQueueManager
import com.example.personeltracking2026.core.utils.MqttLogger
import com.example.personeltracking2026.data.model.RadioDataPayload
import com.example.personeltracking2026.data.model.RadioSosPayload
import com.google.gson.Gson
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class MqttManager(private val context: Context) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    lateinit var queueManager: MqttQueueManager
    private var client: Mqtt3AsyncClient? = null

    private var isConnecting = false

    private var reconnectAttempts = 0

    var onPublishFailed: ((String, String) -> Unit)? = null

    init {
        queueManager = MqttQueueManager(context)
    }

    fun connect() {
        isConnecting = false
        scope.launch { doConnect() }
    }

    private fun doConnect() {
        // Jika sudah dalam proses koneksi, skip
        if (isConnecting) {
            Log.d("MQTT", "Already connecting, skip")
            return
        }

        isConnecting = true

        val config = MqttConfigManager(context).load()
        val builder = Mqtt3Client.builder()
            .identifier("client-${System.currentTimeMillis()}")
            .serverHost(config.host)
            .serverPort(if (config.useWebSocket) config.wsPort else config.tcpPort)

        if (config.useWebSocket) {
            builder.webSocketConfig()
                .serverPath("/")
                .applyWebSocketConfig()
        }

        builder.simpleAuth()
            .username(config.username)
            .password(config.password.toByteArray())
            .applySimpleAuth()

        client = builder.buildAsync()

        try {
            val connAck = client!!.connectWith()
                .cleanSession(true)
                .keepAlive(20)
                .send()
                .get(10, TimeUnit.SECONDS)

            if (connAck != null) {
                Log.d("MQTT", "Connected")
                reconnectAttempts = 0 // Reset attempts after success
                scope.launch {
                    queueManager.flush(this@MqttManager) // Flush queue jika koneksi berhasil
                }
            }

        } catch (e: Exception) {
            Log.e("MQTT", "Connect failed: ${e.message}")

            // Retry mechanism with exponential backoff
            reconnectAttempts++
            if (reconnectAttempts < 5) {
                val delayTime = 2.0.pow(reconnectAttempts.toDouble()).toLong()
                Log.d("MQTT", "Retrying in $delayTime seconds")
                Thread.sleep(delayTime * 1000)  // Delay in seconds
                doConnect() // Retry connect after delay
            } else {
                Log.e("MQTT", "Max reconnect attempts reached")
            }
        } finally {
            isConnecting = false  // Always reset flag after attempt (even if it fails)
        }
    }

    fun publish(topic: String, json: String) {
        if (client != null && client!!.state.isConnected) {
            client?.publishWith()
                ?.topic(topic)
                ?.payload(json.toByteArray())
                ?.send()

            MqttLogger.publish(topic, json)
        } else {
            // Simpan data yang gagal dipublish ke queue (Room)
            scope.launch {
                queueManager.save(topic, json)
            }

            Log.d("MQTT_QUEUE", "Saved to queue DB")
            onPublishFailed?.invoke(topic, "Queued (offline)")  // Inform UI or callback
        }
    }

    fun isConnected(): Boolean = client?.state?.isConnected == true
}