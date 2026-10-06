package com.av123.video.ui.screens.channels

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
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
import com.av123.video.data.repository.FollowedActress
import com.av123.video.R
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
            refreshAlpha.snapTo(0.25f)
            refreshAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            )
        }
    }
    val refreshFlashModifier = Modifier.graphicsLayer { alpha = refreshAlpha.value }

    // 长按封面弹出的预览视频；null 表示弹窗关闭
    var previewVideo by remember { mutableStateOf<Video?>(null) }

    // 女演员页顶栏搜索：false=显示搜索图标（首页同款），true=就地浮出全宽搜索框
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    // 搜索展开时系统返回键优先收起搜索框，而不是直接退出页面
    BackHandler(enabled = searchExpanded) { searchExpanded = false }
    val actressSearchField = state.searchField

    Scaffold(
        topBar = {
            Crossfade(
                targetState = searchExpanded,
                animationSpec = tween(durationMillis = 180),
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
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.channel_back)
                                )
                            }
                        },
                        actions = {
                            // 仅女演员索引页（带 .asearch 表单）显示搜索图标，首页同款
                            if (actressSearchField != null) {
                                IconButton(onClick = { searchExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
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
                            icon = Icons.Outlined.VideoLibrary,
                            title = stringResource(R.string.channel_empty_title),
                            subtitle = stringResource(R.string.channel_empty_subtitle),
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> VideosGrid(
                            state = state,
                            onVideoClick = onVideoClick,
                            onPreview = { previewVideo = it },
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
                icon = Icons.Outlined.VideoLibrary,
                title = stringResource(R.string.channel_empty_title),
                subtitle = stringResource(R.string.channel_empty_subtitle),
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
                    onPreview = { previewVideo = it },
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
            onDismiss = { previewVideo = null },
            onVideoClick = {
                previewVideo = null
                onVideoClick(it.id)
            }
        )
    }
}

/**
 * 是否为“集合-类别”索引页（路径形如 /cn/genres，去掉查询串与结尾斜杠后末段为 genres）。
 * 只有该页展示顶部本地“筛选条目”框，其他聚合页（演员/发行/系列）不显示。
 */
private fun isGenresIndexPath(path: String): Boolean =
    path.substringBefore('?').trimEnd('/').substringAfterLast('/') == "genres"

/**
 * 是否为“女演员”索引页（路径形如 /cn/actresses）。
 * 该页条目用竖向卡片网格，且带服务端搜索框与身高/罩杯/年龄/排序筛选。
 * 注意 /cn/actresses/<name> 是某位演员的视频流页，末段不是 actresses。
 */
private fun isActressesIndexPath(path: String): Boolean =
    path.substringBefore('?').trimEnd('/').substringAfterLast('/') == "actresses"

/**
 * 索引聚合页：
 * - 女演员页：筛选下拉栏（身高/罩杯/年龄/排序）+ 整行演员卡，搜索入口在顶栏图标；
 * - 类别页：本地“筛选条目”框 + 自适应多列小卡；
 * - 系列/制作商页：整行单列卡片。
 */
@Composable
private fun EntriesGrid(
    entries: List<IndexEntry>,
    isLoading: Boolean,
    currentPage: Int,
    totalPages: Int?,
    showLocalFilter: Boolean,
    actressLayout: Boolean,
    serverFilters: List<FilterGroup>,
    filterSelections: Map<String, String>,
    filtersEnabled: Boolean,
    onFilterSelect: (key: String, value: String) -> Unit,
    onFilterReset: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onEntryClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    // 与网页 x-show 过滤一致：条目名包含关键词即显示（忽略大小写）；
    // 非类别页无本地筛选框，始终展示全部条目
    val filtered = remember(entries, query, showLocalFilter) {
        val q = query.trim()
        if (!showLocalFilter || q.isEmpty()) entries
        else entries.filter { it.name.contains(q, ignoreCase = true) }
    }
    // 四种条目形态：系列/演员/制作商=整行单列；类别=自适应多列小卡
    val first = entries.firstOrNull()
    val ranked = first?.rank != null
    val actresses = actressLayout || first?.avatarUrl?.isNotBlank() == true
    val makers = first?.logo?.isNotBlank() == true
    val singleColumn = ranked || actresses || makers
    val gridState = rememberLazyGridState()
    // 翻页或切换筛选条件后回到顶部（筛选切回第 1 页时 page 值不变，故同时盯 selections）
    LaunchedEffect(currentPage, filterSelections) { gridState.scrollToItem(0) }

    Column(modifier = modifier.fillMaxSize()) {
        // 女演员页：身高/罩杯/年龄 + 排序下拉（复用视频流页筛选栏）；
        // 关键词搜索入口已上移到顶栏搜索图标
        if (serverFilters.isNotEmpty()) {
            VideoFilterBar(
                groups = serverFilters,
                selections = filterSelections,
                enabled = filtersEnabled,
                onSelect = onFilterSelect,
                onReset = onFilterReset
            )
        }
        LazyVerticalGrid(
            state = gridState,
            columns = if (singleColumn) GridCells.Fixed(1)
            else GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(if (singleColumn) 0.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showLocalFilter) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        placeholder = { Text(stringResource(R.string.channel_filter_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            if (isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }
            // 类别页本地过滤无匹配
            if (showLocalFilter && query.isNotBlank() && filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.channel_filter_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    )
                }
            }
            // 女演员等索引页服务端筛选 0 结果（非加载中）：保留筛选栏并提示无匹配
            if (!showLocalFilter && filtered.isEmpty() && !isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.channel_filter_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    )
                }
            }
            items(filtered, key = { it.path }) { entry ->
                when {
                    ranked -> EntryRow(entry = entry, onClick = { onEntryClick(entry.name, entry.path) })
                    actresses -> ActressCard(entry = entry, onClick = { onEntryClick(entry.name, entry.path) })
                    makers -> MakerCard(entry = entry, onClick = { onEntryClick(entry.name, entry.path) })
                    else -> EntryCard(entry = entry, onClick = { onEntryClick(entry.name, entry.path) })
                }
            }
            if (totalPages != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PaginationBar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        enabled = !isLoading,
                        onPageSelected = onPageSelected
                    )
                }
            }
        }
    }
}

