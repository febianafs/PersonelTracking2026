package com.example.personeltracking2026.core.mqtt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personeltracking2026.core.mqtt.worker.MqttKickWorker
import com.example.personeltracking2026.core.service.MqttLocationService
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Start service saat device boot
        MqttLocationService.startService(ctx)
    }
}