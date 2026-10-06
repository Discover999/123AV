package com.av123.video.data.source

import com.av123.video.data.model.Video
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jsoup 网页解析器单元测试：用内置 HTML 片段直接喂给 internal 解析入口，
 * 不发起任何网络请求（Jsoup 为纯 JVM 实现）。
 */
class JsoupParserTest {

    private val source = JsoupVideoDataSource(
        JsoupSiteConfig(
            baseUrl = "https://123av.com/",
            localePath = "/cn"
        )
    )

    private fun parse(html: String) =
        Jsoup.parse(html, "https://123av.com/cn/new")

    // ---------- 卡片 / 列表页 ----------

    @Test
    fun parseCategoryDocument_parsesCardFieldsAndAbsCoverUrl() {
        val html = """
            <div class="card">
              <a class="card__cover" href="/cn/v/siro-5733">
                <img class="card__img" src="https://cdn.example.com/covers/siro.jpg">
              </a>
              <div class="card__title"><a href="/cn/v/siro-5733">SIRO-5733 — 测试影片标题</a></div>
              <span class="card__dur">32:10</span>
              <span class="card__views">5.8K</span>
              <div class="card__meta"><span>5 分钟前</span></div>
            </div>
        """.trimIndent()

        val page = source.parseCategoryDocument(parse(html), category = "分类A", page = 1)

        assertEquals(1, page.videos.size)
        val video = page.videos.first()
        assertEquals("siro-5733", video.id)
        assertEquals("SIRO-5733", video.code)
        assertEquals("SIRO-5733 — 测试影片标题", video.title)
        assertEquals("分类A", video.category)
        // 相对 / 协议相对地址必须补成绝对地址
        assertEquals("https://cdn.example.com/covers/siro.jpg", video.coverUrl)
        assertEquals("32:10", video.durationText)
        assertEquals(5800L, video.views)
        assertEquals("/cn/v/siro-5733", video.detailPath)
        assertEquals("https://123av.com/cn/v/siro-5733", video.sourcePageUrl)
    }

    @Test
    fun parseCategoryDocument_parsesPaginationFromLastLink() {
        val html = """
            <div class="card">
              <a class="card__cover" href="/cn/v/a-1"><div class="card__title"><a>A-1</a></div></a>
            </div>
            <div class="pager">
              <div class="pager__pages">
                <a href="/cn/new?page=1">1</a>
                <a class="is-on" href="/cn/new?page=2">2</a>
                <a rel="last" href="/cn/new?page=88">88</a>
              </div>
            </div>
        """.trimIndent()

        val page = source.parseCategoryDocument(parse(html), category = "新发布", page = 2)

        assertEquals(2, page.pagination?.currentPage)
        assertEquals(88, page.pagination?.totalPages)
    }

    @Test
    fun parseCategoryDocument_withoutPagerReturnsNullPagination() {
        val html = """
            <div class="card">
              <a class="card__cover" href="/cn/v/a-1"><div class="card__title"><a>A-1</a></div></a>
            </div>
        """.trimIndent()

        val page = source.parseCategoryDocument(parse(html), category = "x", page = 1)
        assertNull(page.pagination)
    }

    // ---------- 首页 ----------

    @Test
    fun parseHomeDocument_parsesFeaturedSectionsAndHotMorePath() {
        val html = """
            <nav class="nav">
              <div class="nav__item"><a class="nav__link" href="/cn/new">新发布</a></div>
            </nav>
            <div class="featured">
              <a class="featured__link" href="/cn/v/feat-001">
                <img class="featured__img" src="/uploads/feat.jpg">
                <span class="featured__title">FEAT-001 — 精选大片</span>
                <span class="featured__dur">12:34</span>
              </a>
            </div>
            <section class="section">
              <h2 class="section__title">热门视频</h2>
              <a class="section__all" href="/cn/hot">查看全部</a>
              <div class="card">
                <a class="card__cover" href="/cn/v/hot-001">
                  <img class="card__img" src="/uploads/hot.jpg">
                </a>
                <div class="card__title"><a href="/cn/v/hot-001">HOT-001 — 热门影片</a></div>
                <span class="card__views">1.5K</span>
              </div>
            </section>
        """.trimIndent()

        val feed = source.parseHomeDocument(parse(html))

        assertEquals(1, feed.featured.size)
        assertEquals("feat-001", feed.featured.first().id)
        assertEquals("精选", feed.featured.first().category)
        assertEquals(1, feed.hot.size)
        assertEquals("hot-001", feed.hot.first().id)
        assertEquals("/cn/hot", feed.hotMorePath)
        assertTrue(feed.navGroups.isNotEmpty())
    }

