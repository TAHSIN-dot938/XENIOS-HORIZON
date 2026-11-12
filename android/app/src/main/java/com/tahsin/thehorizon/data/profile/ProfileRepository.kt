package com.tahsin.thehorizon.data.profile

import com.tahsin.thehorizon.data.auth.AuthApi
import com.tahsin.thehorizon.data.auth.AuthException
import com.tahsin.thehorizon.data.auth.AuthRepository
import com.tahsin.thehorizon.data.http.ApiException
import com.tahsin.thehorizon.data.http.HttpClient
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException

class ProfileRepository(
    private val http: HttpClient,
    baseUrl: String,
    private val anonKey: String,
    private val auth: AuthRepository,
) {
    private val restUrl = "${baseUrl.trimEnd('/')}/rest/v1"

    /** Loads the caller's own profile (row created by the signup trigger). Throws [ApiException]/[AuthException]. */
    suspend fun loadOwn(userId: String): Profile {
        val token = auth.validAccessToken()
        val headers = mapOf("apikey" to anonKey, "Authorization" to "Bearer $token")
        val url = "$restUrl/profiles?id=eq.$userId&select=id,user_number,username,display_name,bio,avatar_style"
        val res = try {
            http.send("GET", url, headers)
        } catch (e: IOException) {
            throw ApiException(0, AuthApi.NETWORK_MESSAGE)
        }
        if (!res.isSuccess) throw ApiException(res.code, "Could not load your profile (${res.code}).")
        try {
            val arr = JSONArray(res.body)
            if (arr.length() == 0) throw ApiException(404, "Your profile was not found.")
            val o = arr.getJSONObject(0)
            return Profile(
                id = o.getString("id"),
                userNumber = o.getString("user_number"),
                username = o.getString("username"),
                displayName = o.getString("display_name"),
                bio = o.optString("bio"),
                avatarStyle = o.optInt("avatar_style", 0),
            )
        } catch (e: JSONException) {
            throw ApiException(res.code, "Unexpected response while loading your profile.")
        }
    }
}
