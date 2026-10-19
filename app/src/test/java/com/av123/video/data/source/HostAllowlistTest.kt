package com.av123.video.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostAllowlistTest {

    private val allowlist = HostAllowlist(
        baseUrl = "https://123av.com/",
        embedHosts = listOf("sadie-shop.site"),
        mediaHosts = listOf("*.true*.site", "cdn.example.com")
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
}
