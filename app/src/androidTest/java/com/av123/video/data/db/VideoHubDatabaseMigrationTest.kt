package com.av123.video.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实迁移测试：先手工建一个 v1 库（只含 v1 的四张表），
 * 再让 Room 用正式迁移链打开它，验证数据不丢、v2/v3 新表就位。
 */
@RunWith(AndroidJUnit4::class)
class VideoHubDatabaseMigrationTest {

    private lateinit var context: Context
    private var database: VideoHubDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrateFromV1_preservesUserDataAndCreatesNewTables() {
        createV1Database()

        val db = VideoHubDatabase.create(context, TEST_DB).also { database = it }
        val sqlite = db.openHelper.writableDatabase

        assertEquals(3, sqlite.version)

        // v1 的历史/收藏/搜索词/列表缓存必须原样保留
        sqlite.query(
            "SELECT positionMs, durationMs, episodeIndex FROM watch_history WHERE videoId = ?",
            arrayOf<Any?>("v1")
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(12_345L, cursor.getLong(0))
            assertEquals(600_000L, cursor.getLong(1))
            assertEquals(2, cursor.getInt(2))
        }
        sqlite.query(
            "SELECT favoritedAt FROM favorites WHERE videoId = ?",
            arrayOf<Any?>("v1")
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1_700_000_001L, cursor.getLong(0))
        }
        sqlite.query(
            "SELECT searchedAt FROM search_keywords WHERE keyword = ?",
            arrayOf<Any?>("v1")
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1_700_000_002L, cursor.getLong(0))
        }
        sqlite.query(
            "SELECT cachedAt FROM page_cache WHERE cacheKey = ?",
            arrayOf<Any?>("feed")
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1_700_000_003L, cursor.getLong(0))
        }

        // v2/v3 新表必须全部建好，且能正常写入
        listOf(
            "favorite_groups",
            "favorite_group_items",
            "followed_actresses",
            "watch_daily"
        ).forEach { table ->
            assertTrue("缺少迁移表 $table", sqlite.hasTable(table))
        }
        sqlite.execSQL(
            "INSERT INTO watch_daily(day, playMs, updatedAt) VALUES('2026-10-18', 1000, 1)"
        )
        sqlite.query(
            "SELECT playMs FROM watch_daily WHERE day = ?",
            arrayOf<Any?>("2026-10-18")
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1000L, cursor.getLong(0))
        }
    }

    private fun SupportSQLiteDatabase.hasTable(table: String): Boolean =
        query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf<Any?>(table)
        ).use { it.moveToFirst() }

    private fun createV1Database() {
        val raw = context.openOrCreateDatabase(TEST_DB, Context.MODE_PRIVATE, null)
        try {
            raw.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `watch_history` (
                    `videoId` TEXT NOT NULL,
                    `videoJson` TEXT NOT NULL,
                    `positionMs` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `watchedAt` INTEGER NOT NULL,
                    `episodeIndex` INTEGER NOT NULL,
                    PRIMARY KEY(`videoId`)
                )
                """.trimIndent()
            )
            raw.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorites` (
                    `videoId` TEXT NOT NULL,
                    `videoJson` TEXT NOT NULL,
                    `favoritedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`videoId`)
                )
                """.trimIndent()
            )
            raw.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `search_keywords` (
                    `keyword` TEXT NOT NULL,
                    `searchedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`keyword`)
                )
                """.trimIndent()
            )
            raw.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `page_cache` (
                    `cacheKey` TEXT NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `cachedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`cacheKey`)
                )
                """.trimIndent()
            )

            raw.execSQL(
                "INSERT INTO watch_history VALUES('v1', '{}', 12345, 600000, 1700000000, 2)"
            )
            raw.execSQL("INSERT INTO favorites VALUES('v1', '{}', 1700000001)")
            raw.execSQL("INSERT INTO search_keywords VALUES('v1', 1700000002)")
            raw.execSQL("INSERT INTO page_cache VALUES('feed', '{}', 1700000003)")
            raw.version = 1
        } finally {
            raw.close()
        }
    }

    private companion object {
        const val TEST_DB = "videohub-migration-test.db"
    }
}
