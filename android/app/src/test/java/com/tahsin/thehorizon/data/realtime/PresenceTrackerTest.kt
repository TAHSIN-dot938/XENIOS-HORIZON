package com.tahsin.thehorizon.data.realtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceTrackerTest {
    private fun metas(vararg refs: String) =
        """{"metas":[${refs.joinToString(",") { """{"phx_ref":"$it"}""" }}]}"""
    private fun obj(vararg entries: Pair<String, String>) =
        JSONObject("{" + entries.joinToString(",") { "\"${it.first}\":${it.second}" } + "}")
    private fun diff(joins: JSONObject = JSONObject(), leaves: JSONObject = JSONObject()) =
        JSONObject().put("joins", joins).put("leaves", leaves)

    @Test fun stateListsOnlineUsersAndReplacesPreviousState() {
        val t = PresenceTracker()
        t.setState(obj("a" to metas("1"), "b" to metas("2")))
        assertEquals(setOf("a", "b"), t.online())
        t.setState(obj("c" to metas("3")))
        assertEquals(setOf("c"), t.online())
    }

    @Test fun joinsAndLeavesUpdatePresence() {
        val t = PresenceTracker()
        t.setState(obj("a" to metas("1")))
        t.applyDiff(diff(joins = obj("b" to metas("2"))))
        assertEquals(setOf("a", "b"), t.online())
        t.applyDiff(diff(leaves = obj("a" to metas("1"))))
        assertEquals(setOf("b"), t.online())
    }

    @Test fun userWithTwoDevicesStaysOnlineUntilLastSessionLeaves() {
        val t = PresenceTracker()
        t.setState(obj("a" to metas("phone", "tablet")))
        t.applyDiff(diff(leaves = obj("a" to metas("phone"))))
        assertTrue("a" in t.online())
        t.applyDiff(diff(leaves = obj("a" to metas("tablet"))))
        assertFalse("a" in t.online())
    }

    @Test fun leaveForUnknownUserIsIgnored() {
        val t = PresenceTracker()
        t.applyDiff(diff(leaves = obj("ghost" to metas("9"))))
        assertTrue(t.online().isEmpty())
    }

    @Test fun reconnectLeaveAndJoinInOneDiffKeepsUserOnline() {
        val t = PresenceTracker()
        t.setState(obj("a" to metas("old")))
        t.applyDiff(diff(joins = obj("a" to metas("new")), leaves = obj("a" to metas("old"))))
        assertTrue("a" in t.online())
    }

    @Test fun clearForgetsEveryone() {
        val t = PresenceTracker()
        t.setState(obj("a" to metas("1")))
        t.clear()
        assertTrue(t.online().isEmpty())
    }
}
