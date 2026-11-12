package com.tahsin.thehorizon.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tahsin.thehorizon.data.social.Relation
import com.tahsin.thehorizon.data.social.SearchResult
import com.tahsin.thehorizon.ui.components.EmptyState
import com.tahsin.thehorizon.ui.components.HorizonTextField
import com.tahsin.thehorizon.ui.components.PillButton
import com.tahsin.thehorizon.ui.components.ScreenTitle
import com.tahsin.thehorizon.ui.components.StatusChip
import com.tahsin.thehorizon.ui.components.UserRow
import com.tahsin.thehorizon.ui.theme.HorizonColors
import com.tahsin.thehorizon.ui.theme.Spacing

@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    onQuery: (String) -> Unit,
    onAddOrAccept: (SearchResult) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        ScreenTitle("Discover", "Find people by username or 14-digit ID")
        HorizonTextField(
            value = state.query, onValueChange = onQuery, label = "Username or ID",
            imeAction = ImeAction.Search, modifier = Modifier.padding(top = Spacing.sm),
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = Spacing.lg), color = HorizonColors.Purple)
                state.error != null -> EmptyState("Search failed", state.error)
                state.query.trim().length < 2 -> Text(
                    "Type at least 2 characters of a username, or paste someone's full 14-digit ID.",
                    modifier = Modifier.padding(top = Spacing.lg, start = Spacing.xs),
                    style = MaterialTheme.typography.bodyMedium, color = HorizonColors.TextSecondary,
                )
                state.searched && state.results.isEmpty() ->
                    EmptyState("No users found", "Check the spelling, or ask your friend for their exact 14-digit ID.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.results, key = { it.user.id }) { r ->
                        UserRow(
                            displayName = r.user.displayName,
                            subtitle = "@${r.user.username} · ${r.user.userNumber}",
                            avatarStyle = r.user.avatarStyle,
                            modifier = Modifier.animateItem(),
                            trailing = {
                                when (r.relation) {
                                    Relation.NONE -> PillButton("Add", { onAddOrAccept(r) }, loading = r.user.id in state.busy)
                                    Relation.INCOMING -> PillButton("Accept", { onAddOrAccept(r) }, loading = r.user.id in state.busy)
                                    Relation.OUTGOING -> StatusChip("Pending")
                                    Relation.FRIEND -> StatusChip("Friends")
                                    Relation.SELF -> StatusChip("You")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
