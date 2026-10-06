package com.av123.video.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.av123.video.data.model.WatchRecord
import com.av123.video.data.repository.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    val records: StateFlow<List<WatchRecord>> = historyRepository.records
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun clear() {
        viewModelScope.launch { historyRepository.clear() }
    }

    /** 删除单条记录（滑动删除） */
    fun delete(videoId: String) {
        viewModelScope.launch { historyRepository.delete(videoId) }
    }

    /** 批量删除（多选删除） */
    fun delete(videoIds: Collection<String>) {
        viewModelScope.launch { historyRepository.delete(videoIds) }
    }

    /** 撤销删除：恢复一条或多条记录 */
    fun restore(records: List<WatchRecord>) {
        viewModelScope.launch {
            records.forEach { historyRepository.restoreRecord(it) }
        }
    }
}
