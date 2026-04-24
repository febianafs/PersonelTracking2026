package com.example.personeltracking2026.ui.personel

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.personeltracking2026.App
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.base.BaseActivity
import com.example.personeltracking2026.core.map.MapTypeManager
import com.example.personeltracking2026.core.mqtt.MqttConfigManager
import com.example.personeltracking2026.core.mqtt.MqttPayloadBuilder
import com.example.personeltracking2026.core.mqtt.MqttReconnectManager
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.core.sos.SosManager
import com.example.personeltracking2026.data.model.PersonelData
import com.example.personeltracking2026.data.repository.LocationRepository
import com.example.personeltracking2026.data.repository.PersonelRepository
import com.example.personeltracking2026.databinding.ActivityPersonelBinding
import com.example.personeltracking2026.ui.bluetooth.BluetoothLeService
import com.example.personeltracking2026.utils.drawableToBitmap
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.SymbolLayer


class PersonelActivity : BaseActivity() {

    private lateinit var binding: ActivityPersonelBinding
    private lateinit var mqttPrefs: SharedPreferences
    private lateinit var sessionManager: SessionManager
    private lateinit var mapView: MapView
    private lateinit var pagerAdapter: TopPagerAdapter

    private val viewModel: PersonelViewModel by viewModels {
        PersonelViewModel.Factory(
            application,
            PersonelRepository(),
            LocationRepository(this),
            SessionManager(this)
        )
    }

    private val zoneCenterLat    = -7.868729
    private val zoneCenterLon    = 105.643117
    private val zonaRadiusMeters = 500.0
    private var currentMapType   = MapTypeManager.MapType.STANDARD
    private var currentLat = zoneCenterLat
    private var currentLon = zoneCenterLon
    private var mapLibreMap: org.maplibre.android.maps.MapLibreMap? = null

    // ─── SOS ─────────────────────────────────────────────────────────────────
    private var markerBlinkJob: Job? = null

