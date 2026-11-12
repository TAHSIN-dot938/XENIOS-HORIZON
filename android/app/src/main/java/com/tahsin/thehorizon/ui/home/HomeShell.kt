package com.tahsin.thehorizon.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tahsin.thehorizon.data.profile.Profile
import com.tahsin.thehorizon.ui.components.AmbientBackground
import com.tahsin.thehorizon.ui.components.ConnectionBanner
import com.tahsin.thehorizon.ui.components.ToastPill
import com.tahsin.thehorizon.ui.social.DiscoverScreen
import com.tahsin.thehorizon.ui.social.DiscoverViewModel
import com.tahsin.thehorizon.ui.social.FriendsScreen
import com.tahsin.thehorizon.ui.social.RequestsScreen
import com.tahsin.thehorizon.ui.social.SocialViewModel
import com.tahsin.thehorizon.ui.theme.HorizonColors
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    FRIENDS("Friends", Icons.Filled.Person),
    DISCOVER("Discover", Icons.Filled.Search),
    REQUESTS("Requests", Icons.Filled.Email),
    PROFILE("Profile", Icons.Filled.AccountCircle),
}

/** Signed-in shell: tab content + glass bottom bar. Chats and Notifications tabs arrive in later phases. */
@Composable
fun HomeShell(profile: Profile, factory: ViewModelProvider.Factory, onSignOut: () -> Unit) {
    val social: SocialViewModel = viewModel(factory = factory)
    val discover: DiscoverViewModel = viewModel(factory = factory)
    val state by social.state.collectAsStateWithLifecycle()
    val discoverState by discover.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.FRIENDS) }

    LaunchedEffect(profile.id) { social.bind(profile) }
    // Realtime (and therefore "online") only while the app is visible.
    LifecycleStartEffect(profile.id) {
        social.setForeground(true)
        onStopOrDispose { social.setForeground(false) }
    }
    LaunchedEffect(state.revision) { discover.rerun() }
    LaunchedEffect(state.toast) { if (state.toast != null) { delay(2500); social.consumeToast() } }
    LaunchedEffect(discoverState.message) { if (discoverState.message != null) { delay(2500); discover.consumeMessage() } }

    AmbientBackground {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                Column(Modifier.fillMaxSize()) {
                    ConnectionBanner(visible = !state.connected && !state.loading)
                    Box(Modifier.weight(1f)) {
                        Crossfade(targetState = tab, animationSpec = tween(220), label = "tab") { current ->
                            when (current) {
                                Tab.FRIENDS -> FriendsScreen(
                                    state = state,
                                    onRemove = social::remove,
                                    onRetry = social::refreshNow,
                                    onFindPeople = { tab = Tab.DISCOVER },
                                )
                                Tab.DISCOVER -> DiscoverScreen(
                                    state = discoverState,
                                    onQuery = discover::onQuery,
                                    onAddOrAccept = { r -> discover.addOrAccept(r, onSuccess = social::refreshNow) },
                                )
                                Tab.REQUESTS -> RequestsScreen(
                                    state = state,
                                    onAccept = social::accept,
                                    onReject = social::reject,
                                    onCancel = social::cancel,
                                )
                                Tab.PROFILE -> ProfileTab(profile = profile, onSignOut = onSignOut)
                            }
                        }
                    }
                }
                ToastPill(
                    message = state.toast ?: discoverState.message,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
            BottomBar(selected = tab, requestCount = state.incoming.size, onSelect = { tab = it })
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, requestCount: Int, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(HorizonColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, HorizonColors.GlassBorder.copy(alpha = 0.10f))
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
    ) {
        Tab.entries.forEach { t ->
            val active = t == selected
            val tint = if (active) HorizonColors.PurpleSoft else HorizonColors.TextSecondary
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = { onSelect(t) })
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Icon(t.icon, contentDescription = t.label, tint = tint, modifier = Modifier.size(24.dp))
                    if (t == Tab.REQUESTS && requestCount > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).size(10.dp).background(HorizonColors.Danger, CircleShape),
                        )
                    }
                }
                Text(t.label, style = MaterialTheme.typography.labelSmall, color = tint)
                Box(
                    Modifier.padding(top = 3.dp).size(4.dp)
                        .background(if (active) HorizonColors.Purple else Color.Transparent, CircleShape),
                )
            }
        }
    }
}
