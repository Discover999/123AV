package com.av123.video.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 观看历史（按 watchedAt 倒序）。
 * 完整 Video 以 JSON 落盘（videoJson），列表卡片/详情恢复时可拿到全部字段；
 * position/duration/episode 为结构化列，续播查询无需反序列化。
 */
@Entity(tableName = "watch_history")
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val videoJson: String,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long,
    val episodeIndex: Int
)

/** 收藏记录（按 favoritedAt 倒序），videoJson 为完整 Video */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val videoId: String,
    val videoJson: String,
    val favoritedAt: Long
)

/** 搜索历史关键词（按 searchedAt 倒序，同词去重置顶） */
@Entity(tableName = "search_keywords")
data class SearchKeywordEntity(
    @PrimaryKey val keyword: String,
    val searchedAt: Long
)

/**
 * 收藏夹（文件夹）：名称唯一。一个收藏视频可属于 0..n 个收藏夹，
 * 不属于任何收藏夹的收藏即“未分组”。
 */
@Entity(
    tableName = "favorite_groups",
    indices = [Index(value = ["name"], unique = true)]
)
data class FavoriteGroupEntity(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    val name: String,
    val createdAt: Long
)

/**
 * 收藏夹-收藏视频多对多成员关系。
 * 删除收藏夹或取消视频收藏时通过外键级联清理成员关系。
 */
@Entity(
    tableName = "favorite_group_items",
    primaryKeys = ["groupId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteGroupEntity::class,
            parentColumns = ["groupId"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FavoriteEntity::class,
            parentColumns = ["videoId"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId"), Index("videoId")]
)
data class FavoriteGroupItemEntity(
    val groupId: Long,
    val videoId: String,
    val addedAt: Long
)

/**
 * 本地关注的女演员（不依赖站点账号）：
 * path 为女演员个人页站点相对路径（如 /cn/actresses/aiko），兼作身份主键；
 * 头像/名称/资料行为关注时的快照，个人中心聚合新片时直接展示无需再抓。
 */
@Entity(tableName = "followed_actresses")
data class FollowedActressEntity(
    @PrimaryKey val path: String,
    val name: String,
    val avatarUrl: String,
    val meta: String,
    val followedAt: Long
)

/**
 * 每日观看时长聚合（设备时区，day 主键格式 yyyy-MM-dd）：
 * 播放墙钟秒数增量累计（暂停/缓冲/seek 不计时），供观看统计页使用。
 * 该表 v3 才引入，历史版本无法回填，上线前的观看时长不可追溯。
 */
@Entity(tableName = "watch_daily")
data class WatchDailyEntity(
    @PrimaryKey val day: String,
    val playMs: Long,
    val updatedAt: Long
)

/**
 * 首页/列表页磁盘缓存：
 * - cacheKey 规则：feed=首页；cat:<分类名>=分类页；ch:<路径>=分栏栏目页
 * - payloadJson 为 CachedHomeFeed / CachedListPage 的 JSON
 * - 只缓存第 1 页（无筛选参数），命中后先展示再后台刷新
 */
@Entity(tableName = "page_cache")
data class PageCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payloadJson: String,
    val cachedAt: Long
)
