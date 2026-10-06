package com.av123.video.data.repository

import com.av123.video.data.db.AppJson
import com.av123.video.data.db.PageCacheDao
import com.av123.video.data.db.PageCacheEntity
import com.av123.video.data.model.CategoryPage
import com.av123.video.data.model.HomeFeed
import com.av123.video.data.model.Video
import kotlinx.serialization.Serializable

/** 列表页缓存载荷：label 为分栏栏目的显示名（分类页为 null） */
@Serializable
internal data class CachedListPage(
    val label: String? = null,
    val page: CategoryPage
)

/** 详情页缓存载荷：合并后的完整影片 + 静态相关推荐（详情页 SWR 缓存） */
@Serializable
internal data class CachedDetail(
    val video: Video,
    val related: List<Video> = emptyList()
)

/**
 * 首页/列表页磁盘缓存（Room）。冷启动先读缓存即时渲染，再走网络静默刷新；
 * 只缓存第一页（筛选/翻页结果不缓存，避免冷启动落在某次临时筛选条件上）。
 */
class PageCacheStore(private val dao: PageCacheDao) {

    /** 全部缓存条目，启动时一次性灌入内存状态 */
    suspend fun getAll(): List<PageCacheEntity> = dao.getAll()

    suspend fun getFeed(): HomeFeed? = dao.get(FEED_KEY)?.let { entity ->
        runCatching { AppJson.decodeFromString<HomeFeed>(entity.payloadJson) }.getOrNull()
    }

    suspend fun putFeed(feed: HomeFeed) {
        dao.upsert(
            PageCacheEntity(
                cacheKey = FEED_KEY,
                payloadJson = AppJson.encodeToString(HomeFeed.serializer(), feed),
                cachedAt = System.currentTimeMillis()
            )
        )
    }

    internal suspend fun getList(cacheKey: String): Pair<CachedListPage, Long>? =
        dao.get(cacheKey)?.let { entity ->
            runCatching {
                AppJson.decodeFromString(CachedListPage.serializer(), entity.payloadJson)
            }.getOrNull()?.let { it to entity.cachedAt }
        }

    /** 反序列化列表页缓存载荷（损坏返回 null，调用方按无缓存处理） */
    internal fun decodeList(json: String): CachedListPage? =
        runCatching { AppJson.decodeFromString(CachedListPage.serializer(), json) }.getOrNull()

    suspend fun putList(cacheKey: String, label: String?, page: CategoryPage) {
        // 完全空页不写缓存：无映射的分类（如首页"精选"）会返回空 CategoryPage，
        // 落盘后冷启动会把它误恢复成真实栏目状态（空列表+无分页，UI 不会再回退到 feed）。
        // 同时清掉可能存在的同 key 旧缓存，防止缓存永久停留在空态。
        val isEffectivelyEmpty = page.videos.isEmpty() &&
            page.entries.isEmpty() &&
            page.pagination == null &&
            page.filters.isEmpty() &&
            page.searchField == null &&
            page.actressProfile == null
        if (isEffectivelyEmpty) {
            dao.delete(cacheKey)
            return
        }
        dao.upsert(
            PageCacheEntity(
                cacheKey = cacheKey,
                payloadJson = AppJson.encodeToString(
                    CachedListPage.serializer(),
                    CachedListPage(label = label, page = page)
                ),
                cachedAt = System.currentTimeMillis()
            )
        )
    }

    // —— 详情页缓存 ——

    /**
     * 读详情页缓存（损坏返回 null，调用方按无缓存走网络）。
     * 返回载荷与落盘时间，供调用方判断是否过期需后台刷新。
     */
    internal suspend fun getDetail(videoId: String): Pair<CachedDetail, Long>? =
        dao.get(detailKey(videoId))?.let { entity ->
            runCatching {
                AppJson.decodeFromString(CachedDetail.serializer(), entity.payloadJson)
            }.getOrNull()?.let { it to entity.cachedAt }
        }

    /** 写详情页缓存（合并后的完整影片 + 相关推荐），覆盖同影片旧缓存 */
    suspend fun putDetail(videoId: String, video: Video, related: List<Video>) {
        dao.upsert(
            PageCacheEntity(
                cacheKey = detailKey(videoId),
                payloadJson = AppJson.encodeToString(
                    CachedDetail.serializer(),
                    CachedDetail(video, related)
                ),
                cachedAt = System.currentTimeMillis()
            )
        )
    }

    companion object {
        const val FEED_KEY = "feed"
        const val CAT_KEY_PREFIX = "cat:"
        const val CH_KEY_PREFIX = "ch:"
        const val DET_KEY_PREFIX = "det:"
        fun categoryKey(category: String) = "$CAT_KEY_PREFIX$category"
        fun channelKey(path: String) = "$CH_KEY_PREFIX$path"
        fun detailKey(videoId: String) = "$DET_KEY_PREFIX$videoId"
    }
}
