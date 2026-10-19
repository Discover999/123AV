package com.av123.video.ui.screens.detail

import com.av123.video.data.source.HostAllowlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCaptureMessageTest {

    private val allowlist = HostAllowlist(
        baseUrl = "https://123av.com/",
        embedHosts = listOf("sadie-shop.site"),
        mediaHosts = listOf("*.true*.site", "*.realpuremind.site"),
        allowDynamicMediaHosts = true
    )

    @Test
    fun parse_readsStructuredMessage() {
        val message = parseCapturedMediaMessage(
            """
            {
              "url": "https://8vpr.realpuremind.site/video.m3u8",
              "source": "fetch-resp(https://sadie-shop.site/stream?id=1)",
              "requestUrl": "https://sadie-shop.site/stream?id=1",
              "page": "https://sadie-shop.site/e/1"
            }
            """.trimIndent()
        )

        assertEquals("https://8vpr.realpuremind.site/video.m3u8", message?.url)
        assertEquals("fetch-resp(https://sadie-shop.site/stream?id=1)", message?.source)
        assertEquals("https://sadie-shop.site/stream?id=1", message?.requestUrl)
    }

    @Test
    fun parse_keepsLegacyUrlAndRejectsMalformedJson() {
        assertEquals(
            "legacy",
            parseCapturedMediaMessage("https://cdn.example.com/video.m3u8")?.source
        )
        assertNull(parseCapturedMediaMessage("{not-json"))
        assertNull(parseCapturedMediaMessage(""))
    }

    @Test
    fun trustedFetchResponseCanUnlockDynamicMediaHost() {
        val message = CapturedMediaMessage(
            url = "https://new-cdn.example/video.m3u8",
            source = "fetch-resp(https://sadie-shop.site/stream?id=1)",
            requestUrl = "https://sadie-shop.site/stream?id=1"
        )

        assertTrue(isAllowedCapturedMedia(allowlist, message, "https://sadie-shop.site"))
        assertFalse(isAllowedCapturedMedia(allowlist, message, "https://evil.example"))
    }

    @Test
    fun htmlSourceCannotUnlockDynamicMediaHost() {
        val message = CapturedMediaMessage(
            url = "https://new-cdn.example/video.m3u8",
            source = "html",
            requestUrl = "https://sadie-shop.site/e/1"
        )

        assertFalse(isAllowedCapturedMedia(allowlist, message, "https://sadie-shop.site"))
    }

    @Test
    fun responseSourceRequiresTrustedRequestOrigin() {
        val message = CapturedMediaMessage(
            url = "https://new-cdn.example/video.m3u8",
            source = "xhr-resp(https://evil.example/stream)",
            requestUrl = "https://evil.example/stream"
        )

        assertFalse(isAllowedCapturedMedia(allowlist, message, "https://sadie-shop.site"))
    }

    @Test
    fun configuredMediaHostStillWorksFromHtml() {
        val message = CapturedMediaMessage(
            url = "https://806l.trueboldmove.site/video.m3u8",
            source = "html"
        )

        assertTrue(isAllowedCapturedMedia(allowlist, message, null))
    }
}
