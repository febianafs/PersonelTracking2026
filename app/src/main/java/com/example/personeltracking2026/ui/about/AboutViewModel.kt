package com.example.personeltracking2026.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personeltracking2026.data.model.AboutResponse
import com.example.personeltracking2026.data.repository.AboutRepository
import com.example.personeltracking2026.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AboutViewModel(private val repository: AboutRepository) : ViewModel() {

    private val _aboutState = MutableStateFlow<Result<AboutResponse>?>(null)
    val aboutState: StateFlow<Result<AboutResponse>?> = _aboutState

    init {
        fetchAboutUs()
    }

    fun fetchAboutUs() {
        viewModelScope.launch {
            _aboutState.value = Result.Loading
            _aboutState.value = repository.getAboutUs()
        }
    }

    class Factory(private val repository: AboutRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AboutViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AboutViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}