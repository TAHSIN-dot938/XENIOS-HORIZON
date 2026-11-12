package com.tahsin.thehorizon.data.social

import com.tahsin.thehorizon.data.auth.FakeHttpClient
import com.tahsin.thehorizon.data.auth.ok
import com.tahsin.thehorizon.data.http.ApiException
import com.tahsin.thehorizon.data.http.HttpResponse
import com.tahsin.thehorizon.data.http.NetworkException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SocialRepositoryTest {
    private fun repo(http: FakeHttpClient) = SocialRepository(restFor(http))

    private fun serverError(message: String, code: Int = 400) = HttpResponse(code, """{"code":"P0001","message":"$message"}""")

    private fun expectApiError(expectedMessage: String, expectedCode: Int? = null, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(expectedMessage, e.message)
            if (expectedCode != null) assertEquals(expectedCode, e.code)
        }
    }

    @Test fun shortQueriesNeverHitTheNetwork() = runBlocking {
        val http = FakeHttpClient { fail("no request expected"); ok("[]") }
        assertTrue(repo(http).search("a").isEmpty())
        assertTrue(repo(http).search("  ").isEmpty())
        assertEquals(0, http.requests.size)
    }

    @Test fun searchCallsRpcWithTrimmedQueryAndUserToken() = runBlocking {
        val http = FakeHttpClient { ok("""[{"id":"u","user_number":"00000000000002","username":"bob","display_name":"Bob","avatar_style":1,"relation":"none"}]""") }
        val r = repo(http).search("  bob ")
        assertEquals("bob", r.single().user.username)
        val req = http.requests.single()
        assertEquals("POST", req.method)
        assertEquals("https://x.supabase.co/rest/v1/rpc/search_users", req.url)
        assertEquals("bob", JSONObject(req.body!!).getString("p_query"))
        assertEquals("Bearer user-token", req.headers["Authorization"])      // user JWT, never the anon key
        assertEquals("anon", req.headers["apikey"])
    }

    @Test fun searchByFourteenDigitIdIsSentVerbatim() = runBlocking {
        val http = FakeHttpClient { ok("[]") }
        repo(http).search("00000000000042")
        assertEquals("00000000000042", JSONObject(http.requests.single().body!!).getString("p_query"))
    }

    @Test fun loadQueriesFriendshipsWithEmbedsAndBuildsSnapshot() = runBlocking {
        val body = "[" + friendshipJson("f1", "accepted", "2026-01-01", ME, BOB) + "]"
        val http = FakeHttpClient { ok(body) }
        val snap = repo(http).load("me")
        assertEquals("Bob", snap.friends.single().user.displayName)
        val req = http.requests.single()
        assertEquals("GET", req.method)
        assertTrue(req.url.startsWith("https://x.supabase.co/rest/v1/friendships?select="))
        assertTrue(req.url.contains("friendships_requester_id_fkey") && req.url.contains("friendships_addressee_id_fkey"))
    }

    @Test fun actionsCallTheRightRpcsWithTheRightArguments() = runBlocking {
        val http = FakeHttpClient { ok("null") }
        val r = repo(http)
        r.sendRequest("u-1"); r.respond("req-1", true); r.respond("req-2", false); r.cancel("req-3"); r.removeFriend("u-2")
        assertEquals(
            listOf("send_friend_request", "respond_friend_request", "respond_friend_request", "cancel_friend_request", "remove_friend"),
            http.requests.map { it.url.substringAfterLast('/') },
        )
        assertEquals("u-1", JSONObject(http.requests[0].body!!).getString("p_target"))
        assertEquals(true, JSONObject(http.requests[1].body!!).getBoolean("p_accept"))
        assertEquals(false, JSONObject(http.requests[2].body!!).getBoolean("p_accept"))
        assertEquals("req-3", JSONObject(http.requests[3].body!!).getString("p_id"))
        assertEquals("u-2", JSONObject(http.requests[4].body!!).getString("p_other"))
    }

    @Test fun duplicateRequestShowsFriendlyMessage() = expectApiError("A request between you two already exists.", 400) {
        repo(FakeHttpClient { serverError("request_exists") }).sendRequest("u")
    }

    @Test fun alreadyFriendsAndSpamGuardMessages() {
        assertEquals("You're already friends.", SocialRepository.friendly(400, "already_friends"))
        assertEquals("You have too many unanswered requests. Wait for some replies first.", SocialRepository.friendly(400, "too_many_requests"))
    }

    @Test fun staleRequestShowsFriendlyMessage() = expectApiError("That request is no longer available.") {
        repo(FakeHttpClient { serverError("request_not_found") }).respond("x", true)
    }

    @Test fun rawDatabaseTextIsNeverShown() = expectApiError("Something went wrong. Please try again.", 500) {
        repo(FakeHttpClient { HttpResponse(500, """{"message":"duplicate key value violates unique constraint \"friendships_pair_idx\""}""") }).sendRequest("u")
    }

    @Test fun unauthorizedMapsToSessionExpired() = expectApiError("Your session expired. Please sign in again.", 401) {
        repo(FakeHttpClient { HttpResponse(401, """{"message":"JWT expired"}""") }).load("me")
    }

    @Test fun offlineShowsNetworkMessage() = expectApiError("No connection. Check your internet and try again.", 0) {
        repo(FakeHttpClient { throw NetworkException(RuntimeException("offline")) }).load("me")
    }

    @Test fun garbageSuccessBodyBecomesFriendlyError() = expectApiError("Unexpected response from the server.") {
        repo(FakeHttpClient { ok("<html>oops</html>") }).load("me")
    }
}
