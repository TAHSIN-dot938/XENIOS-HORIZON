package com.tahsin.thehorizon.data.realtime

/** Reconnect delay: 1s, 2s, 4s ... capped at 30s, plus up to 25% jitter so clients don't stampede. */
object Backoff {
    const val BASE_MS = 1_000L
    const val MAX_MS = 30_000L

    fun delayMs(attempt: Int, jitter: Double): Long {
        val exp = minOf(MAX_MS, BASE_MS shl attempt.coerceIn(0, 5))
        return exp + (exp * 0.25 * jitter.coerceIn(0.0, 1.0)).toLong()
    }
}
