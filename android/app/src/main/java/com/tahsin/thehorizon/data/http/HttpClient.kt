package com.tahsin.thehorizon.data.http

import java.io.IOException

data class HttpResponse(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
}

/** Thrown for any transport failure (offline, timeout, DNS). */
class NetworkException(cause: Throwable) : IOException(cause.message, cause)

/** Thrown when the server answers with a non-2xx status outside the auth flow. */
class ApiException(val code: Int, message: String) : Exception(message)

/** Tiny seam over the HTTP stack so repositories can be unit-tested with a fake. */
interface HttpClient {
    suspend fun send(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): HttpResponse
}
