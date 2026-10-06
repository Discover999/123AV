package com.av123.video.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.av123.video.data.repository.SearchHistoryRepository
import com.av123.video.data.repository.SearchState
import com.av123.video.data.repository.VideoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: VideoRepository,
    private val searchHistoryRepository: SearchHistoryRepository
) : ViewModel() {

    init {
        // 仓库搜索状态是进程级单例：每次新进入搜索页（新的返回栈条目/ViewModel）
        // 都清空上次结果与关键词，回到初始引导页。
        // 搜索结果 -> 详情页 -> 返回 复用的是同一个 ViewModel，结果不受影响。
        repository.clearSearch()
    }

    /** 搜索状态直接来自仓库：关键词、结果列表、加载/错误标记 */
    val uiState: StateFlow<SearchState> = repository.searchState

    /** 搜索历史（最近搜索在前，最多 20 条），未加载完成前先展示空列表 */
    val history: StateFlow<List<String>> = searchHistoryRepository.keywords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    /** 提交关键词搜索（请求 /cn/search?keyword=<关键词>[&page=<页码>]） */
    fun search(keyword: String, page: Int = 1) {
        val kw = keyword.trim()
        val state = repository.searchState.value
        if (kw.isBlank() || state.isLoading) return
        // 仅在搜索新词时写入历史（翻页为同一关键词，不重复置顶）
        if (kw != state.keyword) {
            viewModelScope.launch { searchHistoryRepository.add(kw) }
        }
        repository.search(kw, page)
    }

    /** 删除单条搜索历史（chip 尾部 X） */
    fun deleteHistory(keyword: String) {
        viewModelScope.launch { searchHistoryRepository.delete(keyword) }
    }

    /** 清空全部搜索历史（弹窗确认后调用） */
    fun clearHistory() {
        viewModelScope.launch { searchHistoryRepository.clear() }
    }

    /** 清空输入与搜索结果，回到初始引导页（点击搜索框 X 时调用） */
    fun clear() {
        repository.clearSearch()
    }
}
