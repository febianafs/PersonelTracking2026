package com.example.personeltracking2026.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.personeltracking2026.core.base.BaseActivity
import com.example.personeltracking2026.core.mqtt.MqttConfig
import com.example.personeltracking2026.core.mqtt.MqttConfigManager
import com.example.personeltracking2026.core.mqtt.MqttManager
import com.example.personeltracking2026.databinding.ActivitySettingsBinding

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private var testMqttManager: MqttManager? = null

    private val intervalOptions = listOf(
        "1 second", "2 seconds", "5 seconds", "10 seconds",
        "15 seconds", "30 seconds", "1 minute", "2 minutes",
        "5 minutes", "10 minutes"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindow()
        setupToolbar()
        setupIntervalDropdown()
        loadSettings()
        setupButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        testMqttManager?.disconnect()
        testMqttManager = null
    }

    // ─── UI SETUP ────────────────────────────────────────────────────────────

    private fun setupWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupIntervalDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            intervalOptions
        )
        binding.actInterval.setAdapter(adapter)

        binding.actInterval.setOnItemClickListener { _, _, _, _ ->
            val interval = binding.actInterval.text.toString()

            getSharedPreferences("mqtt_settings", MODE_PRIVATE).edit {
                putString("interval", interval)
            }

            showSavedIndicator()
        }
    }

    // ─── LOAD CONFIG ─────────────────────────────────────────────────────────

    private fun loadSettings() {
        val config = MqttConfigManager(this).load()
        binding.etServer!!.setText(config.host)
        binding.etTcp!!.setText(config.tcpPort.toString())
        binding.etWs!!.setText(config.wsPort.toString())
        binding.etUsername!!.setText(config.username)
        binding.etPassword!!.setText(config.password)

        val savedInterval = getSharedPreferences("mqtt_settings", MODE_PRIVATE)
            .getString("interval", "")
        if (!savedInterval.isNullOrEmpty()) {
            binding.actInterval.setText(savedInterval, false)
        }
    }

    // ─── BUTTONS ─────────────────────────────────────────────────────────────

    private fun setupButtons() {

        // SAVE CONFIG
        binding.btnSaveConnection!!.setOnClickListener {
            val config = buildConfigFromInput()
            MqttConfigManager(this).save(config)

            showSavedIndicator()
        }

        // TEST CONNECTION
        binding.btnTestConnection!!.setOnClickListener {
            // Simpan config sementara sebelum test
            val config = buildConfigFromInput()
            MqttConfigManager(this).save(config)

            updateStatus("Connecting...", "#FFC107")

            testMqttManager?.disconnect()
            testMqttManager = MqttManager(this).apply {
                onConnected    = { runOnUiThread { updateStatus("Connected ✓", "#69F0AE") } }
                onDisconnected = { runOnUiThread { updateStatus("Failed ✗",    "#FF5252") } }
            }
            testMqttManager?.connect()
        }
    }

    private fun buildConfigFromInput(): MqttConfig {
        return MqttConfig(
            host         = binding.etServer!!.text.toString().trim(),
            tcpPort      = binding.etTcp!!.text.toString().toIntOrNull() ?: 1883,
            wsPort       = binding.etWs!!.text.toString().toIntOrNull() ?: 9001,
            username     = binding.etUsername!!.text.toString().trim(),
            password     = binding.etPassword!!.text.toString(),
            useWebSocket = true
        )
    }

    // ─── STATUS UI ───────────────────────────────────────────────────────────

    private fun updateStatus(text: String, colorHex: String) {
        val color = colorHex.toColorInt()
        binding.tvConnectionStatus!!.text = text
        binding.tvConnectionStatus!!.setTextColor(color)
        binding.dotConnection!!.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun showSavedIndicator() {
        binding.tvSaved!!.visibility = View.VISIBLE
        binding.tvSaved!!.alpha = 0f
        binding.tvSaved!!.animate()
            .alpha(1f)
            .setDuration(200)
            .withEndAction {
                binding.tvSaved!!.postDelayed({
                    binding.tvSaved!!.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction {
                            binding.tvSaved!!.visibility = View.GONE
                        }.start()
                }, 1500)
            }.start()
    }
}