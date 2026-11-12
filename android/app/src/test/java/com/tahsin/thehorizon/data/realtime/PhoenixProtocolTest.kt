package com.tahsin.thehorizon.data.realtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoenixProtocolTest {
    @Test fun encodeDecodeRoundTrip() {
        val text = PhoenixProtocol.encode("realtime:x", "phx_join", JSONObject().put("a", 1), "7")
        val f = PhoenixProtocol.decode(text)!!
        assertEquals("realtime:x", f.topic); assertEquals("phx_join", f.event)
        assertEquals(1, f.payload.getInt("a")); assertEquals("7", f.ref)
    }

    @Test fun decodeToleratesNullRefAndMissingPayload() {
        assertNull(PhoenixProtocol.decode("""{"topic":"t","event":"e","payload":{},"ref":null}""")!!.ref)
        assertEquals(0, PhoenixProtocol.decode("""{"topic":"t","event":"e"}""")!!.payload.length())
    }

    @Test fun decodeRejectsJunk() {
        assertNull(PhoenixProtocol.decode("not json"))
        assertNull(PhoenixProtocol.decode("""{"event":"e"}"""))
    }

    @Test fun dbJoinPayloadSubscribesToTablesWithJwtAndPresenceKey() {
        val p = PhoenixProtocol.joinPayload(listOf("friendships"), "user-1", "jwt")
        assertEquals("jwt", p.getString("access_token"))
        val cfg = p.getJSONObject("config")
        val change = cfg.getJSONArray("postgres_changes").getJSONObject(0)
        assertEquals("*", change.getString("event")); assertEquals("public", change.getString("schema")); assertEquals("friendships", change.getString("table"))
        assertEquals("user-1", cfg.getJSONObject("presence").getString("key"))
        assertFalse(cfg.getBoolean("private"))
    }

    @Test fun presenceJoinHasNoTableSubscriptions() =
        assertEquals(0, PhoenixProtocol.joinPayload(emptyList(), "u", "t").getJSONObject("config").getJSONArray("postgres_changes").length())

    @Test fun channelsUseDistinctRealtimeTopics() {
        assertNotEquals(PhoenixProtocol.DB_TOPIC, PhoenixProtocol.PRESENCE_TOPIC)
        assertTrue(PhoenixProtocol.DB_TOPIC.startsWith("realtime:") && PhoenixProtocol.PRESENCE_TOPIC.startsWith("realtime:"))
    }

    @Test fun trackAndTokenPayloadShapes() {
        val t = PhoenixProtocol.presenceTrackPayload("2026-01-01T00:00:00Z")
        assertEquals("presence", t.getString("type")); assertEquals("track", t.getString("event"))
        assertEquals("2026-01-01T00:00:00Z", t.getJSONObject("payload").getString("online_at"))
        assertEquals("tok", PhoenixProtocol.accessTokenPayload("tok").getString("access_token"))
    }
}