/**
 * 女演员页顶栏浮出的搜索框（点击搜索图标后替换整个 TopAppBar 标题区）：
 * 展开即自动聚焦弹键盘；占位文案取网页 placeholder（如“搜索 女演员…”）；
 * 软键盘搜索/回车提交 q 参数；右侧 X 清空并立即提交空关键词。
 * [committedQuery] 为服务端当前生效关键词，“重置筛选”后同步清空输入框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActressSearchExpandedField(
    hint: String,
    committedQuery: String,
    onSubmit: (String) -> Unit
) {
    var input by rememberSaveable { mutableStateOf(committedQuery) }
    LaunchedEffect(committedQuery) {
        if (input != committedQuery) input = committedQuery
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = {
            // TopAppBar 标题槽默认 titleLarge，会把输入文字放大加粗，这里显式用正文样式
            Text(
                text = hint.ifBlank { stringResource(R.string.channel_filter_hint) },
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (input.isNotEmpty()) {
            {
                IconButton(onClick = {
                    input = ""
                    onSubmit("")
                    keyboardController?.hide()
                }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.search_clear)
                    )
                }
            }
        } else null,
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSubmit(input)
            keyboardController?.hide()
        }),
        colors = TextFieldDefaults.colors(
            // 填入 TopAppBar 标题槽，去掉默认下划线
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun EntryCard(entry: IndexEntry, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            if (entry.count.isNotBlank()) {
                Text(
                    text = stringResource(R.string.channel_entry_count, entry.count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * 制作商页整行卡片（对应网页 .mgrid > a.mcard，一行一个）：
 * 左侧 logo 徽标方块（.mcard__logo 文案，primaryContainer 底），
 * 中间制作商名称 + 视频数副文案（.mcard__meta，如“33,419个视频”），右侧跳转箭头。
 */
