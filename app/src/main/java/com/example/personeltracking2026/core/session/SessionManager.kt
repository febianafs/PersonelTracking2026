package com.example.personeltracking2026.core.session

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREF_NAME = "personel_tracking_session"
        private const val KEY_TOKEN      = "token"
        private const val KEY_NAME       = "name"
        private const val KEY_ROLE       = "role"
        // ── tambahan untuk payload MQTT ──
        private const val KEY_RANK       = "rank"
        private const val KEY_UNIT       = "unit"
        private const val KEY_BATTALION  = "battalion"
        private const val KEY_SQUAD      = "squad"
        private const val KEY_AVATAR     = "avatar"

        const val ROLE_PERSONEL = "personel"
        const val ROLE_BODYCAM  = "bodycam"
    }

    fun getUserId(): Int? {
        val token = getToken() ?: return null
        return try {
            val jwt = com.auth0.android.jwt.JWT(token)
            jwt.getClaim("user_id").asInt()
        } catch (e: Exception) { null }
    }

    fun getUsername(): String? {
        val token = getToken() ?: return null
        return try {
            val jwt = com.auth0.android.jwt.JWT(token)
            jwt.getClaim("username").asString()
        } catch (e: Exception) { null }
    }

    fun saveSession(token: String, name: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_NAME, name)
            .apply()
    }

    /** Simpan data profil tambahan setelah load PersonelDetail dari API */
    fun savePersonelDetail(
        rank: String?,
        unit: String?,
        battalion: String?,
        squad: String?,
        avatar: String?
    ) {
        prefs.edit()
            .putString(KEY_RANK, rank)
            .putString(KEY_UNIT, unit)
            .putString(KEY_BATTALION, battalion)
            .putString(KEY_SQUAD, squad)
            .putString(KEY_AVATAR, avatar)
            .apply()
    }

    fun saveRole(role: String) {
        prefs.edit().putString(KEY_ROLE, role).apply()
    }

    fun getToken(): String?     = prefs.getString(KEY_TOKEN, null)
    fun getName(): String?      = prefs.getString(KEY_NAME, null)
    fun getRole(): String?      = prefs.getString(KEY_ROLE, null)
    fun getRank(): String?      = prefs.getString(KEY_RANK, null)
    fun getUnit(): String?      = prefs.getString(KEY_UNIT, null)
    fun getBattalion(): String? = prefs.getString(KEY_BATTALION, null)
    fun getSquad(): String?     = prefs.getString(KEY_SQUAD, null)
    fun getAvatar(): String?    = prefs.getString(KEY_AVATAR, null)
    fun isLoggedIn(): Boolean   = getToken() != null

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}