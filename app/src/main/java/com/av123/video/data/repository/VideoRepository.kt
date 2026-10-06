package com.av123.video.data.repository

import com.av123.video.data.db.AppJson
import com.av123.video.data.model.ActressProfile
import com.av123.video.data.model.FilterGroup
import com.av123.video.data.model.FilterSearchField
import com.av123.video.data.model.HomeFeed
import com.av123.video.data.model.IndexEntry
import com.av123.video.data.model.Pagination
import com.av123.video.data.model.Video
import com.av123.video.data.net.AppError
import com.av123.video.data.net.AppErrorKind
import com.av123.video.data.net.ErrorReporter
import com.av123.video.data.source.VideoDataSource
import com.av123.video.data.source.VideoDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
     * 冷启动先用磁盘内容即时渲染，超过 [LIST_STALE_MS] 未刷新则在进入栏目时后台静默刷新。
     */
    val diskCachedAt: Long = 0L,
    /** 最近一次加载错误（归一化类型）；无内容时整屏展示，有内容时由全局 Snackbar 提示 */
    val error: AppError? = null
)

/**
 * 搜索页状态：已提交关键词、当前页结果列表、页脚分页信息、
 * 页面头部声明的结果总数（.pagehead__count）、加载中标记与错误信息。
 */
data class SearchState(
    val keyword: String = "",
    val videos: List<Video> = emptyList(),
    val pagination: Pagination? = null,
    val page: Int = 1,
    val totalCount: Int? = null,
    val isLoading: Boolean = false,
    /** 最近一次搜索错误（归一化类型）；无结果时整屏展示，翻页失败由全局 Snackbar 提示 */
    val error: AppError? = null
)

/**
 * 已验证可用的播放源：无头 WebView 拦截到的 m3u8 地址 + CDN 防盗链校验
 * 所需请求头（Referer/Origin/User-Agent 等）。
 */