    // ─── PERMISSION ──────────────────────────────────────────────────────────

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            requestBackgroundLocation()
        }
        else Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                locationPermissionRequest.launch(
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                )
            }
        }
    }

    // ─── LIFECYCLE ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        MapLibre.getInstance(
            this,
            null,
            org.maplibre.android.WellKnownTileServer.MapLibre
        )

        binding        = ActivityPersonelBinding.inflate(layoutInflater)
        sessionManager = SessionManager(this)
        mqttPrefs      = getSharedPreferences("mqtt_settings", MODE_PRIVATE)

        setContentView(binding.root)

        requestBatteryOptimizationExemption()

        pagerAdapter = TopPagerAdapter()
        binding.viewPagerTop.adapter = pagerAdapter

        (binding.viewPagerTop.getChildAt(0) as RecyclerView)
            .itemAnimator = null

        pagerAdapter.onFullDataClick = {
            personelBottomSheet()
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.btnOverflow.setOnClickListener { showOverflowMenu(it) }

        val savedType = getSharedPreferences("map_settings", MODE_PRIVATE)
            .getString("map_type", MapTypeManager.MapType.STANDARD.name)

        currentMapType = MapTypeManager.MapType.valueOf(savedType!!)

        setupMap()

        val app = application as App
        SosManager.init(
            mqtt             = app.mqttManager,
            session          = sessionManager,
            serial           = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown",
            locationProvider = { Pair(currentLat, currentLon) }
        )

        binding.btnZoomIn.setOnClickListener {
            mapLibreMap?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.zoomIn()
            )
        }

        binding.btnZoomOut.setOnClickListener {
            mapLibreMap?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.zoomOut()
            )
        }

        setupVitalSignsInitial()
        updateMqttUI()
        setupClickListeners()
        observeAllStates()
        loadPersonelData()
        requestLocationPermission()
    }

    // FIX ANR: register battery receiver di onStart, bukan di ViewModel.init{}
    override fun onStart() {
        super.onStart()

        viewModel.registerBatteryReceiver(this)
        binding.mapView.onStart()
        updateMqttUI()
        startLocationUpdates()
    }

    // FIX ANR: unregister battery receiver di onStop
    override fun onStop() {
        super.onStop()

        viewModel.unregisterBatteryReceiver(this)
        binding.mapView.onStop()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        val savedType = getSharedPreferences("map_settings", MODE_PRIVATE)
            .getString("map_type", MapTypeManager.MapType.STANDARD.name)

        val newType = MapTypeManager.MapType.valueOf(savedType!!)

        if (newType != currentMapType) {
            currentMapType = newType
            applyMapType(newType)
        }

        val interval = parseIntervalToMs(
            mqttPrefs.getString("interval", "5 seconds") ?: "5 seconds"
        )

        viewModel.updateInterval(interval)
        // FIX ANR: hapus viewModel.refreshBattery() — sudah dihandle receiver
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        //handler.removeCallbacks(syncRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Pastikan blink berhenti dan SOS di-reset saat Activity destroy
        markerBlinkJob?.cancel()
        //handler.removeCallbacks(syncRunnable)
        binding.mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    // ─── OBSERVERS ───────────────────────────────────────────────────────────

    private fun observeAllStates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // SOS — khusus marker blink + publish MQTT
                // Toolbar blink sudah dihandle BaseActivity
                launch {
                    SosManager.isActive.collect { isActive ->
                        if (isActive) {
                            startMarkerBlink()
                        } else {
                            stopMarkerBlink()
                        }
                    }
                }

                // Lokasi GPS
                launch {
                    viewModel.locationState.collect { state ->

                        val accuracy = state.data?.accuracy ?: Float.MAX_VALUE

                        val (signalText) = when {
                            accuracy <= 10 -> "Strong" to "#4FC3F7"
                            accuracy <= 30 -> "Medium" to "#FFD740"
                            accuracy <= 100 -> "Weak" to "#FF5252"
                            else -> "No Signal" to "#FF5252"
                        }

                        if (pagerAdapter.gpsSignal != signalText) {
                            pagerAdapter.gpsSignal = signalText
                            pagerAdapter.notifyItemChanged(1, "GPS")
                        }

                        state.data?.let {
                            updateCoordinates(it.lat, it.lon)
                            if (!SosManager.isActive.value) {
                                updateMarker(it.lat, it.lon)
                            }
                            pagerAdapter.latitude = it.lat
                            pagerAdapter.longitude = it.lon
                            pagerAdapter.notifyItemChanged(0)
                        }
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
                                pagerAdapter.name = "Loading..."
                                pagerAdapter.nrp = ""
                                pagerAdapter.rank = ""
                                pagerAdapter.notifyItemChanged(0)
                            }
                            is PersonelState.Success -> bindPersonelData(state.data)
                            is PersonelState.Error -> {
                                pagerAdapter.name = sessionManager.getName() ?: "-"
                                pagerAdapter.nrp = sessionManager.getUsername() ?: "-"
                                pagerAdapter.rank = sessionManager.getRank() ?: "-"
                                pagerAdapter.notifyItemChanged(0)
                            }
                        }
                    }
                }

                // Last sync
                launch {
                    var lastSyncTime = 0L

                    // ambil update dari MQTT
                    launch {
                        viewModel.lastSyncTime.collect {
                            lastSyncTime = it
                        }
                    }

                    // timer UI tiap detik
                    while (true) {
                        val diffSec = (System.currentTimeMillis() - lastSyncTime) / 1000

                        val (text, color) = when {
                            diffSec < 2 -> "Just now" to "#69F0AE"
                            diffSec < 60 -> "${diffSec}s ago" to "#69F0AE"
                            diffSec < 300 -> "${diffSec / 60}m ago" to "#FFD740"
                            else -> "${diffSec / 60}m ago" to "#FF5252"
                        }

                        pagerAdapter.lastSync = text
                        pagerAdapter.statusColor = color
                        pagerAdapter.notifyDataSetChanged()
                        delay(1000)
                    }
                }

                // Heart rate dari BLE
