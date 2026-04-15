package com.example.personeltracking2026.core.mqtt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personeltracking2026.core.mqtt.worker.MqttKickWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {

        val oneTime = OneTimeWorkRequestBuilder<MqttKickWorker>()
            .setInitialDelay(15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(ctx).enqueue(oneTime)
    }
}