package com.tahsin.thehorizon.data.social

import org.json.JSONArray
import org.json.JSONObject

/** Pure JSON -> model mapping for PostgREST responses (unit-testable without Android). */
object SocialParsers {
    fun user(o: JSONObject) = UserSummary(
        id = o.getString("id"),
        userNumber = o.getString("user_number"),
        username = o.getString("username"),
        displayName = o.getString("display_name"),
        avatarStyle = o.optInt("avatar_style", 0),
    )

    fun relation(raw: String): Relation = when (raw) {
        "self" -> Relation.SELF
        "friend" -> Relation.FRIEND
        "outgoing" -> Relation.OUTGOING
        "incoming" -> Relation.INCOMING
        else -> Relation.NONE
    }

    fun searchResults(body: String): List<SearchResult> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SearchResult(user(o), relation(o.optString("relation")))
        }
    }

    fun friendships(body: String): List<FriendshipRow> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FriendshipRow(
                id = o.getString("id"),
                accepted = o.getString("status") == "accepted",
                requester = user(o.getJSONObject("requester")),
                addressee = user(o.getJSONObject("addressee")),
                createdAt = o.optString("created_at"),
            )
        }
    }
}
