package com.example.personeltracking2026.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.data.repository.AuthRepository
import com.example.personeltracking2026.data.repository.Result
import com.example.personeltracking2026.databinding.ActivitySplashBinding
import com.example.personeltracking2026.ui.bodycam.BodycamActivity
import com.example.personeltracking2026.ui.login.LoginActivity
import com.example.personeltracking2026.ui.main.MainActivity
import com.example.personeltracking2026.ui.personel.PersonelActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var sessionManager: SessionManager
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Animasi logo masuk
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.splash_fade_in)
        binding.imgLogo.startAnimation(fadeIn)

        lifecycleScope.launch {
            // Minimal 2 detik splash ditampilkan
            val minDelay = launch { delay(2000) }

            val destination = if (sessionManager.isLoggedIn()) {
                validateToken()
            } else {
                LoginActivity::class.java
            }

            // Tunggu minimal delay selesai
            minDelay.join()

            // Animasi fade out sebelum pindah
            binding.root.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    val intent = Intent(this@SplashActivity, destination)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }.start()
        }
    }

    private suspend fun validateToken(): Class<*> {
        val token = sessionManager.getToken() ?: return LoginActivity::class.java

        return when (val result = authRepository.checkToken(token)) {
            is Result.Success -> {
                // Token valid — cek role
                when (sessionManager.getRole()) {
                    SessionManager.ROLE_PERSONEL -> PersonelActivity::class.java
                    SessionManager.ROLE_BODYCAM -> BodycamActivity::class.java
                    else -> MainActivity::class.java
                }
            }
            is Result.Error -> {
                if (result.message == "Token expired") {
                    sessionManager.clearSession()
                    LoginActivity::class.java
                } else {
                    // Tidak bisa konek — lanjut dengan token lokal
                    when (sessionManager.getRole()) {
                        SessionManager.ROLE_PERSONEL -> PersonelActivity::class.java
                        SessionManager.ROLE_BODYCAM -> BodycamActivity::class.java
                        else -> MainActivity::class.java
                    }
                }
            }
            else -> LoginActivity::class.java
        }
    }
}