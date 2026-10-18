package com.av123.video.data.repository

import androidx.room.withTransaction
import com.av123.video.data.db.FavoriteEntity
import com.av123.video.data.db.FavoriteGroupEntity
import com.av123.video.data.db.FavoriteGroupItemEntity
import com.av123.video.data.db.FollowedActressEntity
import com.av123.video.data.db.HistoryEntity
import com.av123.video.data.db.SearchKeywordEntity
import com.av123.video.data.db.VideoHubDatabase
import com.av123.video.data.db.WatchDailyEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 备份格式版本：新增字段保持兼容，破坏性变更时递增并拒绝旧客户端导入 */
internal const val BACKUP_SCHEMA_VERSION = 1

@Serializable
internal data class BackupData(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAt: Long = 0L,
    val history: List<HistoryEntity> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val favoriteGroups: List<FavoriteGroupEntity> = emptyList(),
    val favoriteGroupItems: List<FavoriteGroupItemEntity> = emptyList(),
    val followedActresses: List<FollowedActressEntity> = emptyList(),
    val searchKeywords: List<SearchKeywordEntity> = emptyList(),
    val watchDaily: List<WatchDailyEntity> = emptyList()
)

data class BackupCounts(
    val history: Int,
    val favorites: Int,
    val favoriteGroups: Int,
    val favoriteGroupItems: Int,
    val followedActresses: Int,
    val searchKeywords: Int,
    val watchDays: Int
)

sealed interface BackupImportResult {
    data class Success(val counts: BackupCounts) : BackupImportResult
    data class Failure(val message: String) : BackupImportResult
}

private val BackupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

internal fun encodeBackup(data: BackupData): String =
    BackupJson.encodeToString(BackupData.serializer(), data)

internal fun decodeBackup(raw: String): BackupData =
    BackupJson.decodeFromString(BackupData.serializer(), raw)

/**
 * 用户数据导出/导入：历史、收藏、收藏夹、关注、搜索词、观看时长。
 * 导入按主键合并（同 ID 以备份为准），收藏夹按名称复用已有分组，
 * 避免直接写入自增 groupId 造成唯一索引冲突。
 */
class BackupRepository(private val database: VideoHubDatabase) {

    suspend fun exportJson(): String = database.withTransaction {
        encodeBackup(
            BackupData(
                exportedAt = System.currentTimeMillis(),
                history = database.historyDao().getAll(),
                favorites = database.favoriteDao().getAll(),
                favoriteGroups = database.favoriteGroupDao().getAll(),
                favoriteGroupItems = database.favoriteGroupDao().getAllItems(),
                followedActresses = database.followedActressDao().getAll(),
                searchKeywords = database.searchKeywordDao().getAll(),
                watchDaily = database.watchDailyDao().all()
            )
        )
    }

    suspend fun importJson(raw: String): BackupImportResult {
        if (raw.isBlank()) return BackupImportResult.Failure("备份内容为空")
        val data = runCatching { decodeBackup(raw) }.getOrElse {
            return BackupImportResult.Failure("JSON 解析失败: ${it.message ?: "未知错误"}")
        }
        if (data.schemaVersion > BACKUP_SCHEMA_VERSION) {
            return BackupImportResult.Failure("备份版本过新，请升级 App 后再导入")
        }

        return runCatching {
            database.withTransaction {
                val favoriteDao = database.favoriteDao()
                favoriteDao.upsertAll(data.favorites)
                val validFavorites = favoriteDao.getAll().mapTo(HashSet()) { it.videoId }

                val groupDao = database.favoriteGroupDao()
                val groupIdMap = mutableMapOf<Long, Long>()
                data.favoriteGroups.forEach { group ->
                    val newId = groupDao.findByName(group.name)?.groupId
                        ?: groupDao.insert(group.copy(groupId = 0)).takeIf { it > 0 }
                        ?: groupDao.findByName(group.name)?.groupId
                    if (newId != null) groupIdMap[group.groupId] = newId
                }
                val items = data.favoriteGroupItems.mapNotNull { item ->
                    val groupId = groupIdMap[item.groupId] ?: return@mapNotNull null
                    if (item.videoId !in validFavorites) return@mapNotNull null
                    item.copy(groupId = groupId)
                }
                groupDao.upsertItems(items)

                database.historyDao().upsertAll(data.history)
                database.followedActressDao().upsertAll(data.followedActresses)
                database.searchKeywordDao().upsertAll(data.searchKeywords)
                database.watchDailyDao().upsertAll(data.watchDaily)

                BackupCounts(
                    history = data.history.size,
                    favorites = data.favorites.size,
                    favoriteGroups = data.favoriteGroups.size,
                    favoriteGroupItems = items.size,
                    followedActresses = data.followedActresses.size,
                    searchKeywords = data.searchKeywords.size,
                    watchDays = data.watchDaily.size
                )
            }
        }.fold(
            onSuccess = { BackupImportResult.Success(it) },
            onFailure = { BackupImportResult.Failure("导入失败: ${it.message ?: "未知错误"}") }
        )
    }
}
