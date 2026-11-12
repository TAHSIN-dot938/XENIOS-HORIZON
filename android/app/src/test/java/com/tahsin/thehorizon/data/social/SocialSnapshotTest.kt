package com.tahsin.thehorizon.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialSnapshotTest {
    private fun rows() = SocialParsers.friendships(
        "[" + listOf(
            friendshipJson("f-bob", "accepted", "2026-01-01", ME, BOB),          // I asked, accepted
            friendshipJson("f-carol", "accepted", "2026-01-02", CAROL, ME),      // they asked, accepted
            friendshipJson("f-dave", "pending", "2026-01-05", DAVE, ME),         // incoming
            friendshipJson("f-out1", "pending", "2026-01-03", ME, userJson("eve", "00000000000005", "eve", "Eve")),
            friendshipJson("f-out2", "pending", "2026-01-04", ME, userJson("fay", "00000000000006", "fay", "Fay")),
        ).joinToString(",") + "]",
    )

    @Test fun friendsAreTheOtherPersonSortedByNameIgnoringCase() {
        val s = SocialSnapshot.from("me", rows())
        assertEquals(listOf("Bob", "carol the third"), s.friends.map { it.user.displayName })
        assertEquals(listOf("f-bob", "f-carol"), s.friends.map { it.friendshipId })
    }

    @Test fun incomingShowsRequesterOutgoingShowsAddresseeNewestFirst() {
        val s = SocialSnapshot.from("me", rows())
        assertEquals(listOf("dave"), s.incoming.map { it.user.id })
        assertEquals(listOf("fay", "eve"), s.outgoing.map { it.user.id })       // 01-04 before 01-03
    }

    @Test fun otherPersonIsNeverMe() {
        val s = SocialSnapshot.from("me", rows())
        assertTrue((s.friends.map { it.user.id } + s.incoming.map { it.user.id } + s.outgoing.map { it.user.id }).none { it == "me" })
    }

    @Test fun emptyRowsGiveEmptySnapshot() = assertEquals(SocialSnapshot(), SocialSnapshot.from("me", emptyList()))
}
