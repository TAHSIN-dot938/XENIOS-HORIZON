package com.tahsin.thehorizon.data.social

import com.tahsin.thehorizon.data.http.ApiException
import com.tahsin.thehorizon.data.rest.SupabaseRest
import org.json.JSONException
import org.json.JSONObject

class SocialRepository(private val rest: SupabaseRest) {

    /** Users by username prefix or exact 14-digit ID. Needs at least 2 characters. */
    suspend fun search(query: String): List<SearchResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return guard { SocialParsers.searchResults(rest.rpc("search_users", JSONObject().put("p_query", q))) }
    }

    suspend fun load(meId: String): SocialSnapshot = guard {
        val body = rest.get("friendships?select=id,status,created_at,$REQUESTER,$ADDRESSEE&order=created_at.desc")
        SocialSnapshot.from(meId, SocialParsers.friendships(body))
    }

    suspend fun sendRequest(targetId: String) {
        guard { rest.rpc("send_friend_request", JSONObject().put("p_target", targetId)) }
    }

    suspend fun respond(requestId: String, accept: Boolean) {
        guard { rest.rpc("respond_friend_request", JSONObject().put("p_id", requestId).put("p_accept", accept)) }
    }

    suspend fun cancel(requestId: String) {
        guard { rest.rpc("cancel_friend_request", JSONObject().put("p_id", requestId)) }
    }

    suspend fun removeFriend(userId: String) {
        guard { rest.rpc("remove_friend", JSONObject().put("p_other", userId)) }
    }

    /** Converts raw server codes into messages that are safe and friendly to show. */
    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: ApiException) {
        throw ApiException(e.code, friendly(e.code, e.message.orEmpty()))
    } catch (e: JSONException) {
        throw ApiException(0, "Unexpected response from the server.")
    }

    companion object {
        private const val USER_FIELDS = "id,user_number,username,display_name,avatar_style"
        private const val REQUESTER = "requester:profiles!friendships_requester_id_fkey($USER_FIELDS)"
        private const val ADDRESSEE = "addressee:profiles!friendships_addressee_id_fkey($USER_FIELDS)"

        fun friendly(code: Int, raw: String): String = when {
            raw == "request_exists" -> "A request between you two already exists."
            raw == "already_friends" -> "You're already friends."
            raw == "too_many_requests" -> "You have too many unanswered requests. Wait for some replies first."
            raw == "cannot_friend_self" -> "You can't add yourself."
            raw == "user_not_found" -> "That user no longer exists."
            raw == "request_not_found" -> "That request is no longer available."
            raw == "not_friends" -> "You're not friends with that user."
            code == 0 && raw.isNotEmpty() -> raw            // already a friendly network message
            code == 401 || raw == "not_authenticated" -> "Your session expired. Please sign in again."
            else -> "Something went wrong. Please try again."
        }
    }
}
