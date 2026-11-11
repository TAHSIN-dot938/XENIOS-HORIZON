package com.tahsin.thehorizon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tahsin.thehorizon.ui.theme.HorizonColors

/** Gradient presets selectable as `profiles.avatar_style`. */
val AvatarPalettes: List<List<Color>> = listOf(
    listOf(HorizonColors.Purple, HorizonColors.NeonBlue),
    listOf(Color(0xFFEC4899), HorizonColors.Purple),
    listOf(Color(0xFF22D3EE), Color(0xFF6366F1)),
    listOf(Color(0xFFF472B6), Color(0xFFFB923C)),
    listOf(Color(0xFF34D399), Color(0xFF38BDF8)),
    listOf(Color(0xFFA78BFA), Color(0xFFF0ABFC)),
)

fun initialsOf(name: String): String =
    name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** Initials avatar with soft glow; `online == null` hides the presence dot. Photo support arrives with Phase 3. */
@Composable
fun GlowAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    styleIndex: Int = 0,
    online: Boolean? = null,
) {
    val colors = AvatarPalettes[styleIndex.mod(AvatarPalettes.size)]
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.matchParentSize().drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(colors[0].copy(alpha = 0.45f), Color.Transparent),
                        radius = this.size.minDimension * 0.9f,
                    ),
                )
            },
        )
        Box(
            Modifier
                .size(size * 0.86f)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors))
                .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initialsOf(name), style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        if (online != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.27f)
                    .clip(CircleShape)
                    .background(if (online) HorizonColors.Success else HorizonColors.TextSecondary)
                    .border(2.dp, HorizonColors.Void, CircleShape),
            )
        }
    }
}
