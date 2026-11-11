package com.tahsin.thehorizon.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tahsin.thehorizon.R
import com.tahsin.thehorizon.ui.components.AmbientBackground
import com.tahsin.thehorizon.ui.theme.HorizonColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Logo rises and fades in, ambient glow breathes, welcome line fades in, then hands off. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val finished by rememberUpdatedState(onFinished)
    val logoAlpha = remember { Animatable(0f) }
    val logoRise = remember { Animatable(40f) }
    val textAlpha = remember { Animatable(0f) }
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPulse",
    )

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(900)) }
            launch { logoRise.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
            launch { delay(700); textAlpha.animateTo(1f, tween(800)) }
        }
        delay(1000)
        finished()
    }

    AmbientBackground {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(
                Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        translationY = logoRise.value * density
                    },
            ) {
                val w = size.width
                val horizonY = size.height * 0.62f
                val r = w * 0.30f
                drawCircle(
                    Brush.radialGradient(
                        listOf(HorizonColors.Purple.copy(alpha = 0.55f * glow), Color.Transparent),
                        center = Offset(w / 2, horizonY),
                        radius = w * 0.62f,
                    ),
                    radius = w * 0.62f,
                    center = Offset(w / 2, horizonY),
                )
                drawArc(
                    brush = Brush.verticalGradient(listOf(HorizonColors.PurpleSoft, HorizonColors.Purple)),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w / 2 - r, horizonY - r),
                    size = Size(2 * r, 2 * r),
                )
                drawLine(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, HorizonColors.NeonBlue, Color.Transparent)),
                    start = Offset(w * 0.04f, horizonY),
                    end = Offset(w * 0.96f, horizonY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "THE HORIZON",
                style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 8.sp),
                color = HorizonColors.TextPrimary,
                modifier = Modifier.graphicsLayer { alpha = logoAlpha.value },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.welcome_text),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = HorizonColors.PurpleSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
        }
    }
}
