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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * 网页站点配置。选择器默认值对应常见的 BEM 风格列表页
 * （.featured 轮播 / .card 卡片 / .card__img 封面 / .card__meta 元信息）。
 * 接入新站时按实际 DOM 覆盖对应字段即可。
 *
 * P2 起这份配置是唯一站点契约来源：内置 assets/site-config.json 覆盖默认值，
 * 后续 P2.3 再接入远程配置与回退。新增字段必须带默认值，保证旧配置可读。
 */
@Serializable
data class JsoupSiteConfig(
    /** 配置结构版本，与 App versionName 无关 */
    val schemaVersion: Int = 1,
    /** 站点标识，用于多站点扩展与诊断 */
    val id: String = "123av",
    /** 配置内容版本，远程更新时递增 */
    val version: Int = 2,
    val baseUrl: String = "https://example.com",
    /** 语言路径前缀，如 "/cn"；没有则留空 */
    val localePath: String = "",
    val userAgent: String = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    val timeoutMs: Int = 15_000,
    /** Embed 站点白名单（P3 抓流使用；支持精确 host 或 *.example.com） */
    val embedHosts: List<String> = listOf("sadie-shop.site"),
    /** 媒体 CDN 白名单（P3 抓流使用；支持精确 host 或 *.example.com） */
    val mediaHosts: List<String> = listOf("*.true*.site", "*.realpuremind.site"),
    /**
     * 允许从可信 embed 页的真实播放请求动态放行未列入 [mediaHosts] 的 HTTPS 媒体 host。
     * 关闭后只认静态 [mediaHosts]，用于远程 kill switch。
     */
    val allowDynamicMediaHosts: Boolean = true,
    /** 远程配置开关；远程配置本身可下发 false 作为 kill switch */
    val remoteEnabled: Boolean = true,
    /** 远程配置最短刷新间隔（小时） */
    val remoteRefreshHours: Int = 6,
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
internal val BACKGROUND_IMAGE_URL_REGEX = Regex("""url\(\s*(['"]?)([^'")]+)\1\s*\)""")