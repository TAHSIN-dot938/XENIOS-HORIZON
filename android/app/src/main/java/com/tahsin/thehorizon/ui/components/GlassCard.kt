package com.tahsin.thehorizon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.tahsin.thehorizon.ui.theme.HorizonColors
import com.tahsin.thehorizon.ui.theme.Spacing

/**
 * Frosted-glass surface: translucent gradient fill + light edge. True backdrop blur is
 * intentionally not used; it is expensive on budget GPUs and unsupported below Android 12.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Spacing.CornerCard),
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(HorizonColors.GlassFillStrong, HorizonColors.GlassFill)), shape)
            .border(1.dp, Brush.verticalGradient(listOf(HorizonColors.GlassBorder, Color.White.copy(alpha = 0.04f))), shape)
            .padding(contentPadding),
        content = content,
    )
}
