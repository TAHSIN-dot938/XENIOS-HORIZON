package com.tahsin.thehorizon.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tahsin.thehorizon.ui.theme.HorizonColors
import com.tahsin.thehorizon.ui.theme.Spacing

/** A person in a list: glowing avatar, names, optional presence dot and trailing actions. */
@Composable
fun UserRow(
    displayName: String,
    subtitle: String,
    avatarStyle: Int,
    modifier: Modifier = Modifier,
    online: Boolean? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Spacing.CornerControl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HorizonColors.GlassFill, shape)
            .border(1.dp, HorizonColors.GlassBorder.copy(alpha = 0.10f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowAvatar(name = displayName, size = 46.dp, styleIndex = avatarStyle, online = online)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                displayName, style = MaterialTheme.typography.titleMedium, color = HorizonColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle, style = MaterialTheme.typography.bodyMedium, color = HorizonColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

/** Compact action button. [filled] = primary gradient; otherwise an outlined secondary action. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val base = modifier
        .widthIn(min = 72.dp)
        .height(36.dp)
        .alpha(if (enabled) 1f else 0.5f)
        .clip(shape)
    val styled = if (filled) {
        base.background(Brush.horizontalGradient(listOf(HorizonColors.PurpleDeep, HorizonColors.Purple)))
    } else {
        base.background(HorizonColors.GlassFill).border(1.dp, HorizonColors.GlassBorder, shape)
    }
    Box(
        styled.clickable(enabled = enabled && !loading, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = HorizonColors.TextPrimary, strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, color = HorizonColors.TextPrimary)
        }
    }
}

/** Read-only status label such as "Pending" or "Friends". */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(HorizonColors.GlassFill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = HorizonColors.TextSecondary,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = Spacing.md, bottom = Spacing.xs, start = Spacing.xs),
        style = MaterialTheme.typography.labelSmall,
        color = HorizonColors.TextSecondary,
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = Spacing.md, bottom = Spacing.xs, start = Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = HorizonColors.TextPrimary)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HorizonColors.TextSecondary)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    GlassCard(modifier.fillMaxWidth().padding(top = Spacing.lg), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = HorizonColors.TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.xs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = HorizonColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (action != null) {
            Spacer(Modifier.height(Spacing.md))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { action() }
        }
    }
}

/** Slim banner shown while the realtime connection is down. */
@Composable
fun ConnectionBanner(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(HorizonColors.PurpleDeep.copy(alpha = 0.35f))
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(12.dp), color = HorizonColors.PurpleSoft, strokeWidth = 1.5.dp)
            Spacer(Modifier.size(8.dp))
            Text("Connecting…", style = MaterialTheme.typography.labelSmall, color = HorizonColors.PurpleSoft)
        }
    }
}

/** Transient message pill. The caller clears it after a delay. */
@Composable
fun ToastPill(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = message != null, modifier = modifier) {
        Text(
            message.orEmpty(),
            modifier = Modifier
                .clip(CircleShape)
                .background(HorizonColors.SurfaceRaised)
                .border(1.dp, HorizonColors.GlassBorder, CircleShape)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = HorizonColors.TextPrimary,
        )
    }
}
