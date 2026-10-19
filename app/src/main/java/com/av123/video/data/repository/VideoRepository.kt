package com.av123.video.data.repository

import com.av123.video.data.model.HomeFeed
import com.av123.video.data.model.Video
import com.av123.video.data.net.AppError
import com.av123.video.data.net.ErrorReporter
import com.av123.video.data.source.VideoSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 视频仓库 facade：UI 只与仓库交互，不关心数据来自缓存还是网页。
 * 首页、分类/分栏列表、搜索与详情已拆到 [FeedRepository]、[CategoryRepository]、
 * [SearchRepository]、[DetailRepository]；本类保留列表缓存聚合、播放缓存与兼容转发。
 */
class VideoRepository(
    private val dataSource: VideoSite,
    private val scope: CoroutineScope,
    private val errorReporter: ErrorReporter,
    private val pageCacheStore: PageCacheStore
) {
    private val feedRepository = FeedRepository(dataSource, scope, errorReporter, pageCacheStore)
    private val categoryRepository =
        CategoryRepository(dataSource, scope, errorReporter, pageCacheStore)
    private val searchRepository = SearchRepository(dataSource, scope, errorReporter)

    /** 详情仓库通过回调读取列表缓存，不直接依赖上述三个仓库 */
    private val detailRepository = DetailRepository(
        dataSource = dataSource,
        pageCacheStore = pageCacheStore,
        errorReporter = errorReporter,
        cachedVideoProvider = { findCachedVideo(it) }
    )

    /** 进程内播放源缓存，独立于仓库状态 */
    private val playbackCache = PlaybackCache()

    val feed: StateFlow<HomeFeed?> = feedRepository.feed
    val isRefreshing: StateFlow<Boolean> = feedRepository.isRefreshing
    val error: StateFlow<AppError?> = feedRepository.error
    val categoryPages: StateFlow<Map<String, CategoryPageState>> = categoryRepository.categoryPages
    val channelPages: StateFlow<Map<String, CategoryPageState>> = categoryRepository.channelPages
    val searchState: StateFlow<SearchState> = searchRepository.state

    init {
        scope.launch {
            // 冷启动：先把首页/列表页磁盘缓存灌入内存（先缓存后网络 SWR）
            val cached = pageCacheStore.getForHydration()
            val feedCachedAt = feedRepository.hydrate(cached)
            categoryRepository.hydrate(cached)
            // 顺带收敛详情缓存，避免观看历史增长后无限落盘
            pageCacheStore.pruneDetailCache()
            val cachedFeed = feedRepository.feed.value
            val stale = cachedFeed == null ||
                System.currentTimeMillis() - feedCachedAt > FeedRepository.FEED_STALE_MS
            // 无缓存必须拉取（失败走整屏错误）；有缓存但过期则后台静默刷新，不弹打扰
            if (stale) feedRepository.refreshFeed(reportFailureSnackbar = cachedFeed != null)
        }
    }

    /**
     * 拉取首页数据；[force] 为 true 时强制刷新。
     * 返回加载任务，调用方可通过 join 等待其完成；已在刷新中或非强制且已有缓存时返回 null。
     */
    fun refresh(force: Boolean = false): Job? = feedRepository.refresh(force)

    /**
     * 首次进入某分类时拉取其列表页第一页；已加载过则保留原状态
     * （停留在上次翻到的页码），不重复请求。
     */
    fun ensureCategoryLoaded(category: String) =
        categoryRepository.ensureCategoryLoaded(category)

    /** 拉取某分类的指定页；加载期间保留已有视频列表 */
    fun loadCategoryPage(category: String, page: Int): Job =
        categoryRepository.loadCategoryPage(category, page)

    /** 首次进入某分栏栏目时拉取其列表页第一页；已加载过则保留原状态 */
    fun ensureChannelLoaded(path: String, label: String) =
        categoryRepository.ensureChannelLoaded(path, label)

    /** 拉取某分栏栏目的指定页；[queryParams] 为当前筛选条件 */
    fun loadChannelPage(
        path: String,
        label: String,
        page: Int,
        queryParams: Map<String, String> = emptyMap()
    ): Job = categoryRepository.loadChannelPage(path, label, page, queryParams)

    /**
     * 关键词搜索：请求 {baseUrl}{localePath}/search?keyword=<关键词>[&page=<页码>]。
     * - 新关键词：强制从第一页开始，清空上一次结果/分页/总数；
     * - 翻页（关键词不变）：保留已有列表，加载期间顶部显示进度条，避免整屏闪烁。
     * 结果同时进入列表缓存，从搜索结果进入详情页时可携带详情页链接
     * （sourcePageUrl/detailPath）。
     */
    fun search(keyword: String, page: Int = 1) {
        searchRepository.search(keyword, page)
    }

    /** 清空搜索状态：回到未搜索的初始引导页（点击搜索框 X 时调用） */
    fun clearSearch() {
        searchRepository.clear()
    }

    /**
     * 列表缓存查找：首页 feed、搜索结果与分类/栏目列表中的条目
     * （含封面/标题/详情页链接），详情页可先用它即时渲染，
     * 避免等待详情页网络请求期间整屏骨架/黑屏。
     */
    private fun findCachedVideo(videoId: String): Video? =
        feedRepository.cachedVideo(videoId)
            ?: searchRepository.cachedVideo(videoId)
            ?: categoryRepository.cachedVideo(videoId)

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
        playbackCache.get(videoId, episodeIndex)

    /**
     * 缓存某集播放源。仅在播放器确认地址可用（ExoPlayer 进入 STATE_READY，
     * 主播放列表与分片都能正常加载）后调用，避免把 403/失效地址写入缓存。
     */
    fun savePlayback(videoId: String, episodeIndex: Int, playback: PlaybackSource) {
        playbackCache.put(videoId, episodeIndex, playback)
    }

    /**
     * 剔除某集缓存播放源：缓存地址播放失败（如签名过期）时调用，
     * 让详情页回退到 WebView 重新抓流。
     */
    fun invalidatePlayback(videoId: String, episodeIndex: Int = 0) {
        playbackCache.invalidate(videoId, episodeIndex)
    }

    /** 按 id 获取详情（先磁盘后网络的 SWR，详见 [DetailRepository.getVideo]） */
    suspend fun getVideo(videoId: String): DetailResult = detailRepository.getVideo(videoId)

    /** 强制走网络刷新详情（缓存过期后台刷新 / 错误重试） */
    suspend fun refreshDetail(
        videoId: String,
        reportFailureSnackbar: Boolean = true
    ): DetailResult = detailRepository.refreshDetail(videoId, reportFailureSnackbar)

    /** 解析无头 WebView 回传 HTML 中的静态相关推荐 */
    suspend fun parseRelated(html: String, baseUrlHint: String, currentVideoId: String): List<Video> =
        detailRepository.parseRelated(html, baseUrlHint, currentVideoId)
}
