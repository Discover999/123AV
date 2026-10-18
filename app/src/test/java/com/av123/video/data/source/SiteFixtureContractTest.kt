package com.av123.video.data.source

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2.0 基线：真实解析入口 + 外部 HTML fixture。
 * fixture 只保留结构，不包含真实页面完整内容。
 */
class SiteFixtureContractTest {

    private val source = JsoupVideoDataSource(
        JsoupSiteConfig(
            baseUrl = "https://123av.com/",
            localePath = "/cn"
        )
    )

    private fun fixture(name: String): Document {
        val html = checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "缺少 fixture: $name"
        }.bufferedReader().use { it.readText() }
        return Jsoup.parse(html, "https://123av.com/cn/new")
    }

    @Test
    fun homeFixture_parsesFeaturedHotAndNavigation() {
        val feed = source.parseHomeDocument(fixture("home.html"))

        assertEquals(1, feed.featured.size)
        assertEquals("feat-001", feed.featured.first().id)
        assertEquals("精选", feed.featured.first().category)
        assertEquals(1, feed.hot.size)
        assertEquals("hot-001", feed.hot.first().id)
        assertEquals("/cn/hot", feed.hotMorePath)
        assertTrue(feed.navGroups.isNotEmpty())
    }

    @Test
    fun searchFixture_parsesCardsCountAndPagination() {
        val page = source.parseSearchDocument(fixture("search.html"), page = 1)

        assertEquals(1, page.videos.size)
        assertEquals("search-001", page.videos.first().id)
        assertEquals("搜索", page.videos.first().category)
        assertEquals(1234, page.totalCount)
        assertEquals(7, page.pagination?.totalPages)
    }
}
