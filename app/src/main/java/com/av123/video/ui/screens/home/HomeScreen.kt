package com.av123.video.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.model.Video
import com.av123.video.data.model.WatchRecord
import com.av123.video.ui.components.BrandLogo
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.ErrorState
import com.av123.video.ui.components.appErrorText
import com.av123.video.ui.components.FeaturedCard
import com.av123.video.ui.components.LoadingState
import com.av123.video.ui.components.PaginationBar
import com.av123.video.ui.components.SectionHeader
import com.av123.video.ui.components.VideoCompactCard
import com.av123.video.ui.components.VideoGridCard
import com.av123.video.ui.components.VideoPreviewDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSectionMore: (title: String, path: String) -> Unit,
    scrollToTopTick: Int = 0
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel { HomeViewModel(container.videoRepository) }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshTick by viewModel.refreshFinishTick.collectAsStateWithLifecycle()
    // “继续观看”：有进度（>0）且未看完（<95%）的最近 10 条历史
    val continueWatching by container.historyRepository.records
        .map { records ->
            records
                .filter { it.positionMs > 0L && it.progress in 0.01f..0.95f }
                .take(CONTINUE_WATCHING_MAX)
        }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // 下拉刷新完成后的瞬间反馈：内容快速压暗再淡入（约 380ms），
    // 即使网页数据没变化（如精选页），也能明确感知“刷新过了”
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

    // 长按封面弹出的预览视频；null 表示弹窗关闭
    var previewVideo by remember { mutableStateOf<Video?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 站点同款品牌字标：渐变“123” + 主题色“MV” + 渐变装饰块
                    BrandLogo()
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.home_search),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()

        when {
            ui.isLoading -> LoadingState(modifier)
            ui.error != null && ui.feed == null ->
                ErrorState(message = appErrorText(ui.error!!), onRetry = viewModel::refresh, modifier = modifier)
            else -> PullToRefreshBox(
                isRefreshing = ui.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = modifier
            ) {
                HomeContent(
                    ui = ui,
                    continueWatching = continueWatching,
                    scrollToTopTick = scrollToTopTick,
                    onSelectCategory = viewModel::selectCategory,
                    onSelectPage = viewModel::selectPage,
                    onVideoClick = onVideoClick,
                    onVideoLongClick = { previewVideo = it },
                    onSectionMore = onSectionMore,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = refreshAlpha.value }
                )
            }
        }
    }

    // 长按封面预览：静音循环播放网页 data-preview 指向的预览视频
    previewVideo?.let { video ->
        VideoPreviewDialog(
            video = video,
            onDismiss = { previewVideo = null },
            // 点击预览视频：先关闭弹窗（释放预览播放器），再进入对应播放页
            onVideoClick = {
                previewVideo = null
                onVideoClick(it.id)
            }
        )
    }
}

