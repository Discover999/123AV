package com.av123.video.data.repository

import com.av123.video.data.db.PageCacheEntity
import com.av123.video.data.diagnostics.DiagnosticsMetrics
import com.av123.video.data.model.ActressProfile
import com.av123.video.data.model.FilterGroup
import com.av123.video.data.model.FilterSearchField
import com.av123.video.data.model.IndexEntry
import com.av123.video.data.model.Pagination
import com.av123.video.data.model.Video
import com.av123.video.data.net.AppError
import com.av123.video.data.net.ErrorReporter
import com.av123.video.data.source.VideoSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 某个分类列表页的当前状态：已加载视频、分页信息、加载中标记与错误信息。
 * 切换 tab 后保留，回到该 tab 时停留在原页码。
 */
data class CategoryPageState(
    val videos: List<Video> = emptyList(),
    /** 索引聚合页条目（类别/演员/发行/系列）；筛选 0 结果时可能为空，需结合 [isIndex] 判断 */
    val entries: List<IndexEntry> = emptyList(),
    val pagination: Pagination? = null,
    /** 页头声明的视频总数（.pagehead__count，如 361346）；非空时标题下显示副标题 */
    val totalCount: Int? = null,
    /** 页头 .pagehead__count 原文（如“36,558位女演员”），优先于数值格式化展示 */
    val totalCountText: String? = null,
    /** 当前路径是否为索引聚合页（类别/演员/发行/系列）；为 true 时 UI 渲染条目网格 */
    val isIndex: Boolean = false,
    /** 顶部筛选维度（类型/年份，或女演员页身高/罩杯/年龄/排序）；非空时 UI 显示筛选栏 */
    val filters: List<FilterGroup> = emptyList(),
    /** 筛选表单内的文本搜索框（女演员页 q 参数）；无则 null */
    val searchField: FilterSearchField? = null,
    /** 女演员个人页头部资料（.ahero）；仅 /cn/actresses/&lt;slug&gt; 视频流页非空 */
    val actressProfile: ActressProfile? = null,
    val page: Int = 1,
    val isLoading: Boolean = false,
    /**
     * 本状态来自磁盘缓存的时间戳；0 表示本会话内已由网络加载过。
     * 冷启动先用磁盘内容即时渲染，超过 [CategoryRepository.LIST_STALE_MS]
     * 未刷新则在进入栏目时后台静默刷新。
     */
    val diskCachedAt: Long = 0L,
    /** 最近一次加载错误（归一化类型）；无内容时整屏展示，有内容时由全局 Snackbar 提示 */
    val error: AppError? = null
)

/**
 * 分类与分栏列表页职责：按分类名 / 导航路径缓存页面状态，处理首屏加载、
 * 翻页、过期静默刷新与错误归一化；VideoRepository 只做兼容转发。
 */
