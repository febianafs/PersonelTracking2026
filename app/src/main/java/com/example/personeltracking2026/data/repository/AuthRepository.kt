package com.example.personeltracking2026.data.repository

import com.example.personeltracking2026.core.network.RetrofitClient

class AuthRepository {
    private val api = RetrofitClient.instance

    suspend fun checkToken(token: String): Result<Boolean> {
        return try {
            val response = api.checkToken("Bearer $token")
            when (response.code()) {
                200 -> Result.Success(true)
                401 -> Result.Error("Token expired")
                else -> Result.Error("Token invalid")
            }
        } catch (e: Exception) {
            Result.Error("Cannot connect to server")
        }
    }
}