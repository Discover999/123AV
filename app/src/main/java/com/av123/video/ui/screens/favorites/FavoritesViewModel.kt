package com.av123.video.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.av123.video.data.model.FavoriteRecord
import com.av123.video.data.repository.FavoriteGroup
import com.av123.video.data.repository.FavoriteRepository
import com.av123.video.data.repository.GroupEditResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 收藏页筛选条件：全部 / 未分组 / 指定收藏夹 */
sealed interface FavoritesFilter {
    data object All : FavoritesFilter
    data object Ungrouped : FavoritesFilter
    data class Group(val groupId: Long) : FavoritesFilter
}

/** 收藏页聚合状态：收藏记录 + 收藏夹 + 每个视频所属的收藏夹 id 集合 */
data class FavoritesUiState(
    val records: List<FavoriteRecord> = emptyList(),
    val groups: List<FavoriteGroup> = emptyList(),
    val memberships: Map<String, Set<Long>> = emptyMap()
) {
    /** 未分组收藏数（不属于任何收藏夹） */
    val ungroupedCount: Int
        get() = records.count { it.video.id !in memberships || memberships[it.video.id]?.isEmpty() == true }

    /** 指定收藏夹内的收藏数 */
    fun groupCount(groupId: Long): Int =
        records.count { memberships[it.video.id]?.contains(groupId) == true }

    /**
     * 指定收藏夹的预览记录（总览卡片封面用）：按收藏时间倒序取前 [limit] 条，
     * 记录本身已按时间倒序，直接过滤切片即可
     */
    fun groupPreview(groupId: Long, limit: Int = 4): List<FavoriteRecord> =
        records.filter { memberships[it.video.id]?.contains(groupId) == true }.take(limit)

    /** 按筛选条件过滤后的收藏（保持收藏时间倒序） */
    fun filtered(filter: FavoritesFilter): List<FavoriteRecord> = when (filter) {
        FavoritesFilter.All -> records
        FavoritesFilter.Ungrouped ->
            records.filter { memberships[it.video.id].isNullOrEmpty() }
        is FavoritesFilter.Group ->
            records.filter { memberships[it.video.id]?.contains(filter.groupId) == true }
    }
}

class FavoritesViewModel(
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    val uiState: StateFlow<FavoritesUiState> = combine(
        favoriteRepository.favorites,
        favoriteRepository.groups,
        favoriteRepository.groupItems
    ) { records, groups, items ->
        val memberships = items
            .groupBy { it.videoId }
            .mapValues { (_, groupItems) -> groupItems.map { it.groupId }.toSet() }
        FavoritesUiState(records = records, groups = groups, memberships = memberships)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = FavoritesUiState()
    )

    /** 批量取消收藏 */
    fun delete(videoIds: Collection<String>) {
        viewModelScope.launch { favoriteRepository.delete(videoIds) }
    }

    /** 撤销删除：按原始收藏时间恢复一条或多条记录 */
    fun restore(records: List<FavoriteRecord>) {
        viewModelScope.launch { favoriteRepository.restore(records) }
    }

    fun clear() {
        viewModelScope.launch { favoriteRepository.clear() }
    }

    /** 新建收藏夹（挂起：弹窗需根据结果决定关闭还是提示重名） */
    suspend fun createGroupAndWait(name: String): GroupEditResult =
        favoriteRepository.createGroup(name)

    /** 重命名收藏夹（挂起版本，理由同 [createGroupAndWait]） */
    suspend fun renameGroupAndWait(groupId: Long, name: String): GroupEditResult =
        favoriteRepository.renameGroup(groupId, name)

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch { favoriteRepository.deleteGroup(groupId) }
    }

    /** 把选中视频加入某收藏夹（单选选择器确认后调用） */
    fun addToGroup(groupId: Long, videoIds: Collection<String>) {
        if (videoIds.isEmpty()) return
        viewModelScope.launch {
            favoriteRepository.setGroupMembership(groupId, videoIds, true)
        }
    }

    /** 从某收藏夹移出（加入收藏夹 Snackbar 的撤销路径） */
    fun removeFromGroup(groupId: Long, videoIds: Collection<String>) {
        if (videoIds.isEmpty()) return
        viewModelScope.launch {
            favoriteRepository.setGroupMembership(groupId, videoIds, false)
        }
    }
}
