package com.av123.video.data.repository

import com.av123.video.data.diagnostics.DiagnosticsMetrics
import com.av123.video.data.model.Pagination
import com.av123.video.data.model.Video
import com.av123.video.data.net.AppError
import com.av123.video.data.net.ErrorReporter
import com.av123.video.data.runCatchingCancellable
import com.av123.video.data.source.VideoSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 搜索页状态：关键词、结果、分页、总数、加载与错误 */
data class SearchState(
    val keyword: String = "",
    val videos: List<Video> = emptyList(),
    val pagination: Pagination? = null,
    val page: Int = 1,
    val totalCount: Int? = null,
    val isLoading: Boolean = false,
    /** 最近一次搜索错误（归一化类型）；无结果时整屏展示，翻页失败由全局 Snackbar 提示 */
    val error: AppError? = null
)

/** 搜索职责：独立持有搜索状态与请求生命周期，VideoRepository 只做兼容转发 */
class SearchRepository(
    private val dataSource: VideoSite,
    private val scope: CoroutineScope,
    private val errorReporter: ErrorReporter
) {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    fun search(keyword: String, page: Int = 1) {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        val current = _state.value
        if (current.isLoading) return
        val isNewQuery = kw != current.keyword
        val targetPage = if (isNewQuery) 1 else page.coerceAtLeast(1)

        scope.launch {
            _state.update {
                it.copy(
                    keyword = kw,
                    page = targetPage,
                    videos = if (isNewQuery) emptyList() else it.videos,
                    pagination = if (isNewQuery) null else it.pagination,
                    totalCount = if (isNewQuery) null else it.totalCount,
                    isLoading = true,
                    error = null
                )
            }
            runCatchingCancellable { dataSource.search(kw, targetPage) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            videos = result.videos,
                            pagination = result.pagination,
                            totalCount = result.totalCount,
                            page = targetPage,
                            isLoading = false
                        )
                    }
                }
                .onFailure { throwable ->
                    val error = AppError.from(throwable)
                    DiagnosticsMetrics.recordLoadFailure(error.kind)
                    var hadContent = false
                    _state.update {
                        hadContent = it.videos.isNotEmpty()
                        it.copy(isLoading = false, error = error)
                    }
                    if (hadContent) errorReporter.report(error)
                }
        }
    }

    fun clear() {
        _state.value = SearchState()
    }

    /** 搜索结果中的条目，供详情页先用列表数据即时渲染 */
    fun cachedVideo(videoId: String): Video? =
        _state.value.videos.firstOrNull { it.id == videoId }
}
