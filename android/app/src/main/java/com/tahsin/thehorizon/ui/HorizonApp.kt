package com.tahsin.thehorizon.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tahsin.thehorizon.HorizonApplication
import com.tahsin.thehorizon.ui.auth.AuthScreen
import com.tahsin.thehorizon.ui.auth.AuthViewModel
import com.tahsin.thehorizon.ui.components.AmbientBackground
import com.tahsin.thehorizon.ui.home.NotConfiguredScreen
import com.tahsin.thehorizon.ui.home.ProfileErrorScreen
import com.tahsin.thehorizon.ui.home.HomeShell
import com.tahsin.thehorizon.ui.splash.SplashScreen
import com.tahsin.thehorizon.ui.theme.HorizonColors

private enum class Screen { SPLASH, LOADING, NOT_CONFIGURED, AUTH, HOME, PROFILE_ERROR }

@Composable
fun HorizonApp() {
    val container = (LocalContext.current.applicationContext as HorizonApplication).container
    val factory = remember { AppViewModelFactory(container) }
    val sessionVm: SessionViewModel = viewModel(factory = factory)
    val state by sessionVm.state.collectAsStateWithLifecycle()
    var splashDone by rememberSaveable { mutableStateOf(false) }

    val screen = when {
        !splashDone -> Screen.SPLASH
        else -> when (state) {
            SessionState.Loading -> Screen.LOADING
            SessionState.NotConfigured -> Screen.NOT_CONFIGURED
            SessionState.SignedOut -> Screen.AUTH
            is SessionState.SignedIn -> Screen.HOME
            is SessionState.ProfileError -> Screen.PROFILE_ERROR
        }
    }

    Crossfade(targetState = screen, animationSpec = tween(500), label = "root") { target ->
        when (target) {
            Screen.SPLASH -> SplashScreen(onFinished = { splashDone = true })
            Screen.LOADING -> AmbientBackground {
                Box(androidx.compose.ui.Modifier.align(Alignment.Center)) {
                    CircularProgressIndicator(color = HorizonColors.Purple)
                }
            }
            Screen.NOT_CONFIGURED -> NotConfiguredScreen()
            Screen.AUTH -> {
                val authVm: AuthViewModel = viewModel(factory = factory)
                AuthScreen(authVm)
            }
            Screen.HOME -> (state as? SessionState.SignedIn)?.let { HomeShell(it.profile, factory, sessionVm::signOut) }
            Screen.PROFILE_ERROR -> (state as? SessionState.ProfileError)?.let {
                ProfileErrorScreen(it.message, sessionVm::retry, sessionVm::signOut)
            }
        }
    }
}
