package com.example.personeltracking2026.core.network

sealed class NetworkState {
    object Connected : NetworkState()
    object Connecting : NetworkState()
} 