@Composable
private fun MakerCard(entry: IndexEntry, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // logo 徽标：网页为文字 wordmark，超长时尾部省略，保持方块不被撑开
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.logo.ifBlank { entry.name },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = entry.meta.ifBlank {
                    entry.count.takeIf { it.isNotBlank() }
                        ?.let { stringResource(R.string.channel_entry_count, it) }
                        .orEmpty()
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 演员页整行卡片（对应网页 .actress，一行一个）：
 * 左侧圆形头像，中间名字 + 视频/观看图标统计，右侧紧凑关注数胶囊。
 *
 * 右侧胶囊同时是“本地关注/已关注”开关（纯本机，不依赖站点账号）：
 * 点击关注或取关，已关注时显示实心爱心与主题色底；点击胶囊不会触发卡片跳转。
 */
@Composable
private fun ActressCard(entry: IndexEntry, onClick: () -> Unit) {
    val container = LocalAppContainer.current
    val followScope = rememberCoroutineScope()
    // 关注身份只认演员主页基础路径，不带筛选/排序查询串
    val followPath = entry.path.substringBefore('?')
    val isFollowed by container.followedActressRepository.isFollowed(followPath)
        .collectAsStateWithLifecycle(initialValue = false)

    // 胶囊底色 / 爱心色随关注态平滑过渡，爱心带点赞弹出
    val capsuleColor by animateColorAsState(
        targetValue = if (isFollowed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "actressCapsule"
    )
    val heartColor by animateColorAsState(
        targetValue = if (isFollowed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "actressCapsuleHeart"
    )
    val heartScale = rememberTogglePopScale(active = isFollowed)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = entry.avatarUrl,
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 只显示视频/观看；关注数由右侧心形胶囊（紧凑格式）展示，避免重复
                    ActressStat(Icons.Outlined.Videocam, entry.videos, Modifier.weight(1f))
                    ActressStat(Icons.Outlined.Visibility, entry.views, Modifier.weight(1f))
                }
            }
            // 网页给了关注数，或已被本机关注（关注数缺失时也要保留取关入口）：展示胶囊
            if (entry.followersLabel.isNotBlank() || isFollowed) {
                Surface(
                    shape = CircleShape,
                    color = capsuleColor,
                    // 内层点击消费事件，不会冒泡触发整卡跳转
                    modifier = Modifier.clickable(
                        onClickLabel = stringResource(
                            if (isFollowed) R.string.actress_followed
                            else R.string.actress_follow
                        )
                    ) {
                        followScope.launch {
                            if (isFollowed) {
                                container.followedActressRepository.unfollow(followPath)
                            } else {
                                container.followedActressRepository.follow(
                                    FollowedActress(
                                        path = followPath,
                                        name = entry.name,
                                        avatarUrl = entry.avatarUrl,
                                        meta = entry.meta
                                    )
                                )
                            }
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isFollowed) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = stringResource(
                                if (isFollowed) R.string.actress_followed
                                else R.string.actress_follow
                            ),
                            tint = heartColor,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = heartScale
                                    scaleY = heartScale
                                }
                        )
                        Text(
                            text = entry.followersLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** 演员统计项：小图标 + 数值，等宽分布，数值过长省略 */
@Composable
private fun ActressStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    modifier: Modifier = Modifier
) {
    if (value.isBlank()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 系列页排行行：序号 + 名称/副文案 + 右箭头（对应网页 .srow） */
@Composable
private fun EntryRow(entry: IndexEntry, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!entry.rank.isNullOrBlank()) {
                Text(
                    text = entry.rank,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    // 宽度随位数自适应（最小 34dp），避免大序号如 25123 被挤成两行
                    modifier = Modifier.widthIn(min = 34.dp)
                )
                // 序号与名称之间的固定间隔，避免“02项目122”贴在一起
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (entry.meta.isNotBlank()) {
                    Text(
                        text = entry.meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 视频流页顶部筛选栏（对齐网页 form.filters 的结构与交互）：
 * 左侧可横滑的维度胶囊（类型/年份/演员）+ 有非默认选中时出现的“重置”入口，
 * 中间竖分隔线，右侧固定排序（对应网页 .fspace 占位 + 菜单 is-right；
 * 排序选项同样动态解析，并非写死）。
 * 选择任意选项立即回到第 1 页重新加载，与网页 requestSubmit 行为一致。
 */
@Composable
private fun VideoFilterBar(
    groups: List<FilterGroup>,
    selections: Map<String, String>,
    enabled: Boolean,
    onSelect: (key: String, value: String) -> Unit,
    onReset: () -> Unit
) {
    if (groups.isEmpty()) return
    val rightGroup = groups.last()
    val leftGroups = groups.dropLast(1)
    val hasActive = groups.any { g ->
        selections[g.key] != (g.options.firstOrNull()?.value ?: "")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 0.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leftGroups.forEach { group ->
                FilterDropdown(
                    group = group,
                    selectedValue = selections[group.key] ?: group.selectedValue,
                    enabled = enabled,
                    onSelect = onSelect
                )
            }
            if (hasActive) {
                IconButton(
                    onClick = onReset,
                    enabled = enabled,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterAltOff,
                        contentDescription = stringResource(R.string.channel_filters_reset),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        // 分隔竖线：把左侧筛选维度与右侧固定排序视觉上分开（对应网页 .fspace），
        // 放在横滑区域之外，滚动时保持固定；仅当存在左侧维度时显示
        if (leftGroups.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(1.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        FilterDropdown(
            group = rightGroup,
            selectedValue = selections[rightGroup.key] ?: rightGroup.selectedValue,
            enabled = enabled,
            onSelect = onSelect,
            modifier = Modifier.padding(start = 8.dp, end = 16.dp)
        )
    }
}

/**
 * 单个筛选维度的胶囊按钮 + 下拉菜单：
 * 默认态中性底色、显示“维度: 当前值”；选了非默认项时 primaryContainer 高亮。
 * [FilterGroup.columns] > 1 时菜单按网页样式多列排布（年份），选中项带勾。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    group: FilterGroup,
    selectedValue: String,
    enabled: Boolean,
    onSelect: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = group.options.firstOrNull { it.value == selectedValue }?.label
        ?: group.selectedLabel
    val active = selectedValue != (group.options.firstOrNull()?.value ?: "")

    Box(modifier = modifier) {
        Surface(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            shape = CircleShape,
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp)
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = if (group.columns > 1) {
                Modifier.width(264.dp)
            } else {
                Modifier.widthIn(min = 168.dp)
            }
        ) {
            if (group.columns > 1) {
                // 多列菜单：按 --sel-cols 切行，末行不足补空位，保持列宽一致
                group.options.chunked(group.columns).forEach { rowOptions ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (i in 0 until group.columns) {
                            val option = rowOptions.getOrNull(i)
                            if (option == null) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                FilterOptionItem(
                                    option = option,
                                    selected = option.value == selectedValue,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        expanded = false
                                        onSelect(group.key, option.value)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                group.options.forEach { option ->
                    FilterOptionItem(
                        option = option,
                        selected = option.value == selectedValue,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            expanded = false
                            onSelect(group.key, option.value)
                        }
                    )
                }
            }
        }
    }
}

/** 下拉菜单里的一个选项：文案 + 选中勾，多列布局下也可等高复用 */
@Composable
private fun FilterOptionItem(
    option: FilterOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
            )
        }
    }
}

/**
 * 女演员个人页头部（对应网页 .ahero）：
 * 圆形头像 + 姓名/属性行（年龄·身高·三围·罩杯），右侧关注数胶囊（对应 .ahero__follow），
 * 下方视频/观看/关注者/出道四项统计。
 *
 * 底部“关注/已关注”为纯本地关注（不依赖站点账号）：关注后可在“我的-我的关注”里
 * 聚合查看新片。[actressPath] 为演员个人页站点路径（关注身份主键），为空时不显示按钮。
 */
@Composable
private fun ActressHero(
    profile: ActressProfile,
    actressPath: String?
) {
    val container = LocalAppContainer.current
    val followScope = rememberCoroutineScope()
    // 关注身份只认演员主页基础路径，不带筛选/排序查询串
    val followPath = actressPath?.substringBefore('?')
    val followedState = followPath?.let {
        container.followedActressRepository.isFollowed(it)
            .collectAsStateWithLifecycle(initialValue = false)
    }
    val isFollowed = followedState?.value == true
    val colorScheme = MaterialTheme.colorScheme

    // 按钮底色/内容色随关注态平滑过渡，而非瞬切
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isFollowed) {
            colorScheme.primary
        } else {
            colorScheme.secondaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "followButtonContainer"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = if (isFollowed) {
            colorScheme.onPrimary
        } else {
            colorScheme.onSecondaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "followButtonContent"
    )

    // 爱心“点赞弹出”缩放（关注瞬间收缩回弹；取关立即复位）
    val heartScale = rememberTogglePopScale(active = isFollowed)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                )
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile.meta.isNotBlank()) {
                        Text(
                            text = profile.meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                // 关注数胶囊：贴信息行右侧（对应网页 .ahero__follow 位于 .ahero__info 内）
                if (profile.followersLabel.isNotBlank()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(R.string.actress_follow),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = profile.followersLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            // 视频 / 观看 / 关注者 / 出道：等宽四列，某项网页缺失则只留出空位
            val stats = listOf(
                profile.videos to stringResource(R.string.actress_stat_videos),
                profile.views to stringResource(R.string.actress_stat_views),
                profile.followers to stringResource(R.string.actress_stat_followers),
                profile.debut to stringResource(R.string.actress_stat_debut)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stats.forEach { (value, label) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            // 本地关注/已关注：资料写入本机，个人中心“我的关注”聚合新片。
            // 底色/文字/爱心均随关注态做动画：颜色平滑过渡、爱心弹出回弹、文案淡入切换。
            if (!followPath.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = {
                        followScope.launch {
                            if (isFollowed) {
                                container.followedActressRepository.unfollow(followPath)
                            } else {
                                container.followedActressRepository.follow(
                                    FollowedActress(
                                        path = followPath,
                                        name = profile.name,
                                        avatarUrl = profile.avatarUrl,
                                        meta = profile.meta
                                    )
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = animatedContainerColor,
                        contentColor = animatedContentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isFollowed) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Outlined.FavoriteBorder
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                    )
                    Spacer(Modifier.width(8.dp))
                    AnimatedContent(
                        targetState = isFollowed,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)) togetherWith
                                fadeOut(animationSpec = tween(120))
                        },
                        label = "followLabel"
                    ) { followed ->
                        Text(
                            stringResource(
                                if (followed) R.string.actress_followed
                                else R.string.actress_follow
                            )
                        )
                    }
                }
            }
        }
    }
}

/** 视频流页：双列卡片网格 + 顶部女演员资料头（仅演员页）+ 分页进度条 + 页脚换页 */
@Composable
private fun VideosGrid(
    state: CategoryPageState,
    onVideoClick: (String) -> Unit,
    onPreview: (Video) -> Unit,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    actressPath: String? = null
) {
    val gridState = rememberLazyGridState()
    // 翻页后回到列表顶部
    LaunchedEffect(state.page) { gridState.scrollToItem(0) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 女演员个人页头部资料（.ahero）：始终置顶，翻页加载时也不消失
        state.actressProfile?.let { profile ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                ActressHero(profile = profile, actressPath = actressPath)
            }
        }
        // 翻页加载中：顶部进度条（列表不消失，避免整屏闪烁）
        if (state.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
            }
        }
        items(state.videos, key = { it.id }) { video ->
            VideoGridCard(
                video = video,
                onClick = { onVideoClick(video.id) },
                onLongClick = {
                    if (video.previewUrl.isNotBlank()) onPreview(video)
                }
            )
        }
        // 仅当网页页脚解析到 .pager 结构时显示换页组件
        state.pagination?.let { pager ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                PaginationBar(
                    currentPage = state.page,
                    totalPages = pager.totalPages,
                    enabled = !state.isLoading,
                    onPageSelected = onPageSelected
                )
            }
        }
    }
}
