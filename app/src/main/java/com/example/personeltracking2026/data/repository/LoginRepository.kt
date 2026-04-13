package com.example.personeltracking2026.data.repository

import com.example.personeltracking2026.core.network.RetrofitClient
import com.example.personeltracking2026.data.model.LoginRequest
import com.example.personeltracking2026.data.model.LoginResponse

class LoginRepository {
    private val api = RetrofitClient.instance

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                val errorMsg = when (response.code()) {
                    400 -> "Request tidak valid"
                    401 -> "Username atau password salah"
                    else -> "Terjadi kesalahan: ${response.code()}"
                }
                Result.Error(errorMsg)
            }
        } catch (e: Exception) {
            Result.Error("Tidak dapat terhubung ke server")
        }
    }
}