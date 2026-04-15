package com.example.personeltracking2026.core.mqtt.queue

import androidx.room.Insert
import androidx.room.Query

interface MqttDao {

    @Insert
    suspend fun insert(msg: MqttMessageEntity)

    @Query("SELECT * FROM mqtt_queue ORDER BY id ASC")
    suspend fun getAll(): List<MqttMessageEntity>

    @Query("DELETE FROM mqtt_queue")
    suspend fun clear()
}