package com.example.personeltracking2026

import android.app.Application
import com.example.personeltracking2026.core.mqtt.MqttManager
import com.example.personeltracking2026.core.service.MqttLocationService

class App : Application() {

    lateinit var mqttManager: MqttManager

    // ── Heart Rate global state ─────────────────────────────────────
    // Diupdate oleh PersonelViewModel/BluetoothViewModel saat data BLE masuk.
    // Dibaca oleh MqttLocationService saat build payload.
    @Volatile var currentHeartRate   : Int  = 0
    @Volatile var currentHeartRateTs : Long = 0L

    override fun onCreate() {
        super.onCreate()
        mqttManager = MqttManager(this).apply {
            connect()
        }
    }
}