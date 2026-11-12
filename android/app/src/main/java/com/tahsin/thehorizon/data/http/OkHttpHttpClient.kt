package com.tahsin.thehorizon.data.http

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OkHttpHttpClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build(),
) : HttpClient {

    private val json = "application/json; charset=utf-8".toMediaType()

    override suspend fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse =
        suspendCancellableCoroutine { cont ->
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            val needsBody = method == "POST" || method == "PUT" || method == "PATCH"
            val requestBody = when {
                body != null -> body.toRequestBody(json)
                needsBody -> "".toRequestBody(json)
                else -> null
            }
            builder.method(method, requestBody)
            val call = client.newCall(builder.build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(NetworkException(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val text = try { it.body?.string().orEmpty() } catch (e: IOException) {
                            if (cont.isActive) cont.resumeWithException(NetworkException(e))
                            return
                        }
                        if (cont.isActive) cont.resume(HttpResponse(it.code, text))
                    }
                }
            })
        }
}
