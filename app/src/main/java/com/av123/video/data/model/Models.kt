package com.av123.video.data.model

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * 视频条目。
 *
 * 网页列表页常见字段（由 JsoupVideoDataSource 解析填充）：
 * - [code]          编号，如 DEMO-001
 * - [coverUrl]      封面图地址
 * - [previewUrl]    悬停预览图地址
 * - [detailPath]    详情页相对路径，如 /v/demo-001
 * - [durationText]  时长原文，如 "12:34"
 * - [views] / [viewsText]  观看数（数值 / 页面原文，如 "5.8K"）
 * - [publishedAgo]  发布时间原文，如 "4 小时前"
 *
 * 详情页二次解析得到：
 * - [sourcePageUrl]  详情页完整地址，便于排查抓取问题
 * - [embedUrl]       播放器 iframe 嵌入页地址（真实 m3u8 由该页 JS 拉取，
 *                    Jsoup 拿不到，需 WebView 加载拦截）
 */
@Serializable
data class Video(
    val id: String,
    val title: String,
    val category: String,
    val coverUrl: String = "",
    val sourcePageUrl: String = "",
    /** 播放器 iframe 嵌入页地址（首集），WebView 加载它以拦截真实 m3u8 流地址 */
    val embedUrl: String = "",
    val year: Int = 0,
    val synopsis: String = "",
    val episodeCount: Int = 1,
    /**
     * 选集列表（详情页 player(...) 配置解析得到），每项为该集的 iframe 播放页地址；
     * 首集即 [embedUrl]。为空表示单集或网页未给出选集配置，UI 不展示选集栏。
     */
    val episodes: List<Episode> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val code: String = "",
    val previewUrl: String = "",
    val detailPath: String = "",
    val durationText: String = "",
    val views: Long = 0L,
    val viewsText: String = "",
    val publishedAgo: String = "",
    /** 详情页信息行（代码/类型/发布日期/演员/制作商/系列/类别/标签…），按网页顺序排列 */
    val infoRows: List<VideoInfoRow> = emptyList()
) {
    /** 观看数展示文本：优先使用页面原文，否则按数值格式化为 K/M */
    val viewsDisplay: String
        get() = viewsText.ifBlank { if (views > 0L) formatViewCount(views) else "" }
}

/**
 * 选集条目：
 * - [name] 网页配置的集名（可能为空，UI 兜底显示“第N集”）
 * - [url]  该集播放器 iframe 嵌入页地址（真实 m3u8 由该页 JS 发起，需无头 WebView 拦截）
 */
@Serializable
data class Episode(
    val name: String = "",
    val url: String = ""
)

/** 5800 -> "5.8K"，1_500_000 -> "1.5M"，不足一千原样返回 */
fun formatViewCount(count: Long): String {
    if (count < 1000L) return count.toString()
    if (count < 1_000_000L) return "${trimOneDecimal(count / 1000.0)}K"
    return "${trimOneDecimal(count / 1_000_000.0)}M"
}

private fun trimOneDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.US, "%.1f", value)

/**
 * 详情信息行里的可点击胶囊（类型/演员/制作商/系列/类别/标签 等 a 链接）：
 * [text] 为胶囊显示文案，[path] 为站点相对路径（如 /cn/genres/11），点击后进入对应视频流。
 */
@Serializable
data class InfoChip(
    val text: String,
    val path: String
)

/** 详情页一行信息：[label] 为字段名（dt），[value] 为纯文本值，[chips] 为可点击胶囊（含跳转路径） */
@Serializable
data class VideoInfoRow(
    val label: String,
    val value: String,
    val chips: List<InfoChip> = emptyList()
)

/** 首页聚合数据：分类、精选、热门、最新以及按分类分组的全部视频 */
@Serializable
data class HomeFeed(
    val categories: List<String>,
    val featured: List<Video>,
    val hot: List<Video>,
    val latest: List<Video>,
    val byCategory: Map<String, List<Video>>,
    /** 首页顶部导航解析出的分栏分组（快捷入口 + 趋势/类型/集合等下拉组） */
    val navGroups: List<NavChannelGroup> = emptyList(),
    /** "热门"区块右上角"查看全部"链接的相对路径（如 /cn/trending）；空串表示网页未提供 */
    val hotMorePath: String = "",
    /** "最新"区块右上角"查看全部"链接的相对路径（如 /cn/new）；空串表示网页未提供 */
    val latestMorePath: String = ""
) {
    /** 所有视频的去重汇总，方便按 id 查找 */
    val allVideos: List<Video> by lazy {
        (featured + hot + latest + byCategory.values.flatten()).distinctBy { it.id }
    }
}