    // ---------- 播放器剧集配置（转义还原，最易回归） ----------

    @Test
    fun parsePlayerData_unescapesJsonSlashesAndAmpAndExtractsPoster() {
        // 注意：三引号字符串中 \u0022 / \/ / \u0026 均为字面文本（与网页源码一致）
        val html = """
            <div x-data="player(JSON.parse('[{\u0022url\u0022:\u0022https:\/\/123av.com\/e\/play?id=1\u0026poster=https:\/\/123av.com\/img\/p.jpg\u0022,\u0022name\u0022:\u0022第1集\u0022},{\u0022url\u0022:\u0022\/\/123av.com\/e\/2\u0022,\u0022name\u0022:\u0022第2集\u0022}]'), 42, 'SIRO-5733')"></div>
        """.trimIndent()

        val data = source.parsePlayerData(html)

        assertEquals("SIRO-5733", data.code)
        assertEquals(2, data.episodes.size)
        assertEquals("第1集", data.episodes[0].name)
        // \/ -> /，\u0026 -> &：多层转义必须全部还原，否则播放页路径错误
        assertEquals("https://123av.com/e/play?id=1&poster=https://123av.com/img/p.jpg",
            data.episodes[0].url)
        assertEquals("//123av.com/e/2", data.episodes[1].url)
        // poster 取自首集 url 查询参数
        assertEquals("https://123av.com/img/p.jpg", data.poster)
    }

    @Test
    fun parsePlayerData_missingConfigReturnsEmptyButKeepsCode() {
        val html = """<div>no player config here</div>"""
        val data = source.parsePlayerData(html)
        assertTrue(data.episodes.isEmpty())
        assertEquals("", data.poster)
    }

    // ---------- 详情页 ----------

    @Test
    fun parseDetailDocument_parsesTitleEpisodesStatsAndInfoChips() {
        val playerPayload = """[{\u0022url\u0022:\u0022https:\/\/123av.com\/e\/1?poster=https:\/\/123av.com\/poster.jpg\u0022,\u0022name\u0022:\u0022第1集\u0022}]"""
        val html = """
            <html><body>
            <h1 class="watch__title">SIRO-5733 详情页完整标题</h1>
            <div class="player" style="background-image:url('https://123av.com/bg.jpg')"></div>
            <div x-data="player(JSON.parse('$playerPayload'), 42, 'SIRO-5733')"></div>
            <div class="watch__metaitem">1.2万次观看</div>
            <div class="watch__info-row"><dt>演员</dt><dd><a href="/cn/actresses/aiko">Aiko</a></dd></div>
            <div class="watch__info-row"><dt>代码</dt><dd>SIRO-5733</dd></div>
            </body></html>
        """.trimIndent()

        val reference = Video(id = "siro-5733", title = "列表缓存标题", category = "")
        val detail = source.parseDetailDocument(
            Jsoup.parse(html, "https://123av.com/"),
            reference,
            resolvedUrl = "https://123av.com/cn/v/siro-5733"
        )

        requireNotNull(detail)
        assertEquals("SIRO-5733 详情页完整标题", detail.title)
        assertEquals(1, detail.episodes.size)
        assertEquals("https://123av.com/e/1?poster=https://123av.com/poster.jpg", detail.embedUrl)
        // 封面优先取 .player 背景
        assertEquals("https://123av.com/bg.jpg", detail.coverUrl)
        assertEquals(12_000L, detail.views)
        assertEquals(2, detail.infoRows.size)
        val actressRow = detail.infoRows.first { it.label == "演员" }
        assertEquals(1, actressRow.chips.size)
        assertEquals("Aiko", actressRow.chips.first().text)
        assertEquals("/cn/actresses/aiko", actressRow.chips.first().path)
        assertEquals("https://123av.com/cn/v/siro-5733", detail.sourcePageUrl)
    }

