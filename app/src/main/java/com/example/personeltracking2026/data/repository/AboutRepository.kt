package com.example.personeltracking2026.data.repository

import com.example.personeltracking2026.core.network.RetrofitClient
import com.example.personeltracking2026.data.model.AboutResponse

class AboutRepository {
    private val api = RetrofitClient.instance

    suspend fun getAboutUs(): Result<AboutResponse> {
        return try {
            val response = api.getAboutUs()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to load data: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Cannot connect to server")
        }
    }
}