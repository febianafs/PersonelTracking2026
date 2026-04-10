package com.example.personeltracking2026.ui.personel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personeltracking2026.core.mqtt.MqttManager
import com.example.personeltracking2026.core.mqtt.MqttPayloadBuilder
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.data.model.LocationData
import com.example.personeltracking2026.data.model.PersonelData
import com.example.personeltracking2026.data.repository.LocationRepository
import com.example.personeltracking2026.data.repository.PersonelRepository
import com.example.personeltracking2026.data.repository.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocationState(
    val data: LocationData? = null,
    val gpsStrength: Int = 0,
    val isInZone: Boolean = false,
    val error: String? = null
)

data class BatteryState(
    val percent: Int = 0,
    val isCharging: Boolean = false
)

data class HeartRateState(
    val bpm: Int = 0,
    val timestamp: Long = 0L,
    val isConnected: Boolean = false,
    val deviceName: String = ""
)

class PersonelViewModel(
    application: Application,
    private val repository: PersonelRepository,
    private val locationRepository: LocationRepository,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) {

    companion object {
        private const val ZONE_CENTER_LAT    = -7.868729
        private const val ZONE_CENTER_LON    = 105.643117
        private const val ZONE_RADIUS_METERS = 500.0
    }

    private val _personelState  = MutableStateFlow<PersonelState>(PersonelState.Loading)
    val personelState: StateFlow<PersonelState> = _personelState

    private val _locationState  = MutableStateFlow(LocationState())
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val _batteryState   = MutableStateFlow(BatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    private val _mqttConnected  = MutableStateFlow(false)
    val mqttConnected: StateFlow<Boolean> = _mqttConnected.asStateFlow()

    private val _lastSyncTime   = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _heartRateState = MutableStateFlow(HeartRateState())
    val heartRateState: StateFlow<HeartRateState> = _heartRateState.asStateFlow()

    val mqttManager = MqttManager().apply {
        onConnected      = { _mqttConnected.value = true }
        onDisconnected   = { _mqttConnected.value = false }
        onPublishSuccess = { _ -> _lastSyncTime.value = System.currentTimeMillis() }
    }

    // FIX ANR: battery receiver tidak lagi register di init{}
    // Didaftarkan dari Activity lewat fungsi registerBattery()
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // FIX ANR: refreshBattery di IO thread, bukan main thread
            viewModelScope.launch(Dispatchers.IO) { refreshBattery() }
        }
    }

    init {
        // FIX ANR: tidak ada operasi blocking di init{}
        // MQTT connect sudah pakai Dispatchers.IO di dalam MqttManager.connect()
        mqttManager.connect()

        // FIX ANR: battery dibaca di IO thread
        viewModelScope.launch(Dispatchers.IO) { refreshBattery() }
    }

    /**
     * Dipanggil dari Activity.onStart() untuk register broadcast receiver.
     * Receiver HARUS didaftarkan dari Activity/main thread, tapi callback-nya
     * kita lempar ke IO thread.
     */
    fun registerBatteryReceiver(context: Context) {
        context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
    }

    fun unregisterBatteryReceiver(context: Context) {
        try { context.unregisterReceiver(batteryReceiver) }
        catch (e: Exception) { /* ignore jika belum terdaftar */ }
    }

    override fun onCleared() {
        super.onCleared()
        mqttManager.disconnect()
    }

    // ─── PERSONEL ────────────────────────────────────────────────────────────

    fun loadPersonelDetail(userId: Int, token: String) {
        // Sudah di viewModelScope, tapi tambahkan Dispatchers.IO eksplisit
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _personelState.value = PersonelState.Loading
            }
            when (val result = repository.getPersonelDetail(userId, token)) {
                is Result.Success -> {
                    val data = result.data
                    sessionManager.savePersonelDetail(
                        rank      = data.rank?.name,
                        unit      = data.unit?.name,
                        battalion = data.batalyon?.name,
                        squad     = data.regu?.name,
                        avatar    = data.image
                    )
                    withContext(Dispatchers.Main) {
                        _personelState.value = PersonelState.Success(data)
                    }
                }
                is Result.Error -> withContext(Dispatchers.Main) {
                    _personelState.value = PersonelState.Error(result.message)
                }
                else -> {}
            }
        }
    }

    // ─── LOCATION & MQTT PUBLISH ─────────────────────────────────────────────

    fun startLocationUpdates(intervalMs: Long = 5000L) {
        viewModelScope.launch {
            locationRepository.getLocationFlow(intervalMs).collect { kotlinResult ->
                val locationData = kotlinResult.getOrNull()
                val error        = kotlinResult.exceptionOrNull()

                if (locationData != null) {
                    _locationState.value = LocationState(
                        data        = locationData,
                        gpsStrength = accuracyToStrength(locationData.accuracy),
                        isInZone    = checkInZone(locationData.lat, locationData.lon)
                    )
                    // Publish di IO thread agar tidak block UI
                    viewModelScope.launch(Dispatchers.IO) {
                        publishDataPayload(locationData)
                    }
                } else if (error != null) {
                    _locationState.update { it.copy(error = error.message, gpsStrength = 0) }
                }
            }
        }
    }

    private fun publishDataPayload(location: LocationData) {
        if (!mqttManager.isConnected()) return

        val serialNumber = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val hr  = _heartRateState.value
        val bat = _batteryState.value

        val payload = MqttPayloadBuilder.buildDataPayload(
            session       = sessionManager,
            serialNumber  = serialNumber,
            lat           = location.lat,
            lon           = location.lon,
            gpsTimestamp  = location.timestamp,
            heartrate     = hr.bpm,
            heartrateTs   = if (hr.timestamp > 0) hr.timestamp else System.currentTimeMillis(),
            batteryLevel  = bat.percent
        )
        mqttManager.publishData(payload)
    }

    fun publishSos() {
        val loc = _locationState.value.data ?: return
        val serialNumber = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val payload = MqttPayloadBuilder.buildSosPayload(
            session      = sessionManager,
            serialNumber = serialNumber,
            lat          = loc.lat,
            lon          = loc.lon
        )
        mqttManager.publishSos(payload)
    }

    // ─── HEART RATE (BLE) ─────────────────────────────────────────────────────

    fun updateHeartRate(bpm: Int, deviceName: String = "") {
        _heartRateState.update {
            it.copy(
                bpm        = bpm,
                timestamp  = System.currentTimeMillis(),
                deviceName = deviceName.ifEmpty { it.deviceName }
            )
        }
    }

    fun onBleConnected(deviceName: String) {
        _heartRateState.update { it.copy(isConnected = true, deviceName = deviceName) }
    }

    fun onBleDisconnected() {
        _heartRateState.update { it.copy(isConnected = false) }
    }

    // ─── BATTERY ─────────────────────────────────────────────────────────────

    // ✅ FIX ANR: suspend fun, dipanggil dari coroutine IO
    suspend fun refreshBattery() = withContext(Dispatchers.IO) {
        val bm = getApplication<Application>()
            .getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent    = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        if (percent > 0) {
            withContext(Dispatchers.Main) {
                _batteryState.value = BatteryState(percent, isCharging)
            }
        }
    }

    // ─── LEGACY ──────────────────────────────────────────────────────────────

    fun onMqttPublishSuccess() { _lastSyncTime.value = System.currentTimeMillis() }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private fun accuracyToStrength(accuracy: Float) = when {
        accuracy <= 10f  -> 95
        accuracy <= 20f  -> 80
        accuracy <= 50f  -> 60
        accuracy <= 100f -> 40
        else             -> 20
    }

    private fun checkInZone(lat: Double, lon: Double): Boolean {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            lat, lon, ZONE_CENTER_LAT, ZONE_CENTER_LON, results
        )
        return results[0] <= ZONE_RADIUS_METERS
    }

    // ─── FACTORY ─────────────────────────────────────────────────────────────

    class Factory(
        private val application: Application,
        private val repository: PersonelRepository,
        private val locationRepository: LocationRepository,
        private val sessionManager: SessionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PersonelViewModel(application, repository, locationRepository, sessionManager) as T
    }
}

sealed class PersonelState {
    object Loading : PersonelState()
    data class Success(val data: PersonelData) : PersonelState()
    data class Error(val message: String) : PersonelState()
}