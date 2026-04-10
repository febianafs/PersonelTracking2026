package com.example.personeltracking2026.data.model

import com.google.gson.annotations.SerializedName

// ─── TOPIC: radio/data ───────────────────────────────────────────────────────

data class RadioDataPayload(
    @SerializedName("timestamp")      val timestamp: Long,
    @SerializedName("serial_number")  val serialNumber: String,
    @SerializedName("identity")       val identity: IdentityPayload,
    @SerializedName("gps")            val gps: GpsPayload,
    @SerializedName("radio_health")   val radioHealth: RadioHealthPayload,
    @SerializedName("battery")        val battery: BatteryPayload
)

data class IdentityPayload(
    @SerializedName("id")         val id: String,
    @SerializedName("nrp")        val nrp: String,
    @SerializedName("name")       val name: String,
    @SerializedName("rank")       val rank: String,
    @SerializedName("unit")       val unit: String,
    @SerializedName("battalion")  val battalion: String,
    @SerializedName("squad")      val squad: String,
    @SerializedName("avatar")     val avatar: String
)

data class GpsPayload(
    @SerializedName("latitude")       val latitude: Double,
    @SerializedName("longitude")      val longitude: Double,
    @SerializedName("gps_timestamp")  val gpsTimestamp: Long
)

data class RadioHealthPayload(
    @SerializedName("heartrate")            val heartrate: Int,
    @SerializedName("heartrate_timestamp")  val heartrateTimestamp: Long
)

data class BatteryPayload(
    @SerializedName("level") val level: Int
)

// ─── TOPIC: radio/sos ────────────────────────────────────────────────────────

data class RadioSosPayload(
    @SerializedName("timestamp")     val timestamp: Long,
    @SerializedName("serial_number") val serialNumber: String,
    @SerializedName("id")            val id: String,
    @SerializedName("name")          val name: String,
    @SerializedName("avatar")        val avatar: String,
    @SerializedName("sos")           val sos: Int,
    @SerializedName("latitude")      val latitude: Double,
    @SerializedName("longitude")     val longitude: Double
)