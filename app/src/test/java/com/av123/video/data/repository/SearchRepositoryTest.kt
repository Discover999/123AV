package com.av123.video.data.repository

import com.av123.video.data.model.CategoryPage
import com.av123.video.data.model.HomeFeed
import com.av123.video.data.model.Pagination
import com.av123.video.data.model.Video
import com.av123.video.data.net.ErrorEvent
import com.av123.video.data.net.ErrorReporter
import com.av123.video.data.source.VideoDetail
import com.av123.video.data.source.VideoSite
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 搜索职责拆分后的生命周期：新关键词重置、翻页替换、失败保留旧内容与 Snackbar。 */
class SearchRepositoryTest {

    private class FakeSite : VideoSite {
        var searchPage = CategoryPage(emptyList())
        var failSearch = false

        override suspend fun fetchHomeFeed(): HomeFeed = throw RuntimeException("not used")

        override suspend fun fetchByCategory(category: String, page: Int): CategoryPage =
            throw RuntimeException("not used")

        override suspend fun fetchByPath(
            path: String,
            label: String,
            page: Int,
            params: Map<String, String>
        ): CategoryPage = throw RuntimeException("not used")

        override suspend fun fetchVideoDetail(reference: Video): VideoDetail =
            throw NotImplementedError()

        override suspend fun search(keyword: String, page: Int): CategoryPage {
            if (failSearch) throw IOException("boom")
            return searchPage
        }

        override suspend fun parseRelatedFromRenderedHtml(
            html: String,
            baseUrlHint: String
        ): List<Video> = emptyList()
    }

    private val site = FakeSite()
    private val reporter = ErrorReporter()
    private val reported = mutableListOf<ErrorEvent>()
    private val collectorScope = CoroutineScope(Dispatchers.Unconfined)
    private val repository = SearchRepository(
        dataSource = site,
        scope = CoroutineScope(Dispatchers.Unconfined),
        errorReporter = reporter
    )

    @Before
    fun setUp() {
        collectorScope.launch { reporter.events.collect { reported += it } }
    }

    @After
    fun tearDown() {
        collectorScope.cancel()
    }

    @Test
    fun search_newKeyword_loadsFirstPage() {
        site.searchPage = CategoryPage(
            videos = listOf(Video(id = "v1", title = "one", category = "cat")),
            pagination = Pagination(currentPage = 1, totalPages = 2)
        )

        repository.search("one")

        val state = repository.state.value
        assertEquals("one", state.keyword)
        assertEquals(listOf("v1"), state.videos.map { it.id })
        assertEquals(1, state.page)
        assertFalse(state.isLoading)
    }

    @Test
    fun search_nextPage_keepsKeywordAndReplacesPage() {
        site.searchPage = CategoryPage(
            videos = listOf(Video(id = "v1", title = "one", category = "cat"))
        )
        repository.search("one")

        site.searchPage = CategoryPage(
            videos = listOf(Video(id = "v2", title = "two", category = "cat"))
        )
        repository.search("one", page = 2)

        val state = repository.state.value
        assertEquals("one", state.keyword)
        assertEquals(2, state.page)
        assertEquals(listOf("v2"), state.videos.map { it.id })
    }

    @Test
    fun search_failureWithContent_keepsVideosAndReportsSnackbar() {
        site.searchPage = CategoryPage(
            videos = listOf(Video(id = "v1", title = "one", category = "cat"))
        )
        repository.search("one")

        site.failSearch = true
        repository.search("one", page = 2)

        val state = repository.state.value
        assertEquals(listOf("v1"), state.videos.map { it.id })
        assertNotNull(state.error)
        assertFalse(state.isLoading)
        assertEquals(1, reported.size)
    }

    @Test
    fun search_failureWithNoContent_setsErrorWithoutSnackbar() {
        site.failSearch = true

        repository.search("one")

        val state = repository.state.value
        assertTrue(state.videos.isEmpty())
        assertNotNull(state.error)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun search_blankKeyword_isIgnored() {
        repository.search("   ")

        assertEquals("", repository.state.value.keyword)
        assertTrue(repository.state.value.videos.isEmpty())
    }

    @Test
    fun clear_resetsSearchState() {
        site.searchPage = CategoryPage(
            videos = listOf(Video(id = "v1", title = "one", category = "cat"))
        )
        repository.search("one")

        repository.clear()

        assertEquals(SearchState(), repository.state.value)
    }
}
