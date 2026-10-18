package com.av123.video.data.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SiteConfigStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val bundled = JsoupSiteConfig(
        id = "123av",
        version = 1,
        baseUrl = "https://bundled.example/"
    )

    private val cacheFile: File
        get() = File(temporaryFolder.root, "site-config-cache.json")

    private fun newStore(config: JsoupSiteConfig = bundled): SiteConfigStore =
        SiteConfigStore(bundled = config, cacheFile = cacheFile)

    private fun configJson(
        version: Int,
        baseUrl: String = "https://cached.example/"
    ) = """{"schemaVersion":1,"id":"123av","version":$version,"baseUrl":"$baseUrl"}"""

    @Test
    fun withoutCache_usesBundledConfig() {
        val store = newStore()

        assertEquals(SiteConfigSource.BUNDLED, store.state.value.source)
        assertEquals(1, store.current.version)
    }

    @Test
    fun newerCacheWinsAndPersistsAcrossStoreRecreation() {
        val store = newStore()
        assertTrue(store.accept(configJson(2)).isSuccess)

        assertEquals(SiteConfigSource.CACHE, store.state.value.source)
        assertEquals(2, store.current.version)

        val reloaded = newStore()
        assertEquals(SiteConfigSource.CACHE, reloaded.state.value.source)
        assertEquals(2, reloaded.current.version)
        assertEquals("https://cached.example/", reloaded.current.baseUrl)
    }

    @Test
    fun brokenCache_fallsBackToBundledAndIsDeleted() {
        cacheFile.writeText("{not-json")

        val store = newStore()

        assertEquals(SiteConfigSource.BUNDLED, store.state.value.source)
        assertNotNull(store.state.value.lastError)
        assertFalse(cacheFile.exists())
    }

    @Test
    fun bundledNewerThanCache_dropsStaleCache() {
        newStore().accept(configJson(2))
        val newerBundled = bundled.copy(version = 3, baseUrl = "https://newer.example/")

        val store = newStore(newerBundled)

        assertEquals(SiteConfigSource.BUNDLED, store.state.value.source)
        assertEquals(3, store.current.version)
        assertFalse(cacheFile.exists())
    }

    @Test
    fun accept_rejectsLowerVersion() {
        val store = newStore()
        store.accept(configJson(2))

        val result = store.accept(configJson(1))

        assertTrue(result.isFailure)
        assertEquals(2, store.current.version)
    }

    @Test
    fun accept_rejectsNewerSchema() {
        val store = newStore()

        val result = store.accept(
            """{"schemaVersion":2,"id":"123av","version":99,"baseUrl":"https://future.example/"}"""
        )

        assertTrue(result.isFailure)
        assertEquals(SiteConfigSource.BUNDLED, store.state.value.source)
    }

    @Test
    fun clearCache_restoresBundledConfig() {
        val store = newStore()
        store.accept(configJson(2))

        store.clearCache()

        assertEquals(SiteConfigSource.BUNDLED, store.state.value.source)
        assertEquals(1, store.current.version)
        assertFalse(cacheFile.exists())
    }

    @Test
    fun refresh_acceptsNewerConfigAndStoresEtag() = runBlocking {
        var requestedEtag: String? = "not-called"
        val store = SiteConfigStore(bundled, cacheFile) { _, etag ->
            requestedEtag = etag
            SiteConfigHttpResult(
                code = 200,
                body = configJson(2, "https://remote.example/"),
                etag = "\"v2\""
            )
        }

        val result = store.refreshFromNetwork("https://config.example/site.json")

        assertTrue(result is SiteConfigRefreshResult.Updated)
        assertNull(requestedEtag)
        assertEquals(2, store.current.version)
        assertEquals("\"v2\"", store.state.value.etag)
        assertEquals(SiteConfigSource.CACHE, store.state.value.source)
    }

    @Test
    fun refresh_304_marksCheckedAndKeepsConfig() = runBlocking {
        newStore().accept(configJson(2), etag = "\"v2\"")
        var requestedEtag: String? = null
        val store = SiteConfigStore(bundled, cacheFile) { _, etag ->
            requestedEtag = etag
            SiteConfigHttpResult(code = 304, body = "")
        }

        val result = store.refreshFromNetwork(
            url = "https://config.example/site.json",
            force = true
        )

        assertTrue(result is SiteConfigRefreshResult.NotModified)
        assertEquals("\"v2\"", requestedEtag)
        assertEquals(2, store.current.version)
        assertNotNull(store.state.value.lastCheckedAt)
    }

    @Test
    fun refresh_skipsWithinTtl() = runBlocking {
        var called = false
        val store = SiteConfigStore(bundled, cacheFile) { _, _ ->
            called = true
            SiteConfigHttpResult(200, configJson(3))
        }
        store.accept(configJson(2))

        val result = store.refreshFromNetwork("https://config.example/site.json")

        assertTrue(result is SiteConfigRefreshResult.Skipped)
        assertFalse(called)
    }

    @Test
    fun refresh_skipsWhenKillSwitchDisabled() = runBlocking {
        var called = false
        val store = SiteConfigStore(
            bundled.copy(remoteEnabled = false),
            cacheFile
        ) { _, _ ->
            called = true
            SiteConfigHttpResult(200, configJson(2))
        }

        val result = store.refreshFromNetwork("https://config.example/site.json")

        assertTrue(result is SiteConfigRefreshResult.Skipped)
        assertFalse(called)
    }

    @Test
    fun refresh_skipsNonHttpsUrl() = runBlocking {
        var called = false
        val store = SiteConfigStore(bundled, cacheFile) { _, _ ->
            called = true
            SiteConfigHttpResult(200, configJson(2))
        }

        val result = store.refreshFromNetwork(
            url = "http://config.example/site.json",
            force = true
        )

        assertTrue(result is SiteConfigRefreshResult.Skipped)
        assertFalse(called)
    }

    @Test
    fun refresh_failureSetsLastError() = runBlocking {
        val store = SiteConfigStore(bundled, cacheFile) { _, _ ->
            throw IllegalStateException("offline")
        }

        val result = store.refreshFromNetwork("https://config.example/site.json")

        assertTrue(result is SiteConfigRefreshResult.Failed)
        assertNotNull(store.state.value.lastError)
        assertNotNull(store.state.value.lastCheckedAt)
        assertEquals(1, store.current.version)
    }
}
