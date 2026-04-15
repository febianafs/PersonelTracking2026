package com.example.personeltracking2026.core.mqtt.queue

import android.content.Context
import com.example.personeltracking2026.core.mqtt.MqttManager

class MqttQueueManager(context: Context) {

    private val dao = MqttDatabase.getInstance(context).mqttDao()

    suspend fun save(topic: String, payload: String) {
        dao.insert(
            MqttMessageEntity(
                topic = topic,
                payload = payload,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun flush(mqttManager: MqttManager) {

        val list = dao.getAll()

        list.forEach {
            mqttManager.publishDirect(it.topic, it.payload)
        }

        dao.clear()
    }
}