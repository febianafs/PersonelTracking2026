package com.example.personeltracking2026

import android.app.Application
import com.example.personeltracking2026.core.mqtt.MqttManager

class App : Application() {

    lateinit var mqttManager: MqttManager

    override fun onCreate() {
        super.onCreate()
        mqttManager = MqttManager(this).apply {
            connect()
        }
    }
}