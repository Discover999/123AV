package com.av123.video.data.repository

import com.av123.video.data.db.PageCacheDao
import com.av123.video.data.db.PageCacheEntity
import com.av123.video.data.model.CategoryPage
import com.av123.video.data.model.HomeFeed
import com.av123.video.data.model.Video
import com.av123.video.data.net.ErrorReporter
import com.av123.video.data.source.VideoDataSource
import com.av123.video.data.source.VideoDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 详情页磁盘缓存（SWR）链路测试：
 * 用内存假 DAO + 假数据源验证“无缓存走网络并落盘 / 再次进入命中磁盘不打网络 /
 * 过期标记 stale / refreshDetail 强制刷新覆盖”四个关键行为。
 */
class VideoRepositoryDetailCacheTest {

    private class FakePageCacheDao : PageCacheDao {
        val entries = mutableMapOf<String, PageCacheEntity>()
        override suspend fun getAll(): List<PageCacheEntity> = entries.values.toList()
        override suspend fun get(cacheKey: String): PageCacheEntity? = entries[cacheKey]
        override suspend fun upsert(entity: PageCacheEntity) {
            entries[entity.cacheKey] = entity
        }
        override suspend fun delete(cacheKey: String) {
            entries.remove(cacheKey)
        }
        override suspend fun clear() {
            entries.clear()
        }
    }

    private class FakeDataSource(var detail: VideoDetail) : VideoDataSource {
        var detailCalls = 0
        override suspend fun fetchVideoDetail(reference: Video): VideoDetail {
            detailCalls++
            return detail
        }
        // 仓库 init 会尝试拉首页，抛异常被其内部 runCatching 吞掉，其余方法本测试用不到
        override suspend fun fetchHomeFeed(): HomeFeed = throw RuntimeException("no feed in test")
        override suspend fun fetchByCategory(category: String, page: Int): CategoryPage =
            throw NotImplementedError()
        override suspend fun fetchByPath(
            path: String,
            label: String,
            page: Int,
            params: Map<String, String>
        ): CategoryPage = throw NotImplementedError()
        override suspend fun search(keyword: String, page: Int): CategoryPage =
            throw NotImplementedError()
        override suspend fun parseRelatedFromRenderedHtml(
            html: String,
            baseUrlHint: String
        ): List<Video> = throw NotImplementedError()
    }

    private lateinit var dao: FakePageCacheDao
    private lateinit var dataSource: FakeDataSource
    private lateinit var repository: VideoRepository

    @Before
    fun setup() {
        dao = FakePageCacheDao()
        dataSource = FakeDataSource(
            VideoDetail(
                video = Video(id = "v1", title = "网络详情", category = ""),
                relatedStatic = listOf(Video(id = "rel1", title = "相关影片", category = ""))
            )
        )
        repository = VideoRepository(
            dataSource = dataSource,
            // Unconfined：init 的首页加载立即跑完（其失败被内部吞掉），不干扰本测试
            scope = CoroutineScope(Dispatchers.Unconfined),
            errorReporter = ErrorReporter(),
            pageCacheStore = PageCacheStore(dao)
        )
    }

    @Test
    fun getVideo_noCache_fetchesNetworkAndPersists() = runBlocking {
        val first = repository.getVideo("v1")
        assertTrue(first is DetailResult.Success)
        first as DetailResult.Success
        assertEquals("网络详情", first.video.title)
        assertEquals(listOf("rel1"), first.relatedStatic.map { it.id })
        assertFalse(first.staleFromDisk)
        assertEquals(1, dataSource.detailCalls)
        // 磁盘已有缓存：第二次进入直接读盘，不再发起网络请求
        val second = repository.getVideo("v1") as DetailResult.Success
        assertEquals(1, dataSource.detailCalls)
        assertFalse(second.staleFromDisk)
        assertEquals("网络详情", second.video.title)
    }

    @Test
    fun getVideo_expiredCache_markedStaleWithoutNetwork() = runBlocking {
        repository.getVideo("v1") // 先写新鲜缓存
        val key = PageCacheStore.detailKey("v1")
        val entity = dao.entries.getValue(key)
        // 回退落盘时间到 25 小时前（TTL 为 24 小时）
        dao.entries[key] = entity.copy(cachedAt = System.currentTimeMillis() - 25L * 3_600_000L)

        val result = repository.getVideo("v1") as DetailResult.Success

        assertTrue(result.staleFromDisk)
        assertEquals("仍展示缓存内容", "网络详情", result.video.title)
        assertEquals("读缓存不打网络", 1, dataSource.detailCalls)
    }

    @Test
    fun getVideo_keepsFullRelatedPoolBeyondFirstPage() = runBlocking {
        // 静态池 13 条（超过首屏 6 条）：Success 与磁盘缓存都应保留完整池，
        // 由 UI 分页展示，不能在数据层截断
        val pool = (1..13).map {
            Video(id = "rel$it", title = "相关 $it", category = "")
        }
        dataSource.detail = VideoDetail(
            video = Video(id = "v2", title = "多推荐影片", category = ""),
            relatedStatic = pool
        )

        val first = repository.getVideo("v2") as DetailResult.Success
        assertEquals((1..13).map { "rel$it" }, first.relatedStatic.map { it.id })

        // 重进命中磁盘缓存，完整池仍在（离线也能“加载更多”）
        val cached = repository.getVideo("v2") as DetailResult.Success
        assertEquals(13, cached.relatedStatic.size)
    }

    @Test
    fun refreshDetail_forcesNetworkAndOverwritesCache() = runBlocking {
        repository.getVideo("v1") // 落盘旧详情
        dataSource.detail = VideoDetail(
            video = Video(id = "v1", title = "刷新后的详情", category = ""),
            relatedStatic = emptyList()
        )

        val result = repository.refreshDetail("v1") as DetailResult.Success

        assertEquals("刷新后的详情", result.video.title)
        assertEquals(2, dataSource.detailCalls)
        // 磁盘缓存已被新内容覆盖
        val cached = repository.getVideo("v1") as DetailResult.Success
        assertEquals("刷新后的详情", cached.video.title)
        assertEquals("不再额外打网络", 2, dataSource.detailCalls)
    }
}
