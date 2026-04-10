package com.example.personeltracking2026.core.mqtt

import android.os.Build
import android.util.Log
import com.example.personeltracking2026.data.model.RadioDataPayload
import com.example.personeltracking2026.data.model.RadioSosPayload
import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
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

class MqttManager {

    companion object {
        private const val TAG            = "MqttManager"
        const val BROKER_HOST            = "76.13.20.253"
        const val BROKER_PORT            = 9001          // WebSocket port
        const val USERNAME               = "kodau"
        const val PASSWORD               = "kodau2026"
        const val TOPIC_DATA             = "radio/data"
        const val TOPIC_SOS              = "radio/sos"
        const val QOS_DATA               = 1
        const val QOS_SOS                = 2
        private const val MAX_RETRY      = 5
        private const val RETRY_DELAY_MS = 5_000L
    }

    var onConnected: (() -> Unit)?                              = null
    var onDisconnected: (() -> Unit)?                           = null
    var onPublishSuccess: ((topic: String) -> Unit)?            = null
    var onPublishFailed: ((topic: String, reason: String) -> Unit)? = null

    private val gson  = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var client: Mqtt3AsyncClient? = null
    private var retryJob: Job?            = null
    private var retryCount                = 0
    private var isIntentionallyStopped    = false

    // ─── PUBLIC ──────────────────────────────────────────────────────────────

    fun connect() {
        isIntentionallyStopped = false
        retryCount = 0
        scope.launch { doConnect() }
    }

    fun disconnect() {
        isIntentionallyStopped = true
        retryJob?.cancel()
        scope.launch {
            try { client?.disconnect() }
            catch (e: Exception) { Log.w(TAG, "Disconnect error: ${e.message}") }
        }
    }

    fun isConnected(): Boolean = client?.state?.isConnected == true

    fun publishData(payload: RadioDataPayload) {
        scope.launch { publish(TOPIC_DATA, gson.toJson(payload), QOS_DATA) }
    }

    fun publishSos(payload: RadioSosPayload) {
        scope.launch { publish(TOPIC_SOS, gson.toJson(payload), QOS_SOS) }
    }

    // ─── INTERNAL ────────────────────────────────────────────────────────────

    private fun doConnect() {
        val clientId = "android_${Build.MODEL}_${System.currentTimeMillis()}"
            .replace(" ", "_")
            .take(23) // MQTT client ID max 23 chars

        client = Mqtt3Client.builder()
            .identifier(clientId)
            .serverHost(BROKER_HOST)
            .serverPort(BROKER_PORT)
            .webSocketConfig()          // ← aktifkan WebSocket (ws://)
            .serverPath("/")
            .applyWebSocketConfig()
            .simpleAuth()
            .username(USERNAME)
            .password(PASSWORD.toByteArray(StandardCharsets.UTF_8))
            .applySimpleAuth()
            .buildAsync()

        client?.connect()
            ?.whenComplete { connAck: Mqtt3ConnAck?, throwable: Throwable? ->
                if (throwable != null) {
                    Log.e(TAG, "Connect failed: ${throwable.message}")
                    onDisconnected?.invoke()
                    if (!isIntentionallyStopped) scheduleRetry()
                    return@whenComplete
                }
                if (connAck?.returnCode == Mqtt3ConnAckReturnCode.SUCCESS) {
                    Log.i(TAG, "MQTT connected")
                    retryCount = 0
                    onConnected?.invoke()
                } else {
                    Log.e(TAG, "Connect rejected: ${connAck?.returnCode}")
                    onDisconnected?.invoke()
                    if (!isIntentionallyStopped) scheduleRetry()
                }
            }
    }

    private fun publish(topic: String, json: String, qos: Int) {
        if (!isConnected()) {
            Log.w(TAG, "Publish skipped – not connected (topic=$topic)")
            onPublishFailed?.invoke(topic, "Not connected")
            return
        }
        val mqttQos = if (qos >= 2) MqttQos.EXACTLY_ONCE else MqttQos.AT_LEAST_ONCE

        client?.publishWith()
            ?.topic(topic)
            ?.qos(mqttQos)
            ?.payload(json.toByteArray(StandardCharsets.UTF_8))
            ?.retain(false)
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Publish failed ($topic): ${throwable.message}")
                    onPublishFailed?.invoke(topic, throwable.message ?: "Unknown error")
                } else {
                    Log.d(TAG, "Published to $topic")
                    onPublishSuccess?.invoke(topic)
                }
            }
    }

    private fun scheduleRetry() {
        if (retryCount >= MAX_RETRY) {
            Log.e(TAG, "Max retry reached. Giving up.")
            return
        }
        retryJob?.cancel()
        retryJob = scope.launch {
            retryCount++
            val delayMs = RETRY_DELAY_MS * retryCount
            Log.i(TAG, "Retry #$retryCount in ${delayMs / 1000}s...")
            delay(delayMs)
            if (!isIntentionallyStopped) doConnect()
        }
    }
}