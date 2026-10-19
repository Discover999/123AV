package com.av123.video.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SiteConfigLoaderTest {

    @Test
    fun decode_readsOverridesAndIgnoresUnknownFields() {
        val raw = """
            {
              "schemaVersion": 1,
              "id": "test-site",
              "version": 7,
              "baseUrl": "https://example.org/",
              "localePath": "/cn",
              "cardSelector": ".item",
              "unknownField": "ignored"
            }
        """.trimIndent()

        val config = SiteConfigLoader.decode(raw)

        assertEquals("test-site", config.id)
        assertEquals(7, config.version)
        assertEquals("https://example.org/", config.baseUrl)
        assertEquals(".item", config.cardSelector)
        assertTrue(SiteConfigLoader.validate(config).isEmpty())
    }

    @Test
    fun validate_rejectsNonHttpsBaseUrl() {
        val config = SiteConfigLoader.decode("""{"baseUrl":"http://example.com/"}""")

        assertTrue(
            SiteConfigLoader.validate(config).any { it.contains("HTTPS") }
        )
    }

    @Test
    fun bundledAsset_isValidAndPointsToCurrentSite() {
        val file = File("src/main/assets/site-config.json")
        assertTrue("缺少内置站点配置: ${file.absolutePath}", file.isFile)

        val config = SiteConfigLoader.decode(file.readText())

        assertTrue(SiteConfigLoader.validate(config).isEmpty())
        assertEquals("123av", config.id)
        assertEquals("https://123av.com/", config.baseUrl)
        assertEquals("/cn", config.localePath)
        assertEquals("keyword", config.searchQueryParam)
        assertEquals("/new", config.categoryPaths["新发布"])
        assertTrue(config.mediaHosts.contains("*.realpuremind.site"))
        assertTrue(config.allowDynamicMediaHosts)
    }
}