/**
 * 分栏导航条目：首页 nav 中的一个链接。
 * [path] 为站点相对路径（含语言前缀），如 /cn/hot、/cn/all?sort=today、/cn/11。
 */
@Serializable
data class NavChannel(
    val name: String,
    val path: String
)

/**
 * 分栏导航分组：
 * - [title] 为 null 表示顶部直链（新/热门/最近）组成的“快捷入口”组；
 * - 否则为下拉菜单组（趋势/类型/集合/业余/国内），title 即菜单名。
 */
@Serializable
data class NavChannelGroup(
    val title: String?,
    val channels: List<NavChannel>
)

/**
 * 列表页分页信息（由网页页脚 .pager 结构解析归一化得到）。
 * [currentPage] 当前页（从 1 开始），[totalPages] 总页数；
 * 解析不到页脚结构时为 null，UI 不显示换页组件。
 */
@Serializable
data class Pagination(
    val currentPage: Int,
    val totalPages: Int
)

/**
 * 分类/搜索等列表页的一屏数据：视频列表 + 可选的分页信息。
 * [totalCount] 为页面头部声明的结果总数（如搜索页 .pagehead__count 的“960个视频”），
 * 与当前页视频条数不同；页面没有该声明时为 null。[totalCountText] 是该元素的原文
 * （如“36,558位女演员”），保留网页自带单位，供 UI 直接作副标题展示。
 *
 * [isIndex] 为 true 表示该页是“索引聚合页”（如 /cn/genres 类别、/cn/actresses 演员、
 * /cn/makers 发行、/cn/series 系列）：页面本身不含视频卡片，只有 .gchip 条目网格，
 * 点击条目进入其 path 对应的视频流列表。筛选结果为 0 时 [entries] 也可能为空，
 * UI 仍需依据此标记保留索引页与筛选栏。
 */
@Serializable
data class CategoryPage(
    val videos: List<Video>,
    val pagination: Pagination? = null,
    val totalCount: Int? = null,
    /** 页头 .pagehead__count 原文（如“36,558位女演员”）；解析不到为 null */
    val totalCountText: String? = null,
    val entries: List<IndexEntry> = emptyList(),
    /** 页面为索引聚合页（即使筛选后 entries 为空也为 true，保住索引页 UI 与筛选栏） */
    val isIndex: Boolean = false,
    /** 顶部筛选表单（form.filters）解析出的筛选维度；视频流页与女演员索引页可能非空 */
    val filters: List<FilterGroup> = emptyList(),
    /** 筛选表单内的文本搜索框（女演员页 .asearch，参数 q）；无则 null */
    val searchField: FilterSearchField? = null,
    /** 女演员个人页（/cn/actresses/&lt;slug&gt;）头部 .ahero 资料；其他页为 null */
    val actressProfile: ActressProfile? = null
)

/**
 * 筛选表单（form.filters）内的文本搜索框（女演员页 .asearch 内的 input）：
 * [key] 为提交的查询参数名（q），[hint] 为输入框占位文案（如“搜索 女演员…”）。
 */
@Serializable
data class FilterSearchField(
    val key: String,
    val hint: String
)

/** 单个筛选选项：[value] 为提交的查询参数值（如 censored/2024/release_date），[label] 为显示文案 */
@Serializable
data class FilterOption(
    val value: String,
    val label: String
)

/**
 * 一个筛选维度（对应网页 form.filters 内的一个 .sel 下拉）：
 * [key] 查询参数名（type/year/actress/sort），[label] 维度名（类型/年份/…），
 * [selectedValue] 当前选中值（来自隐藏 input 默认值或 is-on 选项），
 * [columns] 下拉菜单列数（年份等长列表网页用 2 列，其余 1 列）。
 */
