package com.tahsin.thehorizon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tahsin.thehorizon.AppContainer
import com.tahsin.thehorizon.ui.auth.AuthViewModel
import com.tahsin.thehorizon.ui.social.DiscoverViewModel
import com.tahsin.thehorizon.ui.social.SocialViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(SessionViewModel::class.java) ->
            SessionViewModel(container.isConfigured, container.authRepository, container.profileRepository) as T
        modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(container.authRepository) as T
        modelClass.isAssignableFrom(SocialViewModel::class.java) ->
            SocialViewModel(container.authRepository, container.socialRepository, container::newRealtime) as T
        modelClass.isAssignableFrom(DiscoverViewModel::class.java) -> DiscoverViewModel(container.socialRepository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
