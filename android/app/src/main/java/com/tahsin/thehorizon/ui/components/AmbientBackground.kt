package com.tahsin.thehorizon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tahsin.thehorizon.ui.theme.HorizonColors

/** Deep-black backdrop with two static radial glows (static = cheap on budget GPUs). */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HorizonColors.Void)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(HorizonColors.PurpleDeep.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.05f),
                        radius = size.maxDimension * 0.7f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(HorizonColors.NeonBlue.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.95f),
                        radius = size.maxDimension * 0.6f,
                    ),
                )
            },
        content = content,
    )
}
