package com.av123.video.data.db

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.av123.video.data.model.Video
import kotlinx.coroutines.flow.first

// 旧版本三个 DataStore 文件名（与重写前的仓库一致），迁移完成后清空
private val Context.legacyHistoryDataStore by preferencesDataStore(name = "watch_history")
private val Context.legacyFavoritesDataStore by preferencesDataStore(name = "favorite_videos")
private val Context.legacySearchDataStore by preferencesDataStore(name = "search_history")
private val Context.migrationStateDataStore by preferencesDataStore(name = "app_state")

/**
 * 把旧版 DataStore 中以 "|" 拼接字符串存储的观看历史/收藏/搜索词
 * 一次性迁移进 Room。迁移成功后清空旧文件并写入标记，不再重复执行。
 *
 * 幂等：中途失败不会写标记，下次启动重跑；upsert 语义保证重复导入不产生重复行。
 */
class LegacyDataMigrator(
    private val context: Context,
    private val database: VideoHubDatabase
) {
    suspend fun migrateIfNeeded() {
        val prefs = context.migrationStateDataStore.data.first()
        if (prefs[KEY_MIGRATION_DONE] == true.toString()) return

        migrateHistory()
        migrateFavorites()
        migrateSearchKeywords()

        context.migrationStateDataStore.edit { it[KEY_MIGRATION_DONE] = true.toString() }
    }

    private suspend fun migrateHistory() {
        val store = context.legacyHistoryDataStore
        val prefs = store.data.first()
        val entities = prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(HISTORY_KEY_PREFIX) || value !is String) return@mapNotNull null
            val f = parseHistory(value.split(SEP)) ?: return@mapNotNull null
            val videoId = key.name.removePrefix(HISTORY_KEY_PREFIX)
            HistoryEntity(
                videoId = videoId,
                videoJson = AppJson.encodeToString(
                    Video.serializer(),
                    Video(
                        id = videoId,
                        title = f.title,
                        category = f.category,
                        coverUrl = f.coverUrl
                    )
                ),
                positionMs = f.positionMs,
                durationMs = f.durationMs,
                watchedAt = f.watchedAt,
                episodeIndex = f.episodeIndex
            )
        }
        entities.forEach { database.historyDao().upsert(it) }
        // 迁移后执行一次上限裁剪
        val count = database.historyDao().count()
        if (count > HISTORY_MAX) {
            database.historyDao()
                .delete(database.historyDao().oldest(count - HISTORY_MAX).map { it.videoId })
        }
        store.edit { it.clear() }
    }

    private suspend fun migrateFavorites() {
        val store = context.legacyFavoritesDataStore
        val prefs = store.data.first()
        val entities = prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(FAV_KEY_PREFIX) || value !is String) return@mapNotNull null
            val parts = value.split(SEP)
            if (parts.size < FAV_FIELD_COUNT) return@mapNotNull null
            val videoId = key.name.removePrefix(FAV_KEY_PREFIX)
            // 标题可能含 "|"：首字段时间戳 + 尾部 6 个定长字段，中间拼回标题
            val titleEnd = parts.size - FAV_TAIL_COUNT
            FavoriteEntity(
                videoId = videoId,
                videoJson = AppJson.encodeToString(
                    Video.serializer(),
                    Video(
                        id = videoId,
                        title = parts.subList(1, titleEnd).joinToString(SEP),
                        category = parts[titleEnd],
                        coverUrl = parts[titleEnd + 1],
                        code = parts[titleEnd + 2],
                        durationText = parts[titleEnd + 3],
                        viewsText = parts[titleEnd + 4],
                        publishedAgo = parts[titleEnd + 5]
                    )
                ),
                favoritedAt = parts[0].toLongOrNull() ?: 0L
            )
        }
        database.favoriteDao().upsertAll(entities)
        store.edit { it.clear() }
    }

    private suspend fun migrateSearchKeywords() {
        val store = context.legacySearchDataStore
        val prefs = store.data.first()
        val entries = prefs.asMap().mapNotNull { (key, value) ->
            val timestamp = key.name.takeIf { it.startsWith(SEARCH_KEY_PREFIX) }
                ?.removePrefix(SEARCH_KEY_PREFIX)?.toLongOrNull()
                ?: return@mapNotNull null
            if (value !is String) return@mapNotNull null
            SearchKeywordEntity(keyword = value, searchedAt = timestamp)
        }.sortedByDescending { it.searchedAt }
        // 旧库可能超上限，只迁最新 SEARCH_MAX 条
        entries.take(SEARCH_MAX).forEach { database.searchKeywordDao().upsert(it) }
        store.edit { it.clear() }
    }

    /**
     * 旧版历史字段切分，兼容两种格式：
     * 7 段（新）：pos|dur|watchedAt|title|category|coverUrl|episodeIndex
     * 6 段（旧）：无末尾 episodeIndex
     */
    private fun parseHistory(parts: List<String>): HistoryFields? {
        if (parts.size < HISTORY_MIN_FIELDS) return null
        val hasEpisode = parts.size >= HISTORY_FULL_FIELDS
        val tailCount = if (hasEpisode) 3 else 2
        val categoryIndex = parts.size - tailCount
        return HistoryFields(
            positionMs = parts[0].toLongOrNull() ?: 0L,
            durationMs = parts[1].toLongOrNull() ?: 0L,
            watchedAt = parts[2].toLongOrNull() ?: 0L,
            title = parts.subList(3, categoryIndex).joinToString(SEP),
            category = parts[categoryIndex],
            coverUrl = parts[categoryIndex + 1],
            episodeIndex = if (hasEpisode) parts.last().toIntOrNull() ?: 0 else 0
        )
    }

    private data class HistoryFields(
        val positionMs: Long,
        val durationMs: Long,
        val watchedAt: Long,
        val title: String,
        val category: String,
        val coverUrl: String,
        val episodeIndex: Int
    )

    private companion object {
        val KEY_MIGRATION_DONE: Preferences.Key<String> =
            stringPreferencesKey("legacy_store_migrated_to_room")
        const val SEP = "|"
        const val HISTORY_KEY_PREFIX = "rec_"
        const val HISTORY_MIN_FIELDS = 6
        const val HISTORY_FULL_FIELDS = 7
        const val HISTORY_MAX = 500
        const val FAV_KEY_PREFIX = "fav_"
        const val FAV_FIELD_COUNT = 8
        const val FAV_TAIL_COUNT = 6
        const val SEARCH_KEY_PREFIX = "kw_"
        const val SEARCH_MAX = 20
    }
}
