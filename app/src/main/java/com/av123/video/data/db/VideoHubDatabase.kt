package com.av123.video.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用本地数据库：观看历史、收藏、收藏夹、关注女演员、搜索词、首页/列表页磁盘缓存。
 * 旧的 DataStore 字符串记录由 LegacyDataMigrator 一次性迁移进来。
 */
@Database(
    entities = [
        HistoryEntity::class,
        FavoriteEntity::class,
        FavoriteGroupEntity::class,
        FavoriteGroupItemEntity::class,
        FollowedActressEntity::class,
        SearchKeywordEntity::class,
        PageCacheEntity::class,
        WatchDailyEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class VideoHubDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun watchDailyDao(): WatchDailyDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun favoriteGroupDao(): FavoriteGroupDao
    abstract fun followedActressDao(): FollowedActressDao
    abstract fun searchKeywordDao(): SearchKeywordDao
    abstract fun pageCacheDao(): PageCacheDao

    companion object {
        @Volatile
        private var instance: VideoHubDatabase? = null

        /**
         * v1 -> v2：收藏夹（favorite_groups）、收藏夹成员关系（favorite_group_items）、
         * 本地关注女演员（followed_actresses）。SQL 必须与 Room 根据实体生成的结构逐字一致，
         * 否则启动时的 schema 校验会失败并走兜底重建。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_groups (
                        groupId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_groups_name` " +
                        "ON favorite_groups (`name`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_group_items (
                        groupId INTEGER NOT NULL,
                        videoId TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(groupId, videoId),
                        FOREIGN KEY(groupId) REFERENCES favorite_groups(groupId)
                            ON DELETE CASCADE,
                        FOREIGN KEY(videoId) REFERENCES favorites(videoId)
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_favorite_group_items_groupId` " +
                        "ON favorite_group_items (`groupId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_favorite_group_items_videoId` " +
                        "ON favorite_group_items (`videoId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS followed_actresses (
                        path TEXT NOT NULL,
                        name TEXT NOT NULL,
                        avatarUrl TEXT NOT NULL,
                        meta TEXT NOT NULL,
                        followedAt INTEGER NOT NULL,
                        PRIMARY KEY(path)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v2 -> v3：每日观看时长表 watch_daily（观看统计页）。
         * 新表独立、不改动既有表，历史观看时长无法回填（上线后开始累计）。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watch_daily (
                        day TEXT NOT NULL PRIMARY KEY,
                        playMs INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): VideoHubDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VideoHubDatabase::class.java,
                    "videohub.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // 迁移路径之外的意外版本兜底销毁重建（缓存可重建、收藏/历史丢失风险极小）
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
