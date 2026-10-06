package com.av123.video.data.source

import com.av123.video.data.model.ActressProfile
import com.av123.video.data.model.CategoryPage
import com.av123.video.data.model.Episode
import com.av123.video.data.model.FilterSearchField
import com.av123.video.data.model.HomeFeed
import com.av123.video.data.model.IndexEntry
import com.av123.video.data.model.NavChannel
import com.av123.video.data.model.NavChannelGroup
import com.av123.video.data.model.Pagination
import com.av123.video.data.model.Video
import com.av123.video.data.model.FilterGroup
import com.av123.video.data.model.FilterOption
import com.av123.video.data.db.AppJson
import com.av123.video.data.model.InfoChip
import com.av123.video.data.model.VideoInfoRow
import com.av123.video.data.net.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * 通用网页数据源：用 Jsoup 解析“列表页 + 详情页”形态的视频站（无官方 API）。
 *
 * 站点差异全部收敛在 [JsoupSiteConfig]：baseUrl、语言路径、CSS 选择器、
 * 区块标题关键字、分页/搜索路径。接入合法授权站点时只需改配置，解析逻辑不用动。
 *
 * 注意：Jsoup 不执行 JavaScript。若列表内容由前端 JS 动态渲染
 * （例如 `<template x-for="...">` 渲染的推荐区块），静态 HTML 中拿不到数据，
 * 这类站点需要改用 WebView 拦截接口，或直接调用其 JSON 接口。
 *
 * 播放地址通常不在详情页 HTML 里：播放器 iframe 嵌入页的 JS 才会发起真实
 * m3u8 请求，Jsoup 拿不到，由详情页的无头 WebView 加载拦截。
 */
