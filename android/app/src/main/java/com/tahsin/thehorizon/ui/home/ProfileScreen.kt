package com.tahsin.thehorizon.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tahsin.thehorizon.data.profile.Profile
import com.tahsin.thehorizon.ui.components.AmbientBackground
import com.tahsin.thehorizon.ui.components.GlassCard
import com.tahsin.thehorizon.ui.components.GlowAvatar
import com.tahsin.thehorizon.ui.components.HorizonButton
import com.tahsin.thehorizon.ui.theme.HorizonColors
import com.tahsin.thehorizon.ui.theme.Spacing

/** Profile tab (content only; the shell provides background and insets). */
@Composable
fun ProfileTab(profile: Profile, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlowAvatar(name = profile.displayName, size = 112.dp, styleIndex = profile.avatarStyle)
        Spacer(Modifier.height(Spacing.md))
        Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, color = HorizonColors.TextPrimary)
        Text("@${profile.username}", style = MaterialTheme.typography.bodyLarge, color = HorizonColors.PurpleSoft)
        Spacer(Modifier.height(Spacing.lg))

        GlassCard(Modifier.fillMaxWidth()) {
            Text("YOUR HORIZON ID", style = MaterialTheme.typography.labelSmall, color = HorizonColors.TextSecondary)
            Spacer(Modifier.height(Spacing.xs))
            Text(profile.userNumber, style = MaterialTheme.typography.headlineSmall, color = HorizonColors.TextPrimary)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Friends can find you with this number or your username.",
                style = MaterialTheme.typography.bodyMedium,
                color = HorizonColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        HorizonButton(text = "SIGN OUT", onClick = onSignOut)
    }
}

/** Shown when the profile could not be loaded (network error etc.). */
@Composable
fun ProfileErrorScreen(message: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    AmbientBackground {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = HorizonColors.TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.lg))
            HorizonButton(text = "TRY AGAIN", onClick = onRetry)
            Spacer(Modifier.height(Spacing.sm))
            androidx.compose.material3.TextButton(onClick = onSignOut) {
                Text("Sign out", color = HorizonColors.PurpleSoft)
            }
        }
    }
}

/** Shown when the build has no Supabase credentials: explains exactly what to do. */
@Composable
fun NotConfiguredScreen() {
    AmbientBackground {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlassCard {
                Text("Backend not configured", style = MaterialTheme.typography.headlineSmall, color = HorizonColors.TextPrimary)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "This build has no Supabase credentials. Add SUPABASE_URL and SUPABASE_ANON_KEY to android/local.properties " +
                        "(see docs/SETUP.md), then rebuild.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HorizonColors.TextSecondary,
                )
            }
        }
    }
}
