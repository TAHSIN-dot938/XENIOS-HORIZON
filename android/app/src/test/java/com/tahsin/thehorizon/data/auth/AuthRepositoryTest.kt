package com.tahsin.thehorizon.data.auth

import com.tahsin.thehorizon.data.http.HttpResponse
import com.tahsin.thehorizon.data.http.NetworkException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class AuthRepositoryTest {
    private var now = 1_000L
    private val store = MemoryStore()

    private fun repo(http: FakeHttpClient): AuthRepository {
        val api = AuthApi(http, "https://x.supabase.co", "anon") { now }
        return AuthRepository(api, SessionStore(store), { now })
    }

    private fun saved(expiresAt: Long) =
        SessionStore(store).save(Session("old-acc", "old-ref", expiresAt, "user-1", "a@b.co"))

    @Test fun restoreWithNothingSavedIsSignedOut() = runBlocking {
        val http = FakeHttpClient { fail("no network expected"); ok("") }
        val r = repo(http); r.restore()
        assertNull(r.session.value); assertEquals(0, http.requests.size)
    }

    @Test fun restoreValidSessionNeedsNoNetwork() = runBlocking {
        saved(expiresAt = 5_000)
        val http = FakeHttpClient { fail("no network expected"); ok("") }
        val r = repo(http); r.restore()
        assertEquals("old-acc", r.session.value?.accessToken); assertEquals(0, http.requests.size)
    }

    @Test fun restoreExpiredRefreshesAndPersists() = runBlocking {
        saved(expiresAt = 1_010)                       // within 60 s skew => expiring
        val http = FakeHttpClient { ok(sessionJson(access = "new-acc", refresh = "new-ref", expiresAt = 9_000)) }
        val r = repo(http); r.restore()
        assertEquals("new-acc", r.session.value?.accessToken)
        assertEquals("new-ref", SessionStore(store).load()?.refreshToken)
        assertEquals("old-ref", org.json.JSONObject(http.requests.single().body!!).getString("refresh_token"))
    }

    @Test fun restoreOfflineKeepsSavedSession() = runBlocking {
        saved(expiresAt = 900)
        val r = repo(FakeHttpClient { throw NetworkException(RuntimeException("offline")) }); r.restore()
        assertEquals("old-acc", r.session.value?.accessToken); assertNotNull(SessionStore(store).load())
    }

    @Test fun restoreWithRevokedRefreshTokenSignsOut() = runBlocking {
        saved(expiresAt = 900)
        val r = repo(FakeHttpClient { errorJson(400, "refresh_token_not_found", "Invalid Refresh Token") }); r.restore()
        assertNull(r.session.value); assertNull(SessionStore(store).load())
    }

    @Test fun signInPersistsSession() = runBlocking {
        val r = repo(FakeHttpClient { ok(sessionJson(expiresAt = 9_000)) })
        r.signIn(" a@b.co ", "pw")
        assertEquals("acc", r.session.value?.accessToken); assertEquals("acc", SessionStore(store).load()?.accessToken)
    }

    @Test fun signUpNeedingConfirmationDoesNotSignIn() = runBlocking {
        val r = repo(FakeHttpClient { ok("""{"id":"u","email":"a@b.co"}""") })
        assertEquals("a@b.co", r.signUp("a@b.co", "pw12345678", "ava", "ava"))
        assertNull(r.session.value)
    }

    @Test fun validAccessTokenRefreshesOnlyWhenNeeded() = runBlocking {
        var calls = 0
        val http = FakeHttpClient { calls++; ok(sessionJson(access = "fresh", expiresAt = 9_000)) }
        saved(expiresAt = 5_000)
        val r = repo(http); r.restore()
        assertEquals("old-acc", r.validAccessToken()); assertEquals(0, calls)
        now = 4_990                                    // now inside the skew window
        assertEquals("fresh", r.validAccessToken()); assertEquals(1, calls)
        assertEquals("fresh", r.validAccessToken()); assertEquals(1, calls)
    }

    @Test fun validAccessTokenWhenSignedOutThrows() {
        try {
            runBlocking { repo(FakeHttpClient { ok("") }).validAccessToken() }
            fail("expected exception")
        } catch (e: AuthException) { assertEquals(AuthErrorCode.NOT_SIGNED_IN, e.code) }
    }

    @Test fun signOutClearsEvenWhenServerFails() = runBlocking {
        saved(expiresAt = 5_000)
        val r = repo(FakeHttpClient { throw NetworkException(RuntimeException("offline")) }); r.restore()
        r.signOut()
        assertNull(r.session.value); assertNull(SessionStore(store).load())
    }

    @Test fun resetPasswordVerifiesThenUpdatesThenSignsIn() = runBlocking {
        val http = FakeHttpClient { req -> if (req.method == "PUT") ok("{}") else ok(sessionJson(access = "rec-acc", expiresAt = 9_000)) }
        val r = repo(http)
        r.resetPassword("a@b.co", "123456", "brandnew1")
        assertEquals(listOf("POST", "PUT"), http.requests.map { it.method })
        assertEquals("Bearer rec-acc", http.requests[1].headers["Authorization"])
        assertEquals("rec-acc", r.session.value?.accessToken)
    }

    @Test fun resetPasswordFailureDoesNotSignIn() {
        val http = FakeHttpClient { req ->
            if (req.method == "PUT") errorJson(422, "same_password", "same") else ok(sessionJson(expiresAt = 9_000))
        }
        val r = repo(http)
        try { runBlocking { r.resetPassword("a@b.co", "123456", "samepass1") }; fail("expected exception") }
        catch (e: AuthException) { assertEquals(AuthErrorCode.WEAK_PASSWORD, e.code) }
        assertNull(r.session.value); assertNull(SessionStore(store).load())
    }
}