class CategoryRepository(
    private val dataSource: VideoSite,
    private val scope: CoroutineScope,
    private val errorReporter: ErrorReporter,
    private val pageCacheStore: PageCacheStore
) {
    /** 各分类列表页状态（按分类名缓存，翻页/切 tab 后保留） */
    private val _categoryPages = MutableStateFlow<Map<String, CategoryPageState>>(emptyMap())
    val categoryPages: StateFlow<Map<String, CategoryPageState>> = _categoryPages.asStateFlow()

    /** 各分栏栏目列表页状态（按导航路径缓存，如 /cn/hot、/cn/11，翻页/返回后保留） */
    private val _channelPages = MutableStateFlow<Map<String, CategoryPageState>>(emptyMap())
    val channelPages: StateFlow<Map<String, CategoryPageState>> = _channelPages.asStateFlow()

    /** 从冷启动缓存条目恢复各分类/栏目第一页到内存状态 */
    fun hydrate(entries: List<PageCacheEntity>) {
        val categories = mutableMapOf<String, CategoryPageState>()
        val channels = mutableMapOf<String, CategoryPageState>()
        entries.forEach { entity ->
            when {
                entity.cacheKey.startsWith(PageCacheStore.CAT_KEY_PREFIX) -> {
                    pageCacheStore.decodeList(entity.payloadJson)?.let { cached ->
                        val key = entity.cacheKey.removePrefix(PageCacheStore.CAT_KEY_PREFIX)
                        categories[key] = cached.toState(entity.cachedAt)
                    }
                }

                entity.cacheKey.startsWith(PageCacheStore.CH_KEY_PREFIX) -> {
                    pageCacheStore.decodeList(entity.payloadJson)?.let { cached ->
                        val key = entity.cacheKey.removePrefix(PageCacheStore.CH_KEY_PREFIX)
                        channels[key] = cached.toState(entity.cachedAt)
                    }
                }
            }
        }
        if (categories.isNotEmpty()) {
            _categoryPages.update { existing -> existing + categories }
        }
        if (channels.isNotEmpty()) {
            _channelPages.update { existing -> existing + channels }
        }
    }

    /**
     * 首次进入某分类时拉取其列表页第一页；已加载过则保留原状态
     * （停留在上次翻到的页码），不重复请求。
     * 若上次加载失败且没有可展示的视频，切回该 tab 时自动重试；
     * 状态来自冷启动磁盘缓存且已过期（[LIST_STALE_MS]）时，保留内容后台刷新第一页。
     */
    fun ensureCategoryLoaded(category: String) {
        val state = _categoryPages.value[category]
        when {
            state == null || (state.videos.isEmpty() && state.error != null) ->
                loadCategoryPage(category, page = state?.page ?: 1)

            state.diskCachedAt > 0L &&
                System.currentTimeMillis() - state.diskCachedAt > LIST_STALE_MS ->
                loadCategoryPage(category, page = 1)
        }
    }

    /**
     * 拉取某分类的指定页；加载期间保留已有视频列表，供 UI 显示顶部进度条而非整屏闪烁。
     * 返回加载任务，调用方可 join 等待完成（下拉刷新场景需要，不能靠观察 isLoading
     * 瞬时跳变判断结束——空页可能毫秒级返回，StateFlow 合并会丢掉中间状态）。
     */
    fun loadCategoryPage(category: String, page: Int): Job = scope.launch {
        _categoryPages.update { states ->
            states + (category to (states[category] ?: CategoryPageState())
                .copy(isLoading = true, error = null, page = page))
        }
        runCatching { dataSource.fetchByCategory(category, page) }
            .onSuccess { result ->
                _categoryPages.update { states ->
                    // 与 loadChannelPage 保持同一套字段保留策略，
                    // 避免分类页将来出现筛选/演员资料时字段在翻页后被清空
                    val prev = states[category]
                    states + (category to CategoryPageState(
                        videos = result.videos,
                        entries = result.entries,
                        pagination = result.pagination,
                        totalCount = result.totalCount ?: prev?.totalCount,
                        totalCountText = result.totalCountText ?: prev?.totalCountText,
                        isIndex = result.isIndex || prev?.isIndex == true,
                        filters = result.filters.ifEmpty { prev?.filters.orEmpty() },
                        searchField = result.searchField ?: prev?.searchField,
                        actressProfile = result.actressProfile ?: prev?.actressProfile,
                        page = page,
                        isLoading = false
                    ))
                }
                // 仅第一页落盘：翻页结果不覆盖冷启动缓存
                if (page == 1) {
                    pageCacheStore.putList(
                        PageCacheStore.categoryKey(category),
                        label = null,
                        page = result
                    )
                }
            }
            .onFailure { e ->
                val error = AppError.from(e)
                DiagnosticsMetrics.recordLoadFailure(error.kind)
                var hadContent = false
                _categoryPages.update { states ->
                    val prev = states[category]
                    hadContent = prev?.videos?.isNotEmpty() == true
                    states + (category to (prev ?: CategoryPageState())
                        .copy(isLoading = false, error = error))
                }
                // 翻页失败但列表已有内容：错误态不可见，用全局 Snackbar 补提示
                if (hadContent) errorReporter.report(error)
            }
    }

    /**
     * 首次进入某分栏栏目时拉取其列表页第一页；已加载过则保留原状态
     * （停留在上次翻到的页码），不重复请求。加载失败且无内容时自动重试。
     * 状态来自冷启动磁盘缓存且已过期（[LIST_STALE_MS]）时，保留内容后台刷新第一页。
     */
    fun ensureChannelLoaded(path: String, label: String) {
        val state = _channelPages.value[path]
        when {
            state == null ||
                (state.videos.isEmpty() && state.entries.isEmpty() && state.error != null) ->
                loadChannelPage(path, label, page = state?.page ?: 1)

            state.diskCachedAt > 0L &&
                System.currentTimeMillis() - state.diskCachedAt > LIST_STALE_MS ->
                loadChannelPage(path, label, page = 1)
        }
    }

    /**
     * 拉取某分栏栏目的指定页；加载期间保留已有列表，供 UI 显示顶部进度条。
     * [queryParams] 为顶部筛选表单当前选中的查询参数（type/year/actress/sort 等），
     * 翻页时需带上当前筛选条件；切换筛选时调用方应把 page 重置为 1。
     * 返回加载任务，调用方可 join 等待完成（下拉刷新场景，不能靠观察 isLoading
     * 瞬时跳变判断结束——空页可能毫秒级返回，StateFlow 合并会丢掉中间状态）。
     */
    fun loadChannelPage(
        path: String,
        label: String,
        page: Int,
        queryParams: Map<String, String> = emptyMap()
    ): Job = scope.launch {
        _channelPages.update { states ->
            states + (path to (states[path] ?: CategoryPageState())
                .copy(isLoading = true, error = null, page = page))
        }
        runCatching { dataSource.fetchByPath(path, label, page, queryParams) }
            .onSuccess { result ->
                _channelPages.update { states ->
                    val prev = states[path]
                    states + (path to CategoryPageState(
                        videos = result.videos,
                        entries = result.entries,
                        pagination = result.pagination,
                        // 个别页（翻页/筛选结果）未渲染 pagehead 时沿用上次总数，避免副标题闪动
                        totalCount = result.totalCount ?: prev?.totalCount,
                        totalCountText = result.totalCountText ?: prev?.totalCountText,
                        // 路径对应唯一页面形态，一旦识别为索引页后续翻页/筛选均保持
                        isIndex = result.isIndex || prev?.isIndex == true,
                        // 新结果有表单以新结果为准，兜底保留旧表单
                        // （极端情况下表单解析失败时筛选栏不至于闪没）
                        filters = result.filters.ifEmpty { prev?.filters.orEmpty() },
                        searchField = result.searchField ?: prev?.searchField,
                        // 翻页（如该演员第 2 页）通常不再渲染 .ahero，沿用首页资料避免头部闪没
                        actressProfile = result.actressProfile ?: prev?.actressProfile,
                        page = page,
                        isLoading = false
                    ))
                }
                // 仅缓存无筛选参数的第一页；筛选/翻页结果不覆盖冷启动缓存
                if (page == 1 && queryParams.isEmpty()) {
                    pageCacheStore.putList(
                        PageCacheStore.channelKey(path),
                        label = label,
                        page = result
                    )
                }
            }
            .onFailure { e ->
                val error = AppError.from(e)
                DiagnosticsMetrics.recordLoadFailure(error.kind)
                var hadContent = false
                _channelPages.update { states ->
                    val prev = states[path]
                    hadContent = prev?.let { it.videos.isNotEmpty() || it.entries.isNotEmpty() } == true
                    states + (path to (prev ?: CategoryPageState())
                        .copy(isLoading = false, error = error))
                }
                // 翻页/切换筛选失败但页面已有内容：保留筛选栏与旧列表，全局 Snackbar 轻提示，
                // 用户可直接改筛选条件或再次翻页，不会被困在错误页
                if (hadContent) errorReporter.report(error)
            }
    }

    /** 分类/栏目列表中的条目，供详情页先用列表数据即时渲染 */
    fun cachedVideo(videoId: String): Video? =
        _categoryPages.value.values.asSequence().flatMap { it.videos.asSequence() }
            .firstOrNull { it.id == videoId }
            ?: _channelPages.value.values.asSequence().flatMap { it.videos.asSequence() }
                .firstOrNull { it.id == videoId }

    companion object {
        /** 列表页第一页磁盘缓存有效期：超过后再次进入该栏目时后台刷新 */
        const val LIST_STALE_MS = 60L * 60_000L
    }
}

/** 磁盘缓存载荷 -> 内存列表状态（标记 diskCachedAt，供过期后台刷新判断） */
private fun CachedListPage.toState(cachedAt: Long): CategoryPageState = CategoryPageState(
    videos = page.videos,
    entries = page.entries,
    pagination = page.pagination,
    totalCount = page.totalCount,
    totalCountText = page.totalCountText,
    isIndex = page.isIndex,
    filters = page.filters,
    searchField = page.searchField,
    actressProfile = page.actressProfile,
    page = 1,
    isLoading = false,
    diskCachedAt = cachedAt
)
