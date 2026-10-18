package com.av123.video.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.av123.video.data.db.PageCacheEntity
import com.av123.video.data.db.VideoHubDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageCacheStoreTest {

    private lateinit var database: VideoHubDatabase
    private lateinit var store: PageCacheStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            VideoHubDatabase::class.java
        ).build()
        store = PageCacheStore(database.pageCacheDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun hydrationExcludesDetailAndPruneKeepsNewest() = runBlocking {
        val dao = database.pageCacheDao()
        dao.upsert(PageCacheEntity(PageCacheStore.FEED_KEY, "{}", 1))
        repeat(5) { i ->
            val key = PageCacheStore.detailKey("v$i")
            dao.upsert(PageCacheEntity(key, "{}", i.toLong()))
        }

        val hydrated = store.getForHydration()
        assertEquals(1, hydrated.size)
        assertTrue(hydrated.none { it.cacheKey.startsWith(PageCacheStore.DET_KEY_PREFIX) })

        store.pruneDetailCache(3)

        val detailKeys = dao.getAll()
            .map { it.cacheKey }
            .filter { it.startsWith(PageCacheStore.DET_KEY_PREFIX) }
        assertEquals(3, detailKeys.size)
        assertTrue(detailKeys.contains(PageCacheStore.detailKey("v4")))
        assertFalse(detailKeys.contains(PageCacheStore.detailKey("v0")))
    }
}
