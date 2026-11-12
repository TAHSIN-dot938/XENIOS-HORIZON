package com.tahsin.thehorizon.data.auth

import com.tahsin.thehorizon.data.http.HttpResponse
import com.tahsin.thehorizon.data.http.NetworkException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthApiTest {
    private fun api(http: FakeHttpClient) = AuthApi(http, "https://x.supabase.co/", "anon-key") { 1_000L }

    private fun expectError(code: AuthErrorCode, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected AuthException($code)")
        } catch (e: AuthException) {
            assertEquals(code, e.code)
        }
    }

    @Test fun signInParsesSessionAndSendsKeys() = runBlocking {
        val http = FakeHttpClient { ok(sessionJson(expiresAt = 5_000)) }
        val s = api(http).signIn("a@b.co", "pw12345678")
        assertEquals("acc", s.accessToken); assertEquals("ref", s.refreshToken)
        assertEquals(5_000L, s.expiresAtSec); assertEquals("user-1", s.userId)
        val req = http.requests.single()
        assertEquals("https://x.supabase.co/auth/v1/token?grant_type=password", req.url)
        assertEquals("anon-key", req.headers["apikey"])
        assertEquals("Bearer anon-key", req.headers["Authorization"])
        assertEquals("a@b.co", JSONObject(req.body!!).getString("email"))
    }

    @Test fun expiresAtFallsBackToClockPlusExpiresIn() = runBlocking {
        val body = """{"access_token":"a","refresh_token":"r","expires_in":100,"user":{"id":"u"}}"""
        val s = api(FakeHttpClient { ok(body) }).signIn("a@b.co", "x")
        assertEquals(1_100L, s.expiresAtSec)
    }

    @Test fun wrongPasswordMapsToInvalidCredentials() = expectError(AuthErrorCode.INVALID_CREDENTIALS) {
        api(FakeHttpClient { errorJson(400, "invalid_credentials", "Invalid login credentials") }).signIn("a@b.co", "bad")
    }

    @Test fun unconfirmedEmailMapsCorrectly() = expectError(AuthErrorCode.EMAIL_NOT_CONFIRMED) {
        api(FakeHttpClient { errorJson(400, "email_not_confirmed", "Email not confirmed") }).signIn("a@b.co", "x")
    }

    @Test fun rateLimitMapsFrom429() = expectError(AuthErrorCode.RATE_LIMITED) {
        api(FakeHttpClient { HttpResponse(429, """{"msg":"slow down"}""") }).signIn("a@b.co", "x")
    }

    @Test fun offlineMapsToNetworkError() = expectError(AuthErrorCode.NETWORK) {
        api(FakeHttpClient { throw NetworkException(RuntimeException("offline")) }).signIn("a@b.co", "x")
    }

    @Test fun signUpWithSessionIsSignedIn() = runBlocking {
        val http = FakeHttpClient { ok(sessionJson()) }
        val r = api(http).signUp("a@b.co", "pw12345678", "ava_1", "ava_1")
        assertTrue(r is SignUpResult.SignedIn)
        val data = JSONObject(http.requests.single().body!!).getJSONObject("data")
        assertEquals("ava_1", data.getString("username")); assertEquals("ava_1", data.getString("display_name"))
    }

    @Test fun signUpWithoutSessionRequiresConfirmation() = runBlocking {
        val r = api(FakeHttpClient { ok("""{"id":"u","email":"a@b.co","confirmation_sent_at":"now"}""") }).signUp("a@b.co", "pw12345678", "ava_1", "ava_1")
        assertEquals(SignUpResult.ConfirmationRequired("a@b.co"), r)
    }

    @Test fun signUpDuplicateEmail() = expectError(AuthErrorCode.USER_EXISTS) {
        api(FakeHttpClient { errorJson(422, "user_already_exists", "User already registered") }).signUp("a@b.co", "pw12345678", "ava_1", "ava_1")
    }

    @Test fun signUpTriggerFailureMapsToUsernameTaken() = expectError(AuthErrorCode.USERNAME_TAKEN) {
        api(FakeHttpClient { errorJson(500, "unexpected_failure", "Database error saving new user") }).signUp("a@b.co", "pw12345678", "ava_1", "ava_1")
    }

    @Test fun badOtpMapsToInvalidCode() = expectError(AuthErrorCode.INVALID_CODE) {
        api(FakeHttpClient { errorJson(403, "otp_expired", "Token has expired or is invalid") }).verifyOtp("a@b.co", "123456", "recovery")
    }

    @Test fun otpOfflineStaysNetworkError() = expectError(AuthErrorCode.NETWORK) {
        api(FakeHttpClient { throw NetworkException(RuntimeException("offline")) }).verifyOtp("a@b.co", "123456", "signup")
    }

    @Test fun updatePasswordUsesUserTokenNotAnonKey() = runBlocking {
        val http = FakeHttpClient { ok("{}") }
        api(http).updatePassword("user-token", "newpass123")
        val req = http.requests.single()
        assertEquals("PUT", req.method); assertEquals("Bearer user-token", req.headers["Authorization"])
        assertEquals("anon-key", req.headers["apikey"])
    }

    @Test fun usernameAvailableParsesBoolean() = runBlocking {
        assertTrue(api(FakeHttpClient { ok("true") }).usernameAvailable("free_name"))
        assertFalse(api(FakeHttpClient { ok("false") }).usernameAvailable("taken"))
    }
}
