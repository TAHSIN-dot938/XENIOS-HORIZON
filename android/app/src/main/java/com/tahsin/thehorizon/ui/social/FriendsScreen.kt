package com.tahsin.thehorizon.ui.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tahsin.thehorizon.data.social.Friend
import com.tahsin.thehorizon.ui.components.EmptyState
import com.tahsin.thehorizon.ui.components.PillButton
import com.tahsin.thehorizon.ui.components.ScreenTitle
import com.tahsin.thehorizon.ui.components.SectionLabel
import com.tahsin.thehorizon.ui.components.UserRow
import com.tahsin.thehorizon.ui.theme.HorizonColors
import com.tahsin.thehorizon.ui.theme.Spacing

@Composable
fun FriendsScreen(
    state: SocialUiState,
    onRemove: (Friend) -> Unit,
    onRetry: () -> Unit,
    onFindPeople: () -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<Friend?>(null) }
    // Presence is only trustworthy while the realtime connection is up.
    val online = if (state.connected) state.onlineIds else emptySet()
    val (onlineFriends, offlineFriends) = state.friends.partition { it.user.id in online }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading && state.friends.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = HorizonColors.Purple)
            state.loadError != null && state.friends.isEmpty() && state.incoming.isEmpty() ->
                EmptyState("Couldn't load your friends", state.loadError) { PillButton("TRY AGAIN", onRetry) }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "title") {
                    ScreenTitle("Friends", "${onlineFriends.size} online · ${state.friends.size} total")
                }
                if (state.friends.isEmpty()) {
                    item(key = "empty") {
                        EmptyState("No friends yet", "Search by username or 14-digit ID in Discover to send your first request.") {
                            PillButton("FIND PEOPLE", onFindPeople)
                        }
                    }
                }
                if (onlineFriends.isNotEmpty()) {
                    item(key = "h-online") { SectionLabel("Online — ${onlineFriends.size}") }
                    items(onlineFriends, key = { it.friendshipId }) { f ->
                        FriendRow(f, online = true, busy = f.friendshipId in state.busy, onRemove = { pendingRemoval = f }, Modifier.animateItem())
                    }
                }
                if (offlineFriends.isNotEmpty()) {
                    item(key = "h-offline") { SectionLabel("Offline — ${offlineFriends.size}") }
                    items(offlineFriends, key = { it.friendshipId }) { f ->
                        FriendRow(f, online = false, busy = f.friendshipId in state.busy, onRemove = { pendingRemoval = f }, Modifier.animateItem())
                    }
                }
            }
        }
    }

    pendingRemoval?.let { friend ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            containerColor = HorizonColors.SurfaceRaised,
            title = { Text("Remove ${friend.user.displayName}?", color = HorizonColors.TextPrimary) },
            text = { Text("You'll no longer be able to message each other until you're friends again.", color = HorizonColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onRemove(friend); pendingRemoval = null }) { Text("Remove", color = HorizonColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel", color = HorizonColors.PurpleSoft) }
            },
        )
    }
}

@Composable
private fun FriendRow(friend: Friend, online: Boolean, busy: Boolean, onRemove: () -> Unit, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    UserRow(
        displayName = friend.user.displayName,
        subtitle = "@${friend.user.username}",
        avatarStyle = friend.user.avatarStyle,
        online = online,
        modifier = modifier,
        trailing = {
            Box {
                IconButton(onClick = { menu = true }, enabled = !busy) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = HorizonColors.TextSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Remove friend", color = HorizonColors.Danger) }, onClick = { menu = false; onRemove() })
                }
            }
        },
    )
}
