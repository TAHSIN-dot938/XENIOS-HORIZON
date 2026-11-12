package com.tahsin.thehorizon.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val s = Session("a", "r", 1_000, "u", "e@x.co")

    @Test fun jsonRoundTrip() = assertEquals(s, Session.fromJson(s.toJson()))
    @Test fun garbageJsonIsNull() { assertNull(Session.fromJson("not json")); assertNull(Session.fromJson("{}")) }
    @Test fun expiringUsesSkew() {
        assertFalse(s.isExpiring(nowSec = 900))
        assertTrue(s.isExpiring(nowSec = 940))
        assertTrue(s.isExpiring(nowSec = 2_000))
    }
}
