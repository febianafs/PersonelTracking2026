package com.example.personeltracking2026.core.sos

import com.example.personeltracking2026.core.mqtt.MqttManager
import com.example.personeltracking2026.core.mqtt.MqttPayloadBuilder
import com.example.personeltracking2026.core.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SosManager {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var mqttManager: MqttManager? = null
    private var sessionManager: SessionManager? = null
    private var serialNumber: String = "unknown"
    private var getLocation: (() -> Pair<Double, Double>)? = null

    fun init(
        mqtt: MqttManager,
        session: SessionManager,
        serial: String,
        locationProvider: () -> Pair<Double, Double>
    ) {
        mqttManager    = mqtt
        sessionManager = session
        serialNumber   = serial
        getLocation    = locationProvider
    }

    fun activate() { _isActive.value = true; publishSos() }
    fun deactivate() { _isActive.value = false }
    fun toggle() { if (_isActive.value) deactivate() else activate() }

    private fun publishSos() {
        val mqtt    = mqttManager ?: return
        val session = sessionManager ?: return
        val (lat, lon) = getLocation?.invoke() ?: return

        val payload = MqttPayloadBuilder.buildSosPayload(
            session      = session,
            serialNumber = serialNumber,
            lat          = lat,
            lon          = lon
        )
        mqtt.publishSos(payload)
    }
}