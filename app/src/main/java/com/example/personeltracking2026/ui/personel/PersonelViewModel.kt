package com.example.personeltracking2026.ui.personel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personeltracking2026.data.model.LocationData
import com.example.personeltracking2026.data.model.PersonelData
import com.example.personeltracking2026.data.repository.LocationRepository
import com.example.personeltracking2026.data.repository.PersonelRepository
import com.example.personeltracking2026.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class PersonelViewModel(
    application: Application,
    private val repository: PersonelRepository,
    private val locationRepository: LocationRepository
) : AndroidViewModel(application) {

    companion object {
        private const val ZONE_CENTER_LAT = -7.868729
        private const val ZONE_CENTER_LON = 105.643117
        private const val ZONE_RADIUS_METERS = 500.0
    }

    private val _personelState = MutableStateFlow<PersonelState>(PersonelState.Loading)
    val personelState: StateFlow<PersonelState> = _personelState

    private val _locationState = MutableStateFlow(LocationState())
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    private val _mqttConnected = MutableStateFlow(false)
    val mqttConnected: StateFlow<Boolean> = _mqttConnected.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshBattery()
    }

    init {
        getApplication<Application>().registerReceiver(
            batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        refreshBattery()
    }

    override fun onCleared() {
        super.onCleared()
        try { getApplication<Application>().unregisterReceiver(batteryReceiver) }
        catch (e: Exception) { }
    }

    fun loadPersonelDetail(userId: Int, token: String) {
        viewModelScope.launch {
            _personelState.value = PersonelState.Loading
            when (val result = repository.getPersonelDetail(userId, token)) {
                is Result.Success -> _personelState.value = PersonelState.Success(result.data)
                is Result.Error   -> _personelState.value = PersonelState.Error(result.message)
                else -> {}
            }
        }
    }

    fun startLocationUpdates(intervalMs: Long = 5000L) {
        viewModelScope.launch {
            locationRepository.getLocationFlow(intervalMs).collect { kotlinResult ->
                // pakai getOrNull / exceptionOrNull agar tidak konflik dengan Result custom
                val locationData = kotlinResult.getOrNull()
                val error = kotlinResult.exceptionOrNull()

                if (locationData != null) {
                    _locationState.value = LocationState(
                        data        = locationData,
                        gpsStrength = accuracyToStrength(locationData.accuracy),
                        isInZone    = checkInZone(locationData.lat, locationData.lon)
                    )
                } else if (error != null) {
                    _locationState.update { it.copy(error = error.message, gpsStrength = 0) }
                }
            }
        }
    }

    fun onMqttConnected()    { _mqttConnected.value = true }
    fun onMqttDisconnected() { _mqttConnected.value = false }

    fun onMqttPublishSuccess() {
        _lastSyncTime.value = System.currentTimeMillis()
    }

    fun refreshBattery() {
        val bm = getApplication<Application>()
            .getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent    = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        if (percent > 0) _batteryState.value = BatteryState(percent, isCharging)
    }

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

    class Factory(
        private val application: Application,
        private val repository: PersonelRepository,
        private val locationRepository: LocationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PersonelViewModel(application, repository, locationRepository) as T
    }
}

sealed class PersonelState {
    object Loading : PersonelState()
    data class Success(val data: PersonelData) : PersonelState()
    data class Error(val message: String) : PersonelState()
}