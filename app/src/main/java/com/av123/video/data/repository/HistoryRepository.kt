package com.av123.video.data.repository

import com.av123.video.data.db.AppJson
import com.av123.video.data.db.HistoryDao
import com.av123.video.data.db.HistoryEntity
import com.av123.video.data.db.WatchDailyDao
import com.av123.video.data.db.WatchDailyEntity
import com.av123.video.data.model.Video
import com.av123.video.data.model.WatchRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 观看历史仓库：Room 持久化，按观看时间倒序。
 * Video 完整结构以 JSON 存于 videoJson 列，进度/选集为结构化列便于续播查询。
 * 历史条数上限 [MAX_RECORDS]，超出淘汰最旧。
 * 同时持有每日观看时长表，负责播放墙钟时长的增量累计与统计快照。
 */
class HistoryRepository(
    private val historyDao: HistoryDao,
    private val dailyDao: WatchDailyDao
) {

    val records: Flow<List<WatchRecord>> =
        historyDao.observeAll().map { entities -> entities.map { it.toRecord() } }

    /** 某天墙钟时长的可观察值（默认今天）；无记录时发射 0 */
    fun observeDayPlayMs(day: String = watchDayKey()): Flow<Long> =
        dailyDao.observeDayPlayMs(day)

    /**
     * 进入详情页时写入/更新一条历史：watchedAt 刷新为当前时间（排到列表最前），
     * 但已存在记录的播放位置/时长/选集必须原样保留——本次抓流可能失败或用户
     * 未起播就退出，若把进度写成 0 会导致上次续播位置永久丢失。
     */
    suspend fun addRecord(video: Video, positionMs: Long = 0L, durationMs: Long = 0L) {
        val existing = historyDao.get(video.id)
        historyDao.upsert(
            HistoryEntity(
                videoId = video.id,
                videoJson = video.toJson(),
                positionMs = existing?.positionMs ?: positionMs.coerceAtLeast(0L),
                durationMs = existing?.durationMs ?: durationMs.coerceAtLeast(0L),
                watchedAt = System.currentTimeMillis(),
                episodeIndex = existing?.episodeIndex ?: 0
            )
        )
        trim()
    }

    /**
     * 播放过程中回传真实进度：只更新位置/时长/选集，
     * 保留进入详情页时写入的 watchedAt（列表排序不随播放跳动）与已有视频信息。
     * 记录不存在（如详情尚未加载完成）时兜底新建一条。
     */
    suspend fun updateProgress(
        video: Video,
        positionMs: Long,
        durationMs: Long,
        episodeIndex: Int = 0
    ) {
        val existing = historyDao.get(video.id)
        historyDao.upsert(
            HistoryEntity(
                videoId = video.id,
                videoJson = video.toJson(),
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                watchedAt = existing?.watchedAt ?: System.currentTimeMillis(),
                episodeIndex = episodeIndex.coerceAtLeast(0)
            )
        )
        trim()
    }

    /**
     * 读取某影片上次播放位置（ms）与选集下标，无记录时返回零值；
     * 用于进入播放后自动切回上次选集并从该位置续播。
     */
    suspend fun getSavedProgress(videoId: String): SavedProgress {
        val entity = historyDao.get(videoId) ?: return SavedProgress(0L, 0)
        return SavedProgress(
            positionMs = entity.positionMs,
            episodeIndex = entity.episodeIndex
        )
    }

    /**
     * 累加某天的播放墙钟时长（暂停/缓冲/seek 不计入，由播放器侧保证只上报播放中增量）。
     * 当天首次写入先插 0 值行再增量更新，避免依赖 insert 冲突语义。
     */
    suspend fun addPlayTime(day: String, deltaMs: Long) {
        if (deltaMs <= 0L) return
        dailyDao.insertIfAbsent(
            WatchDailyEntity(day = day, playMs = 0L, updatedAt = System.currentTimeMillis())
        )
        dailyDao.addPlayTime(day, deltaMs, System.currentTimeMillis())
    }

    /** 观看统计页一次性快照：每日时长全表 + 历史记录，经纯函数聚合 */
    suspend fun getWatchStats(today: LocalDate = LocalDate.now()): WatchStats {
        val daily = dailyDao.all()
        val records = historyDao.getAll().map { it.toRecord() }
        return buildWatchStats(daily, records, today)
    }

    /** 删除单条历史（滑动删除） */
    suspend fun delete(videoId: String) {
        historyDao.delete(videoId)
    }

    /** 批量删除历史（多选删除） */
    suspend fun delete(videoIds: Collection<String>) {
        if (videoIds.isNotEmpty()) historyDao.delete(videoIds)
    }

    /** 撤销删除：按原始时间戳与选集完整恢复记录 */
    suspend fun restoreRecord(record: WatchRecord) {
        historyDao.upsert(
            HistoryEntity(
                videoId = record.video.id,
                videoJson = record.video.toJson(),
                positionMs = record.positionMs,
                durationMs = record.durationMs,
                watchedAt = record.watchedAt,
                episodeIndex = record.episodeIndex
            )
        )
    }

    suspend fun clear() {
        historyDao.clear()
    }

    /** 条数上限淘汰：按 watchedAt 倒序保留最新的 [MAX_RECORDS] 条 */
    private suspend fun trim() {
        val count = historyDao.count()
        if (count > MAX_RECORDS) {
            val staleIds = historyDao.oldest(count - MAX_RECORDS).map { it.videoId }
            historyDao.delete(staleIds)
        }
    }

    private companion object {
        const val MAX_RECORDS = 500
    }
}

/** 续播信息：上次播放位置（ms）与选集下标（0 起） */
data class SavedProgress(
    val positionMs: Long,
    val episodeIndex: Int
)

/** Entity -> 领域模型；videoJson 损坏时用 id 兜底，保证列表不被单条坏数据拖垮 */
internal fun HistoryEntity.toRecord(): WatchRecord = WatchRecord(
    video = runCatching {
        AppJson.decodeFromString(Video.serializer(), videoJson)
    }.getOrNull() ?: Video(id = videoId, title = videoId, category = ""),
    positionMs = positionMs,
    durationMs = durationMs,
    watchedAt = watchedAt,
    episodeIndex = episodeIndex
)

internal fun Video.toJson(): String =
    AppJson.encodeToString(Video.serializer(), this)

/** 每日时长表的日期主键（设备本地时区，yyyy-MM-dd） */
fun watchDayKey(date: LocalDate = LocalDate.now()): String =
    date.format(DateTimeFormatter.ISO_LOCAL_DATE)
