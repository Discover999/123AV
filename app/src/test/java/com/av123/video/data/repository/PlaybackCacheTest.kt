package com.av123.video.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackCacheTest {

    @Test
    fun episodeKeysAreSeparatedAndInvalidateIsScoped() {
        val cache = PlaybackCache()
        val first = PlaybackSource("https://cdn/first.m3u8", mapOf("Referer" to "a"))
        val second = PlaybackSource("https://cdn/second.m3u8")

        cache.put("v1", 0, first)
        cache.put("v1", 1, second)

        assertEquals(first, cache.get("v1"))
        assertEquals(first, cache.get("v1", 0))
        assertEquals(second, cache.get("v1", 1))

        cache.invalidate("v1", 0)

        assertNull(cache.get("v1"))
        assertEquals(second, cache.get("v1", 1))

        cache.clear()
        assertNull(cache.get("v1", 1))
    }
}
