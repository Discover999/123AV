package com.av123.video.ui.screens.channels

import com.av123.video.ui.icons.AppIcons

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.av123.video.LocalAppContainer
import com.av123.video.LocalPreviewOpen
import com.av123.video.data.repository.FollowedActress
import com.av123.video.R
import com.av123.video.ui.theme.Motion
import com.av123.video.data.model.ActressProfile
import com.av123.video.data.model.FilterGroup
import com.av123.video.data.model.FilterOption
import com.av123.video.data.model.IndexEntry
import com.av123.video.data.model.Video
import com.av123.video.data.repository.CategoryPageState
import com.av123.video.data.repository.VideoRepository
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.ErrorState
import com.av123.video.ui.components.LoadingState
import com.av123.video.ui.components.appErrorText
import com.av123.video.ui.components.PaginationBar
import com.av123.video.ui.components.rememberTogglePopScale
import com.av123.video.ui.components.VideoGridCard
import com.av123.video.ui.components.VideoPreviewDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 分栏栏目页：按首页 nav 解析出的路径（/cn/hot、/cn/all?sort=today、
 * /cn/11 等）拉取内容，支持页脚分页；按路径缓存，返回后停留在原页码。
 *
 * 页面有两种形态：
 * - 视频流页：直接渲染双列视频卡片网格；
 * - 索引聚合页（如集合下的类别/演员/发行/系列，/cn/genres 等）：
 *   页面只有 .gchip 条目网格，渲染条目卡片（名称 + 视频数量）并支持本地筛选，
 *   点击条目通过 [onEntryClick] 进入该条目 path 的视频流页。
 */
