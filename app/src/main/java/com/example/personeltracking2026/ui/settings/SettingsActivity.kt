package com.example.personeltracking2026.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.base.BaseActivity
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.databinding.ActivitySettingsBinding

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sessionManager: SessionManager

    private val intervalOptions = listOf(
        "1 second", "2 seconds", "5 seconds", "10 seconds",
        "15 seconds", "30 seconds", "1 minute", "2 minutes",
        "5 minutes", "10 minutes"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        sessionManager = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }

        setupIntervalDropdown()
        loadSettings()
        setupAutoSave()
    }

    private fun setupIntervalDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            intervalOptions
        )
        binding.actInterval.setAdapter(adapter)
        binding.actInterval.setOnItemClickListener { _, _, _, _ ->
            saveSettings()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("mqtt_settings", MODE_PRIVATE)
        binding.etServer.setText(prefs.getString("server", ""))
        binding.etPort.setText(prefs.getString("port", "1883"))
        binding.switchAutoReconnect.isChecked = prefs.getBoolean("auto_reconnect", true)

        val savedInterval = prefs.getString("interval", "")
        if (!savedInterval.isNullOrEmpty()) {
            binding.actInterval.setText(savedInterval, false)
        }
    }

    private fun setupAutoSave() {
        binding.etServer.addTextChangedListener { saveSettings() }
        binding.etPort.addTextChangedListener { saveSettings() }
        binding.switchAutoReconnect.setOnCheckedChangeListener { _, _ -> saveSettings() }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("mqtt_settings", MODE_PRIVATE)
        prefs.edit()
            .putString("server", binding.etServer.text.toString())
            .putString("port", binding.etPort.text.toString())
            .putString("interval", binding.actInterval.text.toString())
            .putBoolean("auto_reconnect", binding.switchAutoReconnect.isChecked)
            .apply()

        showSavedIndicator()
    }

    private fun showSavedIndicator() {
        binding.tvSaved.visibility = View.VISIBLE
        binding.tvSaved.animate()
            .alpha(1f)
            .setDuration(200)
            .withEndAction {
                binding.tvSaved.postDelayed({
                    binding.tvSaved.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction {
                            binding.tvSaved.visibility = View.GONE
                        }.start()
                }, 1500)
            }.start()
    }
}