class JsoupVideoDataSource(
    private val config: JsoupSiteConfig = JsoupSiteConfig()
) : VideoDataSource {

    override suspend fun fetchHomeFeed(): HomeFeed = withContext(Dispatchers.IO) {
        val doc = fetchDoc(joinUrl(config.baseUrl, config.localePath))
        parseHomeDocument(doc)
    }

    /** 首页文档解析（与网络请求分离，便于单元测试直接喂 HTML） */
    internal fun parseHomeDocument(doc: Document): HomeFeed {
        val featured = doc.select(config.featuredItem).mapNotNull { parseFeaturedItem(it) }

        val sections = doc.select(config.sectionSelector).map { sec ->
            val title = sec.selectFirst(config.sectionTitle)?.text()?.trim().orEmpty()
            val videos = sec.select(config.cardSelector).mapNotNull { parseCard(it, title) }
            // 区块右上角"查看全部 →"（如 <a class="section__all" href="/cn/new">），
            // 经 toSitePath 规范化为站点相对路径；外站链接/空锚点归一为空串
            val morePath = sec.selectFirst(config.sectionAll)
                ?.attr("href")
                .let { toSitePath(it.orEmpty()) }
            Section(title, videos, morePath)
        }

        val hotSection = sections.firstOrNull { it.matches(config.hotKeywords) }
        val latestSection = sections.firstOrNull { it.matches(config.latestKeywords) }

        val all = (featured + sections.flatMap { it.videos }).distinctBy { it.id }
        return HomeFeed(
            categories = listOf("全部") + all.map { it.category }
                .filter { it.isNotBlank() }.distinct(),
            featured = featured,
            hot = hotSection?.videos?.take(6) ?: all.sortedByDescending { it.views }.take(6),
            latest = latestSection?.videos?.take(8)
                ?: all.sortedByDescending { it.updatedAt }.take(8),
            byCategory = all.groupBy { it.category },
            navGroups = parseNav(doc),
            hotMorePath = hotSection?.morePath.orEmpty(),
            latestMorePath = latestSection?.morePath.orEmpty()
        )
    }

    /**
     * 解析首页顶部导航 nav.nav（“分栏”数据来源）：
     * - 直链项（.nav__link 带真实 href，如 /cn/new、/cn/hot、/cn/recent）归入
     *   title=null 的“快捷入口”组；
     * - 下拉项（.dropdown 内有链接，如 趋势/类型/集合/业余/国内）各自成组，
     *   组名为 .nav__link 的文本，组内条目取下拉链接的文本与 href。
     * 下拉触发链接的 href 为 "#"，已跳过。
     */
    private fun parseNav(doc: Document): List<NavChannelGroup> {
        val nav = doc.selectFirst(config.navSelector) ?: return emptyList()
        val quick = mutableListOf<NavChannel>()
        val groups = mutableListOf<NavChannelGroup>()
        nav.select(config.navItem).forEach { item ->
            val link = item.selectFirst(config.navLink) ?: return@forEach
            val href = link.attr("href").trim()
            // ownText() 只取 <a> 自身文本（如“新 / 趋势”），不含下拉内的链接文字
            val title = link.ownText().trim()
            val dropdown = item.select(config.navDropdownLink).mapNotNull { a ->
                val h = a.attr("href").trim()
                val name = a.text().trim()
                if (h.isBlank() || h == "#" || name.isBlank()) null
                else NavChannel(name = name, path = h)
            }
            if (dropdown.isNotEmpty()) {
                groups.add(NavChannelGroup(title = title.ifBlank { "更多" }, channels = dropdown))
            } else if (href.isNotBlank() && href != "#") {
                quick.add(NavChannel(name = title.ifBlank { href }, path = href))
            }
        }
        return buildList {
            if (quick.isNotEmpty()) add(NavChannelGroup(title = null, channels = quick))
            addAll(groups)
        }
    }

    override suspend fun fetchByCategory(category: String, page: Int): CategoryPage =
        withContext(Dispatchers.IO) {
            val path = config.categoryPaths[category]
                ?: return@withContext CategoryPage(emptyList())
            val url = buildString {
                append(joinUrl(config.baseUrl, config.localePath + path))
                if (page > 1) append("?page=").append(page)
            }
            parseCategoryDocument(fetchDoc(url), category, page)
        }

    /** 分类列表页文档解析（与网络请求分离，便于单元测试） */
    internal fun parseCategoryDocument(
        doc: Document,
        category: String,
        page: Int
    ): CategoryPage {
        val videos = doc.select(config.cardSelector).mapNotNull { parseCard(it, category) }
        return CategoryPage(videos = videos, pagination = parsePagination(doc, page))
    }

    /**
     * 分栏栏目列表页：path 已是含语言前缀的完整相对路径
     * （如 /cn/hot、/cn/all?sort=today、/cn/11），直接拼 baseUrl；
     * 分页参数按 path 是否已带查询串用 ?/& 拼接。
     *
     * 注意：部分栏目（集合下的类别/演员/制作商/系列）是“索引聚合页”，
     * 页面没有视频卡片，只有条目列表，此时返回 entries，由 UI 渲染条目，
     * 点击条目再进入其 path 的视频流。网页有四种条目结构：
     * .ggrid>a.gchip 网格条目（类别）、.slist>a.srow 排行行（系列）、
     * .agrid>.actress 演员卡（演员，带头像与视频/观看统计）、
     * .mgrid>a.mcard 制作商卡（制作商，带 logo 徽标与视频数）。
     */
    override suspend fun fetchByPath(
        path: String,
        label: String,
        page: Int,
        params: Map<String, String>
    ): CategoryPage =
        withContext(Dispatchers.IO) {
            val url = buildListUrl(path, page, params)
            parseChannelDocument(fetchDoc(url), label, page)
        }

    /** 分栏栏目/索引聚合页文档解析（与网络请求分离，便于单元测试） */
    internal fun parseChannelDocument(
        doc: Document,
        label: String,
        page: Int
    ): CategoryPage {
        val entries = parseIndexEntries(doc)
                .ifEmpty { parseIndexRows(doc) }
                .ifEmpty { parseActresses(doc) }
                .ifEmpty { parseMakers(doc) }
            // 索引容器存在即按索引页处理：筛选 0 结果时条目为空但容器/表单仍在，
            // 不能误判成视频流页，否则筛选栏会消失、用户无法改回条件。
            // 但若页面同时含视频卡片（如演员视频流页带空的相关推荐容器），视频流优先。
            val hasIndexContainer =
                doc.selectFirst(config.indexGridSelector) != null ||
                    doc.selectFirst(config.indexListSelector) != null ||
                    doc.selectFirst(config.indexActressGridSelector) != null ||
                    doc.selectFirst(config.indexMakerGridSelector) != null
            val isIndexPage = entries.isNotEmpty() ||
                (hasIndexContainer && doc.select(config.cardSelector).isEmpty())
            if (isIndexPage) {
                return@parseChannelDocument CategoryPage(
                    videos = emptyList(),
                    entries = entries,
                    pagination = parsePagination(doc, page),
                    // 页头 .pagehead__count（如女演员页“36,558位女演员”）作标题副标题
                    totalCount = parsePageheadCount(doc),
                    totalCountText = parsePageheadCountText(doc),
                    isIndex = true,
                    // 女演员等索引页也带 form.filters（身高/罩杯/年龄/排序 + q 搜索框）
                    filters = parseFilters(doc),
                    searchField = parseFilterSearchField(doc)
                )
            }
        val videos = doc.select(config.cardSelector).mapNotNull { parseCard(it, label) }
        return CategoryPage(
                videos = videos,
                pagination = parsePagination(doc, page),
                // 页头 .pagehead__count 声明的视频总数（如“361,346个视频”），作标题副标题
                totalCount = parsePageheadCount(doc),
                totalCountText = parsePageheadCountText(doc),
                // 视频流页才有筛选表单（form.filters），随视频列表一起返回
                filters = parseFilters(doc),
                // 女演员个人页（/cn/actresses/<slug>）顶部 .ahero 资料；非该页为 null
                actressProfile = parseActressProfile(doc)
            )
        }

    /**
     * 构造栏目列表 URL：baseUrl + path 路径部分，查询参数按顺序合并
     * ① path 自带查询串（如 /cn/all?sort=today）② 筛选表单 [params]（同名覆盖）
     * ③ 分页 page>1 时追加；空值参数不下发（“全部”对应空串，网页也会被服务端忽略）。
     */
    private fun buildListUrl(path: String, page: Int, params: Map<String, String>): String {
        val basePath = path.substringBefore('?')
        val query = linkedMapOf<String, String>()
        // path 自带查询串（如 /cn/all?sort=today），page 始终由分页参数显式控制，先剔除
        path.substringAfter('?', "")
            .takeIf { it.isNotBlank() }
            ?.split('&')
            ?.forEach { pair ->
                val idx = pair.indexOf('=')
                val k = if (idx >= 0) pair.substring(0, idx) else pair
                val v = if (idx >= 0) pair.substring(idx + 1) else ""
                if (k.isNotBlank() && k != config.pageParam) query[k] = v
            }
        params.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) query[k] = v }
        if (page > 1) query[config.pageParam] = page.toString()
        return buildString {
            append(joinUrl(config.baseUrl, basePath))
            if (query.isNotEmpty()) {
                append("?")
                append(query.entries.joinToString("&") { (k, v) -> "$k=$v" })
            }
        }
    }

    /**
     * 解析视频流页顶部筛选表单（form.filters）：每个 .sel 下拉归一化为一个 [FilterGroup]。
     * - 参数名：隐藏 input 的 name（type/year/actress/sort）；form 内的 page 隐藏项不是 .sel，天然跳过
     * - 维度名：.sel__btn > .k 文案（如“类型:”，去掉结尾冒号）
     * - 选项：.sel__opt 按钮，显示文案取按钮文字，提交值从 @click/x-on:click
     *   的 value='...' 中提取（如 censored、2024、release_date）
     * - 选中态：带 is-on 的选项；没有则回退隐藏 input 的默认 value
     * - 菜单列数：.sel__menu--cols 的 --sel-cols 样式（年份=2），默认 1 列
     * 页面无 form.filters 时返回空列表。
     */
    private fun parseFilters(doc: Document): List<FilterGroup> {
        val form = doc.selectFirst(config.filterFormSelector) ?: return emptyList()
        return form.select(config.filterGroupSelector).mapNotNull { sel ->
            val hidden = sel.selectFirst("input[type=hidden]") ?: return@mapNotNull null
            val key = hidden.attr("name").trim()
            if (key.isBlank() || key == config.pageParam) return@mapNotNull null

            val rawLabel = sel.selectFirst(config.filterLabelSelector)?.text()?.trim().orEmpty()
            val label = rawLabel.trimEnd(':', '：')

            val menu = sel.selectFirst(config.filterMenuSelector)
            val columns = menu?.let { m ->
                val styleCols = Regex("""--sel-cols\s*:\s*(\d+)""")
                    .find(m.attr("style"))?.groupValues?.get(1)?.toIntOrNull()
                styleCols ?: if (m.hasClass(config.filterMenuColsClass)) 2 else 1
            } ?: 1

            // 选项值写在 Alpine 事件里：$refs.input.value='2024'; ...；同时保留元素以识别 is-on
            val parsed = sel.select(config.filterOptionSelector).mapNotNull { btn ->
                val text = btn.text().trim()
                val click = btn.attr("@click").ifBlank { btn.attr("x-on:click") }
                val value = filterOptionValuePattern.find(click)?.groupValues?.get(1)
                if (text.isBlank() || value == null) null else Triple(btn, value, text)
            }
            if (label.isBlank() || parsed.isEmpty()) return@mapNotNull null

            val onValue = parsed.firstOrNull { it.first.hasClass(config.filterOptionOnClass) }?.second
            val options = parsed.map { FilterOption(it.second, it.third) }
            val selected = onValue
                ?: hidden.attr("value").trim().takeIf { v -> options.any { it.value == v } }
                ?: options.first().value

            FilterGroup(
                key = key,
                label = label,
                options = options,
                selectedValue = selected,
                columns = columns.coerceAtLeast(1)
            )
        }
    }

    /** 从 Alpine 点击表达式 $refs.input.value='xxx' 中提取选项提交值 */
    private val filterOptionValuePattern = Regex("""\.value\s*=\s*'([^']*)'""")

    /**
     * 解析索引聚合页的条目网格（.ggrid > a.gchip，类别页）：
     * 条目名取 .gchip__name，数量文案取 .gchip__count（如 176,566），
     * href 为条目视频流路径（已含 /cn 前缀）。页面无 .ggrid 时返回空列表。
     */
    private fun parseIndexEntries(doc: Document): List<IndexEntry> {
        val grid = doc.selectFirst(config.indexGridSelector) ?: return emptyList()
        return grid.select(config.indexChipSelector).mapNotNull { a ->
            val href = a.attr("href").trim()
            val name = a.selectFirst(config.indexChipName)?.text()?.trim().orEmpty()
            if (href.isBlank() || href == "#" || name.isBlank()) return@mapNotNull null
            IndexEntry(
                name = name,
                path = href,
                count = a.selectFirst(config.indexChipCount)?.text()?.trim().orEmpty()
            )
        }
    }

    /**
     * 解析索引聚合页的排行行（.slist > a.srow，系列页）：
     * 序号取 .srow__rank（如 01），名称取 .srow__name，
     * 副文案取 .srow__meta（如“4,476个视频 · 4.2M次观看”），整行 href 为视频流路径。
     * 行内的关注按钮（.follow-btn）是 Alpine 交互，不解析。页面无 .slist 时返回空列表。
     */
    private fun parseIndexRows(doc: Document): List<IndexEntry> {
        val list = doc.selectFirst(config.indexListSelector) ?: return emptyList()
        return list.select(config.indexRowSelector).mapNotNull { a ->
            val href = a.attr("href").trim()
            val name = a.selectFirst(config.indexRowName)?.text()?.trim().orEmpty()
            if (href.isBlank() || href == "#" || name.isBlank()) return@mapNotNull null
            IndexEntry(
                name = name,
                path = href,
                meta = a.selectFirst(config.indexRowMeta)?.text()?.trim().orEmpty(),
                rank = a.selectFirst(config.indexRowRank)?.text()?.trim()
            )
        }
    }

    /** background-image: url('...') 中提取图片地址的正则（兼容单/双引号/无引号） */
    private val bgImageUrlPattern = Regex("""url\(\s*['"]?([^'")]+)['"]?\s*\)""")

    /**
     * 解析索引聚合页的演员卡（.agrid > .actress，演员页）：
     * 名字与路径取 .actress__name（href 已含 /cn 前缀），头像取 .actress__avatar
     * 内联样式 background-image 的 url(...)；统计取 .actress__meta 内各 span
     * （span 的 title 为“视频/观看/关注者”，文本为数值），分别落入结构化字段；
     * 关注按钮 .actress__follow 内 span 是紧凑关注数（如 16.3K）。
     * 关注/取关交互是 Alpine 行为，不解析。页面无 .agrid 时返回空列表。
     */
    private fun parseActresses(doc: Document): List<IndexEntry> {
        val grid = doc.selectFirst(config.indexActressGridSelector) ?: return emptyList()
        return grid.select(config.indexActressSelector).mapNotNull { card ->
            val nameLink = card.selectFirst(config.indexActressName)
            val href = nameLink?.attr("href")?.trim().orEmpty()
            val name = nameLink?.text()?.trim().orEmpty()
            if (href.isBlank() || href == "#" || name.isBlank()) return@mapNotNull null

            val avatarStyle = card.selectFirst(config.indexActressAvatar)
                ?.attr("style")?.trim().orEmpty()
            val avatarUrl = bgImageUrlPattern.find(avatarStyle)
                ?.groupValues?.get(1)?.trim()
                // 容错：去掉可能混入的引号/反引号包裹
                ?.trim('\'', '"', '`')
                .orEmpty()

            // <span title="视频">…3,100</span>：title→数值
            val stats = card.select("${config.indexActressMeta} span")
                .mapNotNull { span ->
                    val label = span.attr("title").trim()
                    val value = span.ownText().trim()
                    if (label.isBlank() || value.isBlank()) null else label to value
                }
                .toMap()

            IndexEntry(
                name = name,
                path = href,
                avatarUrl = avatarUrl,
                videos = stats["视频"].orEmpty(),
                views = stats["观看"].orEmpty(),
                followers = stats["关注者"].orEmpty(),
                followersLabel = card.selectFirst(config.indexActressFollow)?.text()?.trim().orEmpty()
            )
        }
    }

    /**
     * 解析女演员个人页头部资料（.ahero，路径 /cn/actresses/<slug>）：
     * 头像取 .ahero__avatar 内联样式 background-image 的 url(...)；
     * 属性行取 .ahero__meta 原文（如“38岁 · 163cm · 88-59-85 · E罩杯”）；
     * .ahero__stats 内每个 .astat：b=数值、span=标签（视频/观看/关注者/出道）。
     * 关注按钮的紧凑粉丝数是 Alpine x-text 渲染（静态 HTML 为空），
     * 直接复用“关注者”统计原文（如 16.3K）。页面无 .ahero 时返回 null。
     */
    private fun parseActressProfile(doc: Document): ActressProfile? {
        val hero = doc.selectFirst(config.actressHeroSelector) ?: return null
        val name = hero.selectFirst(config.actressHeroName)?.text()?.trim().orEmpty()
        if (name.isBlank()) return null

        val avatarStyle = hero.selectFirst(config.actressHeroAvatar)
            ?.attr("style")?.trim().orEmpty()
        val avatarUrl = bgImageUrlPattern.find(avatarStyle)
            ?.groupValues?.get(1)?.trim()
            ?.trim('\'', '"', '`')
            .orEmpty()

        val stats = hero.select(config.actressHeroStatSelector).mapNotNull { stat ->
            val value = stat.selectFirst("b")?.text()?.trim().orEmpty()
            val label = stat.selectFirst("span")?.text()?.trim().orEmpty()
            if (label.isBlank()) null else label to value
        }.toMap()
        val followers = stats["关注者"].orEmpty()

        return ActressProfile(
            name = name,
            avatarUrl = avatarUrl,
            meta = hero.selectFirst(config.actressHeroMeta)?.text()?.trim().orEmpty(),
            videos = stats["视频"].orEmpty(),
            views = stats["观看"].orEmpty(),
            followers = followers,
            debut = stats["出道"].orEmpty(),
            followersLabel = followers
        )
    }

    /**
     * 解析索引聚合页的制作商卡（.mgrid > a.mcard，制作商页）：
     * href 为制作商视频流路径（如 /cn/makers/movies，已含 /cn 前缀）；
     * 名称取 .mcard__name，logo 徽标文案取 .mcard__logo（如 "movies"），
     * 副文案取 .mcard__meta（如“33,419个视频”，已是完整文案直接展示）。
     * 行内关注按钮（.follow-btn）是 Alpine 交互，不解析。页面无 .mgrid 时返回空列表。
     */
    private fun parseMakers(doc: Document): List<IndexEntry> {
        val grid = doc.selectFirst(config.indexMakerGridSelector) ?: return emptyList()
        return grid.select(config.indexMakerSelector).mapNotNull { a ->
            val href = a.attr("href").trim()
            val name = a.selectFirst(config.indexMakerName)?.text()?.trim().orEmpty()
            if (href.isBlank() || href == "#" || name.isBlank()) return@mapNotNull null
            IndexEntry(
                name = name,
                path = href,
                logo = a.selectFirst(config.indexMakerLogo)?.text()?.trim().orEmpty(),
                meta = a.selectFirst(config.indexMakerMeta)?.text()?.trim().orEmpty()
            )
        }
    }

    /**
     * 解析列表页页脚分页结构（BEM 风格 .pager）：
     * - 当前页：.pager__pages 内 .is-on 页码链接的文字；兜底跳转输入框 value
     * - 总页数：跳转输入框 max 属性（最可靠）→ .pager__total “/ 5000” 文案
     *   → rel=last 末页链接的 page 参数 → 页码链接中的最大页数
     * 站点无 .pager 结构或无法确定总页数时返回 null，UI 不显示换页组件。
     */
    private fun parsePagination(doc: Document, requestedPage: Int): Pagination? {
        val pager = doc.selectFirst(config.pagerSelector) ?: return null

        fun pageParam(href: String?): Int? =
            SiteUrls.queryParameter(href, config.pageParam)?.toIntOrNull()

        val totalFromInput = pager.selectFirst("input[name=${config.pageParam}]")
            ?.attr("max")?.toIntOrNull()
        val totalFromText = pager.selectFirst(config.pagerTotal)
            ?.text()?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
        val totalFromLast = pageParam(pager.selectFirst("a[rel=last]")?.absUrl("href"))
        val totalFromLinks = pager.select("${config.pagerPages} a")
            .mapNotNull { pageParam(it.absUrl("href")) ?: it.text().trim().toIntOrNull() }
            .maxOrNull()
        val total = sequenceOf(totalFromInput, totalFromText, totalFromLast, totalFromLinks)
            .filterNotNull().firstOrNull()?.coerceAtLeast(1) ?: return null

        val current = pager.selectFirst("${config.pagerPages} ${config.pagerCurrent}")
            ?.text()?.trim()?.toIntOrNull()
            ?: pager.selectFirst("input[name=${config.pageParam}]")?.attr("value")?.toIntOrNull()
            ?: requestedPage

        return Pagination(
            currentPage = current.coerceIn(1, total),
            totalPages = total
        )
    }

    /**
     * 详情页解析。详情链接直接使用列表卡片中 `<a class="card__cover" href="...">` 的 href：
     * 优先取列表条目保存的绝对地址（Jsoup absUrl，已处理中文/编码），
     * 其次 baseUrl + detailPath，最后才按 id 回退拼路径。
     *
     * 页面结构（watch 页）：
     * - 标题：.watch__title
     * - 播放配置：x-data="player(JSON.parse('[{'url':'...','name':'...'}]'), ...)"
     *   剧集数组给出集数与 iframe 播放页地址，url 的 poster 参数即封面海报；
     *   注意 url 是嵌入播放页而非 mp4/m3u8，ExoPlayer 不能直接播，真实流地址
     *   需由无头 WebView 加载该页拦截
     * - 统计：.watch__metaitem（发布时间 / 观看数）
     * - 详情：.watch__info-row 内 dt=字段名、dd=字段值（代码/类型/发布日期/演员/制作商/类别/标签）
     */
    override suspend fun fetchVideoDetail(reference: Video): VideoDetail = withContext(Dispatchers.IO) {
        val url = reference.sourcePageUrl.ifBlank {
            reference.detailPath.takeIf { it.isNotBlank() }?.let { joinUrl(config.baseUrl, it) }
        } ?: joinUrl(config.baseUrl, "${config.localePath}/${config.videoPathSegment}/${reference.id}")
        // 网络错误直接向上抛出，由仓库归一化为 AppError（此前 runCatching 吞异常
        // 会让详情页无法区分“网络失败”与“页面无内容”，统一走仓库错误链路）
        parseDetailWithRelated(fetchDoc(url), reference, url)
    }

    /**
     * 同一份详情 [Document] 的纯解析入口（与网络请求分离，便于单元测试）：
     * 详情主体 + 文档内静态可见的相关推荐一次性产出，避免重复解析/重复抓页。
     */
    internal fun parseDetailWithRelated(
        doc: Document,
        reference: Video,
        resolvedUrl: String
    ): VideoDetail = VideoDetail(
        video = parseDetailDocument(doc, reference, resolvedUrl),
        relatedStatic = selectRelatedCards(doc)
    )

    /**
     * 详情页文档解析（与网络请求分离，便于单元测试）。
     * [resolvedUrl] 为实际请求的绝对地址，写入 Video.sourcePageUrl。
     */
    internal fun parseDetailDocument(
        doc: Document,
        reference: Video,
        resolvedUrl: String
    ): Video? {
        val title = doc.selectFirst(config.detailTitleSelector)?.text()?.trim().orEmpty()

        // 播放配置：剧集列表 + 海报封面
        val player = parsePlayerData(doc.html())
        // 封面以 .player 容器的 background-image 为准（与网页播放区显示一致），
        // 剧集 url 的 poster 参数与 og:image 仅作兜底（实测 poster 可能指向其他影片）
        val cover = parsePlayerBackground(doc)
            .ifBlank { player.poster }
            .ifBlank { doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty() }

        // 统计元信息：.watch__metaitem（“32 分钟 15 秒前” / “0次观看”）
        var viewsText: String? = null
        var publishedAgo: String? = null
        doc.select(config.detailMetaItem).forEach { item ->
            val t = item.text().trim()
            when {
                t.contains("观看") || t.contains("views", ignoreCase = true) -> viewsText = t
                t.contains("前") || t.contains("ago", ignoreCase = true) -> publishedAgo = t
            }
        }

        // 详情键值对：dt 字段名 -> dd 字段值，按网页文档顺序保留；
        // chips 取 dd 内所有带 href 的同站链接（类型/演员/制作商/系列/类别/标签 等胶囊按钮），
        // 保留跳转路径供点击进入对应视频流；代码/发布日期等纯文本行 chips 为空
        val rows = doc.select(config.detailInfoRow).mapNotNull { row ->
            val label = row.selectFirst(config.detailInfoLabel)?.text()?.trim().orEmpty()
            val dd = row.selectFirst(config.detailInfoValue) ?: return@mapNotNull null
            val chips = dd.select("a[href]").mapNotNull { a ->
                val name = a.text().trim()
                val path = toSitePath(a.attr("href"))
                if (name.isBlank() || path.isBlank()) null else InfoChip(name, path)
            }
            VideoInfoRow(
                label = label,
                value = dd.text().trim(),
                chips = chips
            )
        }
        fun rowValue(keywords: List<String>): String? =
            rows.firstOrNull { r -> keywords.any { r.label.contains(it, ignoreCase = true) } }?.value

        val code = rowValue(listOf("代码", "代碼", "code")).orEmpty()
            .ifBlank { player.code }
        val type = rowValue(listOf("类型", "類型", "类型")).orEmpty()
        val dateText = rowValue(listOf("发布日期", "發布日期", "上映", "日期", "date")).orEmpty()
        val year = Regex("""(?:19|20)\d{2}""").find(dateText)?.value?.toIntOrNull() ?: 0
        // 影片简介：只认 .watch__desc-text 正文区块（部分影片没有该区块）。
        // 严禁回退到 meta[name=description] / og:description——那些是 SEO 样板文案
        // （站点名、宣传语等），并非影片简介，填进去只会显示错误内容。
        // 没有简介区块时留空，UI 会自动隐藏“简介”板块。
        val synopsis = doc.selectFirst(config.detailDescSelector)?.text()?.trim().orEmpty()
            .ifBlank {
                config.detailSynopsisSelector.takeIf { it.isNotBlank() }
                    ?.let { doc.selectFirst(it)?.text()?.trim() }
                    .orEmpty()
            }

        if (title.isBlank() && rows.isEmpty() && player.episodes.isEmpty()) return null
        return Video(
            id = reference.id,
            title = title.ifBlank { reference.title.ifBlank { reference.id } },
            category = type.ifBlank { reference.category },
            coverUrl = cover,
            sourcePageUrl = resolvedUrl,
            // 首集 iframe 播放页地址：真实 m3u8 由该页 JS 发起请求，交给 WebView 拦截
            embedUrl = player.episodes.firstOrNull()?.url.orEmpty(),
            // 全部选集（集名 + 各集 iframe 播放页），供详情页选集切换；首集与 embedUrl 相同
            episodes = player.episodes.map { Episode(it.name, it.url) },
            detailPath = reference.detailPath,
            code = code,
            year = year,
            synopsis = synopsis,
            infoRows = rows,
            episodeCount = player.episodes.size.coerceAtLeast(1),
            durationText = reference.durationText,
            views = parseViews(viewsText),
            viewsText = viewsText?.trim().orEmpty(),
            publishedAgo = publishedAgo?.trim().orEmpty(),
            updatedAt = parsePublishedAgo(publishedAgo) ?: reference.updatedAt
        )
    }

    /**
     * 相关推荐解析（与网络请求分离，便于单元测试）：
     * WebView 渲染后回传的卡片 HTML（可能是多个 .card / a.vside 的 outerHTML 拼接片段，
     * 也可能是整页 HTML），以详情页地址为 baseUri 解析，口径见 selectRelatedCards。
     */
    internal fun parseRelatedHtml(html: String, baseUrlHint: String): List<Video> {
        if (html.isBlank()) return emptyList()
        return selectRelatedCards(Jsoup.parse(html, baseUrlHint))
    }

    /**
     * 从文档中选取相关推荐卡片，静态详情文档与 WebView 渲染后回传 HTML 共用同一口径。
     *
     * 优先取详情页服务端直出的“相关视频”侧栏 [JsoupSiteConfig.sideVideoSelector]
     * （aside.watch-side 内 a.vside，实测每页 12 条、与 JS/推荐 API 无关，最可靠）；
     * 没有侧栏时再回退到非 `<template>` 原型、且封面链接非空的 .card 卡片
     * （兼容 WebView 渲染后回传的卡片片段及旧结构）。
     */
    private fun selectRelatedCards(doc: Document): List<Video> {
        val sideVideos = doc.select(config.sideVideoSelector).mapNotNull { parseSideVideo(it) }
        if (sideVideos.isNotEmpty()) return sideVideos
        return doc.select(config.cardSelector)
            .filter { el ->
                el.parents().none { it.tagName().equals("template", ignoreCase = true) }
            }
            .filter { el ->
                el.selectFirst(config.cardCoverLink)?.attr("href")?.isNotBlank() == true
            }
            .mapNotNull { parseCard(it, "") }
    }

    /**
     * 解析详情页侧栏单条“相关视频” a.vside：
     * - 封面在 .vside__thumb 的 style="background-image:url(...)" 里（可能缺省）
     * - .vside__dur 时长（可能缺省）、.vside__title 标题
     * - .vside__meta 内依次为发布时间、观看数（用关键字区分，顺序不作硬假设）
     */
    private fun parseSideVideo(el: Element): Video? {
        val href = el.attr("href")
        if (href.isBlank()) return null
        val slug = href.trim('/').substringAfterLast('/')
        val thumb = el.selectFirst(config.sideVideoThumb)
        val meta = el.selectFirst(config.sideVideoMeta)
        // 注意：Jsoup 的 select 会包含调用元素自身（.vside__meta 自身也是 span），
        // 其 text() 是整段合并文本，必须先排除自身，再按关键字识别发布时间/观看数
        val metaSpans = meta?.select("span")
            ?.filterNot { it === meta }
            ?.map { it.text().trim() }
            .orEmpty()
        val publishedAgo = metaSpans.firstOrNull {
            it.contains("前") || it.contains("ago", ignoreCase = true)
        }
        val viewsText = metaSpans.firstOrNull {
            it.contains("观看") || it.contains("views", ignoreCase = true)
        }
        return buildVideo(
            slug = slug,
            rawTitle = el.selectFirst(config.sideVideoTitle)?.text()?.trim().orEmpty(),
            category = "相关视频",
            cover = parseBackgroundImageUrl(thumb),
            // thumb 上另有 data-preview 预览动态图，与列表卡 data-preview 同口径，顺手取全
            preview = thumb?.let { parseAttrUrl(it, "data-preview") }.orEmpty(),
            href = href,
            absHref = el.absUrl("href"),
            durationText = el.selectFirst(config.sideVideoDuration)?.text(),
            viewsText = viewsText,
            publishedAgo = publishedAgo
        )
    }

    /**
     * 取元素 style="background-image:url(...)" 中的图片地址：
     * 原样可能带引号/相对路径；相对地址以元素 baseUri 补全为绝对地址。
     */
    private fun parseBackgroundImageUrl(el: Element?): String {
        val style = el?.attr("style").orEmpty()
        val raw = BACKGROUND_IMAGE_URL_REGEX
            .find(style)?.groupValues?.get(2)?.trim().orEmpty()
        if (raw.isBlank()) return ""
        if (raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        ) {
            return raw
        }
        val base = el?.baseUri().orEmpty()
        if (base.isBlank()) return raw
        return runCatching { java.net.URL(java.net.URL(base), raw).toString() }.getOrDefault(raw)
    }

    override suspend fun parseRelatedFromRenderedHtml(
        html: String,
        baseUrlHint: String
    ): List<Video> = withContext(Dispatchers.IO) {
        parseRelatedHtml(html, baseUrlHint)
    }

    /**
     * 搜索页解析，结构与分类列表页一致：
     * - URL：{baseUrl}{localePath}/search?keyword=<关键词>[&page=<页码>]
     * - 结果总数：.pagehead__count 文案（如“960个视频”），与当前页条数不同
     * - 分页：页脚 .pager 结构（与分类页同一套解析），检测不到则不显示换页组件
     */
    override suspend fun search(keyword: String, page: Int): CategoryPage =
        withContext(Dispatchers.IO) {
            if (keyword.isBlank()) return@withContext CategoryPage(emptyList())
            val query = URLEncoder.encode(keyword, "UTF-8")
            val url = buildString {
                append(joinUrl(config.baseUrl, config.localePath + config.searchPath))
                append("?").append(config.searchQueryParam).append("=").append(query)
                if (page > 1) append("&").append(config.pageParam).append("=").append(page)
            }
            parseSearchDocument(fetchDoc(url), page)
        }

    /** 搜索结果页文档解析（与网络请求分离，便于单元测试） */
    internal fun parseSearchDocument(doc: Document, page: Int): CategoryPage {
        val videos = doc.select(config.cardSelector).mapNotNull { parseCard(it, "搜索") }
        return CategoryPage(
            videos = videos,
            pagination = parsePagination(doc, page),
            totalCount = parsePageheadCount(doc)
        )
    }

    /**
     * 解析页面头部结果总数 .pagehead__count（如“960个视频”/“1,234 个视频”）。
     * 取文案中第一段数字（兼容千分位逗号）；元素缺失或无数字时返回 null。
     */
    private fun parsePageheadCount(doc: Document): Int? =
        doc.selectFirst(config.pageheadCountSelector)?.text()
            ?.let { Regex("""[\d,]+""").find(it)?.value?.replace(",", "")?.toIntOrNull() }

    /**
     * 解析页头 .pagehead__count 的完整原文（如“36,558位女演员”“361,346个视频”）。
     * 不同索引页单位不同（位女演员/个视频/个系列），原文直接展示最忠实；空文本归 null。
     */
    private fun parsePageheadCountText(doc: Document): String? =
        doc.selectFirst(config.pageheadCountSelector)?.text()?.trim()?.ifBlank { null }

    /**
     * 解析筛选表单内的文本搜索框（女演员页 form.filters > .asearch input[type=text]）：
     * 参数名取 name（q），占位提示取 placeholder（搜索 女演员…）。
     * 表单无搜索输入框（视频流页/类别页）时返回 null。
     */
    private fun parseFilterSearchField(doc: Document): FilterSearchField? {
        val input = doc.selectFirst(
            "${config.filterFormSelector} ${config.filterSearchInputSelector}"
        ) ?: return null
        val key = input.attr("name").trim()
        if (key.isBlank() || key == config.pageParam) return null
        return FilterSearchField(key = key, hint = input.attr("placeholder").trim())
    }

    // -------- 列表解析 --------

    private fun parseFeaturedItem(el: Element): Video? {
        val link = el.selectFirst(config.featuredLink) ?: return null
        val href = link.attr("href")
        if (href.isBlank()) return null
        val slug = href.trim('/').substringAfterLast('/')
        return buildVideo(
            slug = slug,
            rawTitle = el.selectFirst(config.featuredTitle)?.text()?.trim().orEmpty(),
            category = "精选",
            cover = el.selectFirst(config.featuredImg)?.absUrl("src").orEmpty(),
            preview = "",
            href = href,
            absHref = link.absUrl("href"),
            durationText = el.selectFirst(config.featuredDuration)?.text(),
            viewsText = null,
            publishedAgo = null
        )
    }

    private fun parseCard(el: Element, sectionTitle: String): Video? {
        // 详情链接以封面链接 <a class="card__cover" href="/cn/v/..."> 为准，标题链接兜底
        val coverLink = el.selectFirst(config.cardCoverLink)
        val titleLink = el.selectFirst(config.cardTitleLink)
        val link = coverLink ?: titleLink ?: return null
        val href = link.attr("href")
        if (href.isBlank()) return null
        val rawTitle = (titleLink?.text() ?: link.text()).trim()
        val slug = href.trim('/').substringAfterLast('/')
        val poster = el.selectFirst(config.cardPoster)
        val meta = el.selectFirst(config.cardMeta)
        return buildVideo(
            slug = slug,
            rawTitle = rawTitle,
            category = sectionTitle.ifBlank { "视频" },
            cover = el.selectFirst(config.cardImg)?.absUrl("src").orEmpty(),
            // data-preview 是 Alpine 模板字面量（首尾带反引号），且内容为隐式 mp4
            preview = poster?.let { parseAttrUrl(it, "data-preview") }.orEmpty(),
            href = href,
            absHref = link.absUrl("href"),
            durationText = el.selectFirst(config.cardDuration)?.text(),
            viewsText = el.selectFirst(config.cardViews)?.text(),
            publishedAgo = meta?.select("span")?.firstOrNull()?.text()
        )
    }

    private fun buildVideo(
        slug: String,
        rawTitle: String,
        category: String,
        cover: String,
        preview: String,
        href: String,
        absHref: String,
        durationText: String?,
        viewsText: String?,
        publishedAgo: String?
    ): Video {
        // 列表标题常见形态：“CODE — 描述文字”，取破折号前的编号；取不到则用路径 slug
        val code = Regex("""^([A-Za-z0-9][A-Za-z0-9\-]{1,19})\s*[—–-]\s*""")
            .find(rawTitle)?.groupValues?.get(1)
        return Video(
            id = slug.ifBlank { absHref.ifBlank { rawTitle.hashCode().toString() } },
            code = code ?: slug.uppercase(),
            title = rawTitle.ifBlank { slug },
            category = category,
            coverUrl = cover,
            previewUrl = preview,
            sourcePageUrl = absHref,
            detailPath = href,
            durationText = durationText?.trim().orEmpty(),
            views = parseViews(viewsText),
            viewsText = viewsText?.trim().orEmpty(),
            publishedAgo = publishedAgo?.trim().orEmpty(),
            updatedAt = parsePublishedAgo(publishedAgo) ?: System.currentTimeMillis()
        )
    }

    /**
     * 取元素上的 URL 型属性并归一化：
     * 网页里 data-preview 等属性写的是 Alpine 模板字面量，属性值首尾带反引号
     * （data-preview="`https://.../preview.png`"），Jsoup 原样取出会含反引号，
     * 直接交给播放器会请求失败；先去掉首尾的反引号 / 引号 / 空白，
     * 相对地址再交 absUrl 按 baseUri 补全为绝对地址。
     */
    private fun parseAttrUrl(el: Element, attr: String): String {
        val raw = el.attr(attr).trim().trim('`', '"', '\'')
        if (raw.isBlank()) return ""
        if (raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        ) {
            return raw
        }
        return el.absUrl(attr).trim().trim('`', '"', '\'')
    }

    /**
     * 抓取网页：HTTP 传输走统一 [com.av123.video.data.net.HttpClient]
     * （超时/重试/连接池），Jsoup 只负责把响应体解析成 DOM；
     * 传入 url 作为 baseUri，保证 DOM 内相对链接的 absUrl() 可解析为绝对地址。
     */
    private fun fetchDoc(url: String): Document {
        val html = HttpClient.getString(
            url = url,
            headers = mapOf("User-Agent" to config.userAgent),
            timeoutMs = config.timeoutMs
        )
        return Jsoup.parse(html, url)
    }

    /** baseUrl 与路径拼接，自动收拢首尾斜杠，避免出现 "site.com//cn/v/.." 双斜杠 */
    private fun joinUrl(base: String, path: String): String =
        base.trimEnd('/') + "/" + path.trimStart('/')

    /**
     * 把详情信息行胶囊的 href 规范化为站点相对路径（保留查询串），
     * 如 https://123av.com/cn/genres/11 → /cn/genres/11，供频道流直接抓取。
     * 相对路径原样返回；# / javascript: / 外站链接返回空串（不生成可点击胶囊）。
     */
    private fun toSitePath(href: String): String {
        val raw = href.trim()
        if (raw.isBlank() || raw.startsWith("#") ||
            raw.startsWith("javascript:", ignoreCase = true)
        ) {
            return ""
        }
        if (!raw.startsWith("http://", ignoreCase = true) &&
            !raw.startsWith("https://", ignoreCase = true)
        ) {
            return raw
        }
        return runCatching {
            val siteHost = SiteUrls.host(config.baseUrl)
            if (!siteHost.isNullOrBlank() &&
                SiteUrls.host(raw).equals(siteHost, ignoreCase = true)
            ) {
                SiteUrls.pathAndQuery(raw)
            } else {
                ""
            }
        }.getOrDefault("")
    }

    /** x-data="player(JSON.parse('[...]'), ...)" 中的剧集配置 */
    internal data class PlayerData(
        val episodes: List<PlayerEpisode>,
        val poster: String,
        val code: String
    )
    internal data class PlayerEpisode(val name: String, val url: String)

    private val playerJsonRegex = Regex("""player\(\s*JSON\.parse\('([^']+)'\)""")

    /** player(JSON.parse('...'), 数字id, '影片代号', ...) 中第三个参数的代号，如 'siro-5733' */
    private val playerCodeRegex =
        Regex("""player\(\s*JSON\.parse\('[^']*'\)\s*,\s*\d+\s*,\s*'([^']*)'""")

    /**
     * 从详情页 HTML 提取 player(JSON.parse('...')) 里的剧集数组：
     * JS 字符串里的 \u0022 还原为双引号、\/ 还原为斜杠后按 JSON 解析；
     * 首集 url 的 poster 查询参数即详情页海报封面。
     */
    internal fun parsePlayerData(html: String): PlayerData {
        val code = playerCodeRegex.find(html)?.groupValues?.get(1)?.trim().orEmpty()
        val raw = playerJsonRegex.find(html)?.groupValues?.get(1)
            ?.replace("""\u0022""", "\"")
            ?.replace("""\/""", "/")
            ?: return PlayerData(emptyList(), "", code)
        val episodes = runCatching {
            AppJson.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                // 播放页地址经 HTML 属性 -> JS 字符串 -> JSON 三层转义，
                // JSON 解析后仍可能残留 "\/"、"\u0026"，必须还原，
                // 否则 WebView 会把反斜杠规整成斜杠，拼出 //e//xxx 错误路径
                val url = unescapePlayerUrl(
                    obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
                if (url.isBlank()) {
                    null
                } else {
                    PlayerEpisode(obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), url)
                }
            }
        }.getOrDefault(emptyList())
        val poster = episodes.firstOrNull()?.let { ep ->
            SiteUrls.queryParameter(ep.url, "poster")
        }.orEmpty()
        return PlayerData(episodes, poster, code)
    }

    /**
     * 播放页地址多层转义还原：HTML 属性 → JS 单引号字符串 → JSON 三层嵌套后，
     * org.json 解析出的地址仍可能残留字面量 "\/"（反斜杠+斜杠）与 "\u0026"（&）。
     * 不还原的话 WebView 会把 "\" 按 "/" 规整，拼出 //e//xxx 错误路径导致播放页报错。
     */
    private fun unescapePlayerUrl(raw: String): String =
        raw.replace("\\/", "/")
            .replace("\\u0026", "&", ignoreCase = true)

    /**
     * 从 .player 容器的 style="background-image:url('...')" 提取海报。
     * 页面实际写法可能混用单引号与反引号（url('`https://...`')），
     * 故先取 url(...) 括号内全部内容，再剔除首尾的引号 / 反引号 / 空白。
     */
    private fun parsePlayerBackground(doc: Document): String =
        doc.selectFirst(".player")?.attr("style")
            ?.let { Regex("""url\(\s*([^)]*)\)""").find(it)?.groupValues?.get(1) }
            ?.trim()
            ?.trim('\'', '"', '`')
            .orEmpty()

    private class Section(
        val title: String,
        val videos: List<Video>,
        /** 区块"查看全部"链接的相对路径，空串表示网页未提供 */
        val morePath: String = ""
    ) {
        fun matches(keywords: List<String>): Boolean =
            keywords.any { kw -> title.contains(kw, ignoreCase = true) }
    }

    companion object {
        /** "5.8K" -> 5800，"1.5M" -> 1500000，"1.2万" -> 12000，纯数字原样解析 */
        fun parseViews(text: String?): Long {
            val t = text?.trim().orEmpty()
            if (t.isBlank()) return 0L
            val num = Regex("""[\d.]+""").find(t)?.value?.toDoubleOrNull() ?: return 0L
            return when {
                t.contains("万") -> (num * 10_000L).toLong()
                t.contains("M", ignoreCase = true) -> (num * 1_000_000L).toLong()
                t.contains("K", ignoreCase = true) || t.contains("千") -> (num * 1_000L).toLong()
                else -> num.toLong()
            }
        }

        /** 解析 "4 小时 27 分钟前" / "8 分钟前" / "2 days ago" 为时间戳；无法解析返回 null */
        fun parsePublishedAgo(text: String?): Long? {
            val t = text?.trim().orEmpty()
            if (t.isBlank()) return null
            var millis = 0L
            Regex("""(\d+)\s*天""").find(t)?.let { millis += it.groupValues[1].toLong() * 86_400_000L }
            Regex("""(\d+)\s*小时""").find(t)?.let { millis += it.groupValues[1].toLong() * 3_600_000L }
            Regex("""(\d+)\s*分钟?""").find(t)?.let { millis += it.groupValues[1].toLong() * 60_000L }
            Regex("""(\d+)\s*秒""").find(t)?.let { millis += it.groupValues[1].toLong() * 1_000L }
            Regex("""(\d+)\s*days?""", RegexOption.IGNORE_CASE).find(t)
                ?.let { millis += it.groupValues[1].toLong() * 86_400_000L }
            Regex("""(\d+)\s*hours?""", RegexOption.IGNORE_CASE).find(t)
                ?.let { millis += it.groupValues[1].toLong() * 3_600_000L }
            Regex("""(\d+)\s*(?:mins?|minutes?)""", RegexOption.IGNORE_CASE).find(t)
                ?.let { millis += it.groupValues[1].toLong() * 60_000L }
            return if (millis > 0L) System.currentTimeMillis() - millis else null
        }
    }
}