    @Test
    fun parseDetailWithRelated_staticRelatedCardsReturnedAlongsideVideo() {
        // 同一份详情文档：详情主体 + template 原型卡（须排除）+ 静态真实推荐卡
        val html = """
            <html><body>
            <h1 class="watch__title">CUR-001 当前影片</h1>
            <div x-data="player(JSON.parse('[{&quot;url&quot;:&quot;https://123av.com/e/1&quot;,&quot;name&quot;:&quot;第1集&quot;}]'), 1, 'CUR-001')"></div>
            <template id="related-tpl">
              ${relatedCard("tpl-rec")}
            </template>
            <section class="related">
              ${relatedCard("real-rec-1")}
              ${relatedCard("real-rec-2")}
            </section>
            </body></html>
        """.trimIndent()

        val result = source.parseDetailWithRelated(
            Jsoup.parse(html, "https://123av.com/"),
            reference = Video(id = "cur-001", title = "CUR-001", category = ""),
            resolvedUrl = "https://123av.com/cn/v/cur-001"
        )

        requireNotNull(result.video)
        assertEquals("CUR-001 当前影片", result.video.title)
        // 静态推荐只取真实卡，template 原型卡被排除
        assertEquals(listOf("real-rec-1", "real-rec-2"), result.relatedStatic.map { it.id })
    }

    // ---------- 数字 / 时间工具 ----------

    @Test
    fun parseViews_handlesSuffixes() {
        assertEquals(1_500_000L, JsoupVideoDataSource.parseViews("1.5M次观看"))
        assertEquals(5800L, JsoupVideoDataSource.parseViews("5.8K"))
        assertEquals(12_000L, JsoupVideoDataSource.parseViews("1.2万"))
        assertEquals(300L, JsoupVideoDataSource.parseViews("300"))
        assertEquals(0L, JsoupVideoDataSource.parseViews(null))
        assertEquals(0L, JsoupVideoDataSource.parseViews(""))
    }

    @Test
    fun parsePublishedAgo_chineseAndEnglish() {
        val now = System.currentTimeMillis()
        val fiveMinAgo = JsoupVideoDataSource.parsePublishedAgo("5 分钟前")
        requireNotNull(fiveMinAgo)
        assertTrue(kotlin.math.abs(now - fiveMinAgo - 300_000L) < 5_000L)

        val twoDaysAgo = JsoupVideoDataSource.parsePublishedAgo("2 days ago")
        requireNotNull(twoDaysAgo)
        assertTrue(kotlin.math.abs(now - twoDaysAgo - 2L * 86_400_000L) < 5_000L)

        assertNull(JsoupVideoDataSource.parsePublishedAgo("刚刚"))
        assertNull(JsoupVideoDataSource.parsePublishedAgo(null))
    }

    // ---------- 纯 JVM URL 工具 ----------

    @Test
    fun siteUrls_queryParameter_decodesAndHandlesMissing() {
        assertEquals("2", SiteUrls.queryParameter("/cn/new?page=2", "page"))
        assertNull(SiteUrls.queryParameter("/cn/new", "page"))
        assertNull(SiteUrls.queryParameter(null, "page"))
        assertEquals("a b", SiteUrls.queryParameter("/s?q=a%20b", "q"))
        assertEquals("123av.com", SiteUrls.host("https://123av.com/cn/new"))
        assertEquals("/cn/v/1?page=3", SiteUrls.pathAndQuery("https://123av.com/cn/v/1?page=3"))
    }

    // ---------- 相关推荐 ----------

    private fun relatedCard(id: String) = """
        <div class="card">
          <a class="card__cover" href="/cn/v/$id">
            <img class="card__img" src="https://cdn.example.com/$id.jpg">
          </a>
          <div class="card__title"><a href="/cn/v/$id">$id — 推荐影片</a></div>
        </div>
    """.trimIndent()

    @Test
    fun parseRelatedHtml_parsesCardsWithBaseUrl() {
        val html = relatedCard("rec-001") + relatedCard("rec-002")

        val related = source.parseRelatedHtml(html, "https://123av.com/cn/v/current-1")

        assertEquals(listOf("rec-001", "rec-002"), related.map { it.id })
        assertEquals("https://cdn.example.com/rec-001.jpg", related.first().coverUrl)
        assertEquals("https://123av.com/cn/v/rec-001", related.first().sourcePageUrl)
    }

    @Test
    fun parseRelatedHtml_excludesCardsInsideTemplate() {
        val html = """
            <template id="more-tpl">
              ${relatedCard("tpl-001")}
            </template>
            <div>
              ${relatedCard("real-001")}
            </div>
        """.trimIndent()

        val related = source.parseRelatedHtml(html, "https://123av.com/cn/v/current-1")

        assertEquals(listOf("real-001"), related.map { it.id })
    }

