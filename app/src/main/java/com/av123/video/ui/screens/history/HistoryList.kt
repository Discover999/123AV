package com.av123.video.ui.screens.history

import com.av123.video.data.model.WatchRecord
import com.av123.video.data.repository.FINISHED_THRESHOLD
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 历史页筛选：全部 / 未看完 / 已看完 */
enum class HistoryFilter { ALL, UNFINISHED, FINISHED }

/** 历史页日期分组 */
enum class HistoryGroup { TODAY, YESTERDAY, EARLIER }

data class HistorySection(val group: HistoryGroup, val records: List<WatchRecord>)

/** 看完只看播放进度，避免把「打开未播」误标成已看完（position/duration 的 0 值不参与判定） */
fun WatchRecord.isFinished(): Boolean =
    durationMs > 0L && positionMs.toFloat() / durationMs >= FINISHED_THRESHOLD

fun filterHistory(records: List<WatchRecord>, filter: HistoryFilter): List<WatchRecord> =
    when (filter) {
        HistoryFilter.ALL -> records
        HistoryFilter.UNFINISHED -> records.filter { it.positionMs > 0L && !it.isFinished() }
        HistoryFilter.FINISHED -> records.filter { it.isFinished() }
    }

/**
 * 按观看日期分组，保持输入顺序（仓库已按 watchedAt 倒序）。
 * 未来时间归到今天，避免时钟漂移导致记录消失。
 */
fun groupHistory(
    records: List<WatchRecord>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): List<HistorySection> {
    val yesterday = today.minusDays(1)
    val buckets = HashMap<HistoryGroup, MutableList<WatchRecord>>()
    records.forEach { record ->
        val date = Instant.ofEpochMilli(record.watchedAt).atZone(zone).toLocalDate()
        val group = when {
            date >= today -> HistoryGroup.TODAY
            date == yesterday -> HistoryGroup.YESTERDAY
            else -> HistoryGroup.EARLIER
        }
        buckets.getOrPut(group) { mutableListOf() }.add(record)
    }
    return listOf(HistoryGroup.TODAY, HistoryGroup.YESTERDAY, HistoryGroup.EARLIER)
        .mapNotNull { group -> buckets[group]?.let { HistorySection(group, it) } }
}
