package com.av123.video.ui.screens.followed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.av123.video.data.model.Video
import com.av123.video.data.repository.FollowedActress
import com.av123.video.data.repository.FollowedActressRepository
import com.av123.video.data.source.VideoDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 单个关注演员“新片”分区状态：拉取其个人页第一页视频 */
data class ActressFeedState(
    val videos: List<Video> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false
)

/**
 * “我的关注”页 ViewModel：
 * 关注列表来自本地 Room；每位演员的新片在进入页面时并发拉取个人页第一页
 * （[MAX_CONCURRENCY] 路并发，避免关注很多时打爆站点），单个演员失败只在该分区
 * 显示重试，不影响其他演员。
 */
class FollowedViewModel(
    private val followedActressRepository: FollowedActressRepository,
    private val dataSource: VideoDataSource
) : ViewModel() {

    val actresses: StateFlow<List<FollowedActress>> =
        followedActressRepository.actresses.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _feeds = MutableStateFlow<Map<String, ActressFeedState>>(emptyMap())
    val feeds: StateFlow<Map<String, ActressFeedState>> = _feeds.asStateFlow()

    /** 已自动加载过的演员 path，避免重组/回页面时重复拉取 */
    private val loaded = mutableSetOf<String>()
    private val fetchLock = Semaphore(MAX_CONCURRENCY)

    init {
        // 首次订阅时为已有关注加载；之后关注列表变化（去关注页关注新演员后返回）自动补拉
        viewModelScope.launch {
            actresses.collect { list ->
                list.forEach { actress ->
                    if (loaded.add(actress.path)) loadActress(actress)
                }
            }
        }
    }

    /** 手动刷新：全部演员重新拉取 */
    fun refresh() {
        val current = actresses.value
        loaded.clear()
        loaded.addAll(current.map { it.path })
        current.forEach { loadActress(it, force = true) }
    }

    /** 单个分区失败重试 */
    fun retry(path: String) {
        actresses.value.firstOrNull { it.path == path }?.let { loadActress(it, force = true) }
    }

    private fun loadActress(actress: FollowedActress, force: Boolean = false) {
        _feeds.value = _feeds.value +
            (actress.path to (_feeds.value[actress.path] ?: ActressFeedState())
                .copy(loading = true, error = false))
        viewModelScope.launch {
            val result = fetchLock.withPermit {
                runCatching { dataSource.fetchByPath(actress.path, actress.name, page = 1) }
            }
            _feeds.value = _feeds.value + (actress.path to result.fold(
                onSuccess = { page ->
                    ActressFeedState(videos = page.videos, loading = false, error = false)
                },
                onFailure = {
                    // 保留已加载内容（手动刷新失败时不清空）
                    val previous = _feeds.value[actress.path]
                    ActressFeedState(
                        videos = previous?.videos.orEmpty(),
                        loading = false,
                        error = previous?.videos.isNullOrEmpty()
                    )
                }
            ))
        }
    }

    private companion object {
        /** 同时进行的演员新片请求上限 */
        const val MAX_CONCURRENCY = 4
    }
}
