package com.av123.video.ui.screens.favorites

import com.av123.video.data.model.FavoriteRecord
import java.text.Collator
import java.util.Locale

/** 收藏内容排序：最近收藏（默认，与原顺序一致）/ 标题 */
enum class FavoritesSort { RECENT, TITLE }

/**
 * 排序纯函数，便于单测。
 * 标题用中文 Collator（拼音序），避免按 Unicode 码点把「阿」排在「白」后面。
 */
fun sortFavorites(records: List<FavoriteRecord>, sort: FavoritesSort): List<FavoriteRecord> =
    when (sort) {
        FavoritesSort.RECENT -> records.sortedByDescending { it.favoritedAt }
        FavoritesSort.TITLE -> {
            val collator = Collator.getInstance(Locale.CHINA)
            records.sortedWith { a, b -> collator.compare(a.video.title, b.video.title) }
        }
    }

/** 当前范围内的本地搜索：按标题包含匹配，空查询原样返回 */
fun searchFavorites(records: List<FavoriteRecord>, query: String): List<FavoriteRecord> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return records
    return records.filter { it.video.title.contains(keyword, ignoreCase = true) }
}
