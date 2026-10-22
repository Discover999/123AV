package com.av123.video.ui.screens.channels

import com.av123.video.ui.icons.AppIcons

import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.util.rememberAppHaptics

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
import com.av123.video.ui.theme.Motion
import com.av123.video.data.model.ActressProfile
import com.av123.video.data.model.FilterGroup
import com.av123.video.data.model.FilterOption
import com.av123.video.data.model.IndexEntry
import com.av123.video.data.model.Video
import com.av123.video.data.repository.CategoryPageState
import com.av123.video.data.repository.VideoRepository
import com.av123.video.ui.components.ActressAvatar
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

internal fun isGenresIndexPath(path: String): Boolean =
    path.substringBefore('?').trimEnd('/').substringAfterLast('/') == "genres"

/**
 * 是否为“女演员”索引页（路径形如 /cn/actresses）。
 * 该页条目用竖向卡片网格，且带服务端搜索框与身高/罩杯/年龄/排序筛选。
 * 注意 /cn/actresses/<name> 是某位演员的视频流页，末段不是 actresses。
 */
internal fun isActressesIndexPath(path: String): Boolean =
    path.substringBefore('?').trimEnd('/').substringAfterLast('/') == "actresses"

/**
 * 索引聚合页：
 * - 女演员页：筛选下拉栏（身高/罩杯/年龄/排序）+ 整行演员卡，搜索入口在顶栏图标；
 * - 类别页：本地“筛选条目”框 + 自适应多列小卡；
 * - 系列/制作商页：整行单列卡片。
 */
@Composable
internal fun EntriesGrid(
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
                        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                        singleLine = true,
                        shape = AppShapes.Medium
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
internal fun ActressSearchExpandedField(
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
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        trailingIcon = if (input.isNotEmpty()) {
            {
                IconButton(onClick = {
                    input = ""
                    onSubmit("")
                    keyboardController?.hide()
                }) {
                    Icon(
                        imageVector = AppIcons.Close,
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
internal fun EntryCard(entry: IndexEntry, onClick: () -> Unit) {
    Surface(
        shape = AppShapes.Medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Medium)
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
internal fun MakerCard(entry: IndexEntry, onClick: () -> Unit) {
    Surface(
        shape = AppShapes.ExtraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.ExtraLarge)
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
                    .clip(AppShapes.Medium)
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
                imageVector = AppIcons.ChevronRight,
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
internal fun ActressCard(entry: IndexEntry, onClick: () -> Unit) {
    val container = LocalAppContainer.current
    val followScope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    // 关注身份只认演员主页基础路径，不带筛选/排序查询串
    val followPath = entry.path.substringBefore('?')
    val isFollowed by container.followedActressRepository.isFollowed(followPath)
        .collectAsStateWithLifecycle(initialValue = false)
    val followLabel = stringResource(
        if (isFollowed) R.string.actress_followed
        else R.string.actress_follow
    )

    // 胶囊底色 / 爱心色随关注态平滑过渡，爱心带点赞弹出
    val capsuleColor by animateColorAsState(
        targetValue = if (isFollowed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = Motion.MorphColorSpring,
        label = "actressCapsule"
    )
    val heartColor by animateColorAsState(
        targetValue = if (isFollowed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = Motion.MorphColorSpring,
        label = "actressCapsuleHeart"
    )
    val heartScale = rememberTogglePopScale(active = isFollowed)

    Surface(
        shape = AppShapes.ExtraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.ExtraLarge)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActressAvatar(
                url = entry.avatarUrl,
                modifier = Modifier.size(84.dp),
                iconSize = 36.dp
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
                    ActressStat(AppIcons.VideocamOutline, entry.videos, Modifier.weight(1f))
                    ActressStat(AppIcons.VisibilityOutline, entry.views, Modifier.weight(1f))
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
                        haptics.tick()
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
                                AppIcons.Favorite
                            } else {
                                AppIcons.FavoriteBorder
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
                            text = entry.followersLabel.ifBlank { followLabel },
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
internal fun ActressStat(
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
internal fun EntryRow(entry: IndexEntry, onClick: () -> Unit) {
    Surface(
        shape = AppShapes.Medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Medium)
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
                imageVector = AppIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