data class PlaybackSource(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * 详情加载结果：网络失败时仍可能带回列表缓存条目（cached），
 * UI 据 [error] 决定显示错误重试态还是在缓存内容上轻提示。
 */
sealed interface DetailResult {
    /**
     * @param relatedStatic 详情文档静态可见的相关推荐完整池（已去自身/去重/上限截断）。
     * UI 先展示 [RELATED_PAGE_SIZE] 条，“加载更多”就地追加；
     * 为空时 UI 才挂载无头 WebView 渲染兜底
     * @param staleFromDisk 本次结果来自磁盘缓存且已超过详情缓存 TTL：
     * UI 先渲染缓存，并在后台静默发起一次网络刷新
     */
    data class Success(
        val video: Video,
        val relatedStatic: List<Video> = emptyList(),
        val staleFromDisk: Boolean = false
    ) : DetailResult

    data class Failure(val error: AppError, val cached: Video?) : DetailResult
}

/**
 * 视频仓库：UI 只与仓库交互，不关心数据来自示例还是网页。
 */
class VideoRepository(
    private val dataSource: VideoDataSource,
    private val scope: CoroutineScope,
    private val errorReporter: ErrorReporter,
    private val pageCacheStore: PageCacheStore
) {
    private val _feed = MutableStateFlow<HomeFeed?>(null)
    val feed: StateFlow<HomeFeed?> = _feed.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    /** 各分类列表页状态（按分类名缓存，翻页/切 tab 后保留） */
    private val _categoryPages = MutableStateFlow<Map<String, CategoryPageState>>(emptyMap())
    val categoryPages: StateFlow<Map<String, CategoryPageState>> = _categoryPages.asStateFlow()

    /** 各分栏栏目列表页状态（按导航路径缓存，如 /cn/hot、/cn/11，翻页/返回后保留） */
    private val _channelPages = MutableStateFlow<Map<String, CategoryPageState>>(emptyMap())
    val channelPages: StateFlow<Map<String, CategoryPageState>> = _channelPages.asStateFlow()

    /** 搜索状态：最近一次关键词搜索的结果与加载情况 */
    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    /**
     * 已验证可用播放源的进程内缓存（"videoId#选集下标" -> m3u8 地址+请求头）。
     * 首集 key 不带后缀，兼容旧调用与阅读直觉。仅内存缓存、不持久化：
     * m3u8 普遍带时效签名，落盘后跨进程复用极易 403；同一会话内退出播放页再
     * 进入同一影片/同一集时直接复用，跳过 WebView 重新抓流。
     */
    private val playbackCache = ConcurrentHashMap<String, PlaybackSource>()

    private fun playbackCacheKey(videoId: String, episodeIndex: Int): String =
        if (episodeIndex <= 0) videoId else "$videoId#$episodeIndex"

    init {
        scope.launch {
            // 冷启动：先把首页/列表页磁盘缓存灌入内存（先缓存后网络 SWR）
            val feedCachedAt = hydrateFromDisk()
            val feed = _feed.value
            val stale = feed == null ||
                System.currentTimeMillis() - feedCachedAt > FEED_STALE_MS
            // 无缓存必须拉取（失败走整屏错误）；有缓存但过期则后台静默刷新，不弹打扰
            if (stale) refreshFeed(reportFailureSnackbar = feed != null)
        }
    }

    /**
     * 从磁盘缓存恢复首页 feed 与各栏目第一页到内存状态。返回首页缓存时间（无缓存返回 0）。
     */
    private suspend fun hydrateFromDisk(): Long {
        var feedCachedAt = 0L
        val categories = mutableMapOf<String, CategoryPageState>()
        val channels = mutableMapOf<String, CategoryPageState>()
        pageCacheStore.getAll().forEach { entity ->
            when {
                entity.cacheKey == PageCacheStore.FEED_KEY -> {
                    runCatching {
                        AppJson.decodeFromString(HomeFeed.serializer(), entity.payloadJson)
                    }.getOrNull()?.let {
                        _feed.value = it
                        feedCachedAt = entity.cachedAt
                    }
                }

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
        return feedCachedAt
    }

    /**
     * 拉取首页数据；[force] 为 true 时强制刷新。
     * 返回加载任务，调用方可通过 join 等待其完成；已在刷新中或非强制且已有缓存时返回 null。
     */
    fun refresh(force: Boolean = false): Job? {
        if (_isRefreshing.value) return null
        if (!force && _feed.value != null) return null
        return scope.launch { refreshFeed(reportFailureSnackbar = true) }
    }

    /**
     * 首页网络拉取：成功更新内存并落盘；无内容时失败写整屏错误，
     * 有内容时失败按 [reportFailureSnackbar] 决定是否弹全局 Snackbar
     * （冷启动后台静默刷新不弹，用户下拉刷新失败弹）。
     */
    private suspend fun refreshFeed(reportFailureSnackbar: Boolean) {
        _isRefreshing.value = true
        _error.value = null
        runCatching { dataSource.fetchHomeFeed() }
            .onSuccess { feed ->
                _feed.value = feed
                pageCacheStore.putFeed(feed)
            }
            .onFailure { e ->
                val error = AppError.from(e)
                if (_feed.value == null) {
                    _error.value = error
                } else if (reportFailureSnackbar) {
                    // 已有内容时（强制/后台刷新失败）：保留页面，仅轻提示不打断浏览
                    errorReporter.report(error)
                }
            }
        _isRefreshing.value = false
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
                var hadContent = false
                _categoryPages.update { states ->
                    val prev = states[category]
                    hadContent = prev?.videos?.isNotEmpty() == true
                    states + (category to (prev ?: CategoryPageState())
                        .copy(isLoading = false, error = AppError.from(e)))
                }
                // 翻页失败但列表已有内容：错误态不可见，用全局 Snackbar 补提示
                if (hadContent) errorReporter.report(e)
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
                var hadContent = false
                _channelPages.update { states ->
                    val prev = states[path]
                    hadContent = prev?.let { it.videos.isNotEmpty() || it.entries.isNotEmpty() } == true
                    states + (path to (prev ?: CategoryPageState())
                        .copy(isLoading = false, error = AppError.from(e)))
                }
                // 翻页/切换筛选失败但页面已有内容：保留筛选栏与旧列表，全局 Snackbar 轻提示，
                // 用户可直接改筛选条件或再次翻页，不会被困在错误页
                if (hadContent) errorReporter.report(e)
            }
    }

    /**
     * 关键词搜索：请求 {baseUrl}{localePath}/search?keyword=<关键词>[&page=<页码>]。
     * - 新关键词：强制从第一页开始，清空上一次结果/分页/总数；
     * - 翻页（关键词不变）：保留已有列表，加载期间顶部显示进度条，避免整屏闪烁。
     * 结果同时进入列表缓存，从搜索结果进入详情页时可携带详情页链接
     * （sourcePageUrl/detailPath）。
     */
    fun search(keyword: String, page: Int = 1) {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        val state = _searchState.value
        if (state.isLoading) return
        val isNewQuery = kw != state.keyword
        val targetPage = if (isNewQuery) 1 else page.coerceAtLeast(1)
        scope.launch {
            _searchState.update {
                it.copy(
                    keyword = kw,
                    page = targetPage,
                    videos = if (isNewQuery) emptyList() else it.videos,
                    pagination = if (isNewQuery) null else it.pagination,
                    totalCount = if (isNewQuery) null else it.totalCount,
                    isLoading = true,
                    error = null
                )
            }
            runCatching { dataSource.search(kw, targetPage) }
                .onSuccess { result ->
                    _searchState.update {
                        it.copy(
                            videos = result.videos,
                            pagination = result.pagination,
                            totalCount = result.totalCount,
                            page = targetPage,
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    var hadContent = false
                    _searchState.update {
                        hadContent = it.videos.isNotEmpty()
                        it.copy(isLoading = false, error = AppError.from(e))
                    }
                    // 新关键词首查失败：整屏错误态；翻页失败且已有结果：Snackbar 轻提示
                    if (hadContent) errorReporter.report(e)
                }
        }
    }

    /**
     * 清空搜索状态：回到未搜索的初始引导页（点击搜索框 X 时调用）。
     * 同时清掉关键词、结果列表、分页与总数。
     */
    fun clearSearch() {
        _searchState.value = SearchState()
    }

    /**
     * 列表缓存查找：首页 feed 与搜索结果中的条目（含封面/标题/详情页链接），
     * 详情页可先用它即时渲染，避免等待详情页网络请求期间整屏骨架/黑屏。
     */
    private fun findCachedVideo(videoId: String): Video? =
        _feed.value?.allVideos?.firstOrNull { it.id == videoId }
            ?: _searchState.value.videos.firstOrNull { it.id == videoId }
            ?: _channelPages.value.values.flatMap { it.videos }
                .firstOrNull { it.id == videoId }

    /**
     * 已缓存的列表条目（含封面/标题），详情页可先用它即时渲染，
     * 避免等待详情页网络请求期间整屏骨架/黑屏。
     */
    fun getCachedVideo(videoId: String): Video? = findCachedVideo(videoId)

    /**
     * 读取缓存的播放源；非 null 表示该地址在本会话内已验证可播放，
     * 详情页可直接起播，无需再次启动无头 WebView 抓流。
     */
    fun getCachedPlayback(videoId: String, episodeIndex: Int = 0): PlaybackSource? =
        playbackCache[playbackCacheKey(videoId, episodeIndex)]

    /**
     * 缓存某集播放源。仅在播放器确认地址可用（ExoPlayer 进入 STATE_READY，
     * 主播放列表与分片都能正常加载）后调用，避免把 403/失效地址写入缓存。
     */
    fun savePlayback(videoId: String, episodeIndex: Int, playback: PlaybackSource) {
        playbackCache[playbackCacheKey(videoId, episodeIndex)] = playback
    }

    /**
     * 剔除某集缓存播放源：缓存地址播放失败（如签名过期）时调用，
     * 让详情页回退到 WebView 重新抓流。
     */
    fun invalidatePlayback(videoId: String, episodeIndex: Int = 0) {
        playbackCache.remove(playbackCacheKey(videoId, episodeIndex))
    }

    /**
     * 按 id 获取详情（SWR：先磁盘后网络）：
     * 1. 有磁盘详情缓存：立即返回缓存；缓存时间超过 [DETAIL_STALE_MS] 时置 staleFromDisk，
     *    调用方据此在后台静默刷新（[refreshDetail]）；
     * 2. 无磁盘缓存：走网络（内存列表条目用于合并并即时渲染），成功后落盘；
     *    网络/解析失败返回 [DetailResult.Failure] 并按需弹 Snackbar。
     */
    suspend fun getVideo(videoId: String): DetailResult {
        pageCacheStore.getDetail(videoId)?.let { (cachedDetail, cachedAt) ->
            val stale = System.currentTimeMillis() - cachedAt > DETAIL_STALE_MS
            return DetailResult.Success(
                video = cachedDetail.video,
                relatedStatic = cachedDetail.related,
                staleFromDisk = stale
            )
        }
        return fetchDetailFromNetwork(videoId, reportFailureSnackbar = true)
    }

    /**
     * 强制走网络刷新详情（磁盘缓存过期后的后台静默刷新 / 整屏错误重试）：
     * 成功后覆盖磁盘缓存。后台静默刷新传 [reportFailureSnackbar]=false，
     * 网络失败不弹 Snackbar——页面仍在展示缓存内容，不打扰用户。
     */
    suspend fun refreshDetail(
        videoId: String,
        reportFailureSnackbar: Boolean = true
    ): DetailResult = fetchDetailFromNetwork(videoId, reportFailureSnackbar)

    /** 详情网络拉取：内存列表条目合并详情文档，成功写磁盘，失败归一化为 Failure */
    private suspend fun fetchDetailFromNetwork(
        videoId: String,
        reportFailureSnackbar: Boolean
    ): DetailResult {
        val cached = findCachedVideo(videoId)
        val detail = try {
            dataSource.fetchVideoDetail(
                cached ?: Video(id = videoId, title = videoId, category = "")
            )
        } catch (e: Exception) {
            val error = AppError.from(e)
            if (cached != null && reportFailureSnackbar) errorReporter.report(error)
            return DetailResult.Failure(error, cached)
        }
        val detailVideo = detail.video
        val merged = when {
            detailVideo != null && cached != null -> mergeVideo(cached, detailVideo)
            detailVideo != null -> detailVideo
            else -> cached
        }
        // 页面存在但解析不出任何内容（结构变更/被反爬拦截），按解析失败处理
        return if (merged != null) {
            // 静态推荐与详情来自同一份文档：去自身/去重后保留完整池（硬上限截断），
            // 与详情一起落盘；UI 先渲染一页，“加载更多”纯本地展开、无需再请求网络。
            // 池非空时 UI 不再创建无头 WebView 兜底
            val related = dedupRelated(detail.relatedStatic, merged.id, RELATED_MAX_POOL)
            pageCacheStore.putDetail(merged.id, merged, related)
            DetailResult.Success(merged, related)
        } else {
            val error = AppError(AppErrorKind.PARSE)
            DetailResult.Failure(error, null)
        }
    }

    /**
     * 解析无头 WebView 回传的详情页 HTML，得到“相关推荐”完整候选池：
     * 去掉当前影片自身与重复 id，按页面顺序保留至 [RELATED_MAX_POOL] 上限。
     * 任何异常都静默为空列表——推荐是锦上添花，不能影响详情页主流程。
     */
    suspend fun parseRelated(html: String, baseUrlHint: String, currentVideoId: String): List<Video> =
        runCatching { dataSource.parseRelatedFromRenderedHtml(html, baseUrlHint) }
            .getOrDefault(emptyList())
            .let { dedupRelated(it, currentVideoId, RELATED_MAX_POOL) }

    /** 列表数据为底，详情页解析结果覆盖（空值不覆盖列表已有内容） */
    private fun mergeVideo(base: Video, detail: Video): Video = base.copy(
        title = detail.title.ifBlank { base.title },
        code = detail.code.ifBlank { base.code },
        category = detail.category.ifBlank { base.category },
        coverUrl = detail.coverUrl.ifBlank { base.coverUrl },
        embedUrl = detail.embedUrl.ifBlank { base.embedUrl },
        synopsis = detail.synopsis.ifBlank { base.synopsis },
        infoRows = detail.infoRows.ifEmpty { base.infoRows },
        year = if (detail.year > 0) detail.year else base.year,
        episodeCount = maxOf(detail.episodeCount, base.episodeCount),
        episodes = detail.episodes.ifEmpty { base.episodes },
        durationText = detail.durationText.ifBlank { base.durationText },
        viewsText = detail.viewsText.ifBlank { base.viewsText },
        views = if (detail.views > 0) detail.views else base.views,
        publishedAgo = detail.publishedAgo.ifBlank { base.publishedAgo },
        sourcePageUrl = detail.sourcePageUrl.ifBlank { base.sourcePageUrl },
        detailPath = detail.detailPath.ifBlank { base.detailPath }
    )

    companion object {
        /** 首页磁盘缓存有效期（30 分钟）：超过后冷启动在展示缓存的同时后台静默刷新 */
        const val FEED_STALE_MS = 30L * 60_000L

        /** 列表页第一页磁盘缓存有效期：超过后再次进入该栏目时后台刷新 */
        const val LIST_STALE_MS = 60L * 60_000L

        /**
         * 相关推荐每“页”展示条数：初始 6 条，点“加载更多”再就地追加 6 条。
         * 站点侧栏实测直出 12 条，即一次“加载更多”展示全部
         */
        const val RELATED_PAGE_SIZE = 6

        /** 静态推荐池硬上限（当前站点直出 12 条，预留余量防止站点扩容后数据被截断） */
        const val RELATED_MAX_POOL = 30

        /**
         * 详情磁盘缓存有效期（24 小时）：详情主体（简介/选集/封面）极少变化，
         * 超过后保留缓存即时展示、后台静默刷新；离线时过期缓存仍可正常浏览
         */
        const val DETAIL_STALE_MS = 24L * 60 * 60_000L
    }
}

/**
 * 相关推荐去重纯函数：剔除当前影片自身与重复 id（保留首次出现顺序），
 * 再截断到 [limit]。无 Android 依赖，可直接 JVM 单测。
 */
fun dedupRelated(videos: List<Video>, currentVideoId: String, limit: Int = 6): List<Video> {
    val seen = LinkedHashSet<String>()
    val result = ArrayList<Video>()
    for (video in videos) {
        if (video.id.isBlank() || video.id == currentVideoId) continue
        if (seen.add(video.id)) result.add(video)
        if (result.size >= limit) break
    }
    return result
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
