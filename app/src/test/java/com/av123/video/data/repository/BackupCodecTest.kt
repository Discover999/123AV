package com.av123.video.data.repository

import com.av123.video.data.db.HistoryEntity
import com.av123.video.data.db.SearchKeywordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {

    @Test
    fun backupJson_roundTripsUserData() {
        val data = BackupData(
            exportedAt = 1_700_000_000_000L,
            history = listOf(
                HistoryEntity(
                    videoId = "v1",
                    videoJson = "{}",
                    positionMs = 1_234,
                    durationMs = 5_678,
                    watchedAt = 100,
                    episodeIndex = 2
                )
            ),
            searchKeywords = listOf(SearchKeywordEntity("测试", 42))
        )

        val decoded = decodeBackup(encodeBackup(data))

        assertEquals(BACKUP_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(1_700_000_000_000L, decoded.exportedAt)
        assertEquals(data.history, decoded.history)
        assertEquals(data.searchKeywords, decoded.searchKeywords)
    }

    @Test
    fun formatCacheSize_usesReadableUnits() {
        assertEquals("512 B", formatCacheSize(512))
        assertEquals("1.0 KB", formatCacheSize(1024))
        assertEquals("1.5 KB", formatCacheSize(1536))
        assertEquals("1.0 MB", formatCacheSize(1024L * 1024L))
        assertEquals("1.00 GB", formatCacheSize(1024L * 1024L * 1024L))
    }
}
