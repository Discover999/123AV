package com.av123.video.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostAllowlistTest {

    private val allowlist = HostAllowlist(
        baseUrl = "https://123av.com/",
        embedHosts = listOf("sadie-shop.site"),
        mediaHosts = listOf("*.true*.site", "*.realpuremind.site", "cdn.example.com")
    )

    private val dynamicAllowlist = HostAllowlist(
        baseUrl = "https://123av.com/",
        embedHosts = listOf("sadie-shop.site"),
        mediaHosts = listOf("*.true*.site"),
        allowDynamicMediaHosts = true
    )

    @Test
    fun embed_allowsBaseAndConfiguredHostOnly() {
        assertTrue(allowlist.isAllowedEmbed("https://123av.com/cn/v/1"))
        assertTrue(allowlist.isAllowedEmbed("https://sadie-shop.site/embed/1"))
        assertFalse(allowlist.isAllowedEmbed("https://evil.example/x"))
        assertFalse(allowlist.isAllowedEmbed("http://sadie-shop.site/embed/1"))
    }

    @Test
    fun media_wildcardHandlesSubdomainAndScheme() {
        assertTrue(allowlist.isAllowedMedia("https://806l.trueboldmove.site/a.m3u8"))
        assertTrue(allowlist.isAllowedMedia("https://8vpr.realpuremind.site/a.m3u8"))
        assertTrue(allowlist.isAllowedMedia("https://CDN.EXAMPLE.COM/a.m3u8"))
        assertFalse(allowlist.isAllowedMedia("https://trueboldmove.site/a.m3u8"))
        assertFalse(allowlist.isAllowedMedia("http://cdn.example.com/a.m3u8"))
    }

    @Test
    fun navigation_allowsEmbedAndMediaButNotUnknown() {
        assertTrue(allowlist.isAllowedNavigation("https://123av.com/cn"))
        assertTrue(allowlist.isAllowedNavigation("https://806l.trueboldmove.site/a.m3u8"))
        assertFalse(allowlist.isAllowedNavigation("https://evil.example/"))
    }

    @Test
    fun emptyAllowlist_failsClosedForMedia() {
        val empty = HostAllowlist("https://123av.com", emptyList(), emptyList())

        assertTrue(empty.isAllowedEmbed("https://123av.com/cn"))
        assertFalse(empty.isAllowedMedia("https://cdn.example.com/a.m3u8"))
    }

    @Test
    fun dynamicMedia_requiresTrustedPageAndHttps() {
        assertTrue(
            dynamicAllowlist.isAllowedMedia(
                "https://new-cdn.example/video.m3u8",
                "https://sadie-shop.site"
            )
        )
        assertFalse(
            dynamicAllowlist.isAllowedMedia(
                "https://new-cdn.example/video.m3u8",
                "https://evil.example"
            )
        )
        assertFalse(dynamicAllowlist.isAllowedMedia("https://new-cdn.example/video.m3u8"))
        assertFalse(
            dynamicAllowlist.isAllowedMedia(
                "http://new-cdn.example/video.m3u8",
                "https://sadie-shop.site"
            )
        )
    }

    @Test
    fun dynamicMedia_blocksLocalAndPrivateAddresses() {
        val trusted = "https://sadie-shop.site"

        assertFalse(dynamicAllowlist.isAllowedMedia("https://localhost/a.m3u8", trusted))
        assertFalse(dynamicAllowlist.isAllowedMedia("https://127.0.0.1/a.m3u8", trusted))
        assertFalse(dynamicAllowlist.isAllowedMedia("https://192.168.1.10/a.m3u8", trusted))
        assertFalse(dynamicAllowlist.isAllowedMedia("https://169.254.1.1/a.m3u8", trusted))
        assertFalse(dynamicAllowlist.isAllowedMedia("https://[::1]/a.m3u8", trusted))
    }

    @Test
    fun dynamicMedia_doesNotOpenMainFrameNavigation() {
        assertFalse(dynamicAllowlist.isAllowedNavigation("https://new-cdn.example/video.m3u8"))
        assertTrue(dynamicAllowlist.isAllowedNavigation("https://806l.trueboldmove.site/a.m3u8"))
    }
}
