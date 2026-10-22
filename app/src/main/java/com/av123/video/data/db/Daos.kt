package com.av123.video.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    /** 一次性快照：观看统计页计算影片总数/看完率/分类分布 */
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    suspend fun getAll(): List<HistoryEntity>

    @Query("SELECT * FROM watch_history WHERE videoId = :videoId")
    suspend fun get(videoId: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<HistoryEntity>)

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM watch_history WHERE videoId IN (:videoIds)")
    suspend fun delete(videoIds: Collection<String>)

    @Query("DELETE FROM watch_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM watch_history")
    suspend fun count(): Int

    /** 取最旧的若干条，用于超出上限时淘汰 */
    @Query("SELECT * FROM watch_history ORDER BY watchedAt ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<HistoryEntity>
}

@Dao
interface WatchDailyDao {

    /** 当天首次计时时建行（playMs=0），已存在则忽略，随后再走增量 UPDATE */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: WatchDailyEntity)

    @Query("UPDATE watch_daily SET playMs = playMs + :deltaMs, updatedAt = :updatedAt WHERE day = :day")
    suspend fun addPlayTime(day: String, deltaMs: Long, updatedAt: Long): Int

    @Query("SELECT * FROM watch_daily WHERE day BETWEEN :start AND :end ORDER BY day ASC")
    suspend fun range(start: String, end: String): List<WatchDailyEntity>

    @Query("SELECT * FROM watch_daily ORDER BY day ASC")
    suspend fun all(): List<WatchDailyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WatchDailyEntity>)

    /** 观察某天的墙钟时长（无行时为 0）；用于"我的"页入口副标题实时显示今日时长 */
    @Query("SELECT COALESCE((SELECT playMs FROM watch_daily WHERE day = :day), 0)")
    fun observeDayPlayMs(day: String): Flow<Long>

    /** 全部日期的观看时长合计；历史页汇总行用 */
    @Query("SELECT COALESCE(SUM(playMs), 0) FROM watch_daily")
    fun observeTotalPlayMs(): Flow<Long>
}

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY favoritedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT 1 FROM favorites WHERE videoId = :videoId LIMIT 1")
    fun observeIsFavorite(videoId: String): Flow<Int?>

    @Query("SELECT COUNT(*) FROM favorites")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM favorites WHERE videoId IN (:videoIds)")
    suspend fun delete(videoIds: Collection<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FavoriteEntity>)

    @Query("SELECT * FROM favorites ORDER BY favoritedAt DESC")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface SearchKeywordDao {

    @Query("SELECT * FROM search_keywords ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchKeywordEntity>>

    @Query("SELECT * FROM search_keywords ORDER BY searchedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SearchKeywordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchKeywordEntity)

    @Query("SELECT * FROM search_keywords ORDER BY searchedAt DESC")
    suspend fun getAll(): List<SearchKeywordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SearchKeywordEntity>)

    @Query("DELETE FROM search_keywords WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM search_keywords WHERE LOWER(keyword) = LOWER(:keyword)")
    suspend fun deleteIgnoreCase(keyword: String)

    @Query("DELETE FROM search_keywords")
    suspend fun clear()

    /** 超出上限时删除最旧的若干条 */
    @Query(
        "DELETE FROM search_keywords WHERE keyword IN (" +
            "SELECT keyword FROM search_keywords ORDER BY searchedAt ASC LIMIT :count)"
    )
    suspend fun deleteOldest(count: Int)

    @Query("SELECT COUNT(*) FROM search_keywords")
    suspend fun count(): Int
}

/** 页面缓存占用的轻量投影：只取 key 与载荷字节数，不把 JSON 读进内存 */
data class PageCacheStatRow(val cacheKey: String, val bytes: Long)

@Dao
interface PageCacheDao {

    @Query("SELECT * FROM page_cache")
    suspend fun getAll(): List<PageCacheEntity>

    @Query("SELECT cacheKey, LENGTH(CAST(payloadJson AS BLOB)) AS bytes FROM page_cache")
    suspend fun stats(): List<PageCacheStatRow>

    /** 冷启动水合只需要首页/列表页，不把详情缓存整包读进内存 */
    @Query("SELECT * FROM page_cache WHERE cacheKey NOT LIKE 'det:%'")
    suspend fun getAllForHydration(): List<PageCacheEntity>

    /** 按最近使用排序，返回超出保留上限的详情缓存 key */
    @Query(
        "SELECT cacheKey FROM page_cache WHERE cacheKey LIKE 'det:%' " +
            "ORDER BY cachedAt DESC LIMIT -1 OFFSET :keep"
    )
    suspend fun detailKeysBeyond(keep: Int): List<String>

    @Query("DELETE FROM page_cache WHERE cacheKey IN (:cacheKeys)")
    suspend fun deleteByKeys(cacheKeys: List<String>)

    @Query("SELECT COALESCE(SUM(LENGTH(CAST(payloadJson AS BLOB))), 0) FROM page_cache")
    suspend fun totalBytes(): Long

    @Query("SELECT * FROM page_cache WHERE cacheKey = :cacheKey")
    suspend fun get(cacheKey: String): PageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PageCacheEntity)

    @Query("DELETE FROM page_cache WHERE cacheKey = :cacheKey")
    suspend fun delete(cacheKey: String)

    @Query("DELETE FROM page_cache")
    suspend fun clear()
}

