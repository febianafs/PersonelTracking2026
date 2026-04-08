package com.example.personeltracking2026.core.base

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowManager
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.ui.about.AboutActivity
import com.example.personeltracking2026.ui.login.LoginActivity
import com.example.personeltracking2026.ui.settings.SettingsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

abstract class BaseActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var connectivityManager: ConnectivityManager
    private var noInternetSnackbar: Snackbar? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { hideNoInternetBanner() }
        }

        override fun onLost(network: Network) {
            runOnUiThread { showNoInternetBanner() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()

        // Register network callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Cek kondisi internet saat ini
        if (!isInternetAvailable()) {
            showNoInternetBanner()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore kalau belum terdaftar
        }
    }

    private fun isInternetAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNoInternetBanner() {
        val rootView = window.decorView.findViewById<View>(android.R.id.content)
        noInternetSnackbar = Snackbar.make(rootView, "No internet connection", Snackbar.LENGTH_INDEFINITE)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.secondary))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .setActionTextColor(ContextCompat.getColor(this, R.color.primary))
        noInternetSnackbar?.show()
    }

    private fun hideNoInternetBanner() {
        noInternetSnackbar?.dismiss()
        noInternetSnackbar = null
    }

    fun showOverflowMenu(anchor: View) {
        val wrapper = ContextThemeWrapper(this, R.style.DarkPopupMenu)
        val popup = PopupMenu(wrapper, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }
                R.id.action_logout -> {
                    showLogoutConfirmation()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    fun showLogoutConfirmation() {
        val title = SpannableString("Logout")
        title.setSpan(
            ForegroundColorSpan(getColor(R.color.white)),
            0, title.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val message = SpannableString("Are you sure you want to logout?")
        message.setSpan(
            ForegroundColorSpan(getColor(R.color.white)),
            0, message.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Logout") { _, _ -> logout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        sessionManager.clearSession()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}