package com.tahsin.thehorizon.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tahsin.thehorizon.ui.components.EmptyState
import com.tahsin.thehorizon.ui.components.PillButton
import com.tahsin.thehorizon.ui.components.ScreenTitle
import com.tahsin.thehorizon.ui.components.SectionLabel
import com.tahsin.thehorizon.ui.components.UserRow
import com.tahsin.thehorizon.data.social.FriendRequest
import com.tahsin.thehorizon.ui.theme.Spacing

@Composable
fun RequestsScreen(
    state: SocialUiState,
    onAccept: (FriendRequest) -> Unit,
    onReject: (FriendRequest) -> Unit,
    onCancel: (FriendRequest) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") { ScreenTitle("Requests", "${state.incoming.size} incoming · ${state.outgoing.size} sent") }
        if (state.incoming.isEmpty() && state.outgoing.isEmpty()) {
            item(key = "empty") { EmptyState("All caught up", "Friend requests you send or receive will show up here.") }
        }
        if (state.incoming.isNotEmpty()) {
            item(key = "h-in") { SectionLabel("Incoming — ${state.incoming.size}") }
            items(state.incoming, key = { it.id }) { r ->
                val busy = r.id in state.busy
                UserRow(
                    displayName = r.user.displayName, subtitle = "@${r.user.username}", avatarStyle = r.user.avatarStyle,
                    modifier = Modifier.animateItem(),
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PillButton("Decline", { onReject(r) }, filled = false, enabled = !busy, modifier = Modifier)
                            PillButton("Accept", { onAccept(r) }, enabled = !busy, loading = busy)
                        }
                    },
                )
            }
        }
        if (state.outgoing.isNotEmpty()) {
            item(key = "h-out") { SectionLabel("Sent — ${state.outgoing.size}") }
            items(state.outgoing, key = { it.id }) { r ->
                UserRow(
                    displayName = r.user.displayName, subtitle = "@${r.user.username}", avatarStyle = r.user.avatarStyle,
                    modifier = Modifier.animateItem(),
                    trailing = { PillButton("Cancel", { onCancel(r) }, filled = false, enabled = r.id !in state.busy) },
                )
            }
        }
    }
}
