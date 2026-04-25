package com.example.personeltracking2026.core.mqtt

import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.data.model.BatteryPayload
import com.example.personeltracking2026.data.model.GpsPayload
import com.example.personeltracking2026.data.model.IdentityPayload
import com.example.personeltracking2026.data.model.RadioDataPayload
import com.example.personeltracking2026.data.model.RadioHealthPayload
import com.example.personeltracking2026.data.model.RadioSosPayload
import com.example.personeltracking2026.data.model.StreamPayload

/**
 * Helper untuk build payload MQTT dari state yang ada di app.
 * Pisahkan dari MqttManager agar mudah di-test dan di-modify.
 */
object MqttPayloadBuilder {

    /**
     * Bangun payload radio/data.
     *
     * @param session         SessionManager untuk ambil user info
     * @param serialNumber    Serial number perangkat (IMEI/device ID)
     * @param lat             Latitude dari GPS
     * @param lon             Longitude dari GPS
     * @param gpsTimestamp    Timestamp epoch saat fix GPS diterima
     * @param heartrate       BPM dari BLE smartwatch (0 jika belum ada)
     * @param heartrateTs     Timestamp epoch saat HR diterima
     * @param batteryLevel    Level baterai 0-100
     * @param appVersion      Versi aplikasi yang sedang berjalan
     * @param rtmpUrl         Url livestream dari setiap device
     */
    fun buildDataPayload(
        session: SessionManager,
        serialNumber: String,
        lat: Double,
        lon: Double,
        gpsTimestamp: Long,
        heartrate: Int,
        heartrateTs: Long,
        batteryLevel: Int,
        appVersion: String,
        rtmpUrl: String
    ): RadioDataPayload {
        val nowSec = System.currentTimeMillis() / 1000

        val identity = IdentityPayload(
            id        = session.getUserId()?.toString() ?: "",
            nrp       = session.getNrp() ?: "",
            name      = session.getName() ?: "",
            rank      = session.getRank() ?: "",
            unit      = session.getUnit() ?: "",
            battalion = session.getBattalion() ?: "",
            squad     = session.getSquad() ?: "",
            avatarUrl = session.getAvatar() ?: ""
        )

        return RadioDataPayload(
            timestamp    = nowSec,
            serialNumber = serialNumber,
            appVersion   = appVersion,
            identity     = identity,
            gps = GpsPayload(
                gpsTimestamp = gpsTimestamp,
                latitude = lat,
                longitude = lon
            ),
            radioHealth = RadioHealthPayload(
                heartrateTimestamp = heartrateTs,
                heartrate = heartrate
            ),
            battery = BatteryPayload(
                batteryTimestamp = nowSec,
                level = batteryLevel
            ),
            stream = StreamPayload(
                rtmpUrl = rtmpUrl
            )
        )
    }

    /**
     * Bangun payload radio/sos.
     *
     * @param session       SessionManager
     * @param serialNumber  Serial number perangkat
     * @param lat           Latitude posisi saat SOS
     * @param lon           Longitude posisi saat SOS
     */
    fun buildSosPayload(
        session: SessionManager,
        serialNumber: String,
        lat: Double,
        lon: Double,
        sos: Int
    ): RadioSosPayload {
        val nowSec = System.currentTimeMillis() / 1000
        return RadioSosPayload(
            timestamp    = nowSec,
            serialNumber = serialNumber,
            id           = session.getUserId()?.toString() ?: "",
            name         = session.getName() ?: "",
            avatarUrl    = session.getAvatar() ?: "",
            sos          = sos,
            latitude     = lat,
            longitude    = lon
        )
    }
}