@Composable
private fun HomeContent(
    ui: HomeUiState,
    continueWatching: List<WatchRecord>,
    scrollToTopTick: Int,
    onSelectCategory: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
    onVideoClick: (String) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onSectionMore: (title: String, path: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 分类筛选属于"原地内容刷新"：网格容器位置不变，仅内容交叉淡化。
    // 按 M3 规范这属于 Fade 模式（fade through 用于无关联的底部导航切换，
    // shared axis 用于有空间导航关系的页面跳转，均不适合筛选场景）。
    // 参考：https://m2.material.io/design/motion/the-motion-system.html
    Crossfade(
        targetState = ui.selectedCategory,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        modifier = modifier,
        label = "homeCategorySwitch"
    ) { category ->
        // 分类列表页状态：请求过该分类的列表页且拿到视频时使用它（带分页栏）；
        // 未请求/为空/失败时回退到首页 feed 的分组数据。
        val pageState = if (category == "全部") null else ui.categoryPages[category]
        val pagedVideos = pageState?.videos?.takeIf { it.isNotEmpty() }
        val gridState = rememberLazyGridState()
        // 翻页后回到列表顶部
        LaunchedEffect(pageState?.page) {
            if (pageState?.page != null) gridState.scrollToItem(0)
        }
        // 重复点击底部 Tab：回到顶部（tick 为 0 的初始值不触发）
        LaunchedEffect(scrollToTopTick) {
            if (scrollToTopTick > 0) gridState.scrollToItem(0)
        }
        // 区块"查看全部"文案（LazyGridScope 内不可直接调 stringResource，提前取出）
        val viewAllText = stringResource(R.string.home_section_view_all)
        val hotTitle = stringResource(R.string.home_section_hot)
        val latestTitle = stringResource(R.string.home_section_latest)
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 分类筛选（网页同款胶囊标签，选中态为站点粉色渐变）
            item(span = { GridItemSpan(maxLineSpan) }) {
                CategoryChips(
                    categories = ui.categories,
                    selected = ui.selectedCategory,
                    onSelect = onSelectCategory
                )
            }

            if (category == "全部") {
                val feed = ui.feed

                // 精选轮播
                if (feed != null && feed.featured.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FeaturedPager(feed.featured, onVideoClick)
                    }
                }

                // 继续观看（未看完的历史，带进度条）
                if (continueWatching.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueWatchingSection(
                            records = continueWatching,
                            onVideoClick = onVideoClick
                        )
                    }
                }

                if (feed != null) {
                    // 热门排行（两列网格）
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = hotTitle,
                            // 仅当网页区块提供了 section__all 链接（如 /cn/trending）时显示
                            actionText = viewAllText.takeIf { feed.hotMorePath.isNotBlank() },
                            onActionClick = { onSectionMore(hotTitle, feed.hotMorePath) }
                        )
                    }
                    items(feed.hot, key = { it.id }) { video ->
                        VideoGridCard(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            onLongClick = { if (video.previewUrl.isNotBlank()) onVideoLongClick(video) }
                        )
                    }

                    // 最新上架（横向滑动）
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = latestTitle,
                            actionText = viewAllText.takeIf { feed.latestMorePath.isNotBlank() },
                            onActionClick = { onSectionMore(latestTitle, feed.latestMorePath) }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LatestRow(feed.latest, onVideoClick, onVideoLongClick)
                    }
                }
            } else if (pagedVideos != null) {
                // 分类列表页数据（来自 /new、/recent 等独立列表页，可能带分页）
                if (pageState.isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        )
                    }
                }
                items(pagedVideos, key = { it.id }) { video ->
                    VideoGridCard(
                        video = video,
                        onClick = { onVideoClick(video.id) },
                        onLongClick = { if (video.previewUrl.isNotBlank()) onVideoLongClick(video) }
                    )
                }
                // 仅当网页页脚解析到 .pager 结构时显示换页组件
                pageState.pagination?.let { pager ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PaginationBar(
                            currentPage = pager.currentPage,
                            totalPages = pager.totalPages,
                            enabled = !pageState.isLoading,
                            onPageSelected = onSelectPage
                        )
                    }
                }
            } else if (pageState?.isLoading == true) {
                // 首次加载分类列表页
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.height(240.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                // 回退：首页 feed 中按分类名分组的视频（无独立列表页或请求失败）
                val fallbackVideos = ui.feed?.byCategory?.get(category).orEmpty()
                if (fallbackVideos.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.height(240.dp)) {
                            EmptyState(
                                icon = Icons.Outlined.VideoLibrary,
                                title = stringResource(R.string.home_category_empty_title),
                                subtitle = stringResource(R.string.home_category_empty_subtitle)
                            )
                        }
                    }
                } else {
                    items(fallbackVideos, key = { it.id }) { video ->
                        VideoGridCard(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            onLongClick = { if (video.previewUrl.isNotBlank()) onVideoLongClick(video) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            CategoryPill(
                text = category,
                selected = category == selected,
                onClick = { onSelect(category) }
            )
        }
    }
}

/**
 * 分类胶囊标签：全圆角、表面色底、灰字；
 * 选中时为主题 primary 底 + onPrimary 字 + primary 色投影。
 * 颜色全部取自 colorScheme：动态取色时跟随壁纸，关闭时为品牌粉 #ff5b79。
 */
@Composable
private fun CategoryPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val container by animateColorAsState(
        targetValue = if (selected) primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        label = "pillContainer"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillText"
    )
    Box(
        modifier = Modifier
            .then(
                if (selected) Modifier.shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = primary,
                    ambientColor = primary
                ) else Modifier
            )
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun FeaturedPager(
    featured: List<Video>,
    onVideoClick: (String) -> Unit
) {
    // 初始定位到中间页：聚焦式轮播居中展开，左右两侧同时有卡片探出
    val initialPage = featured.size / 2
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { featured.size })
    val scope = rememberCoroutineScope()

    // 自动轮播：每 5s 前进一页；仅页面 STARTED 时运行（后台/详情页返回后停止），
    // 用户手动拖动的当拍跳过；末页回首页用瞬时跳转避免反向连穿整列
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(pagerState, lifecycleOwner, featured.size) {
        if (featured.size < 2) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(FEATURED_AUTO_SCROLL_MS)
                if (pagerState.isScrollInProgress) continue
                val next = (pagerState.currentPage + 1) % featured.size
                if (next == 0) {
                    pagerState.scrollToPage(0)
                } else {
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }

    Column {
        // 聚焦式轮播：中心页全尺寸高亮，两侧页缩小、变暗从边缘探出（留间隙，不叠压中心卡）
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 44.dp),
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            // 该页相对中心页的偏移量：0 = 居中，1 = 完全相邻
            val offset = kotlin.math.abs(
                pagerState.currentPage - page + pagerState.currentPageOffsetFraction
            ).coerceIn(0f, 1f)
            val scale = 1f - 0.14f * offset
            val alpha = 1f - 0.45f * offset
            FeaturedCard(
                video = featured[page],
                // 点击侧边卡：滚动到居中；点击中心卡：进入详情（主流轮播交互）
                onClick = {
                    if (page == pagerState.currentPage) {
                        onVideoClick(featured[page].id)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            )
        }

        if (featured.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(featured.size) { i ->
                    val selected = i == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 18.dp else 6.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "dotWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun LatestRow(
    videos: List<Video>,
    onVideoClick: (String) -> Unit,
    onVideoLongClick: (Video) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(videos, key = { it.id }) { video ->
            VideoCompactCard(
                video = video,
                onClick = { onVideoClick(video.id) },
                onLongClick = { if (video.previewUrl.isNotBlank()) onVideoLongClick(video) }
            )
        }
    }
}

/** 首页“继续观看”区块：标题 + 带进度条的横向卡片列表 */
@Composable
private fun ContinueWatchingSection(
    records: List<WatchRecord>,
    onVideoClick: (String) -> Unit
) {
    Column {
        SectionHeader(title = stringResource(R.string.home_continue_watching))
        LazyRow(
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(records, key = { it.video.id }) { record ->
                VideoCompactCard(
                    video = record.video,
                    onClick = { onVideoClick(record.video.id) },
                    progress = record.progress
                )
            }
        }
    }
}

/** “继续观看”最多展示条数 */
private const val CONTINUE_WATCHING_MAX = 10

/** 精选轮播自动翻页间隔 */
private const val FEATURED_AUTO_SCROLL_MS = 5_000L
