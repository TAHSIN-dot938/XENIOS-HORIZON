package com.tahsin.thehorizon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = HorizonColors.Purple,
    onPrimary = HorizonColors.TextPrimary,
    secondary = HorizonColors.NeonBlue,
    background = HorizonColors.Void,
    onBackground = HorizonColors.TextPrimary,
    surface = HorizonColors.Surface,
    onSurface = HorizonColors.TextPrimary,
    surfaceVariant = HorizonColors.SurfaceRaised,
    onSurfaceVariant = HorizonColors.TextSecondary,
    error = HorizonColors.Danger,
    outline = HorizonColors.GlassBorder,
)

/** The app is dark-only by design. Light/other themes arrive with Phase 8 settings. */
@Composable
fun HorizonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = HorizonTypography, content = content)
}