class ChannelViewModel(
    private val repository: VideoRepository,
    private val path: String,
    private val label: String
) : ViewModel() {

    val state: StateFlow<CategoryPageState> = repository.channelPages
        .map { it[path] ?: CategoryPageState(isLoading = true) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CategoryPageState(isLoading = true)
        )

    // 各筛选维度当前选中值（key=type/year/actress/sort）。
    // 首次拿到带表单的列表后按服务端 is-on/默认值初始化一次，之后以用户选择为准，
    // 避免每次翻页后服务端回显与本地选择互相覆盖。
    private val _filterSelections = MutableStateFlow<Map<String, String>>(emptyMap())
    val filterSelections: StateFlow<Map<String, String>> = _filterSelections.asStateFlow()
    private var selectionsInitialized = false

    // 下拉刷新指示器状态（与翻页/筛选的 isLoading 区分，避免翻页时顶部指示器误亮）
    private val _isPullRefreshing = MutableStateFlow(false)
    val isPullRefreshing: StateFlow<Boolean> = _isPullRefreshing.asStateFlow()

    // 每次下拉刷新完成（成功或失败）自增一次，UI 据此播放瞬间淡入反馈
    private val _refreshFinishTick = MutableStateFlow(0L)
    val refreshFinishTick: StateFlow<Long> = _refreshFinishTick.asStateFlow()

    init {
        repository.ensureChannelLoaded(path, label)
        // 表单随第一页视频流返回后初始化默认选中（如 sort=release_date）
        viewModelScope.launch {
            repository.channelPages
                .map { it[path]?.filters }
                .first { !it.isNullOrEmpty() }
                ?.let { groups ->
                    if (!selectionsInitialized) {
                        selectionsInitialized = true
                        _filterSelections.value =
                            groups.associate { it.key to it.selectedValue }
                    }
                }
        }
    }

    /** 选择某筛选维度的某个值：立即回到第 1 页重新加载（与网页 requestSubmit 行为一致） */
    fun applyFilter(key: String, value: String) {
        if (repository.channelPages.value[path]?.isLoading == true) return
        _filterSelections.update { it + (key to value) }
        repository.loadChannelPage(path, label, page = 1, queryParams = activeParams())
    }

    /**
     * 提交女演员索引页的关键词搜索（表单 .asearch 的 q 参数）：立即回到第 1 页。
     * 关键词与下拉筛选共存于同一个 selections，提交时一起带给服务端。
     */
    fun submitSearch(key: String, query: String) {
        if (repository.channelPages.value[path]?.isLoading == true) return
        _filterSelections.update { it + (key to query.trim()) }
        repository.loadChannelPage(path, label, page = 1, queryParams = activeParams())
    }

    /** 全部维度恢复默认（各菜单首项）并清空搜索关键词，回到第 1 页 */
    fun resetFilters() {
        if (repository.channelPages.value[path]?.isLoading == true) return
        val current = repository.channelPages.value[path]
        val defaults = current?.filters
            ?.associate { it.key to (it.options.firstOrNull()?.value ?: "") }
            .orEmpty()
        // 搜索框参数（q）不在下拉维度里，重置时需显式清空
        val searchKey = current?.searchField?.key
        _filterSelections.value = buildMap {
            putAll(defaults)
            if (searchKey != null) put(searchKey, "")
        }
        repository.loadChannelPage(path, label, page = 1, queryParams = activeParams())
    }

    /** 实际下发的查询参数：空值（“全部”）不下发 */
    private fun activeParams(): Map<String, String> =
        _filterSelections.value.filterValues { it.isNotBlank() }

    fun loadPage(page: Int) {
        val total = repository.channelPages.value[path]?.pagination?.totalPages ?: return
        // 翻页带上当前筛选条件
        if (page in 1..total) {
            repository.loadChannelPage(path, label, page, activeParams())
        }
    }

    fun retry() {
        val page = repository.channelPages.value[path]?.page ?: 1
        repository.loadChannelPage(path, label, page, activeParams())
    }

    /**
     * 下拉刷新：带着当前筛选条件重载当前页，加载期间保留已有列表。
     * 指示器至少展示 [MIN_REFRESH_INDICATOR_MS]，避免请求过快结束时
     * PullToRefreshBox 指示器卡在阈值；结束后自增 tick 触发内容瞬间淡入。
     */
    fun refresh() {
        if (_isPullRefreshing.value) return
        val current = repository.channelPages.value[path]
        if (current?.isLoading == true) return

        viewModelScope.launch {
            _isPullRefreshing.value = true
            val start = System.currentTimeMillis()
            try {
                // 直接 join 仓库任务等待完成，不依赖 isLoading 的瞬时 true 跳变：
                // 状态在 IO 线程毫秒级翻转时 StateFlow 合并会丢掉中间态，
                // 旧写法 first { isLoading == true } 可能永久挂起，指示器一直转。
                repository.loadChannelPage(
                    path, label, current?.page ?: 1, activeParams()
                ).join()
            } finally {
                val elapsed = System.currentTimeMillis() - start
                if (elapsed < MIN_REFRESH_INDICATOR_MS) {
                    delay(MIN_REFRESH_INDICATOR_MS - elapsed)
                }
                _isPullRefreshing.value = false
                _refreshFinishTick.update { it + 1 }
            }
        }
    }

    private companion object {
        /** 下拉指示器最短展示时长：兼容手势动画落定 + 保证用户能看到反馈 */
        const val MIN_REFRESH_INDICATOR_MS = 600L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    title: String,
    path: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onEntryClick: (title: String, path: String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: ChannelViewModel = viewModel(key = path) {
        ChannelViewModel(container.videoRepository, path, title)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filterSelections by viewModel.filterSelections.collectAsStateWithLifecycle()
    val isPullRefreshing by viewModel.isPullRefreshing.collectAsStateWithLifecycle()
    val refreshTick by viewModel.refreshFinishTick.collectAsStateWithLifecycle()

    // 下拉刷新完成后的瞬间反馈：内容快速压暗再淡入（约 380ms），
    // 即使网页数据没变化，用户也能明确感知“刷新过了”
    val refreshAlpha = remember { Animatable(1f) }
    LaunchedEffect(refreshTick) {
        if (refreshTick > 0L) {
            refreshAlpha.snapTo(Motion.REFRESH_DIM)
            refreshAlpha.animateTo(
                targetValue = 1f,
                animationSpec = Motion.Refresh
            )
        }
    }
    val refreshFlashModifier = Modifier.graphicsLayer { alpha = refreshAlpha.value }

    // 长按封面弹出的预览视频；null 表示弹窗关闭
    var previewVideo by remember { mutableStateOf<Video?>(null) }
    // 弹窗是独立窗口：把开关交给外壳，弹窗期间整体模糊页面内容，遮罩透出磨砂。
    // 在打开回调里同步置位，避免弹窗与模糊落在不同帧。
    val previewOpen = LocalPreviewOpen.current
    DisposableEffect(Unit) {
        onDispose { previewOpen.value = false }
    }

    // 女演员页顶栏搜索：false=显示搜索图标（首页同款），true=就地浮出全宽搜索框
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    // 搜索展开时系统返回键优先收起搜索框，而不是直接退出页面
    BackHandler(enabled = searchExpanded) { searchExpanded = false }
    val actressSearchField = state.searchField

    Scaffold(
        topBar = {
            Crossfade(
                targetState = searchExpanded,
                animationSpec = Motion.Overlay,
                label = "channelTopBar"
            ) { expanded ->
                if (expanded && actressSearchField != null) {
                    // 浮出态：返回箭头收起 + 全宽搜索框（自动聚焦，回车提交 q）
                    TopAppBar(
                        title = {
                            ActressSearchExpandedField(
                                hint = actressSearchField.hint,
                                committedQuery = filterSelections[actressSearchField.key].orEmpty(),
                                onSubmit = { query ->
                                    viewModel.submitSearch(actressSearchField.key, query)
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { searchExpanded = false }) {
                                Icon(
                                    imageVector = AppIcons.ArrowBack,
                                    contentDescription = stringResource(R.string.channel_back)
                                )
                            }
                        },
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )
                } else {
                    TopAppBar(
                        title = {
                            // 页头 .pagehead__count 作副标题：优先展示网页原文
                            // （视频页“361,346个视频”、女演员页“36,558位女演员”单位不同）；
                            // 只有数值没有原文时回退“N 个视频”格式
                            Column {
                                Text(title, maxLines = 1)
                                val countSubtitle = state.totalCountText
                                    ?: state.totalCount?.let {
                                        stringResource(R.string.channel_video_count, it)
                                    }
                                countSubtitle?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = AppIcons.ArrowBack,
                                    contentDescription = stringResource(R.string.channel_back)
                                )
                            }
                        },
                        actions = {
                            // 仅女演员索引页（带 .asearch 表单）显示搜索图标，首页同款
                            if (actressSearchField != null) {
                                IconButton(onClick = { searchExpanded = true }) {
                                    Icon(
                                        imageVector = AppIcons.Search,
                                        contentDescription = stringResource(R.string.home_search),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        val hasContent = state.videos.isNotEmpty() || state.entries.isNotEmpty()

        when {
            // 索引聚合页（类别/演员/制作商/系列）：条目网格。
            // 用 isIndex 而非 entries 非空判断：女演员页筛选 0 结果时条目为空，
            // 但搜索框/筛选栏必须保留，否则用户无法改回条件
            state.isIndex -> EntriesGrid(
                entries = state.entries,
                isLoading = state.isLoading,
                currentPage = state.page,
                totalPages = state.pagination?.totalPages,
                // “集合-类别”(/cn/genres) 条目多，保留本地过滤框（网页 x-show 纯前端过滤）
                showLocalFilter = isGenresIndexPath(path),
                // 女演员页（/cn/actresses）：整行卡片 + 服务端筛选（搜索入口在顶栏）
                actressLayout = isActressesIndexPath(path),
                serverFilters = state.filters,
                filterSelections = filterSelections,
                filtersEnabled = !state.isLoading,
                onFilterSelect = viewModel::applyFilter,
                onFilterReset = viewModel::resetFilters,
                onPageSelected = viewModel::loadPage,
                onEntryClick = onEntryClick,
                modifier = modifier
            )
            // 带筛选表单的视频流页：该分支必须排在通用“加载/出错/空态”之前，
            // 否则筛选到 0 条结果时整屏空态会先命中，筛选栏被藏掉导致无法改回条件。
            // 内部自行处理加载中/出错/0 结果/有列表四种状态，筛选栏始终保留。
            state.filters.isNotEmpty() -> PullToRefreshBox(
                isRefreshing = isPullRefreshing,
                onRefresh = viewModel::refresh,
                modifier = modifier
            ) {
                Column(Modifier.fillMaxSize().then(refreshFlashModifier)) {
                    VideoFilterBar(
                        groups = state.filters,
                        selections = filterSelections,
                        enabled = !state.isLoading,
                        onSelect = viewModel::applyFilter,
                        onReset = viewModel::resetFilters
                    )
                    when {
                        state.isLoading && state.videos.isEmpty() ->
                            LoadingState(Modifier.fillMaxSize())
                        state.error != null && state.videos.isEmpty() ->
                            ErrorState(
                                message = appErrorText(state.error!!),
                                onRetry = viewModel::retry,
                                modifier = Modifier.fillMaxSize()
                            )
                        state.videos.isEmpty() -> EmptyState(
                            icon = AppIcons.VideoLibraryOutline,
                            title = stringResource(R.string.channel_empty_title),
                            subtitle = stringResource(R.string.channel_empty_subtitle),
                            actionText = stringResource(R.string.common_retry),
                            onAction = viewModel::retry,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> VideosGrid(
                            state = state,
                            onVideoClick = onVideoClick,
                            onPreview = {
                                previewOpen.value = true
                                previewVideo = it
                            },
                            onPageSelected = viewModel::loadPage,
                            modifier = Modifier.fillMaxSize(),
                            actressPath = path
                        )
                    }
                }
            }
            // 以下为无筛选表单页面的通用状态（首次加载时筛选表单也尚未解析回来）
            state.isLoading && !hasContent -> LoadingState(modifier)
            state.error != null && !hasContent ->
                ErrorState(
                    message = appErrorText(state.error!!),
                    onRetry = viewModel::retry,
                    modifier = modifier
                )
            !hasContent -> EmptyState(
                icon = AppIcons.VideoLibraryOutline,
                title = stringResource(R.string.channel_empty_title),
                subtitle = stringResource(R.string.channel_empty_subtitle),
                actionText = stringResource(R.string.common_retry),
                onAction = viewModel::retry,
                modifier = modifier
            )
            // 无筛选表单的视频流页（如部分快捷入口）：直接渲染视频网格
            else -> PullToRefreshBox(
                isRefreshing = isPullRefreshing,
                onRefresh = viewModel::refresh,
                modifier = modifier
            ) {
                VideosGrid(
                    state = state,
                    onVideoClick = onVideoClick,
                    onPreview = {
                        previewOpen.value = true
                        previewVideo = it
                    },
                    onPageSelected = viewModel::loadPage,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(refreshFlashModifier),
                    actressPath = path
                )
            }
        }
    }

    previewVideo?.let { video ->
        VideoPreviewDialog(
            video = video,
            onDismiss = {
                previewVideo = null
                previewOpen.value = false
            },
            onVideoClick = {
                previewVideo = null
                previewOpen.value = false
                onVideoClick(it.id)
            }
        )
    }
}

/**
 * 是否为“集合-类别”索引页（路径形如 /cn/genres，去掉查询串与结尾斜杠后末段为 genres）。
 * 只有该页展示顶部本地“筛选条目”框，其他聚合页（演员/发行/系列）不显示。
 */