package com.tahsin.thehorizon.data.auth

import com.tahsin.thehorizon.data.http.HttpClient
import com.tahsin.thehorizon.data.http.HttpResponse
import com.tahsin.thehorizon.data.security.KeyValueStore

data class RecordedRequest(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

class FakeHttpClient(private val handler: (RecordedRequest) -> HttpResponse) : HttpClient {
    val requests = mutableListOf<RecordedRequest>()
    override suspend fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse {
        val req = RecordedRequest(method, url, headers, body)
        requests += req
        return handler(req)
    }
}

class MemoryStore : KeyValueStore {
    val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

fun ok(body: String) = HttpResponse(200, body)

fun sessionJson(access: String = "acc", refresh: String = "ref", expiresAt: Long = 10_000, userId: String = "user-1", email: String = "a@b.co") =
    """{"access_token":"$access","token_type":"bearer","expires_in":3600,"expires_at":$expiresAt,"refresh_token":"$refresh","user":{"id":"$userId","email":"$email"}}"""

fun errorJson(code: Int, errorCode: String, msg: String) = HttpResponse(code, """{"code":$code,"error_code":"$errorCode","msg":"$msg"}""")
