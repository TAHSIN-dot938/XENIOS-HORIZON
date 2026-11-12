package com.tahsin.thehorizon.data.auth

import org.json.JSONObject

data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Unix seconds when the access token expires. */
    val expiresAtSec: Long,
    val userId: String,
    val email: String,
) {
    /** True when the token is expired or within [skewSec] of expiring. */
    fun isExpiring(nowSec: Long, skewSec: Long = 60): Boolean = nowSec >= expiresAtSec - skewSec

    fun toJson(): String = JSONObject()
        .put("access_token", accessToken)
        .put("refresh_token", refreshToken)
        .put("expires_at", expiresAtSec)
        .put("user_id", userId)
        .put("email", email)
        .toString()

    companion object {
        fun fromJson(raw: String): Session? = try {
            val o = JSONObject(raw)
            Session(
                accessToken = o.getString("access_token"),
                refreshToken = o.getString("refresh_token"),
                expiresAtSec = o.getLong("expires_at"),
                userId = o.getString("user_id"),
                email = o.optString("email"),
            )
        } catch (e: Exception) {
            null
        }
    }
}
