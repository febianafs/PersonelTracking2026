package com.example.personeltracking2026.data.model

data class LoginResponse(
    val code: Int,
    val data: LoginData?,
    val message: String,
    val status: String
)

data class LoginData(
    val token: String,
    val user: UserData
)

data class UserData(
    val name: String,
    val roles: List<RoleData>
)

data class RoleData(
    val id: Int,
    val name: String
)