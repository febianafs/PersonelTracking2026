package com.example.personeltracking2026.ui.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.example.personeltracking2026.R

class BluetoothActivity : AppCompatActivity() {

    private lateinit var switchBluetooth: SwitchCompat
    private lateinit var tvToggleLabel: TextView
    private lateinit var btnBack: ImageButton

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        // INIT VIEW
        switchBluetooth = findViewById(R.id.switchBluetooth)
        tvToggleLabel = findViewById(R.id.tvToggleLabel)
        btnBack = findViewById(R.id.btnBack)

        // BACK BUTTON
        btnBack.setOnClickListener {
            finish()
        }

        // SET STATE AWAL DARI DEVICE
        val isBluetoothOn = bluetoothAdapter?.isEnabled == true
        switchBluetooth.isChecked = isBluetoothOn
        updateUI(isBluetoothOn)

        // LISTENER SWITCH
        switchBluetooth.setOnCheckedChangeListener { _, isChecked ->

            if (!hasBluetoothPermission()) {
                requestPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                switchBluetooth.isChecked = !isChecked // balikin state
                return@setOnCheckedChangeListener
            }

            try {
                if (isChecked) {
                    bluetoothAdapter?.enable()
                } else {
                    bluetoothAdapter?.disable()
                }
                updateUI(isChecked)

            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    // UPDATE UI
    private fun updateUI(isOn: Boolean) {
        if (isOn) {
            tvToggleLabel.text = "Aktif"
            tvToggleLabel.setTextColor(
                ContextCompat.getColor(this, R.color.primary)
            )
        } else {
            tvToggleLabel.text = "Non-aktif"
            tvToggleLabel.setTextColor(
                ContextCompat.getColor(this, R.color.gray)
            )
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Permission Bluetooth diperlukan", Toast.LENGTH_SHORT).show()
            }
        }
}