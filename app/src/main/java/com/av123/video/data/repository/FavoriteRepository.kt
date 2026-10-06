package com.av123.video.data.repository

import com.av123.video.data.db.AppJson
import com.av123.video.data.db.FavoriteDao
import com.av123.video.data.db.FavoriteEntity
import com.av123.video.data.db.FavoriteGroupDao
import com.av123.video.data.db.FavoriteGroupEntity
import com.av123.video.data.db.FavoriteGroupItemEntity
import com.av123.video.data.model.FavoriteRecord
import com.av123.video.data.model.Video
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 收藏夹（文件夹）领域模型 */
data class FavoriteGroup(
    val id: Long,
    val name: String,
    val createdAt: Long
)

/** 收藏-收藏夹成员关系领域模型 */
data class FavoriteGroupItem(
    val groupId: Long,
    val videoId: String,
    val addedAt: Long
)

/** 新建/重命名收藏夹的结果（重名时由 UI 提示） */
sealed interface GroupEditResult {
    data class Success(val group: FavoriteGroup) : GroupEditResult
    data object DuplicateName : GroupEditResult
}

/**
 * 收藏仓库：Room 持久化，按收藏时间倒序。完整 Video 以 JSON 存储，
 * 站点字段扩展（演员/简介/选集等）后收藏卡片与详情恢复无需改表结构。
 * 收藏夹（多对多文件夹）经 [groupDao] 管理。
 */
class FavoriteRepository(
    private val favoriteDao: FavoriteDao,
    private val groupDao: FavoriteGroupDao
) {

    val favorites: Flow<List<FavoriteRecord>> =
        favoriteDao.observeAll().map { entities -> entities.map { it.toRecord() } }

    /** 已收藏的视频 id 集合，供详情页等按 id 判断收藏态 */
    val favoriteIds: Flow<Set<String>> =
        favoriteDao.observeAll().map { entities -> entities.map { it.videoId }.toSet() }

    /** 是否已收藏指定视频 */
    suspend fun isFavorite(videoId: String): Boolean =
        favoriteDao.observeIsFavorite(videoId).first() != null

    /** 添加收藏；已存在时保留原收藏时间，不顶到最前 */
    suspend fun add(video: Video) {
        favoriteDao.insertIfAbsent(
            FavoriteEntity(
                videoId = video.id,
                videoJson = video.toJson(),
                favoritedAt = System.currentTimeMillis()
            )
        )
    }

    /** 取消收藏 */
    suspend fun remove(videoId: String) {
        favoriteDao.delete(videoId)
    }

    /** 批量取消收藏（多选删除） */
    suspend fun delete(videoIds: Collection<String>) {
        if (videoIds.isNotEmpty()) favoriteDao.delete(videoIds)
    }

    /** 撤销删除：按原始收藏时间完整恢复记录 */
    suspend fun restore(records: List<FavoriteRecord>) {
        if (records.isEmpty()) return
        favoriteDao.upsertAll(
            records.map { record ->
                FavoriteEntity(
                    videoId = record.video.id,
                    videoJson = record.video.toJson(),
                    favoritedAt = record.favoritedAt
                )
            }
        )
    }

    suspend fun clear() {
        favoriteDao.clear()
    }

    // —— 收藏夹 ——

    val groups: Flow<List<FavoriteGroup>> =
        groupDao.observeGroups().map { entities ->
            entities.map { FavoriteGroup(it.groupId, it.name, it.createdAt) }
        }

    val groupItems: Flow<List<FavoriteGroupItem>> =
        groupDao.observeItems().map { entities ->
            entities.map { FavoriteGroupItem(it.groupId, it.videoId, it.addedAt) }
        }

    /**
     * 新建收藏夹。名称去首尾空格后为空或与已有收藏夹同名时，
     * 返回 [GroupEditResult.DuplicateName]，不重复创建。
     */
    suspend fun createGroup(rawName: String): GroupEditResult {
        val name = rawName.trim()
        if (name.isBlank()) return GroupEditResult.DuplicateName
        if (groupDao.findByName(name) != null) return GroupEditResult.DuplicateName
        val id = groupDao.insert(
            FavoriteGroupEntity(name = name, createdAt = System.currentTimeMillis())
        )
        // IGNORE 命中唯一索引返回 -1：并发创建同名时回查
        val entity = if (id >= 0) groupDao.get(id) else groupDao.findByName(name)
        return if (entity != null) {
            GroupEditResult.Success(FavoriteGroup(entity.groupId, entity.name, entity.createdAt))
        } else {
            GroupEditResult.DuplicateName
        }
    }

    /** 重命名收藏夹；目标名称已存在时返回 [GroupEditResult.DuplicateName] */
    suspend fun renameGroup(groupId: Long, rawName: String): GroupEditResult {
        val name = rawName.trim()
        if (name.isBlank()) return GroupEditResult.DuplicateName
        val existing = groupDao.findByName(name)
        if (existing != null && existing.groupId != groupId) {
            return GroupEditResult.DuplicateName
        }
        groupDao.rename(groupId, name)
        val entity = groupDao.get(groupId)
        return if (entity != null) {
            GroupEditResult.Success(FavoriteGroup(entity.groupId, entity.name, entity.createdAt))
        } else {
            GroupEditResult.DuplicateName
        }
    }

    /** 删除收藏夹（成员关系由外键级联清理，收藏视频本身保留） */
    suspend fun deleteGroup(groupId: Long) {
        groupDao.delete(groupId)
    }

    /** 把若干收藏视频加入/移出收藏夹（多选管理用） */
    suspend fun setGroupMembership(
        groupId: Long,
        videoIds: Collection<String>,
        member: Boolean
    ) {
        if (videoIds.isEmpty()) return
        if (member) {
            val now = System.currentTimeMillis()
            groupDao.upsertItems(
                videoIds.map {
                    FavoriteGroupItemEntity(groupId = groupId, videoId = it, addedAt = now)
                }
            )
        } else {
            groupDao.removeItems(groupId, videoIds.toList())
        }
    }
}

/** Entity -> 领域模型；videoJson 损坏时用 id 兜底 */
internal fun FavoriteEntity.toRecord(): FavoriteRecord = FavoriteRecord(
    video = runCatching {
        AppJson.decodeFromString(Video.serializer(), videoJson)
    }.getOrNull() ?: Video(id = videoId, title = videoId, category = ""),
    favoritedAt = favoritedAt
)
