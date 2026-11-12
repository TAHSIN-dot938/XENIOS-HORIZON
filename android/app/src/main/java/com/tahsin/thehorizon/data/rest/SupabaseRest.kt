package com.tahsin.thehorizon.data.rest

import com.tahsin.thehorizon.data.auth.AuthApi
import com.tahsin.thehorizon.data.auth.AuthRepository
import com.tahsin.thehorizon.data.http.ApiException
import com.tahsin.thehorizon.data.http.HttpClient
import org.json.JSONObject
import java.io.IOException

/**
 * Authenticated PostgREST access. Every call carries the user's JWT, so Row Level Security in
 * the database decides what the user may see or do. Non-2xx responses become [ApiException]
 * whose message is the server's `message` (e.g. the code raised by an RPC such as "request_exists").
 */
class SupabaseRest(
    private val http: HttpClient,
    baseUrl: String,
    private val anonKey: String,
    private val auth: AuthRepository,
) {
    private val restUrl = "${baseUrl.trimEnd('/')}/rest/v1"

    suspend fun get(path: String): String = request("GET", path, null)

    suspend fun rpc(function: String, args: JSONObject = JSONObject()): String =
        request("POST", "rpc/$function", args.toString())

    private suspend fun request(method: String, path: String, body: String?): String {
        val token = auth.validAccessToken()
        val headers = mapOf(
            "apikey" to anonKey,
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
        )
        val res = try {
            http.send(method, "$restUrl/$path", headers, body)
        } catch (e: IOException) {
            throw ApiException(0, AuthApi.NETWORK_MESSAGE)
        }
        if (!res.isSuccess) throw ApiException(res.code, serverMessage(res.body))
        return res.body
    }

    companion object {
        fun serverMessage(body: String): String = try {
            JSONObject(body).optString("message")
        } catch (e: Exception) {
            ""
        }
    }
}
