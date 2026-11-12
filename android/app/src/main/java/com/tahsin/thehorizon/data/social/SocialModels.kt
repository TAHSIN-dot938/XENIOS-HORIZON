package com.tahsin.thehorizon.data.social

data class UserSummary(
    val id: String,
    val userNumber: String,
    val username: String,
    val displayName: String,
    val avatarStyle: Int,
)

/** How a search result relates to the signed-in user (computed by the database). */
enum class Relation { SELF, FRIEND, OUTGOING, INCOMING, NONE }

data class SearchResult(val user: UserSummary, val relation: Relation)

data class FriendshipRow(
    val id: String,
    val accepted: Boolean,
    val requester: UserSummary,
    val addressee: UserSummary,
    val createdAt: String,
)

data class Friend(val friendshipId: String, val user: UserSummary)

/** A pending request; [user] is always the OTHER person. */
data class FriendRequest(val id: String, val user: UserSummary)

data class SocialSnapshot(
    val friends: List<Friend> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
) {
    companion object {
        fun from(meId: String, rows: List<FriendshipRow>): SocialSnapshot {
            fun other(r: FriendshipRow) = if (r.requester.id == meId) r.addressee else r.requester
            val friends = rows.filter { it.accepted }
                .map { Friend(it.id, other(it)) }
                .sortedBy { it.user.displayName.lowercase() }
            val pending = rows.filter { !it.accepted }.sortedByDescending { it.createdAt }
            return SocialSnapshot(
                friends = friends,
                incoming = pending.filter { it.addressee.id == meId }.map { FriendRequest(it.id, it.requester) },
                outgoing = pending.filter { it.requester.id == meId }.map { FriendRequest(it.id, it.addressee) },
            )
        }
    }
}
