package com.av123.video.data.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpClientRetryTest {

    @Test
    fun retryableStatus_only429AndServerErrors() {
        assertTrue(HttpClient.isRetryableStatus(429))
        assertTrue(HttpClient.isRetryableStatus(500))
        assertTrue(HttpClient.isRetryableStatus(503))
        assertTrue(HttpClient.isRetryableStatus(599))

        assertFalse(HttpClient.isRetryableStatus(400))
        assertFalse(HttpClient.isRetryableStatus(401))
        assertFalse(HttpClient.isRetryableStatus(403))
        assertFalse(HttpClient.isRetryableStatus(404))
        assertFalse(HttpClient.isRetryableStatus(600))
    }

    @Test
    fun retryDelay_growsExponentiallyWithinJitterBounds() {
        repeat(100) {
            assertTrue(HttpClient.retryDelayMs(0) in 400L..600L)
            assertTrue(HttpClient.retryDelayMs(1) in 800L..1200L)
        }
    }
}
