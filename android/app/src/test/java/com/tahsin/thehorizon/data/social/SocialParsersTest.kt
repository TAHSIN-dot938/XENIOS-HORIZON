package com.tahsin.thehorizon.data.social

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialParsersTest {
    @Test fun parsesSearchResultsWithRelations() {
        val body = """[
          {"id":"u1","user_number":"00000000000009","username":"zed","display_name":"Zed","avatar_url":null,"avatar_style":3,"relation":"friend"},
          {"id":"u2","user_number":"00000000000010","username":"yan","display_name":"Yan","avatar_style":0,"relation":"incoming"},
          {"id":"u3","user_number":"00000000000011","username":"xi","display_name":"Xi","avatar_style":0,"relation":"outgoing"},
          {"id":"u4","user_number":"00000000000012","username":"wu","display_name":"Wu","avatar_style":0,"relation":"self"},
          {"id":"u5","user_number":"00000000000013","username":"vi","display_name":"Vi","avatar_style":0,"relation":"none"}]"""
        val r = SocialParsers.searchResults(body)
        assertEquals(listOf(Relation.FRIEND, Relation.INCOMING, Relation.OUTGOING, Relation.SELF, Relation.NONE), r.map { it.relation })
        assertEquals("00000000000009", r[0].user.userNumber)
        assertEquals(3, r[0].user.avatarStyle)
    }

    @Test fun unknownRelationFallsBackToNone() = assertEquals(Relation.NONE, SocialParsers.relation("weird"))

    @Test fun missingAvatarStyleDefaultsToZero() {
        val r = SocialParsers.searchResults("""[{"id":"u","user_number":"00000000000001","username":"a","display_name":"A","relation":"none"}]""")
        assertEquals(0, r.single().user.avatarStyle)
    }

    @Test fun emptySearchResult() = assertTrue(SocialParsers.searchResults("[]").isEmpty())

    @Test fun parsesFriendshipsWithEmbeddedProfiles() {
        val rows = SocialParsers.friendships("[" + friendshipJson("f1", "accepted", "2026-01-02", ME, BOB) + "," +
            friendshipJson("f2", "pending", "2026-01-03", CAROL, ME) + "]")
        assertEquals(2, rows.size)
        assertTrue(rows[0].accepted); assertFalse(rows[1].accepted)
        assertEquals("bob", rows[0].addressee.username)
        assertEquals("carol", rows[1].requester.id)
    }

    @Test(expected = JSONException::class) fun malformedBodyThrows() { SocialParsers.friendships("""{"not":"an array"}""") }

    @Test(expected = JSONException::class) fun friendshipMissingEmbeddedProfileThrows() {
        SocialParsers.friendships("""[{"id":"f","status":"accepted","created_at":"x"}]""")
    }
}
