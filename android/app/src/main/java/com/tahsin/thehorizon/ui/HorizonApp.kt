package com.tahsin.thehorizon.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tahsin.thehorizon.ui.auth.AuthScreen
import com.tahsin.thehorizon.ui.splash.SplashScreen

@Composable
fun HorizonApp() {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    Crossfade(targetState = showSplash, animationSpec = tween(600), label = "root") { splash ->
        if (splash) SplashScreen(onFinished = { showSplash = false }) else AuthScreen()
    }
}