//                launch {
//                    viewModel.heartRateState.collect { state ->
//                        updateHeartRate(state.bpm)
//                    }
//                }
                // Heart rate dari BLE — GANTI BLOCK INI
                launch {
                    // Observe koneksi BLE
                    launch {
                        BluetoothLeService.connectionState.collect { state ->
                            val isConnected = state == BluetoothLeService.ConnectionState.CONNECTED
                            val deviceName  = BluetoothLeService.connectedDevice.value?.name ?: "--"

                            pagerAdapter.bleConnected  = isConnected
                            pagerAdapter.bleDeviceName = if (isConnected) deviceName else "--"
                            pagerAdapter.notifyItemChanged(1)
                        }
                    }

                    // Observe BPM realtime
                    BluetoothLeService.bpmValue.collect { bpm ->
                        pagerAdapter.bleBpm = bpm
                        pagerAdapter.notifyItemChanged(1)
                    }
                }
            }
        }
    }

    // ─── SOS (marker only — toolbar dihandle BaseActivity) ───────────────────

    private fun startMarkerBlink() {
        markerBlinkJob?.cancel()
        markerBlinkJob = lifecycleScope.launch {
            var toggle = false
            while (SosManager.isActive.value) {
                val color = if (toggle) Color.parseColor("#FF1744")
                else Color.parseColor("#FF8A80")
                updateMarkerWithColor(color)
                toggle = !toggle
                delay(500)
            }
        }
    }

    private fun stopMarkerBlink() {
        markerBlinkJob?.cancel()
        markerBlinkJob = null
        updateMarkerWithColor(Color.rgb(255, 82, 82))
    }

    // ─── PERSONEL ────────────────────────────────────────────────────────────

    private fun loadPersonelData() {
        val token  = sessionManager.getToken()  ?: return
        val userId = sessionManager.getUserId() ?: return
        viewModel.loadPersonelDetail(userId, token)
    }

    private fun bindPersonelData(data: PersonelData) {
        pagerAdapter.name = sessionManager.getName() ?: "-"
        pagerAdapter.nrp = sessionManager.getUsername() ?: "-"
        pagerAdapter.rank = sessionManager.getRank() ?: "-"
        pagerAdapter.notifyItemChanged(0)
    }

    private fun updateBattery(percent: Int) {
        pagerAdapter.battery = percent
        pagerAdapter.notifyItemChanged(1)
    }

    private fun personelBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_personel, null)

        // ambil data dari ViewModel
        val name = sessionManager.getName() ?: "-"
        val nrp  = sessionManager.getUsername() ?: "-"
        val personel = (viewModel.personelState.value as? PersonelState.Success)?.data
        val location = viewModel.locationState.value.data

        // SET DATA
        view.findViewById<TextView>(R.id.tvName).text = name
        view.findViewById<TextView>(R.id.tvNRP).text = nrp
        view.findViewById<TextView>(R.id.tvRank).text = personel?.rank?.name ?: "-"
        view.findViewById<TextView>(R.id.tvUnit).text = personel?.unit?.name ?: "-"
        view.findViewById<TextView>(R.id.tvSquad).text = personel?.regu?.name ?: "-"
        view.findViewById<TextView>(R.id.tvPersonelStatus).text = "Active"

        dialog.setContentView(view)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }

    // ─── MAP ─────────────────────────────────────────────────────────────────

    private fun setupMap() {
        MapLibre.getInstance(this)

        mapView = binding.mapView
        mapView.onCreate(null)

        mapView.getMapAsync { map ->
            mapLibreMap = map

            val styleUrl = MapTypeManager.getStyleUrl(currentMapType)
            map.setStyle(
                Style.Builder().fromUri(styleUrl)
            ) {
                val point = LatLng(currentLat, currentLon)

                map.cameraPosition = CameraPosition.Builder()
                    .target(point)
                    .zoom(15.0)
                    .build()

                updateMarker(currentLat, currentLon)
            }
        }
    }

    private fun updateMarker(lat: Double, lon: Double) {
        val map = mapLibreMap ?: return
        val point = org.maplibre.android.geometry.LatLng(lat, lon)
        val geoJsonSource = org.maplibre.android.style.sources.GeoJsonSource(
            "personel-source",
            org.maplibre.geojson.Point.fromLngLat(lon, lat)
        )

        map.style?.apply {

            getLayer("personel-layer")?.let { removeLayer(it) }
            getSource("personel-source")?.let { removeSource(it) }

            addSource(geoJsonSource)
            // AMBIL drawable
            val drawable = ContextCompat.getDrawable(this@PersonelActivity, R.drawable.ic_location_pin)
                ?: return  // kalau null, stop biar gak crash

            drawable.setTint(Color.rgb(255, 82, 82))

            val bitmap = drawableToBitmap(drawable)

            // addImage HARUS sebelum layer
            addImage("marker-icon", bitmap)

            val symbolLayer = SymbolLayer(
                "personel-layer",
                "personel-source"
            ).withProperties(
                iconImage("marker-icon"),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )

            addLayer(symbolLayer)
        }

        map.animateCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newLatLng(point)
        )
    }

    private fun updateMarkerWithColor(tintColor: Int) {
        val map = mapLibreMap ?: return

        val geoJsonSource = org.maplibre.android.style.sources.GeoJsonSource(
            "personel-source",
            org.maplibre.geojson.Point.fromLngLat(currentLon, currentLat)
        )

        map.style?.apply {
            getLayer("personel-layer")?.let { removeLayer(it) }
            getSource("personel-source")?.let { removeSource(it) }

            addSource(geoJsonSource)

            val drawable = ContextCompat.getDrawable(
                this@PersonelActivity, R.drawable.ic_location_pin
            ) ?: return

            drawable.setTint(tintColor)
            addImage("marker-icon", drawableToBitmap(drawable))

            addLayer(
                SymbolLayer("personel-layer", "personel-source")
                    .withProperties(
                        iconImage("marker-icon"),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true)
                    )
            )
        }
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
                    applyMapType(it)

                    getSharedPreferences("map_settings", MODE_PRIVATE).edit {
                        putString("map_type", it.name)
                    }
                }
            true
        }
        popup.show()
    }

    private fun updateCoordinates(lat: Double, lon: Double) {
        currentLat = lat
        currentLon = lon

        binding.tvCoordinates.text  = "$lat,"
        binding.tvCoordinates2.text = " $lon"
    }

    private fun applyMapType(type: MapTypeManager.MapType) {
        val map = mapLibreMap ?: return

        // IMPAN posisi kamera sekarang
        val currentCamera = map.cameraPosition

        val styleUrl = MapTypeManager.getStyleUrl(type)

        map.setStyle(
            Style.Builder().fromUri(styleUrl)
        ) {
            // RESTORE kamera
            map.cameraPosition = currentCamera

            // Tambahin marker lagi
            updateMarker(currentLat, currentLon)
        }
    }

    // ─── LOCATION ────────────────────────────────────────────────────────────

    private fun requestLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted)
            //startLocationUpdates()
        else locationPermissionRequest.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun startLocationUpdates() {
        viewModel.startLocationUpdates(2000)
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
    }

    private fun updateHeartRate(bpm: Int) {
        pagerAdapter.heartRate = bpm
        pagerAdapter.notifyItemChanged(1)
    }

    // ─── UPDATE MQTT ────────────────────────────────────────────────────────────

    private fun updateMqttUI() {
        val config = MqttConfigManager(this).load()

        pagerAdapter.mqttHost = config.host ?: "-"
        pagerAdapter.mqttPort = config.tcpPort.toString()
        pagerAdapter.interval = mqttPrefs.getString("interval", "5 seconds") ?: "5 seconds"

        pagerAdapter.notifyItemChanged(2)
    }

    // ─── CLICK LISTENERS ─────────────────────────────────────────────────────

    private fun setupClickListeners() {

        binding.btnFullMap.setOnClickListener {
            val intent = Intent(this, FullscreenMapActivity::class.java)
            intent.putExtra("lat", currentLat)
            intent.putExtra("lon", currentLon)
            intent.putExtra("mapType", currentMapType.name)

            startActivity(intent)
        }
//        binding.btnUbahInterval.setOnClickListener {
//            startActivity(Intent(this, SettingsActivity::class.java))
//        }
        binding.btnMapType.setOnClickListener {
            showMapTypeMenu()
        }
    }
}