package com.av123.video.ui.screens.favorites

import com.av123.video.data.model.FavoriteRecord
import com.av123.video.data.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesSortTest {

    private fun record(id: String, title: String, favoritedAt: Long) = FavoriteRecord(
        video = Video(id = id, title = title, category = ""),
        favoritedAt = favoritedAt
    )

    // 纯中文样本：中文 Collator 会把拉丁字母排到中文之前，混排断言不稳定
    private val records = listOf(
        record("a", "白日梦", 100L),
        record("b", "阿尔法", 300L),
        record("c", "长夜", 200L)
    )

    @Test
    fun recentSortKeepsFavoritedAtDescending() {
        val sorted = sortFavorites(records, FavoritesSort.RECENT)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.video.id })
    }

    @Test
    fun titleSortUsesChineseCollation() {
        val sorted = sortFavorites(records, FavoritesSort.TITLE)
        // 拼音序：阿 (a) < 白 (bai) < 长 (chang)
        assertEquals(listOf("b", "a", "c"), sorted.map { it.video.id })
    }

    @Test
    fun searchMatchesTitleSubstring() {
        assertEquals(listOf("c"), searchFavorites(records, "夜").map { it.video.id })
        assertEquals(records, searchFavorites(records, "  "))
    }

    @Test
    fun emptyQueryReturnsInputOrder() {
        assertEquals(listOf("a", "b", "c"), searchFavorites(records, "").map { it.video.id })
    }
}
