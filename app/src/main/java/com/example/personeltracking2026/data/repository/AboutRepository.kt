package com.example.personeltracking2026.data.repository

import com.example.personeltracking2026.core.network.RetrofitClient
import com.example.personeltracking2026.data.model.AboutResponse

class AboutRepository {

    private val api = RetrofitClient.instance

    suspend fun getAboutUs(): Result<AboutResponse> {
        return try {
            val response = api.getAboutUs()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.Success(body)
                } else {
                    Result.Error("Response body kosong")
                }
            } else {
                Result.Error("Gagal: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Result.Error(e.localizedMessage ?: "Terjadi kesalahan")
        }
    }
}