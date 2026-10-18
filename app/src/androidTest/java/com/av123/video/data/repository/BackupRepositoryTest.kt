package com.av123.video.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.av123.video.data.db.FavoriteEntity
import com.av123.video.data.db.FavoriteGroupEntity
import com.av123.video.data.db.FavoriteGroupItemEntity
import com.av123.video.data.db.HistoryEntity
import com.av123.video.data.db.SearchKeywordEntity
import com.av123.video.data.db.VideoHubDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private lateinit var database: VideoHubDatabase
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            VideoHubDatabase::class.java
        ).build()
        repository = BackupRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportImport_restoresFavoritesGroupsHistoryAndSearch() = runBlocking {
        database.favoriteDao().upsertAll(
            listOf(FavoriteEntity("v1", "{}", 100))
        )
        val groupId = database.favoriteGroupDao().insert(
            FavoriteGroupEntity(name = "默认", createdAt = 1)
        )
        database.favoriteGroupDao().upsertItems(
            listOf(FavoriteGroupItemEntity(groupId, "v1", addedAt = 2))
        )
        database.searchKeywordDao().upsert(SearchKeywordEntity("keyword", 3))
        database.historyDao().upsert(
            HistoryEntity("v1", "{}", positionMs = 10, durationMs = 20, watchedAt = 30, episodeIndex = 1)
        )

        val backup = repository.exportJson()

        database.favoriteDao().clear()
        database.favoriteGroupDao().delete(groupId)
        database.searchKeywordDao().clear()
        database.historyDao().clear()

        val result = repository.importJson(backup)

        assertTrue(result is BackupImportResult.Success)
        assertEquals(1, database.favoriteDao().getAll().size)
        assertEquals(1, database.historyDao().count())
        assertEquals(1, database.searchKeywordDao().getAll().size)
        assertEquals(1, database.favoriteGroupDao().getAll().size)
        assertEquals(1, database.favoriteGroupDao().getAllItems().size)
    }

    @Test
    fun import_rejectsNewerSchemaVersion() = runBlocking {
        val result = repository.importJson("""{"schemaVersion":99}""")
        assertTrue(result is BackupImportResult.Failure)
    }
}
