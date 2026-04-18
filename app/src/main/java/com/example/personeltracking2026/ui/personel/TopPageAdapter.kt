package com.example.personeltracking2026.ui.personel

import android.content.Intent
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.personeltracking2026.R
import com.example.personeltracking2026.ui.settings.SettingsActivity

class TopPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_PROFILE = 0
        private const val TYPE_VITAL = 1
        private const val TYPE_MQTT = 2
    }

    var name: String = "-"
    var nrp: String = "-"
    var rank: String = "-"
    var lastSync: String = "-"
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var heartRate: Int = 0
    var battery: Int = 0
    var gpsSignal: String = "-"
    var statusColor: String = "#69F0AE"
    var mqttHost: String = "-"
    var mqttPort: String = "-"
    var interval: String = "-"
    var onFullDataClick: (() -> Unit)? = null

    override fun getItemCount() = 3

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        // Deteksi orientasi landscape
        val isLandscape = parent.context.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

        return when (viewType) {
            TYPE_PROFILE -> {
                val layoutRes = if (isLandscape) R.layout.personel_items_land else R.layout.personel_items
                val view = inflater.inflate(layoutRes, parent, false)
                ProfileVH(view)
            }
            TYPE_VITAL -> {
                val view = inflater.inflate(R.layout.vital_items, parent, false)
                VitalVH(view)
            }
            TYPE_MQTT -> {
                val view = inflater.inflate(R.layout.mqtt_items, parent, false)
                MqttVH(view)
            }
            else -> throw IllegalArgumentException()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ProfileVH -> holder.bind(name, nrp, rank, latitude, longitude, battery, lastSync, statusColor, onFullDataClick)
            is VitalVH -> holder.bind(heartRate, lastSync, statusColor)
            is MqttVH -> holder.bind(mqttHost, mqttPort, interval, lastSync, statusColor)
        }
    }

    class ProfileVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName = itemView.findViewById<TextView?>(R.id.tvName)
        private val tvNRP = itemView.findViewById<TextView?>(R.id.tvNRP)
        private val tvRank = itemView.findViewById<TextView>(R.id.tvRank)
        private val tvLat = itemView.findViewById<TextView>(R.id.tvLat)
        private val tvLon = itemView.findViewById<TextView>(R.id.tvLon)
        private val tvBattery = itemView.findViewById<TextView?>(R.id.tvBattery)
        private val tvLastSync = itemView.findViewById<TextView?>(R.id.tvLastSync)
        private val dot = itemView.findViewById<View>(R.id.dotLastSync)
        private val btnFullData = itemView.findViewById<TextView>(R.id.btnDataSelengkapnya)

        fun bind(
            name: String,
            nrp: String,
            rank: String,
            latitude: Double,
            longitude: Double,
            battery: Int,
            lastSync: String,
            statusColor: String,
            onFullDataClick: (() -> Unit)? = null
        ) {
            tvName?.text = name
            tvNRP?.text = nrp
            tvRank?.text = rank
            tvLat?.text = "Lat : $latitude"
            tvLon?.text = "Lon : $longitude"
            tvBattery?.text = "$battery%"
            tvLastSync?.text = lastSync

            val parsedColor = android.graphics.Color.parseColor(statusColor)
            tvLastSync.setTextColor(parsedColor)

            dot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(parsedColor)

            btnFullData.setOnClickListener {
                onFullDataClick?.invoke()
            }
        }
    }

    class VitalVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeartRate = itemView.findViewById<TextView?>(R.id.tvHeartRate)
        private val tvLastSync = itemView.findViewById<TextView?>(R.id.tvLastSync)
        private val dot = itemView.findViewById<View>(R.id.dotLastSync)

        fun bind(
            heartRate: Int,
            lastSync: String,
            statusColor: String
        ) {
            tvHeartRate?.text = "$heartRate BPM"
            tvLastSync?.text = lastSync

            val parsedColor = android.graphics.Color.parseColor(statusColor)
            tvLastSync.setTextColor(parsedColor)

            dot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(parsedColor)
        }
    }

    class MqttVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHostIp = itemView.findViewById<TextView>(R.id.tvHostIp)
        private val tvPort = itemView.findViewById<TextView>(R.id.tvPort)
        private val tvInterval = itemView.findViewById<TextView>(R.id.tvInterval)
        private val tvLastSync = itemView.findViewById<TextView>(R.id.tvLastSync)
        private val dot = itemView.findViewById<View>(R.id.dotLastSync)
        private val btnSettings = itemView.findViewById<TextView>(R.id.mqttSettingBtn)

        fun bind(
            hostIp: String,
            port: String,
            interval: String,
            lastSync: String,
            statusColor: String
        ) {
            tvHostIp.text = "$hostIp"
            tvPort.text = "$port"
            tvInterval.text = interval
            tvLastSync.text = lastSync

            val parsedColor = android.graphics.Color.parseColor(statusColor)
            tvLastSync.setTextColor(parsedColor)

            dot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(parsedColor)

            btnSettings.setOnClickListener {
                val context = itemView.context
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }
        }
    }
}