/**
 * 网页站点配置。选择器默认值对应常见的 BEM 风格列表页
 * （.featured 轮播 / .card 卡片 / .card__img 封面 / .card__meta 元信息）。
 * 接入新站时按实际 DOM 覆盖对应字段即可。
 */
data class JsoupSiteConfig(
    val baseUrl: String = "https://example.com",
    /** 语言路径前缀，如 "/cn"；没有则留空 */
    val localePath: String = "",
    val userAgent: String = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    val timeoutMs: Int = 15_000,
    // —— 精选轮播 ——
    val featuredItem: String = ".featured",
    val featuredImg: String = ".featured__img",
    val featuredTitle: String = ".featured__title",
    val featuredLink: String = ".featured__link",
    val featuredDuration: String = ".featured__dur",
    // —— 列表区块与卡片 ——
    val sectionSelector: String = "section.section",
    val sectionTitle: String = ".section__title",
    /** 区块右上角"查看全部 →"链接（href 为 /cn/new 这样的相对路径） */
    val sectionAll: String = "a.section__all",
    val cardSelector: String = ".card",
    val cardImg: String = ".card__img",
    val cardPoster: String = ".card__poster",
    /** 卡片上包裹封面的链接 <a class="card__cover" href="/cn/v/...">，详情页入口，优先于标题链接 */
    val cardCoverLink: String = "a.card__cover",
    val cardTitleLink: String = ".card__title a",
    val cardDuration: String = ".card__dur",
    val cardMeta: String = ".card__meta",
    val cardViews: String = ".card__views",
    // —— 详情页服务端直出的“相关视频”侧栏 aside.watch-side（不依赖前端 JS/推荐 API）——
    /** 单条相关视频链接 <a class="vside" href="/cn/v/..."> */
    val sideVideoSelector: String = "a.vside",
    val sideVideoThumb: String = ".vside__thumb",
    val sideVideoDuration: String = ".vside__dur",
    val sideVideoTitle: String = ".vside__title",
    val sideVideoMeta: String = ".vside__meta",
    // —— 顶部导航（分栏）：nav.nav > .nav__item > a.nav__link + .dropdown a ——
    val navSelector: String = "nav.nav",
    val navItem: String = ".nav__item",
    val navLink: String = "a.nav__link",
    val navDropdownLink: String = ".dropdown a",
    // —— 索引聚合页：网格条目（类别/演员/发行）.ggrid > a.gchip ——
    val indexGridSelector: String = ".ggrid",
    val indexChipSelector: String = "a.gchip",
    val indexChipName: String = ".gchip__name",
    val indexChipCount: String = ".gchip__count",
    // —— 索引聚合页：排行行（系列）.slist > a.srow ——
    val indexListSelector: String = ".slist",
    val indexRowSelector: String = "a.srow",
    val indexRowRank: String = ".srow__rank",
    val indexRowName: String = ".srow__name",
    val indexRowMeta: String = ".srow__meta",
    // —— 索引聚合页：演员卡（演员）.agrid > .actress ——
    val indexActressGridSelector: String = ".agrid",
    val indexActressSelector: String = ".actress",
    val indexActressAvatar: String = ".actress__avatar",
    val indexActressName: String = ".actress__name",
    val indexActressMeta: String = ".actress__meta",
    /** 关注按钮内紧凑关注数（<span x-text="followLabel">16.3K</span>） */
    val indexActressFollow: String = ".actress__follow span",
    // —— 女演员个人页头部 .ahero（/cn/actresses/<slug>）——
    val actressHeroSelector: String = ".ahero",
    val actressHeroAvatar: String = ".ahero__avatar",
    val actressHeroName: String = ".ahero__name",
    val actressHeroMeta: String = ".ahero__meta",
    /** 单个统计块（b=数值，span=标签：视频/观看/关注者/出道） */
    val actressHeroStatSelector: String = ".astat",
    // —— 索引聚合页：制作商卡（制作商）.mgrid > a.mcard ——
    val indexMakerGridSelector: String = ".mgrid",
    val indexMakerSelector: String = "a.mcard",
    val indexMakerLogo: String = ".mcard__logo",
    val indexMakerName: String = ".mcard__name",
    val indexMakerMeta: String = ".mcard__meta",
    // —— 视频流页顶部筛选表单 form.filters ——
    val filterFormSelector: String = "form.filters",
    /** 表单内文本搜索框（女演员页 .asearch 里的 input[type=text]，参数名 q） */
    val filterSearchInputSelector: String = ".asearch input[type=text]",
    val filterGroupSelector: String = ".sel",
    val filterLabelSelector: String = ".sel__btn .k",
    val filterMenuSelector: String = ".sel__menu",
    /** 多列下拉菜单修饰类（年份），列数优先取 --sel-cols 内联样式 */
    val filterMenuColsClass: String = "sel__menu--cols",
    val filterOptionSelector: String = ".sel__opt",
    /** 当前选中选项的高亮 class */
    val filterOptionOnClass: String = "is-on",
    // —— 区块归类关键字：区块标题命中即归入热门 / 最新 ——
    val hotKeywords: List<String> = listOf("热门", "推荐", "hot", "trending", "recommend"),
    val latestKeywords: List<String> = listOf("新发布", "最新", "最近", "new", "recent", "latest"),
    // —— 分页 / 搜索 / 详情 ——
    /** 分类名 -> 列表路径（拼接在 baseUrl + localePath 之后），如 "新发布" to "/new" */
    val categoryPaths: Map<String, String> = emptyMap(),
    /** 列表页页脚分页容器；解析不到则不显示换页组件 */
    val pagerSelector: String = ".pager",
    /** 页码链接容器（内含各页 <a> 与当前页标记） */
    val pagerPages: String = ".pager__pages",
    /** 当前页标记（页码链接上的高亮 class） */
    val pagerCurrent: String = "a.is-on",
    /** “/ 5000” 总页数文案元素 */
    val pagerTotal: String = ".pager__total",
    /** 分页 URL 的页码查询参数名 */
    val pageParam: String = "page",
    /** 页面头部结果总数元素（搜索页为 .pagehead__count，如“960个视频”）；解析不到则总数回退为当前页条数 */
    val pageheadCountSelector: String = ".pagehead__count",
    val searchPath: String = "/search",
    val searchQueryParam: String = "q",
    /** 详情页路径段，完整路径为 {baseUrl}{localePath}/{videoPathSegment}/{id} */
    val videoPathSegment: String = "v",
    /** 详情页标题（watch 页为 .watch__title） */
    val detailTitleSelector: String = ".watch__title, h1",
    /** 详情页统计条目（发布时间 / 观看数），watch 页为 .watch__metaitem */
    val detailMetaItem: String = ".watch__metaitem",
    /** 详情键值对行：watch 页为 .watch__info-row，内部 dt=字段名、dd=字段值 */
    val detailInfoRow: String = ".watch__info-row",
    val detailInfoLabel: String = "dt",
    val detailInfoValue: String = "dd",
    /** 影片简介正文选择器（watch 页为 .watch__desc-text），部分影片无此区块 */
    val detailDescSelector: String = ".watch__desc-text",
    /** 备用简介选择器（detailDescSelector 未命中时使用）；留空则简介留空，UI 自动隐藏。
     *  注意：不要把 meta description / og:description 当简介，那是 SEO 文案不是正文 */
    val detailSynopsisSelector: String = ""
)

/** 解析 style="background-image:url(...)" 的地址（第 2 组），全局复用避免重复编译 */
private val BACKGROUND_IMAGE_URL_REGEX = Regex("""url\(\s*(['"]?)([^'")]+)\1\s*\)""")
