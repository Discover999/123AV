package com.av123.video.data.repository

import com.av123.video.data.db.WatchDailyEntity
import com.av123.video.data.model.WatchRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** 某一天的观看时长（统计页近 7 天柱图单元） */
data class DayWatch(
    val date: LocalDate,
    val playMs: Long
)

/** 分类维度的影片数量 */
data class CategoryStat(
    val category: String,
    val count: Int
)

/**
 * 观看统计快照（全部由纯函数 [buildWatchStats] 计算，无 Android 依赖、可单测）：
 * - [todayMs] 今日播放墙钟时长
 * - [last7DaysMs] 近 7 天（含今天）总时长
 * - [week7] 近 7 天逐日时长，最旧→今天，缺测日期补 0
 * - [totalVideos] 历史累计影片数（观看历史条数）
 * - [finishedVideos] 已看完影片数（见 [FINISHED_THRESHOLD] 与 0 进度兜底说明）
 * - [streakDays] 截至今天/昨天的连续观看天数
 * - [activeDays] 有观看时长记录的总天数
 * - [categories] 分类影片数，按数量降序、名称升序，UI 取前 3
 */
data class WatchStats(
    val todayMs: Long,
    val last7DaysMs: Long,
    val week7: List<DayWatch>,
    val totalVideos: Int,
    val finishedVideos: Int,
    val streakDays: Int,
    val activeDays: Int,
    val categories: List<CategoryStat>
) {
    /** 已看完比例 0f..1f（无历史时为 0） */
    val finishedRatio: Float
        get() = if (totalVideos == 0) 0f else finishedVideos.toFloat() / totalVideos
}

/** 看完判定阈值：保存的播放进度达到时长 90% 视为看完 */
const val FINISHED_THRESHOLD = 0.9f

private val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * 由每日时长聚合行与观看历史快照计算统计结果。
 *
 * 注意：播放器播放结束（STATE_ENDED）时历史进度会被写成 0，因此看完判定除
 * “进度 ≥ [FINISHED_THRESHOLD]”外，还把 `durationMs>0 且 positionMs==0` 视为看完
 * （从未播放过的记录 durationMs 恒为 0，不会误判）。
 *
 * @param today 注入“今天”便于单测；生产传 [LocalDate.now]
 */
fun buildWatchStats(
    daily: List<WatchDailyEntity>,
    records: List<WatchRecord>,
    today: LocalDate
): WatchStats {
    val msByDay = HashMap<LocalDate, Long>()
    for (row in daily) {
        val date = try {
            LocalDate.parse(row.day, DAY_FORMATTER)
        } catch (e: DateTimeParseException) {
            continue
        }
        if (row.playMs > 0L) msByDay[date] = (msByDay[date] ?: 0L) + row.playMs
    }

    val weekStart = today.minusDays(6)
    val week7 = (0..6).map { offset ->
        val date = weekStart.plusDays(offset.toLong())
        DayWatch(date = date, playMs = msByDay[date] ?: 0L)
    }

    val streak = run {
        // 今天尚未观看时连续打卡可以从昨天算起，今天稍后产生时长后自然接上
        var cursor = if ((msByDay[today] ?: 0L) > 0L) today else today.minusDays(1)
        var count = 0
        while ((msByDay[cursor] ?: 0L) > 0L) {
            count++
            cursor = cursor.minusDays(1)
        }
        count
    }

    val finished = records.count { record ->
        record.durationMs > 0L && (
            record.positionMs == 0L ||
                record.positionMs.toFloat() / record.durationMs >= FINISHED_THRESHOLD
            )
    }

    val categories = records
        .map { it.video.category.trim() }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .map { (category, count) -> CategoryStat(category, count) }
        .sortedWith(compareByDescending<CategoryStat> { it.count }.thenBy { it.category })

    return WatchStats(
        todayMs = msByDay[today] ?: 0L,
        last7DaysMs = week7.sumOf { it.playMs },
        week7 = week7,
        totalVideos = records.size,
        finishedVideos = finished,
        streakDays = streak,
        activeDays = msByDay.size,
        categories = categories
    )
}
