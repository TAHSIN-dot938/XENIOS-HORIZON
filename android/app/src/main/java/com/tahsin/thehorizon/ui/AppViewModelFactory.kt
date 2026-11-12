package com.tahsin.thehorizon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tahsin.thehorizon.AppContainer
import com.tahsin.thehorizon.ui.auth.AuthViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(SessionViewModel::class.java) ->
            SessionViewModel(container.isConfigured, container.authRepository, container.profileRepository) as T
        modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(container.authRepository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
