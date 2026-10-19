package com.av123.video.data.repository

import com.av123.video.data.db.AppJson
import com.av123.video.data.db.PageCacheEntity
import com.av123.video.data.diagnostics.DiagnosticsMetrics
import com.av123.video.data.model.HomeFeed
import com.av123.video.data.runCatchingCancellable
import com.av123.video.data.model.Video
import com.av123.video.data.net.AppError
import com.av123.video.data.net.ErrorReporter
import com.av123.video.data.source.VideoSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 首页聚合数据职责：feed 状态、下拉刷新与整屏错误；VideoRepository 只做兼容转发。 */
class FeedRepository(
    private val dataSource: VideoSite,
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

    /** 从冷启动缓存条目恢复首页 feed，返回缓存落盘时间（无缓存返回 0） */
    fun hydrate(entries: List<PageCacheEntity>): Long {
        val entity = entries.firstOrNull { it.cacheKey == PageCacheStore.FEED_KEY }
            ?: return 0L
        val feed = runCatching {
            AppJson.decodeFromString(HomeFeed.serializer(), entity.payloadJson)
        }.getOrNull() ?: return 0L
        _feed.value = feed
        return entity.cachedAt
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
    internal suspend fun refreshFeed(reportFailureSnackbar: Boolean) {
        _isRefreshing.value = true
        _error.value = null
        runCatchingCancellable { dataSource.fetchHomeFeed() }
            .onSuccess { feed ->
                _feed.value = feed
                pageCacheStore.putFeed(feed)
            }
            .onFailure { e ->
                val error = AppError.from(e)
                DiagnosticsMetrics.recordLoadFailure(error.kind)
                if (_feed.value == null) {
                    _error.value = error
                } else if (reportFailureSnackbar) {
                    // 已有内容时（强制/后台刷新失败）：保留页面，仅轻提示不打断浏览
                    errorReporter.report(error)
                }
            }
        _isRefreshing.value = false
    }

    /** 首页 feed 中的条目，供详情页先用列表数据即时渲染 */
    fun cachedVideo(videoId: String): Video? =
        _feed.value?.allVideos?.firstOrNull { it.id == videoId }

    companion object {
        /** 首页磁盘缓存有效期（30 分钟）：超过后冷启动在展示缓存的同时后台静默刷新 */
        const val FEED_STALE_MS = 30L * 60_000L
    }
}
