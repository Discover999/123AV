package com.av123.video.data.repository

import com.av123.video.data.db.WatchDailyEntity
import com.av123.video.data.model.Video
import com.av123.video.data.model.WatchRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WatchStatsTest {

    private val today = LocalDate.of(2025, 3, 10) // 周一

    private fun daily(day: String, ms: Long) = WatchDailyEntity(day, ms, 0L)

    private fun record(
        id: String,
        category: String,
        positionMs: Long,
        durationMs: Long
    ) = WatchRecord(
        video = Video(id = id, title = id, category = category),
        positionMs = positionMs,
        durationMs = durationMs,
        watchedAt = 0L
    )

    @Test
    fun emptyInput_zeroedWeekAndTotals() {
        val stats = buildWatchStats(emptyList(), emptyList(), today)

        assertEquals(0L, stats.todayMs)
        assertEquals(0L, stats.last7DaysMs)
        assertEquals(7, stats.week7.size)
        assertEquals(today.minusDays(6), stats.week7.first().date)
        assertEquals(today, stats.week7.last().date)
        assertTrue(stats.week7.all { it.playMs == 0L })
        assertEquals(0, stats.streakDays)
        assertEquals(0, stats.activeDays)
        assertEquals(0, stats.totalVideos)
        assertEquals(0, stats.finishedVideos)
        assertEquals(0f, stats.finishedRatio, 0f)
        assertTrue(stats.categories.isEmpty())
    }

    @Test
    fun week7_zeroFillsGapsAndSums() {
        val stats = buildWatchStats(
            daily = listOf(
                daily("2025-03-03", 5_000L), // 窗口外（第 8 天）
                daily("2025-03-05", 1_000L),
                daily("2025-03-07", 2_000L),
                daily("2025-03-10", 4_000L)
            ),
            records = emptyList(),
            today = today
        )

        assertEquals(4_000L, stats.todayMs)
        assertEquals(7_000L, stats.last7DaysMs)
        assertEquals(listOf(0L, 1000L, 0L, 2000L, 0L, 0L, 4000L), stats.week7.map { it.playMs })
        assertEquals(4, stats.activeDays)
    }

    @Test
    fun streakCountsBackFromToday() {
        val stats = buildWatchStats(
            daily = listOf(
                daily("2025-03-10", 1L),
                daily("2025-03-09", 1L),
                daily("2025-03-08", 1L),
                daily("2025-03-06", 1L) // 07 断档
            ),
            records = emptyList(),
            today = today
        )
        assertEquals(3, stats.streakDays)
    }

    @Test
    fun streakCanStartFromYesterdayWhenTodayEmpty() {
        val stats = buildWatchStats(
            daily = listOf(
                daily("2025-03-09", 1L),
                daily("2025-03-08", 1L)
            ),
            records = emptyList(),
            today = today
        )
        assertEquals(2, stats.streakDays)
    }

    @Test
    fun zeroPlayMsRowIsNotAnActiveDay() {
        val stats = buildWatchStats(
            daily = listOf(daily("2025-03-10", 0L)),
            records = emptyList(),
            today = today
        )
        assertEquals(0, stats.activeDays)
        assertEquals(0, stats.streakDays)
    }

    @Test
    fun invalidDayRowsAreIgnored() {
        val stats = buildWatchStats(
            daily = listOf(
                daily("not-a-date", 9_999L),
                daily("2025/03/10", 9_999L),
                daily("2025-03-10", 3_000L)
            ),
            records = emptyList(),
            today = today
        )
        assertEquals(1, stats.activeDays)
        assertEquals(3_000L, stats.todayMs)
    }

    @Test
    fun finishedVideos_thresholdAndEndedZero() {
        val records = listOf(
            record("a", "X", positionMs = 90_000L, durationMs = 100_000L),   // 恰好 0.9：看完
            record("b", "X", positionMs = 89_999L, durationMs = 100_000L),   // 差一点：未看完
            record("c", "X", positionMs = 0L, durationMs = 1_200_000L),      // ENDED 写 0：看完
            record("d", "X", positionMs = 0L, durationMs = 0L),              // 从未播放：不算
            record("e", "X", positionMs = 500L, durationMs = 0L)             // 时长未知：不算
        )
        val stats = buildWatchStats(emptyList(), records, today)

        assertEquals(5, stats.totalVideos)
        assertEquals(2, stats.finishedVideos)
        assertEquals(0.4f, stats.finishedRatio, 0.0001f)
    }

    @Test
    fun categories_blankExcluded_sortedByCountThenName() {
        val records = listOf(
            record("1", "剧情", 1, 1),
            record("2", "剧情", 1, 1),
            record("3", "动作", 1, 1),
            record("4", "  ", 1, 1),
            record("5", "", 1, 1),
            record("6", "喜剧", 1, 1)
        )
        val stats = buildWatchStats(emptyList(), records, today)

        assertEquals(
            listOf("剧情" to 2, "动作" to 1, "喜剧" to 1),
            stats.categories.map { it.category to it.count }
        )
    }
}
