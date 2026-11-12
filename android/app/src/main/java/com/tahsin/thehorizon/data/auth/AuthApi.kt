package com.tahsin.thehorizon.data.auth

import com.tahsin.thehorizon.data.http.HttpClient
import com.tahsin.thehorizon.data.http.HttpResponse
import org.json.JSONObject
import java.io.IOException

sealed interface SignUpResult {
    data class SignedIn(val session: Session) : SignUpResult
    /** Email confirmation is enabled on the project: the user must enter the emailed code. */
    data class ConfirmationRequired(val email: String) : SignUpResult
}

/** Thin client for Supabase Auth (GoTrue). Holds no state; the anon key is public by design. */
class AuthApi(
    private val http: HttpClient,
    baseUrl: String,
    private val anonKey: String,
    private val clock: () -> Long,
) {
    private val authUrl = "${baseUrl.trimEnd('/')}/auth/v1"
    private val restUrl = "${baseUrl.trimEnd('/')}/rest/v1"

    private fun headers(bearer: String? = null) = mapOf(
        "apikey" to anonKey,
        "Authorization" to "Bearer ${bearer ?: anonKey}",
        "Content-Type" to "application/json",
    )

    private suspend fun call(method: String, url: String, bearer: String? = null, body: JSONObject? = null): HttpResponse {
        val res = try {
            http.send(method, url, headers(bearer), body?.toString())
        } catch (e: IOException) {
            throw AuthException(AuthErrorCode.NETWORK, NETWORK_MESSAGE)
        }
        if (!res.isSuccess) throw mapError(res)
        return res
    }

    suspend fun signIn(email: String, password: String): Session {
        val res = call("POST", "$authUrl/token?grant_type=password", body = JSONObject().put("email", email).put("password", password))
        return parseSession(res.body)
    }

    suspend fun signUp(email: String, password: String, username: String, displayName: String): SignUpResult {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("data", JSONObject().put("username", username).put("display_name", displayName))
        val res = call("POST", "$authUrl/signup", body = body)
        val json = JSONObject(res.body)
        return if (json.has("access_token")) SignUpResult.SignedIn(parseSession(res.body)) else SignUpResult.ConfirmationRequired(email)
    }

    /** [type] is "signup" or "recovery". Any failure except network/rate-limit is reported as an invalid code. */
    suspend fun verifyOtp(email: String, token: String, type: String): Session {
        val res = try {
            call("POST", "$authUrl/verify", body = JSONObject().put("type", type).put("email", email).put("token", token))
        } catch (e: AuthException) {
            if (e.code == AuthErrorCode.NETWORK || e.code == AuthErrorCode.RATE_LIMITED) throw e
            throw AuthException(AuthErrorCode.INVALID_CODE, "That code is invalid or has expired.")
        }
        return parseSession(res.body)
    }

    suspend fun refresh(refreshToken: String): Session {
        val res = call("POST", "$authUrl/token?grant_type=refresh_token", body = JSONObject().put("refresh_token", refreshToken))
        return parseSession(res.body)
    }

    /** Always succeeds from the caller's view (the server does not reveal whether the email exists). */
    suspend fun recover(email: String) {
        call("POST", "$authUrl/recover", body = JSONObject().put("email", email))
    }

    suspend fun updatePassword(accessToken: String, newPassword: String) {
        call("PUT", "$authUrl/user", bearer = accessToken, body = JSONObject().put("password", newPassword))
    }

    suspend fun signOut(accessToken: String) {
        call("POST", "$authUrl/logout", bearer = accessToken)
    }

    suspend fun usernameAvailable(username: String): Boolean {
        val res = call("POST", "$restUrl/rpc/username_available", body = JSONObject().put("p_username", username))
        return res.body.trim() == "true"
    }

    private fun parseSession(body: String): Session {
        try {
            val o = JSONObject(body)
            val user = o.getJSONObject("user")
            val expiresAt = if (o.has("expires_at")) o.getLong("expires_at") else clock() + o.optLong("expires_in", 3600)
            return Session(
                accessToken = o.getString("access_token"),
                refreshToken = o.getString("refresh_token"),
                expiresAtSec = expiresAt,
                userId = user.getString("id"),
                email = user.optString("email"),
            )
        } catch (e: org.json.JSONException) {
            throw AuthException(AuthErrorCode.UNKNOWN, "Unexpected response from the server.")
        }
    }

    companion object {
        const val NETWORK_MESSAGE = "No connection. Check your internet and try again."

        fun mapError(res: HttpResponse): AuthException {
            val json = try { JSONObject(res.body) } catch (e: Exception) { JSONObject() }
            val errorCode = json.optString("error_code")
            val msg = json.optString("msg").ifEmpty { json.optString("error_description") }.ifEmpty { json.optString("message") }
            return when {
                errorCode == "invalid_credentials" || msg.contains("Invalid login credentials", ignoreCase = true) ->
                    AuthException(AuthErrorCode.INVALID_CREDENTIALS, "Incorrect email or password.")
                errorCode == "email_not_confirmed" ->
                    AuthException(AuthErrorCode.EMAIL_NOT_CONFIRMED, "Confirm your email first, then sign in.")
                errorCode == "user_already_exists" || errorCode == "email_exists" || msg.contains("already registered", ignoreCase = true) ->
                    AuthException(AuthErrorCode.USER_EXISTS, "An account with this email already exists.")
                errorCode == "weak_password" ->
                    AuthException(AuthErrorCode.WEAK_PASSWORD, "That password is too easy to guess. Try a longer or less common one.")
                errorCode == "same_password" ->
                    AuthException(AuthErrorCode.WEAK_PASSWORD, "Choose a password different from your old one.")
                res.code == 429 || errorCode.contains("rate_limit") ->
                    AuthException(AuthErrorCode.RATE_LIMITED, "Too many attempts. Please wait a few minutes and try again.")
                msg.contains("Database error saving new user", ignoreCase = true) ->
                    AuthException(AuthErrorCode.USERNAME_TAKEN, "That username is taken. Try another one.")
                else -> AuthException(AuthErrorCode.UNKNOWN, "Something went wrong (${res.code}). Please try again.")
            }
        }
    }
}
