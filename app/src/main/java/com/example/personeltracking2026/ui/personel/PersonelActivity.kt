package com.example.personeltracking2026.ui.personel

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.base.BaseActivity
import com.example.personeltracking2026.core.map.MapTypeManager
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.data.model.PersonelData
import com.example.personeltracking2026.data.repository.LocationRepository
import com.example.personeltracking2026.data.repository.PersonelRepository
import com.example.personeltracking2026.databinding.ActivityPersonelBinding
import com.example.personeltracking2026.ui.settings.SettingsActivity
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class PersonelActivity : BaseActivity() {

    private lateinit var binding: ActivityPersonelBinding
    private lateinit var mqttPrefs: SharedPreferences
    private lateinit var sessionManager: SessionManager

    private val viewModel: PersonelViewModel by viewModels {
        PersonelViewModel.Factory(
            application,
            PersonelRepository(),
            LocationRepository(this),
            SessionManager(this)
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var syncRunnable: Runnable

    private val zoneCenterLat    = -7.868729
    private val zoneCenterLon    = 105.643117
    private val zonaRadiusMeters = 500.0
    private var currentMapType   = MapTypeManager.MapType.STANDARD

    // ─── PERMISSION ──────────────────────────────────────────────────────────

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) startLocationUpdates()
        else Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
    }

    // ─── LIFECYCLE ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding        = ActivityPersonelBinding.inflate(layoutInflater)
        sessionManager = SessionManager(this)
        mqttPrefs      = getSharedPreferences("mqtt_settings", MODE_PRIVATE)

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.btnOverflow.setOnClickListener { showOverflowMenu(it) }

        setupMap()
        setupVitalSignsInitial()
        loadIntervalFromSettings()
        setupClickListeners()
        startSyncTimer()
        observeAllStates()
        loadPersonelData()
        requestLocationPermission()
    }

    // FIX ANR: register battery receiver di onStart, bukan di ViewModel.init{}
    override fun onStart() {
        super.onStart()
        viewModel.registerBatteryReceiver(this)
    }

    // FIX ANR: unregister battery receiver di onStop
    override fun onStop() {
        super.onStop()
        viewModel.unregisterBatteryReceiver(this)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        loadIntervalFromSettings()
        // FIX ANR: hapus viewModel.refreshBattery() — sudah dihandle receiver
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        handler.removeCallbacks(syncRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(syncRunnable)
    }

    // ─── OBSERVERS ───────────────────────────────────────────────────────────

    private fun observeAllStates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Lokasi GPS
                launch {
                    viewModel.locationState.collect { state ->
                        state.data?.let { updateCoordinates(it.lat, it.lon); drawMapOverlays(it.lat, it.lon) }
                        updateGpsSignal(state.gpsStrength)
                        updateZoneStatus(state.isInZone)
                        state.error?.let { Toast.makeText(this@PersonelActivity, it, Toast.LENGTH_SHORT).show() }
                    }
                }

                // Baterai
                launch {
                    viewModel.batteryState.collect { updateBattery(it.percent) }
                }

                // Profil personel
                launch {
                    viewModel.personelState.collect { state ->
                        when (state) {
                            is PersonelState.Loading -> {
                                binding.tvName.text = "Loading..."
                                binding.tvNRP.text  = ""
                            }
                            is PersonelState.Success -> bindPersonelData(state.data)
                            is PersonelState.Error   -> {
                                binding.tvName.text = sessionManager.getName() ?: "-"
                                binding.tvNRP.text  = sessionManager.getUsername() ?: "-"
                            }
                        }
                    }
                }

                // MQTT status
                launch {
                    viewModel.mqttConnected.collect { updateMqttStatus(it) }
                }

                // Last sync
                launch {
                    viewModel.lastSyncTime.collect { updateLastSyncUI(it) }
                }

                // Heart rate dari BLE
                launch {
                    viewModel.heartRateState.collect { state ->
                        updateHeartRate(state.bpm)
                    }
                }
            }
        }
    }

    // ─── PERSONEL ────────────────────────────────────────────────────────────

    private fun loadPersonelData() {
        val token  = sessionManager.getToken()  ?: return
        val userId = sessionManager.getUserId() ?: return
        viewModel.loadPersonelDetail(userId, token)
    }

    private fun bindPersonelData(data: PersonelData) {
        binding.tvName.text = sessionManager.getName()     ?: "-"
        binding.tvNRP.text  = sessionManager.getUsername() ?: "-"
    }

    // ─── MAP ─────────────────────────────────────────────────────────────────

    private fun setupMap() {
        Configuration.getInstance().userAgentValue = packageName
        binding.mapView.apply {
            setTileSource(MapTypeManager.getTileSource(currentMapType))
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(zoneCenterLat, zoneCenterLon))
        }
        drawMapOverlays(zoneCenterLat, zoneCenterLon)
        updateCoordinates(zoneCenterLat, zoneCenterLon)
        updateZoneStatus(true)
    }

    private fun drawMapOverlays(lat: Double, lon: Double) {
        binding.mapView.overlays.clear()
        val marker = Marker(binding.mapView).apply {
            position = GeoPoint(lat, lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Personnel Position"
            icon  = ResourcesCompat.getDrawable(resources, R.drawable.ic_location_pin, theme)
        }
        val circle = Polygon(binding.mapView).apply {
            points = Polygon.pointsAsCircle(GeoPoint(zoneCenterLat, zoneCenterLon), zonaRadiusMeters)
            fillPaint.color    = android.graphics.Color.parseColor("#1A4FC3F7")
            outlinePaint.color = android.graphics.Color.parseColor("#4FC3F7")
            outlinePaint.strokeWidth = 2f
        }
        binding.mapView.overlays.add(circle)
        binding.mapView.overlays.add(marker)
        binding.mapView.controller.setCenter(GeoPoint(lat, lon))
        binding.mapView.invalidate()
    }

    private fun showMapTypeMenu() {
        val wrapper = android.view.ContextThemeWrapper(this, R.style.DarkPopupMenu)
        val popup   = android.widget.PopupMenu(wrapper, binding.btnMapType)
        MapTypeManager.MapType.values().forEach { popup.menu.add(it.label) }
        popup.setOnMenuItemClickListener { item ->
            MapTypeManager.MapType.values()
                .firstOrNull { it.label == item.title.toString() }
                ?.let {
                    currentMapType = it
                    binding.mapView.setTileSource(MapTypeManager.getTileSource(it))
                    binding.mapView.invalidate()
                }
            true
        }
        popup.show()
    }

    private fun updateCoordinates(lat: Double, lon: Double) {
        binding.tvCoordinates.text  = "$lat,"
        binding.tvCoordinates2.text = " $lon"
    }

    private fun updateZoneStatus(inZone: Boolean) {
        val color = if (inZone) "#69F0AE" else "#FF5252"
        binding.tvZoneStatus.text = if (inZone) "In Zone ✓" else "Out of Zone ✗"
        binding.tvZoneStatus.setTextColor(android.graphics.Color.parseColor(color))
    }

    // ─── LOCATION ────────────────────────────────────────────────────────────

    private fun requestLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) startLocationUpdates()
        else locationPermissionRequest.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun startLocationUpdates() {
        val intervalMs = parseIntervalToMs(
            mqttPrefs.getString("interval", "5 seconds") ?: "5 seconds"
        )
        viewModel.startLocationUpdates(intervalMs)
    }

    private fun parseIntervalToMs(interval: String): Long {
        return when {
            interval.contains("minute") -> {
                val m = interval.filter { it.isDigit() }.toLongOrNull() ?: 1L
                m * 60 * 1000
            }
            interval.contains("second") -> {
                val s = interval.filter { it.isDigit() }.toLongOrNull() ?: 5L
                s * 1000
            }
            else -> 5000L
        }
    }

    // ─── VITAL SIGNS UI ──────────────────────────────────────────────────────

    private fun setupVitalSignsInitial() {
        updateHeartRate(0)
        updateGpsSignal(0)
        updateMqttStatus(false)
    }

    private fun updateHeartRate(bpm: Int) {
        if (bpm <= 0) {
            binding.tvHeartRate.text = "-- BPM"
            binding.progressHeartRate.progress = 0
            return
        }
        binding.tvHeartRate.text = "$bpm BPM"
        val progress = (bpm.coerceIn(40, 180) - 40) * 100 / 140
        binding.progressHeartRate.progress = progress
    }

    private fun updateBattery(percent: Int) {
        val color = when {
            percent > 60      -> "#69F0AE"
            percent in 30..60 -> "#FFD740"
            percent in 10..29 -> "#FF6D00"
            else              -> "#FF5252"
        }
        val icon = when {
            percent > 60      -> R.drawable.ic_battery_full
            percent in 30..60 -> R.drawable.ic_battery_half
            percent in 10..29 -> R.drawable.ic_battery_low
            else              -> R.drawable.ic_battery_empty
        }
        val parsedColor    = android.graphics.Color.parseColor(color)
        val colorStateList = android.content.res.ColorStateList.valueOf(parsedColor)

        binding.tvBattery.text                   = "$percent%"
        binding.tvBattery.setTextColor(parsedColor)
        binding.iconBattery.setImageResource(icon)
        binding.iconBattery.imageTintList        = colorStateList
        binding.progressBattery.progress         = percent
        binding.progressBattery.progressTintList = colorStateList

        if (percent < 10) {
            val blink = android.view.animation.AlphaAnimation(1f, 0f).apply {
                duration    = 500
                repeatMode  = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
            }
            binding.iconBattery.startAnimation(blink)
        } else {
            binding.iconBattery.clearAnimation()
        }
    }

    private fun updateGpsSignal(strength: Int) {
        val (label, color) = when {
            strength >= 80     -> Pair("Strong",    "#4FC3F7")
            strength in 50..79 -> Pair("Medium",    "#FFD740")
            strength > 0       -> Pair("Weak",      "#FF5252")
            else               -> Pair("No Signal", "#FF5252")
        }
        val parsedColor = android.graphics.Color.parseColor(color)
        binding.tvGpsSignal.text = label
        binding.tvGpsSignal.setTextColor(parsedColor)
        binding.progressGps.progress = strength
        binding.progressGps.progressTintList =
            android.content.res.ColorStateList.valueOf(parsedColor)
    }

    private fun updateMqttStatus(connected: Boolean) {
        val color  = if (connected) "#69F0AE" else "#FF5252"
        val label  = if (connected) "Connected" else "Disconnected"
        val parsed = android.graphics.Color.parseColor(color)
        binding.tvMqttStatus.text = label
        binding.tvMqttStatus.setTextColor(parsed)
        binding.dotMqtt.backgroundTintList =
            android.content.res.ColorStateList.valueOf(parsed)
    }

    // ─── INTERVAL ────────────────────────────────────────────────────────────

    private fun loadIntervalFromSettings() {
        val interval = mqttPrefs.getString("interval", "5 seconds") ?: "5 seconds"
        binding.tvInterval.text = interval
            .replace(" seconds", "s").replace(" second", "s")
            .replace(" minutes", "m").replace(" minute", "m")
    }

    // ─── LAST SYNC ───────────────────────────────────────────────────────────

    private fun updateLastSyncUI(lastSyncTime: Long) {
        val diffMs  = System.currentTimeMillis() - lastSyncTime
        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val (text, color) = when {
            diffSec < 10 -> Pair("Just now", "#69F0AE")
            diffSec < 60 -> Pair("${diffSec}s ago", "#69F0AE")
            diffMin < 5  -> Pair("${diffMin}m ago", "#FFD740")
            else         -> Pair(
                "Failed · ${
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(lastSyncTime))
                }",
                "#FF5252"
            )
        }
        val parsed = android.graphics.Color.parseColor(color)
        binding.tvLastSync.text = text
        binding.tvLastSync.setTextColor(parsed)
        binding.dotLastSync.backgroundTintList = android.content.res.ColorStateList.valueOf(parsed)
        binding.iconSync.imageTintList         = android.content.res.ColorStateList.valueOf(parsed)
    }

    private fun startSyncTimer() {
        syncRunnable = Runnable {
            updateLastSyncUI(viewModel.lastSyncTime.value)
            handler.postDelayed(syncRunnable, 1000)
        }
        handler.post(syncRunnable)
    }

    // ─── CLICK LISTENERS ─────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnDataSelengkapnya.setOnClickListener {
            // TODO: buka bottom sheet data lengkap
        }
        binding.btnFullMap.setOnClickListener {
            binding.mapView.controller.setZoom(17.0)
        }
        binding.btnUbahInterval.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnMapType.setOnClickListener {
            showMapTypeMenu()
        }
    }
}