    @Test
    fun parseRelatedHtml_ignoresCardWithoutCoverLink() {
        val html = """
            <div class="card">
              <div class="card__title"><a href="/cn/v/no-cover">无封面卡片</a></div>
            </div>
            ${relatedCard("real-002")}
        """.trimIndent()

        val related = source.parseRelatedHtml(html, "https://123av.com/cn/v/current-1")

        assertEquals(listOf("real-002"), related.map { it.id })
    }

    @Test
    fun parseRelatedHtml_blankInputReturnsEmpty() {
        assertTrue(source.parseRelatedHtml("", "https://123av.com/").isEmpty())
    }

    @Test
    fun parseRelatedHtml_parsesServerSideVsideEntries() {
        // 详情页服务端直出的“相关视频”侧栏：封面在 thumb 的 background-image 里，
        // meta 内依次为发布时间 / 分隔点 / 观看数
        val html = """
            <aside class="watch-side"><ul class="watch-side__list">
              <li><a class="vside" href="/cn/v/side-001">
                <span class="vside__thumb"
                      data-preview="https://icdn.123av.me/preview/side-001/preview.png"
                      style="background-image:url('https://icdn.123av.me/img/side-001/cover.jpg')">
                  <span class="vside__dur">2:06:21</span>
                </span>
                <span class="vside__body">
                  <span class="vside__title">SIDE-001 — 侧栏相关视频</span>
                  <span class="vside__meta">
                    <span>1 天 12 小时前</span>
                    <span class="dot"></span>
                    <span>18.1K次观看</span>
                  </span>
                </span>
              </a></li>
            </ul></aside>
        """.trimIndent()

        val related = source.parseRelatedHtml(html, "https://123av.com/cn/v/current-1")

        assertEquals(listOf("side-001"), related.map { it.id })
        val item = related.first()
        assertEquals("https://icdn.123av.me/img/side-001/cover.jpg", item.coverUrl)
        assertEquals("https://icdn.123av.me/preview/side-001/preview.png", item.previewUrl)
        assertEquals("2:06:21", item.durationText)
        assertEquals("1 天 12 小时前", item.publishedAgo)
        assertEquals("18.1K次观看", item.viewsText)
        assertEquals("https://123av.com/cn/v/side-001", item.sourcePageUrl)
    }

    @Test
    fun parseDetailWithRelated_sideVideosPreferredOverTemplateCards() {
        // 同时存在侧栏条目与 template 原型卡时，静态推荐取侧栏、原型卡不入选
        val html = """
            <html><body>
            <h1 class="watch__title">CUR-001 当前影片</h1>
            <div x-data="player(JSON.parse('[{&quot;url&quot;:&quot;https://123av.com/e/1&quot;,&quot;name&quot;:&quot;第1集&quot;}]'), 1, 'CUR-001')"></div>
            <section class="rec">
              <template x-for="item in items">${relatedCard("tpl-rec")}</template>
            </section>
            <aside class="watch-side"><ul class="watch-side__list">
              <li><a class="vside" href="/cn/v/side-a">
                <span class="vside__thumb" style="background-image:url('https://icdn.123av.me/a.jpg')"></span>
                <span class="vside__body"><span class="vside__title">SIDE-A</span></span>
              </a></li>
            </ul></aside>
            </body></html>
        """.trimIndent()

        val result = source.parseDetailWithRelated(
            Jsoup.parse(html, "https://123av.com/"),
            reference = Video(id = "cur-001", title = "CUR-001", category = ""),
            resolvedUrl = "https://123av.com/cn/v/cur-001"
        )

        assertEquals(listOf("side-a"), result.relatedStatic.map { it.id })
    }

    @Test
    fun dedupRelated_removesSelfDuplicatesBlankAndLimits() {
        fun v(id: String) = Video(id = id, title = id, category = "视频")
        val input = listOf(
            v("a"), v("current"), v("b"), v("a"), v(""), v("c"), v("d"), v("e")
        )

        val result = com.av123.video.data.repository.dedupRelated(
            videos = input,
            currentVideoId = "current",
            limit = 4
        )

        assertEquals(listOf("a", "b", "c", "d"), result.map { it.id })
    }
}
