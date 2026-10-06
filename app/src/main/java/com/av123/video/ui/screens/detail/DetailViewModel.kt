package com.av123.video.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.av123.video.data.model.Video
import com.av123.video.data.net.AppError
import com.av123.video.data.repository.DetailResult
import com.av123.video.data.repository.FavoriteRepository
import com.av123.video.data.repository.HistoryRepository
import com.av123.video.data.repository.PlaybackSource
import com.av123.video.data.repository.VideoRepository
import com.av123.video.data.repository.watchDayKey
import com.av123.video.ui.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DetailViewModel(
    private val videoRepository: VideoRepository,
    private val historyRepository: HistoryRepository,
    private val favoriteRepository: FavoriteRepository,
    private val videoId: String
) : ViewModel() {

    private val _video = MutableStateFlow<Video?>(null)
    val video: StateFlow<Video?> = _video.asStateFlow()

    /** 当前视频是否已收藏，收藏列表变化（其他页面取消收藏）时自动同步 */
    val isFavorite: StateFlow<Boolean> = favoriteRepository.favoriteIds
        .map { it.contains(videoId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false
        )

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 详情首屏加载错误（无列表缓存可展示时）：UI 显示整屏错误 + 重试；
     * 有缓存时不会置位（页面照常渲染缓存，错误经全局 Snackbar 提示）。
     */
    private val _loadError = MutableStateFlow<AppError?>(null)
    val loadError: StateFlow<AppError?> = _loadError.asStateFlow()

    /** WebView 拦截到的真实播放地址（m3u8 + 请求头）；null 表示尚未解析到 */
    private val _playback = MutableStateFlow<PlaybackSource?>(null)
    val playback: StateFlow<PlaybackSource?> = _playback.asStateFlow()

    /** 上次播放位置（ms），起播后 seekTo 到该位置实现续播；无记录为 0 */
    private val _resumePositionMs = MutableStateFlow(0L)
    val resumePositionMs: StateFlow<Long> = _resumePositionMs.asStateFlow()

    /** 历史记录里上次停留的选集下标（0 起）；详情到达后据此自动切回该集 */
    private var resumeEpisodeIndex = 0

    /** 当前选中的选集下标（0 起）；切换后驱动抓流目标页与播放器换源 */
    private val _selectedEpisodeIndex = MutableStateFlow(0)
    val selectedEpisodeIndex: StateFlow<Int> = _selectedEpisodeIndex.asStateFlow()

    /** 解析是否已失败（嵌入页/详情页均超时或主帧加载失败）；失败后展示重试入口 */
    private val _resolveFailed = MutableStateFlow(false)
    val resolveFailed: StateFlow<Boolean> = _resolveFailed.asStateFlow()

    /**
     * 拦截 WebView 的代次：每次重试 +1，作为 DisposableEffect 的 key，
     * 变化时旧 WebView 销毁、新 WebView 重新加载播放页抓流
     */
    private val _captureEpoch = MutableStateFlow(0)
    val captureEpoch: StateFlow<Int> = _captureEpoch.asStateFlow()

    /**
     * 详情页“相关推荐”当前**可见**的卡片（完整池的第一页，点“加载更多”就地追加）。
     * 独立 state、不写入 Video/历史 JSON；解析失败保持空列表，UI 整区隐藏。
     */
    private val _related = MutableStateFlow<List<Video>>(emptyList())
    val related: StateFlow<List<Video>> = _related.asStateFlow()

    /** 完整推荐池（全部落盘），可见列表只是它的前 N 条，展开纯本地切片、不再请求网络 */
    private var allRelated: List<Video> = emptyList()
    private var visibleRelatedCount = 0

    /** 是否还有未展示的推荐（控制“加载更多”按钮显隐） */
    private val _canLoadMoreRelated = MutableStateFlow(false)
    val canLoadMoreRelated: StateFlow<Boolean> = _canLoadMoreRelated.asStateFlow()

    /** 推荐提取是否仍在进行（无头 WebView 加载/轮询窗口内显示骨架，结束置 false） */
    private val _relatedLoading = MutableStateFlow(true)
    val relatedLoading: StateFlow<Boolean> = _relatedLoading.asStateFlow()

    /**
     * 当前播放地址是否来自仓库缓存。命中缓存时不再创建抓流 WebView；
     * 若缓存地址播放失败（签名过期等），需剔除缓存并回退为重新抓流。
     */
    private var playbackFromCache = false

    // —— 观看时长统计（墙钟口径，仅播放器 isPlaying 期间由 UI 回调增量）——
    /** 累计达到该阈值（ms）落一次库，避免每个轮询 tick 写库；退出/跨午夜兜底再落 */
    private val flushIntervalMs = 30_000L

    /** 当前批次归属日期（本地时区 yyyy-MM-dd），跨午夜时把旧批次先落库再切换 */
    private var pendingDay: String = watchDayKey()
    private var pendingPlayMs: Long = 0L

    init {
        // 先用首页缓存的列表条目即时渲染封面/标题，消除详情页网络请求期间的黑屏等待；
        // 详情数据（简介/信息行/播放配置/选集列表）返回后同位置补全
        _video.value = videoRepository.getCachedVideo(videoId)
        // 首集本会话已验证可用的播放地址直接复用，跳过无头 WebView 抓流与“解析中”等待；
        // 其他集的缓存命中在详情（含选集列表）到达后处理
        videoRepository.getCachedPlayback(videoId, episodeIndex = 0)?.let { cached ->
            _playback.value = cached
            playbackFromCache = true
        }
        viewModelScope.launch {
            // 先读历史续播信息（位置 + 上次选集），再拉详情：详情返回后若上次停留在
            // 非首集则自动切到该集；抓流 WebView 只在 isLoading=false 后创建，
            // 因此不会先白抓一遍首集
            val saved = historyRepository.getSavedProgress(videoId)
            _resumePositionMs.value = saved.positionMs
            resumeEpisodeIndex = saved.episodeIndex
            loadDetailOnce()
        }
    }

    /**
     * 播放器周期性/退出时回传真实播放进度，写入观看历史：
     * 历史列表展示进度条，再次进入同一影片时自动切回上次选集并从该位置续播。
     */
    fun savePlaybackProgress(episodeIndex: Int, positionMs: Long, durationMs: Long) {
        val video = _video.value ?: return
        viewModelScope.launch {
            historyRepository.updateProgress(video, positionMs, durationMs, episodeIndex)
        }
    }

    /**
     * 播放器墙钟增量回调（仅 isPlaying 且无错误期间）：暂停/缓冲/seek 不计时。
     * 内存累计到约 30s 批量落库；跨过本地午夜时先落旧日期，剩余归新日期。
     */
    fun onPlayingElapsed(deltaMs: Long) {
        if (deltaMs <= 0L) return
        val today = watchDayKey()
        if (today != pendingDay) {
            flushPending(pendingDay)
            pendingDay = today
        }
        pendingPlayMs += deltaMs
        if (pendingPlayMs >= flushIntervalMs) flushPending(pendingDay)
    }

    /** 把指定日期的待写增量异步落库并清零（清零在 launch 前同步完成，不阻塞后续累计） */
    private fun flushPending(day: String) {
        val ms = pendingPlayMs
        if (ms <= 0L) return
        pendingPlayMs = 0L
        viewModelScope.launch { historyRepository.addPlayTime(day, ms) }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope 已取消，最后不足 30s 的零头同步落库，避免退出详情丢时长
        val ms = pendingPlayMs
        if (ms > 0L) {
            runCatching { runBlocking { historyRepository.addPlayTime(pendingDay, ms) } }
        }
    }

    /** 拉取（或重试拉取）详情数据；成功后补全页面并写入观看历史 */
    fun loadDetail() {
        viewModelScope.launch { loadDetailOnce() }
    }

    /** [loadDetail] 的挂起实现：init 需要等它完成后再恢复选集/缓存 */
    private suspend fun loadDetailOnce() {
        _isLoading.value = true
        _loadError.value = null
        when (val result = videoRepository.getVideo(videoId)) {
            is DetailResult.Success -> {
                applyDetail(result)
                // 历史记录显示上次停留在非首集：详情（含选集地址）到达后自动切回该集，
                // 续播位置保持；该集本会话已有缓存播放源则直接起播
                val savedIndex = resumeEpisodeIndex
                if (savedIndex > 0 &&
                    savedIndex < result.video.episodes.size &&
                    _selectedEpisodeIndex.value == 0
                ) {
                    switchEpisode(savedIndex, keepResumePosition = true)
                }
                historyRepository.addRecord(result.video)
                // 结果来自已过期磁盘缓存：页面先渲染缓存，后台静默拉新，成功后就地补全
                if (result.staleFromDisk) silentRefreshDetail()
            }
            is DetailResult.Failure -> {
                // 有缓存：页面已渲染缓存内容，错误由全局 Snackbar 提示；
                // 无缓存：整屏错误态 + 重试（不再显示误导性的“未找到”）
                result.cached?.let { _video.value = it }
                if (result.cached == null) _loadError.value = result.error
            }
        }
        _isLoading.value = false
    }

    /**
     * 把详情结果写入页面状态：影片主体 + 相关推荐池。
     * 推荐非空时收起骨架并重置为第一页；为空则保留骨架，由无头 WebView 兜底。
     * 静默刷新（池已初始化）时保留用户当前展开的条数，避免列表突然收缩。
     */
    private fun applyDetail(result: DetailResult.Success) {
        _video.value = result.video
        if (result.relatedStatic.isNotEmpty()) {
            replaceRelated(result.relatedStatic, preserveExpanded = allRelated.isNotEmpty())
            _relatedLoading.value = false
            AppLog.d(TAG_RELATED, "相关推荐就位 ${result.relatedStatic.size} 条")
        } else {
            AppLog.d(TAG_RELATED, "本次详情无静态推荐，等待无头 WebView 兜底")
        }
    }

    /**
     * “加载更多”：可见条数追加一页（纯本地切片，同步完成无需 loading 态）。
     */
    fun loadMoreRelated() {
        if (!_canLoadMoreRelated.value) return
        visibleRelatedCount = relatedNextVisible(
            currentVisible = visibleRelatedCount,
            total = allRelated.size,
            pageSize = VideoRepository.RELATED_PAGE_SIZE
        )
        publishRelated()
    }

    /** 用新的完整池替换数据：首次只展示第一页；刷新时保留已展开条数（夹在一页与总量之间） */
    private fun replaceRelated(items: List<Video>, preserveExpanded: Boolean) {
        allRelated = items
        val firstPage = minOf(VideoRepository.RELATED_PAGE_SIZE, items.size)
        visibleRelatedCount = if (preserveExpanded) {
            visibleRelatedCount.coerceIn(firstPage, items.size)
        } else {
            firstPage
        }
        publishRelated()
    }

    /** 依据完整池与可见条数刷新对外状态 */
    private fun publishRelated() {
        _related.value = allRelated.take(visibleRelatedCount)
        _canLoadMoreRelated.value = visibleRelatedCount < allRelated.size
    }

    /**
     * 磁盘详情缓存过期后的后台静默刷新：
     * 成功就地替换影片/推荐（不改选集、不写历史排序）；网络失败与离开页面均不提示。
     */
    private fun silentRefreshDetail() {
        viewModelScope.launch {
            AppLog.d(TAG_RELATED, "详情磁盘缓存已过期，后台静默刷新")
            when (val fresh = videoRepository.refreshDetail(
                videoId,
                reportFailureSnackbar = false
            )) {
                is DetailResult.Success -> applyDetail(fresh)
                is DetailResult.Failure -> Unit
            }
        }
    }

    /** 用户点击选集芯片：切换到对应集，手动切换一律从头播放 */
    fun selectEpisode(index: Int) = switchEpisode(index, keepResumePosition = false)

    /**
     * 切换选集：
     * - 该集本会话已抓到过播放源：直接换源起播；
     * - 否则清空当前播放源并递增抓流代次，无头 WebView 携带该集播放页地址重建；
     * - [keepResumePosition] 为 true（进入详情自动恢复上次选集）时保留历史续播位置；
     *   手动切集置 0，避免把别的集进度 seek 到新集上。
     */
    private fun switchEpisode(index: Int, keepResumePosition: Boolean) {
        val episodes = _video.value?.episodes ?: return
        if (index !in episodes.indices) return
        if (index == _selectedEpisodeIndex.value) return
        _selectedEpisodeIndex.value = index
        if (!keepResumePosition) _resumePositionMs.value = 0L
        _resolveFailed.value = false
        val cached = videoRepository.getCachedPlayback(videoId, index)
        if (cached != null) {
            _playback.value = cached
            playbackFromCache = true
        } else {
            // 播放源置空 -> 播放区显示“解析中”，抓流 WebView 以新代次重建抓该集
            playbackFromCache = false
            _playback.value = null
            _captureEpoch.value += 1
        }
    }

    /**
     * 无头 WebView 拦截到 m3u8 时回调。首个地址为主播放列表（master playlist），
     * 后续的码率/分片清单忽略；若已有地址缺少请求头而新回调带完整头，则补全。
     */
    fun onPlaybackCaptured(url: String, headers: Map<String, String>) {
        if (url.isBlank()) return
        val existing = _playback.value
        when {
            existing == null -> _playback.value = PlaybackSource(url, headers)
            existing.headers.isEmpty() && headers.isNotEmpty() ->
                _playback.value = existing.copy(headers = headers)
        }
        // 地址来自本轮实时抓流而非缓存
        playbackFromCache = false
        // 迟到的成功回调（如失败页正在展示时后台突然抓到）也要清除失败态
        _resolveFailed.value = false
    }

    /**
     * ExoPlayer 进入 STATE_READY：播放地址经实际请求验证可用
     * （m3u8 与分片均未被防盗链拦截），写入仓库缓存，
     * 本会话内再次进入该影片即可直接起播、无需重新抓流。
     */
    fun onPlaybackReady() {
        val current = _playback.value ?: return
        videoRepository.savePlayback(
            videoId,
            _selectedEpisodeIndex.value,
            PlaybackSource(current.url, current.headers)
        )
    }

    /**
     * ExoPlayer 播放出错。若当前用的是缓存地址（可能签名已过期），
     * 剔除缓存并清空地址、递增代次，触发抓流 WebView 重建重新解析；
     * 实时抓流得到的地址失败则不干预，由播放器自身的错误重试层处理。
     */
    fun onPlaybackError() {
        val wasCached = playbackFromCache
        videoRepository.invalidatePlayback(videoId, _selectedEpisodeIndex.value)
        if (wasCached) {
            playbackFromCache = false
            _playback.value = null
            _resolveFailed.value = false
            _captureEpoch.value += 1
        }
    }

    /**
     * 拦截超时（嵌入页回退详情页后仍未抓到）或播放页主帧加载失败时回调。
     * 已抓到地址则忽略，避免网络慢时的误报。
     */
    fun onResolveFailed() {
        if (_playback.value == null) {
            _resolveFailed.value = true
        }
    }

    /**
     * 无头 WebView 渲染详情页后回传推荐卡片 HTML：解析并与已有池合并去重，
     * 重置分页（兜底场景下此前静态池为空，等同首次加载展示第一页）。
     * 空白/解析失败一律静默，不影响详情主流程。
     */
    fun onRelatedHtml(html: String?) {
        if (html.isNullOrBlank()) return
        val baseUrl = _video.value?.sourcePageUrl.orEmpty()
        viewModelScope.launch {
            val list = videoRepository.parseRelated(html, baseUrl, videoId)
            if (list.isNotEmpty()) {
                replaceRelated(
                    (allRelated + list).distinctBy { it.id },
                    preserveExpanded = allRelated.isNotEmpty()
                )
            }
        }
    }

    /** 推荐提取结束（成功或超时）：收起骨架；空结果时整区不渲染 */
    fun onRelatedFinished() {
        _relatedLoading.value = false
    }

    /** 用户点击重试：清除失败标记并递增代次，触发拦截 WebView 重建重新抓流 */
    fun retryResolve() {
        _resolveFailed.value = false
        playbackFromCache = false
        _playback.value = null
        _captureEpoch.value += 1
    }

    /** 收藏 / 取消收藏当前视频；返回操作后是否为收藏态，便于 UI 弹提示 */
    fun toggleFavorite(): Boolean {
        val video = _video.value ?: return isFavorite.value
        val nowFavorite = !isFavorite.value
        viewModelScope.launch {
            if (nowFavorite) favoriteRepository.add(video)
            else favoriteRepository.remove(video.id)
        }
        return nowFavorite
    }

    private companion object {
        const val TAG_RELATED = "Related"
    }
}

/**
 * 相关推荐“加载更多”后的可见条数：当前可见数追加一页，但不超过完整池总量。
 * 抽成纯函数便于单测；总量不足一页的余数由 coerceAtMost 自然兜住。
 */
internal fun relatedNextVisible(currentVisible: Int, total: Int, pageSize: Int): Int =
    (currentVisible + pageSize).coerceAtMost(total)
