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

    // ── BLE data (diisi dari PersonelActivity via observer) ──────────────
    var bleDeviceName: String = "--"          // nama device terhubung
    var bleConnected: Boolean = false         // status koneksi
    var bleBpm: Int = 0                       // BPM realtime

    override fun getItemCount() = 3

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val isLandscape = parent.context.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

        return when (viewType) {
            TYPE_PROFILE -> {
                val layoutRes = if (isLandscape) R.layout.personel_items_land else R.layout.personel_items
                ProfileVH(inflater.inflate(layoutRes, parent, false))
            }
            TYPE_VITAL -> VitalVH(inflater.inflate(R.layout.vital_items, parent, false))
            TYPE_MQTT  -> MqttVH(inflater.inflate(R.layout.mqtt_items, parent, false))
            else -> throw IllegalArgumentException()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ProfileVH -> holder.bind(name, nrp, rank, latitude, longitude, battery, lastSync, statusColor, onFullDataClick)
            is VitalVH   -> holder.bind(heartRate, lastSync, statusColor, bleDeviceName, bleConnected, bleBpm)
            is MqttVH    -> holder.bind(mqttHost, mqttPort, interval, lastSync, statusColor)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // VIEW HOLDERS
    // ══════════════════════════════════════════════════════════════════════

    class ProfileVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName     = itemView.findViewById<TextView?>(R.id.tvName)
        private val tvNRP      = itemView.findViewById<TextView?>(R.id.tvNRP)
        private val tvRank     = itemView.findViewById<TextView>(R.id.tvRank)
        private val tvLat      = itemView.findViewById<TextView>(R.id.tvLat)
        private val tvLon      = itemView.findViewById<TextView>(R.id.tvLon)
        private val tvBattery  = itemView.findViewById<TextView?>(R.id.tvBattery)
        private val tvLastSync = itemView.findViewById<TextView?>(R.id.tvLastSync)
        private val dot        = itemView.findViewById<View>(R.id.dotLastSync)
        private val btnFullData = itemView.findViewById<TextView>(R.id.btnDataSelengkapnya)

        fun bind(
            name: String, nrp: String, rank: String,
            latitude: Double, longitude: Double,
            battery: Int, lastSync: String, statusColor: String,
            onFullDataClick: (() -> Unit)? = null
        ) {
            tvName?.text     = name
            tvNRP?.text      = nrp
            tvRank?.text     = rank
            tvLat?.text      = "Lat : $latitude"
            tvLon?.text      = "Lon : $longitude"
            tvBattery?.text  = "$battery%"
            tvLastSync?.text = lastSync

            val parsedColor = android.graphics.Color.parseColor(statusColor)
            tvLastSync?.setTextColor(parsedColor)
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(parsedColor)

            btnFullData.setOnClickListener { onFullDataClick?.invoke() }
        }
    }

    class VitalVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeartRate   = itemView.findViewById<TextView?>(R.id.tvHeartRate)
        private val tvLastSync    = itemView.findViewById<TextView?>(R.id.tvLastSync)
        private val dot           = itemView.findViewById<View>(R.id.dotLastSync)
        private val btnBleSettings = itemView.findViewById<TextView>(R.id.btnBleSettings)

        // View BLE info di vital_items.xml
        private val tvDeviceName  = itemView.findViewById<TextView?>(R.id.tvDeviceName)
        private val tvBleStatus   = itemView.findViewById<TextView?>(R.id.tvBleStatus)
        private val tvBpmCategory = itemView.findViewById<TextView?>(R.id.tvBpmCategory)

        fun bind(
            heartRate: Int,
            lastSync: String,
            statusColor: String,
            bleDeviceName: String,
            bleConnected: Boolean,
            bleBpm: Int
        ) {
            // ── Last sync ─────────────────────────────────────────────────
            tvLastSync?.text = lastSync
            val parsedColor = android.graphics.Color.parseColor(statusColor)
            tvLastSync?.setTextColor(parsedColor)
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(parsedColor)

            // ── BPM ───────────────────────────────────────────────────────
            val displayBpm = if (bleBpm > 0) bleBpm else heartRate
            tvHeartRate?.text = if (displayBpm > 0) "$displayBpm BPM" else "-- BPM"

            // ── Kategori BPM ──────────────────────────────────────────────
            val (categoryText, categoryColor) = when {
                !bleConnected || displayBpm == 0 -> "--" to "#9E9E9E"
                displayBpm < 60  -> "Di Bawah Normal" to "#FF9800"
                displayBpm > 100 -> "Di Atas Normal"  to "#FF5252"
                else             -> "Normal"           to "#69F0AE"
            }
            tvBpmCategory?.text = categoryText
            tvBpmCategory?.setTextColor(android.graphics.Color.parseColor(categoryColor))

            // ── Nama device & status ──────────────────────────────────────
            if (bleConnected) {
                tvDeviceName?.text = bleDeviceName.ifBlank { "Unknown Device" }
                tvBleStatus?.text  = "Connected"
                tvBleStatus?.setTextColor(android.graphics.Color.parseColor("#4FC3F7"))
            } else {
                tvDeviceName?.text = "Tidak ada perangkat"
                tvBleStatus?.text  = "Disconnected"
                tvBleStatus?.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            }

            // ── Navigasi ke BLE Settings ──────────────────────────────────
            btnBleSettings.setOnClickListener {
                val context = itemView.context
                context.startActivity(
                    Intent(context, com.example.personeltracking2026.ui.bluetooth.BluetoothActivity::class.java)
                )
            }
        }
    }

    class MqttVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHostIp   = itemView.findViewById<TextView>(R.id.tvHostIp)
        private val tvPort     = itemView.findViewById<TextView>(R.id.tvPort)
        private val tvInterval = itemView.findViewById<TextView>(R.id.tvInterval)
        private val tvLastSync = itemView.findViewById<TextView>(R.id.tvLastSync)
        private val dot        = itemView.findViewById<View>(R.id.dotLastSync)
        private val btnSettings = itemView.findViewById<TextView>(R.id.mqttSettingBtn)

        fun bind(
            hostIp: String, port: String, interval: String,
            lastSync: String, statusColor: String
        ) {
            tvHostIp.text   = hostIp
            tvPort.text     = port
            tvInterval.text = interval
            tvLastSync.text = lastSync

            val parsedColor = android.graphics.Color.parseColor(statusColor)
            tvLastSync.setTextColor(parsedColor)
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(parsedColor)

            btnSettings.setOnClickListener {
                val context = itemView.context
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }
        }
    }
}