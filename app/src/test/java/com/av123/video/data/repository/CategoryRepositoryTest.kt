package com.av123.video.data.repository

import com.av123.video.data.db.PageCacheDao
import com.av123.video.data.db.PageCacheEntity
import com.av123.video.data.model.CategoryPage
import com.av123.video.data.model.HomeFeed
import com.av123.video.data.model.Video
import com.av123.video.data.net.ErrorReporter
import com.av123.video.data.source.VideoDetail
import com.av123.video.data.source.VideoSite
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类/栏目列表职责拆分后的行为测试：
 * 首屏成功落盘、翻页不覆盖缓存、失败保留旧内容、筛选页不落盘、冷启动水合。
 */
class CategoryRepositoryTest {

    private class FakePageCacheDao : PageCacheDao {
        val entries = mutableMapOf<String, PageCacheEntity>()
        override suspend fun getAll(): List<PageCacheEntity> = entries.values.toList()
        override suspend fun getAllForHydration(): List<PageCacheEntity> =
            entries.values.filterNot { it.cacheKey.startsWith(PageCacheStore.DET_KEY_PREFIX) }
        override suspend fun detailKeysBeyond(keep: Int): List<String> = emptyList()
        override suspend fun deleteByKeys(cacheKeys: List<String>) {
            cacheKeys.forEach { entries.remove(it) }
        }
        override suspend fun totalBytes(): Long =
            entries.values.sumOf { it.payloadJson.length.toLong() }
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

    private class FakeSite(
        var categoryPage: CategoryPage = CategoryPage(emptyList()),
        var channelPage: CategoryPage = CategoryPage(emptyList())
    ) : VideoSite {
        var failCategory = false

        override suspend fun fetchHomeFeed(): HomeFeed = throw RuntimeException("no feed in test")

        override suspend fun fetchByCategory(category: String, page: Int): CategoryPage {
            if (failCategory) throw IOException("boom")
            return categoryPage
        }

        override suspend fun fetchByPath(
            path: String,
            label: String,
            page: Int,
            params: Map<String, String>
        ): CategoryPage = channelPage

        override suspend fun fetchVideoDetail(reference: Video): VideoDetail =
            throw NotImplementedError()

        override suspend fun search(keyword: String, page: Int): CategoryPage =
            throw NotImplementedError()

        override suspend fun parseRelatedFromRenderedHtml(
            html: String,
            baseUrlHint: String
        ): List<Video> = throw NotImplementedError()
    }

    private val dao = FakePageCacheDao()
    private val store = PageCacheStore(dao)
    private val site = FakeSite()
    private val repository = CategoryRepository(
        dataSource = site,
        scope = CoroutineScope(Dispatchers.Unconfined),
        errorReporter = ErrorReporter(),
        pageCacheStore = store
    )

    @Test
    fun loadCategoryPage_firstPagePersistsButPagingDoesNotOverwriteCache() = runBlocking {
        site.categoryPage = CategoryPage(
            videos = listOf(Video(id = "v1", title = "第一页", category = "热门"))
        )
        repository.loadCategoryPage("热门", page = 1).join()

        val first = repository.categoryPages.value.getValue("热门")
        assertEquals(listOf("v1"), first.videos.map { it.id })
        assertEquals(1, first.page)
        assertFalse(first.isLoading)
        val cached = store.getList(PageCacheStore.categoryKey("热门"))
        assertNotNull("第一页应落盘", cached)
        assertEquals(listOf("v1"), cached!!.first.page.videos.map { it.id })

        site.categoryPage = CategoryPage(
            videos = listOf(Video(id = "v2", title = "第二页", category = "热门"))
        )
        repository.loadCategoryPage("热门", page = 2).join()

        val second = repository.categoryPages.value.getValue("热门")
        assertEquals(listOf("v2"), second.videos.map { it.id })
        assertEquals(2, second.page)
        assertEquals(
            "翻页结果不应覆盖冷启动缓存",
            listOf("v1"),
            store.getList(PageCacheStore.categoryKey("热门"))!!.first.page.videos.map { it.id }
        )
    }

    @Test
    fun loadCategoryPage_failureKeepsExistingVideosAndExposesError() = runBlocking {
        site.categoryPage = CategoryPage(
            videos = listOf(Video(id = "v1", title = "已有内容", category = "热门"))
        )
        repository.loadCategoryPage("热门", page = 1).join()

        site.failCategory = true
        repository.loadCategoryPage("热门", page = 2).join()

        val failed = repository.categoryPages.value.getValue("热门")
        assertEquals("失败不清空已有列表", listOf("v1"), failed.videos.map { it.id })
        assertNotNull(failed.error)
        assertFalse(failed.isLoading)
    }

    @Test
    fun loadChannelPage_onlyCachesUnfilteredFirstPage() = runBlocking {
        site.channelPage = CategoryPage(
            videos = listOf(Video(id = "c1", title = "栏目", category = "热门"))
        )

        repository.loadChannelPage(
            path = "/cn/hot",
            label = "热门",
            page = 1,
            queryParams = mapOf("type" to "censored")
        ).join()
        assertFalse(
            "筛选结果不应落盘",
            dao.entries.containsKey(PageCacheStore.channelKey("/cn/hot"))
        )

        repository.loadChannelPage("/cn/hot", "热门", page = 1).join()
        val cached = store.getList(PageCacheStore.channelKey("/cn/hot"))
        assertNotNull("无筛选第一页应落盘", cached)
        assertEquals(listOf("c1"), cached!!.first.page.videos.map { it.id })
    }

    @Test
    fun hydrate_restoresCategoryAndChannelFirstPages() = runBlocking {
        store.putList(
            PageCacheStore.categoryKey("热门"),
            label = null,
            page = CategoryPage(
                videos = listOf(Video(id = "v1", title = "分类缓存", category = "热门"))
            )
        )
        store.putList(
            PageCacheStore.channelKey("/cn/hot"),
            label = "热门",
            page = CategoryPage(
                videos = listOf(Video(id = "c1", title = "栏目缓存", category = "热门"))
            )
        )

        repository.hydrate(store.getForHydration())

        val category = repository.categoryPages.value.getValue("热门")
        val channel = repository.channelPages.value.getValue("/cn/hot")
        assertEquals(listOf("v1"), category.videos.map { it.id })
        assertEquals(listOf("c1"), channel.videos.map { it.id })
        assertEquals(1, category.page)
        assertEquals(1, channel.page)
        assertTrue("应标记缓存时间用于过期刷新", category.diskCachedAt > 0L)
        assertTrue(channel.diskCachedAt > 0L)
    }
}