@Dao
interface FavoriteGroupDao {

    @Query("SELECT * FROM favorite_groups ORDER BY createdAt ASC")
    fun observeGroups(): Flow<List<FavoriteGroupEntity>>

    @Query("SELECT * FROM favorite_group_items")
    fun observeItems(): Flow<List<FavoriteGroupItemEntity>>

    @Query("SELECT * FROM favorite_groups ORDER BY createdAt ASC")
    suspend fun getAll(): List<FavoriteGroupEntity>

    @Query("SELECT * FROM favorite_group_items")
    suspend fun getAllItems(): List<FavoriteGroupItemEntity>

    @Query("SELECT COUNT(*) FROM favorite_groups")
    suspend fun count(): Int

    @Query("SELECT * FROM favorite_groups WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): FavoriteGroupEntity?

    @Query("SELECT * FROM favorite_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun get(groupId: Long): FavoriteGroupEntity?

    /** 重名时返回 -1（IGNORE），由调用方回查已有同名收藏夹 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FavoriteGroupEntity): Long

    @Query("UPDATE favorite_groups SET name = :name WHERE groupId = :groupId")
    suspend fun rename(groupId: Long, name: String)

    /** 成员关系由外键级联删除 */
    @Query("DELETE FROM favorite_groups WHERE groupId = :groupId")
    suspend fun delete(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<FavoriteGroupItemEntity>)

    @Query("DELETE FROM favorite_group_items WHERE groupId = :groupId AND videoId IN (:videoIds)")
    suspend fun removeItems(groupId: Long, videoIds: Collection<String>)
}

@Dao
interface FollowedActressDao {

    @Query("SELECT * FROM followed_actresses ORDER BY followedAt DESC")
    fun observeAll(): Flow<List<FollowedActressEntity>>

    @Query("SELECT * FROM followed_actresses WHERE path = :path LIMIT 1")
    fun observeByPath(path: String): Flow<FollowedActressEntity?>

    @Query("SELECT * FROM followed_actresses WHERE path = :path LIMIT 1")
    suspend fun get(path: String): FollowedActressEntity?

    @Query("SELECT * FROM followed_actresses ORDER BY followedAt DESC")
    suspend fun getAll(): List<FollowedActressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FollowedActressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FollowedActressEntity>)

    @Query("DELETE FROM followed_actresses WHERE path = :path")
    suspend fun delete(path: String)

    @Query("DELETE FROM followed_actresses")
    suspend fun clear()
}
