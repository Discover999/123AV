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
        assertEquals(0L, stats.totalMs)
        assertEquals(0, stats.longestStreak)
        assertEquals(0, stats.continueWatching)
        assertEquals(HEATMAP_DAYS, stats.heatmap.size)
        assertEquals(today.minusDays((HEATMAP_DAYS - 1).toLong()), stats.heatmap.first().date)
        assertEquals(today, stats.heatmap.last().date)
        assertTrue(stats.heatmap.all { it.playMs == 0L })
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
        // 累计含窗口外的 3/3
        assertEquals(12_000L, stats.totalMs)
        assertEquals(1, stats.longestStreak)
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
        assertEquals(3, stats.longestStreak)
    }

    @Test
    fun longestStreakUsesAllDaysNotOnlyCurrentStreak() {
        val stats = buildWatchStats(
            daily = listOf(
                daily("2025-02-20", 1L),
                daily("2025-02-21", 1L),
                daily("2025-02-22", 1L),
                daily("2025-02-23", 1L),
                daily("2025-03-09", 1L),
                daily("2025-03-10", 1L)
            ),
            records = emptyList(),
            today = today
        )
        assertEquals(2, stats.streakDays)
        assertEquals(4, stats.longestStreak)
    }

    @Test
    fun heatmapCovers84DaysEndingToday() {
        val stats = buildWatchStats(
            daily = listOf(
                daily("2025-03-09", 2_000L),
                daily("2025-03-10", 4_000L),
                daily("2024-12-16", 9_999L) // today - 84，窗口外
            ),
            records = emptyList(),
            today = today
        )

        assertEquals(HEATMAP_DAYS, stats.heatmap.size)
        assertEquals(today.minusDays(83), stats.heatmap.first().date)
        assertEquals(today, stats.heatmap.last().date)
        assertEquals(0L, stats.heatmap.first().playMs)
        assertEquals(2_000L, stats.heatmap[HEATMAP_DAYS - 2].playMs)
        assertEquals(4_000L, stats.heatmap.last().playMs)
    }

    @Test
    fun continueWatchingCountsStartedButUnfinishedOnly() {
        val records = listOf(
            record("a", "X", positionMs = 50_000L, durationMs = 100_000L),    // 50%：继续
            record("b", "X", positionMs = 90_000L, durationMs = 100_000L),    // 90%：看完
            record("c", "X", positionMs = 0L, durationMs = 100_000L),         // 0 进度：不算继续
            record("d", "X", positionMs = 0L, durationMs = 0L),               // 未播
            record("e", "X", positionMs = 500L, durationMs = 0L),             // 时长未知
            record("f", "X", positionMs = 99_999L, durationMs = 100_000L),    // 99.999%：不算继续
            record("g", "X", positionMs = 1L, durationMs = 100_000L)          // 刚开始：继续
        )
        val stats = buildWatchStats(emptyList(), records, today)
        assertEquals(2, stats.continueWatching)
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
    fun finishedVideos_thresholdAndEnded() {
        val records = listOf(
            record("a", "X", positionMs = 90_000L, durationMs = 100_000L),   // 恰好 0.9：看完
            record("b", "X", positionMs = 89_999L, durationMs = 100_000L),   // 差一点：未看完
            record("c", "X", positionMs = 100_000L, durationMs = 100_000L),  // ENDED 回传满时长：看完
            record("d", "X", positionMs = 0L, durationMs = 0L),              // 从未播放：不算
            record("e", "X", positionMs = 500L, durationMs = 0L),            // 时长未知：不算
            record("f", "X", positionMs = 0L, durationMs = 100_000L)         // 缓冲完未起播：不算看完
        )
        val stats = buildWatchStats(emptyList(), records, today)

        assertEquals(6, stats.totalVideos)
        assertEquals(2, stats.finishedVideos)
        assertEquals(2f / 6f, stats.finishedRatio, 0.0001f)
        // b 的 89.999% 是唯一「起播但没看完」的记录
        assertEquals(1, stats.continueWatching)
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
