package com.tahsin.thehorizon.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {
    @Test fun growsExponentiallyWithoutJitter() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), (0..4).map { Backoff.delayMs(it, 0.0) })
    }

    @Test fun isCappedAtThirtySeconds() {
        assertEquals(Backoff.MAX_MS, Backoff.delayMs(5, 0.0))
        assertEquals(Backoff.MAX_MS, Backoff.delayMs(500, 0.0))
    }

    @Test fun jitterAddsAtMostQuarter() {
        assertEquals(1_250L, Backoff.delayMs(0, 1.0))
        assertTrue(Backoff.delayMs(3, 0.5) in 8_000L..10_000L)
        assertEquals(Backoff.delayMs(2, 1.0), Backoff.delayMs(2, 99.0))      // jitter is clamped
    }

    @Test fun negativeAttemptBehavesLikeFirst() = assertEquals(1_000L, Backoff.delayMs(-3, 0.0))
}
