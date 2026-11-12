package com.tahsin.thehorizon.data.realtime

import org.json.JSONArray
import org.json.JSONObject

/** One Phoenix channel frame (Supabase Realtime, serializer vsn 1.0.0: JSON objects). */
data class Frame(val topic: String, val event: String, val payload: JSONObject, val ref: String?)

object PhoenixProtocol {
    /** Postgres Changes channel (RLS-filtered per user via their JWT). */
    const val DB_TOPIC = "realtime:horizon-db"
    /** Shared channel: every signed-in client joins it so friends can see each other's presence. */
    const val PRESENCE_TOPIC = "realtime:horizon-presence"
    const val HEARTBEAT_TOPIC = "phoenix"

    fun encode(topic: String, event: String, payload: JSONObject, ref: String): String =
        JSONObject().put("topic", topic).put("event", event).put("payload", payload).put("ref", ref).toString()

    fun decode(text: String): Frame? = try {
        val o = JSONObject(text)
        Frame(
            topic = o.getString("topic"),
            event = o.getString("event"),
            payload = o.optJSONObject("payload") ?: JSONObject(),
            ref = if (o.isNull("ref")) null else o.optString("ref"),
        )
    } catch (e: Exception) {
        null
    }

    /**
     * Join payload: Postgres Changes for [tables] (RLS applies, using the user's JWT) and
     * Presence keyed by [presenceKey] (the user id, so multiple devices collapse to one user).
     */
    fun joinPayload(tables: List<String>, presenceKey: String, accessToken: String): JSONObject {
        val changes = JSONArray()
        tables.forEach {
            changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", it))
        }
        val config = JSONObject()
            .put("broadcast", JSONObject().put("ack", false).put("self", false))
            .put("presence", JSONObject().put("key", presenceKey))
            .put("postgres_changes", changes)
            .put("private", false)
        return JSONObject().put("config", config).put("access_token", accessToken)
    }

    fun accessTokenPayload(token: String): JSONObject = JSONObject().put("access_token", token)

    fun presenceTrackPayload(onlineAtIso: String): JSONObject =
        JSONObject().put("type", "presence").put("event", "track")
            .put("payload", JSONObject().put("online_at", onlineAtIso))
}

/**
 * Folds `presence_state` / `presence_diff` frames into the set of user ids currently online.
 * A user stays online while at least one of their sessions (meta ref) remains.
 */
class PresenceTracker {
    private val refsByKey = mutableMapOf<String, MutableSet<String>>()

    fun online(): Set<String> = refsByKey.filterValues { it.isNotEmpty() }.keys.toSet()

    fun setState(state: JSONObject) {
        refsByKey.clear()
        state.keys().forEach { key -> refsByKey[key] = refsOf(state.optJSONObject(key)) }
    }

    fun applyDiff(diff: JSONObject) {
        diff.optJSONObject("leaves")?.let { leaves ->
            leaves.keys().forEach { key ->
                refsByKey[key]?.removeAll(refsOf(leaves.optJSONObject(key)))
                if (refsByKey[key]?.isEmpty() == true) refsByKey.remove(key)
            }
        }
        diff.optJSONObject("joins")?.let { joins ->
            joins.keys().forEach { key ->
                refsByKey.getOrPut(key) { mutableSetOf() }.addAll(refsOf(joins.optJSONObject(key)))
            }
        }
    }

    fun clear() = refsByKey.clear()

    private fun refsOf(entry: JSONObject?): MutableSet<String> {
        val metas = entry?.optJSONArray("metas") ?: return mutableSetOf()
        return (0 until metas.length()).mapNotNull { metas.optJSONObject(it)?.optString("phx_ref")?.ifEmpty { null } }
            .toMutableSet()
    }
}
