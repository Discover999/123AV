package com.av123.video.data.repository

import com.av123.video.data.db.FollowedActressDao
import com.av123.video.data.db.FollowedActressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 本地关注的女演员（纯本机数据，不依赖站点账号）。
 * [path] 为女演员个人页站点相对路径，兼作唯一身份。
 */
data class FollowedActress(
    val path: String,
    val name: String,
    val avatarUrl: String = "",
    val meta: String = "",
    val followedAt: Long = 0L
)

/** 关注女演员仓库：关注列表、关注态查询、关注/取关 */
class FollowedActressRepository(private val dao: FollowedActressDao) {

    val actresses: Flow<List<FollowedActress>> =
        dao.observeAll().map { entities -> entities.map { it.toModel() } }

    /** 指定演员是否已关注，供演员页头部按钮实时响应 */
    fun isFollowed(path: String): Flow<Boolean> =
        dao.observeByPath(path).map { it != null }

    /** 关注（已存在则用最新资料快照覆盖） */
    suspend fun follow(actress: FollowedActress) {
        dao.upsert(
            FollowedActressEntity(
                path = actress.path,
                name = actress.name,
                avatarUrl = actress.avatarUrl,
                meta = actress.meta,
                followedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun unfollow(path: String) {
        dao.delete(path)
    }
}

internal fun FollowedActressEntity.toModel(): FollowedActress = FollowedActress(
    path = path,
    name = name,
    avatarUrl = avatarUrl,
    meta = meta,
    followedAt = followedAt
)
