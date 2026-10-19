package com.av123.video.data.repository

import com.av123.video.data.diagnostics.DiagnosticsMetrics
import com.av123.video.data.model.Video
import com.av123.video.data.net.AppError
import com.av123.video.data.net.AppErrorKind
import com.av123.video.data.net.ErrorReporter
import com.av123.video.data.runCatchingCancellable
import com.av123.video.data.source.VideoSite
import kotlinx.coroutines.CancellationException

/**
 * 详情加载结果：网络失败时仍可能带回列表缓存条目（cached），
 * UI 据 [error] 决定显示错误重试态还是在缓存内容上轻提示。
 */
sealed interface DetailResult {
    /**
     * @param relatedStatic 详情文档静态可见的相关推荐完整池（已去自身/去重/上限截断）。
     * UI 先展示 [DetailRepository.RELATED_PAGE_SIZE] 条，“加载更多”就地追加；
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
 * 详情页职责：磁盘缓存 SWR、网络拉取与列表条目合并、静态相关推荐去重。
 * 列表缓存查找通过 [cachedVideoProvider] 注入，避免依赖首页/搜索/分类仓库；
 * VideoRepository 只做兼容转发。
 */
class DetailRepository(
    private val dataSource: VideoSite,
    private val pageCacheStore: PageCacheStore,
    private val errorReporter: ErrorReporter,
    private val cachedVideoProvider: (String) -> Video?
) {
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

    /**
     * 解析无头 WebView 回传的详情页 HTML，得到“相关推荐”完整候选池：
     * 去掉当前影片自身与重复 id，按页面顺序保留至 [RELATED_MAX_POOL] 上限。
     * 任何异常都静默为空列表——推荐是锦上添花，不能影响详情页主流程。
     */
    suspend fun parseRelated(html: String, baseUrlHint: String, currentVideoId: String): List<Video> =
        runCatchingCancellable { dataSource.parseRelatedFromRenderedHtml(html, baseUrlHint) }
            .getOrDefault(emptyList())
            .let { dedupRelated(it, currentVideoId, RELATED_MAX_POOL) }

    /** 详情网络拉取：内存列表条目合并详情文档，成功写磁盘，失败归一化为 Failure */
    private suspend fun fetchDetailFromNetwork(
        videoId: String,
        reportFailureSnackbar: Boolean
    ): DetailResult {
        val cached = cachedVideoProvider(videoId)
        val detail = try {
            dataSource.fetchVideoDetail(
                cached ?: Video(id = videoId, title = videoId, category = "")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = AppError.from(e)
            DiagnosticsMetrics.recordLoadFailure(error.kind)
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
            DiagnosticsMetrics.recordLoadFailure(error.kind)
            DetailResult.Failure(error, null)
        }
    }

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