@Serializable
data class FilterGroup(
    val key: String,
    val label: String,
    val options: List<FilterOption>,
    val selectedValue: String = "",
    val columns: Int = 1
) {
    /** 当前选中选项的显示文案；找不到匹配项时回退到首个选项（通常是“全部”） */
    val selectedLabel: String
        get() = options.firstOrNull { it.value == selectedValue }?.label
            ?: options.firstOrNull()?.label.orEmpty()

    /**
     * 是否选了非默认值（用于筛选栏高亮/重置入口）。
     * 网页每个菜单首个选项即该维度默认项（全部/发布日期），以其 value 为基准比较。
     */
    val isActive: Boolean
        get() = selectedValue != (options.firstOrNull()?.value ?: "")
}

/**
 * 索引聚合页条目（网页两种列表结构的归一化模型）：
 * - 网格条目 .ggrid > a.gchip（类别/发行）：仅名称 + 数量文案；
 * - 排行行 .slist > a.srow（系列）：带 [rank] 序号与 [meta] 文案
 *   （如“4,476个视频 · 4.2M次观看”），UI 据此渲染整行样式；
 * - 演员卡 .agrid > .actress（演员）：带 [avatarUrl] 头像，UI 渲染头像卡片。
 *
 * path 已含语言前缀（如 /cn/genres/11、/cn/series/22、/cn/actresses/xxx），
 * 可直接交给 fetchByPath 拉视频流。
 */
@Serializable
data class IndexEntry(
    val name: String,
    val path: String,
    /** 网格条目（.gchip）下的视频数量文案，如 "176,566"；网页未提供时为空串 */
    val count: String = "",
    /** 排行行（.srow）/演员卡（.actress）的完整副文案，如“4,476个视频 · 4.2M次观看”；为空则没有 */
    val meta: String = "",
    /** 排行行序号文案（如 "01"）；非空时 UI 渲染为整行样式 */
    val rank: String? = null,
    /** 演员头像 URL（取自 .actress__avatar 的 background-image）；非空时 UI 渲染头像卡片 */
    val avatarUrl: String = "",
    /** 制作商标识文案（.mcard__logo，如 "movies"）；非空时 UI 渲染制作商徽标卡片 */
    val logo: String = "",
    // —— 演员卡（.actress）结构化统计，均为网页原始文案 ——
    /** 视频数，如 "3,100" */
    val videos: String = "",
    /** 观看数，如 "5.2M" */
    val views: String = "",
    /** 关注者数，如 "16,291" */
    val followers: String = "",
    /** 关注按钮上的紧凑关注数，如 "16.3K"（右侧胶囊展示） */
    val followersLabel: String = ""
)

/**
 * 女演员个人页头部资料（网页 .ahero，路径形如 /cn/actresses/<slug>）：
 * - [name] 姓名（.ahero__name）
 * - [avatarUrl] 头像（.ahero__avatar 的 background-image）
 * - [meta] 属性原文（.ahero__meta，如“38岁 · 163cm · 88-59-85 · E罩杯”）
 * - [videos]/[views]/[followers]/[debut] .astat 四项统计原文（视频/观看/关注者/出道）
 * - [followersLabel] 关注按钮上的紧凑关注数（与关注者统计同源，如 16.3K）
 * 关注/取关是 Alpine save() 交互（需站点账号），App 不解析行为。
 */
@Serializable
data class ActressProfile(
    val name: String,
    val avatarUrl: String = "",
    val meta: String = "",
    val videos: String = "",
    val views: String = "",
    val followers: String = "",
    val debut: String = "",
    val followersLabel: String = ""
)

/** 收藏记录：只持久化网格卡片展示与详情跳转所需字段，按收藏时间倒序 */
data class FavoriteRecord(
    val video: Video,
    val favoritedAt: Long
)

/** 观看历史记录 */
data class WatchRecord(
    val video: Video,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long,
    /** 上次观看的选集下标（0 起），多集影片再进入时自动切回该集并续播；单集恒为 0 */
    val episodeIndex: Int = 0
) {
    /** 播放进度 0f..1f */
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
