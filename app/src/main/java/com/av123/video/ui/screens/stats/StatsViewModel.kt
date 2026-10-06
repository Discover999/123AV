package com.av123.video.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.av123.video.data.repository.HistoryRepository
import com.av123.video.data.repository.WatchStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 观看统计页 UI 状态：[stats] 未就绪前 [loading] 为 true */
data class StatsUiState(
    val loading: Boolean = true,
    val stats: WatchStats? = null
)

/**
 * 观看统计 ViewModel：进入页面一次性聚合（每日时长全表 + 历史快照，
 * 见 [HistoryRepository.getWatchStats]），刷新动作重查。
 * 页面不是常驻高频页，无需对底层表做流式联动。
 */
class StatsViewModel(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) { historyRepository.getWatchStats() }
            _uiState.value = StatsUiState(loading = false, stats = stats)
        }
    }
}
