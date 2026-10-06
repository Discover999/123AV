package com.av123.video.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.av123.video.data.model.HomeFeed
import com.av123.video.data.net.AppError
import com.av123.video.data.repository.CategoryPageState
import com.av123.video.data.repository.VideoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    /** 已有内容时的下拉刷新（指示器用）；首屏加载走整屏 LoadingState */
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val categories: List<String> = listOf("全部"),
    val selectedCategory: String = "全部",
    val feed: HomeFeed? = null,
    val categoryPages: Map<String, CategoryPageState> = emptyMap()
) {
    /** 当前分类的列表页状态（未发起过列表页请求时为 null，UI 回退到首页分组数据） */
    val categoryPageState: CategoryPageState?
        get() = if (selectedCategory == "全部") null else categoryPages[selectedCategory]
}

class HomeViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val selectedCategory = MutableStateFlow("全部")

    // 下拉刷新指示器由 ViewModel 自管：同步置 true，加载完成且满足最短展示时长后才复位。
    // 不能直接透传 repository.isRefreshing：精选页强制刷新可能在系统下拉手势动画落定前
    // 就翻回 false，Material3 1.3.1 的 PullToRefreshBox 会把指示器永久停在阈值处（卡住）。
    private val _isPullRefreshing = MutableStateFlow(false)

    // 每次下拉刷新完成（成功或失败）自增一次，UI 据此播放瞬间淡入反馈
    private val _refreshFinishTick = MutableStateFlow(0L)
    val refreshFinishTick: StateFlow<Long> = _refreshFinishTick

    val uiState: StateFlow<HomeUiState> = combine(
        repository.feed,
        repository.isRefreshing,
        repository.error,
        selectedCategory,
        repository.categoryPages
    ) { feed, refreshing, error, category, categoryPages ->
        HomeUiState(
            isLoading = refreshing && feed == null,
            error = error,
            categories = feed?.categories ?: listOf("全部"),
            selectedCategory = category,
            feed = feed,
            categoryPages = categoryPages
        )
    }.combine(_isPullRefreshing) { state, pulling ->
        state.copy(isRefreshing = pulling)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        // 直接以仓库现值为初始态：冷启动时 Application 阶段已把磁盘缓存灌入内存，
        // 第一帧就是缓存内容而非整屏加载（compose 首帧不等 combine 的首次发射）
        initialValue = HomeUiState(
            isLoading = repository.feed.value == null,
            categories = repository.feed.value?.categories ?: listOf("全部"),
            feed = repository.feed.value,
            categoryPages = repository.categoryPages.value
        )
    )

    fun selectCategory(category: String) {
        if (selectedCategory.value == category) return
        selectedCategory.value = category
        // 首次进入该分类时请求其列表页（拿分页页脚）；已加载过则保留页码与数据
        repository.ensureCategoryLoaded(category)
    }

    /** 跳转到当前分类的指定页 */
    fun selectPage(page: Int) {
        val category = selectedCategory.value
        if (category == "全部") return
        val state = repository.categoryPages.value[category]
        val total = state?.pagination?.totalPages ?: return
        if (page in 1..total && page != state.page) {
            repository.loadCategoryPage(category, page)
        }
    }

    /**
     * 下拉刷新（首屏错误页的“重试”也复用此入口）：
     * - “全部”以及内容派生自首页 feed 的分类（如“精选”：没有对应的站点列表页，
     *   网格实际展示首页轮播/分组数据）：强制重拉首页 feed；
     * - 有独立列表页的分类（新发布/最近添加等）：重载当前分类所在页。
     * 指示器至少展示 [MIN_REFRESH_INDICATOR_MS]，避免请求过快结束时
     * PullToRefreshBox 指示器卡在阈值；结束后自增 tick 触发内容瞬间淡入。
     */
    fun refresh() {
        if (_isPullRefreshing.value) return
        val category = selectedCategory.value
        val pageState = repository.categoryPages.value[category]
        // “空成功态”（无视频/无分页/无错误）说明该分类没有独立列表页（如“精选”，
        // fetchByCategory 立即返回空），其内容来自首页 feed 分组，应走 feed 刷新；
        // 加载失败的空态仍按分类页重试。
        val loadCategoryPage = category != "全部" && pageState != null &&
            (pageState.videos.isNotEmpty() ||
                pageState.pagination != null ||
                pageState.error != null)
        if (!loadCategoryPage && repository.isRefreshing.value) return

        viewModelScope.launch {
            _isPullRefreshing.value = true
            val start = System.currentTimeMillis()
            try {
                if (loadCategoryPage) {
                    // 直接 join 仓库任务等待完成，不依赖 isLoading 的瞬时 true 跳变：
                    // 空页在 IO 线程可能毫秒级结束，StateFlow 合并会丢掉中间态，
                    // 旧写法 first { isLoading == true } 会永久挂起，指示器一直转。
                    repository.loadCategoryPage(category, pageState!!.page).join()
                } else {
                    repository.refresh(force = true)?.join()
                }
            } finally {
                val elapsed = System.currentTimeMillis() - start
                if (elapsed < MIN_REFRESH_INDICATOR_MS) {
                    delay(MIN_REFRESH_INDICATOR_MS - elapsed)
                }
                _isPullRefreshing.value = false
                _refreshFinishTick.value++
            }
        }
    }

    private companion object {
        /** 下拉指示器最短展示时长：兼容手势动画落定 + 保证用户能看到反馈 */
        const val MIN_REFRESH_INDICATOR_MS = 600L
    }
}
