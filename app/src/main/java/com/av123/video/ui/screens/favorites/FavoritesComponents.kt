package com.av123.video.ui.screens.favorites

import com.av123.video.ui.icons.AppIcons

import com.av123.video.ui.theme.AppShapes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.model.FavoriteRecord
import com.av123.video.data.repository.FavoriteGroup
import com.av123.video.data.repository.GroupEditResult
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.VideoGridCard
import com.av123.video.ui.components.VideoLayoutToggle
import com.av123.video.ui.util.rememberAppHaptics
import kotlinx.coroutines.launch

/**
 * 收藏页普通顶栏：
 * - 总览：标题“我的收藏(N)” + 新建收藏夹（有收藏时显示清空）；
 * - 内容：返回总览 + 范围标题；指定收藏夹时可从溢出位置打开操作表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesTopBar(
    destination: FavoritesDestination,
    totalCount: Int,
    onBack: () -> Unit,
    onNewGroup: () -> Unit,
    onClear: () -> Unit,
    onGroupMenu: (FavoriteGroup) -> Unit,
    resolveGroup: (Long) -> FavoriteGroup?
) {
    val haptics = rememberAppHaptics()
    TopAppBar(
        title = {
            when (destination) {
                FavoritesDestination.Overview ->
                    Text(
                        if (totalCount > 0) {
                            stringResource(R.string.favorites_title_count, totalCount)
                        } else {
                            stringResource(R.string.favorites_title)
                        }
                    )

                is FavoritesDestination.Content ->
                    when (val filter = destination.filter) {
                        FavoritesFilter.All ->
                            Text(stringResource(R.string.favorites_all_videos))
                        FavoritesFilter.Ungrouped ->
                            Text(stringResource(R.string.favorites_ungrouped_label))
                        is FavoritesFilter.Group ->
                            Text(resolveGroup(filter.groupId)?.name.orEmpty())
                    }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    haptics.tick()
                    onBack()
                }
            ) {
                Icon(
                    AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.favorites_back)
                )
            }
        },
        actions = {
            // 网格 / 单列切换（与首页、频道、搜索共用一份偏好）
            VideoLayoutToggle()
            val filter = (destination as? FavoritesDestination.Content)?.filter
            val groupFilter = filter as? FavoritesFilter.Group
            if (groupFilter != null) {
                IconButton(onClick = {
                    haptics.tick()
                    resolveGroup(groupFilter.groupId)?.let(onGroupMenu)
                }) {
                    Icon(
                        AppIcons.MoreVert,
                        contentDescription = stringResource(R.string.favorites_folder_actions)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        haptics.tick()
                        onNewGroup()
                    }
                ) {
                    Icon(
                        AppIcons.CreateNewFolder,
                        contentDescription = stringResource(R.string.favorites_new_group)
                    )
                }
                // “清空”只在全部视频范围提供，未分组/空列表不展示
                if (filter == FavoritesFilter.All && totalCount > 0) {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onClear()
                        }
                    ) {
                        Icon(
                            AppIcons.DeleteSweep,
                            contentDescription = stringResource(R.string.favorites_clear)
                        )
                    }
                }
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

/**
 * 收藏总览：快捷入口（全部视频 / 未分组）+ 收藏夹列表 + 新建按钮。
 * 参考 Google Photos / iOS 相册的相册页：文件夹是一等公民，行内可见封面与数量。
 */
@Composable
internal fun FavoritesOverview(
    state: FavoritesUiState,
    onNavigateHome: () -> Unit,
    onOpenFilter: (FavoritesFilter) -> Unit,
    onNewGroup: () -> Unit,
    onFolderActions: (FavoriteGroup) -> Unit
) {
    if (state.records.isEmpty() && state.groups.isEmpty()) {
        EmptyState(
            icon = AppIcons.VideoLibrary,
            title = stringResource(R.string.favorites_empty_title),
            subtitle = stringResource(R.string.favorites_empty_subtitle),
            actionText = stringResource(R.string.action_go_home),
            onAction = onNavigateHome,
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShortcutCard(
                    icon = AppIcons.VideoLibrary,
                    label = stringResource(R.string.favorites_all_videos),
                    count = state.records.size,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onOpenFilter(FavoritesFilter.All) },
                    modifier = Modifier.weight(1f)
                )
                ShortcutCard(
                    icon = AppIcons.Inbox,
                    label = stringResource(R.string.favorites_ungrouped_label),
                    count = state.ungroupedCount,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onOpenFilter(FavoritesFilter.Ungrouped) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.favorites_section_groups),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
            )
        }
        if (state.groups.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.favorites_no_groups_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
        }
        items(state.groups, key = { it.id }) { group ->
            FolderRow(
                group = group,
                previewRecords = state.groupPreview(group.id),
                count = state.groupCount(group.id),
                onClick = { onOpenFilter(FavoritesFilter.Group(group.id)) },
                onLongClick = { onFolderActions(group) }
            )
        }
        item {
            OutlinedButton(
                onClick = onNewGroup,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Icon(AppIcons.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.favorites_new_group),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** 总览顶部的快捷入口卡片：着色图标 + 名称 + 数量 */
@Composable
internal fun ShortcutCard(
    icon: ImageVector,
    label: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(AppShapes.ExtraLarge)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = stringResource(R.string.favorites_group_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.8f)
        )
    }
}

/**
 * 收藏夹行：最新收藏封面（无封面用文件夹图标）+ 名称·数量 + 进入箭头；
 * 长按弹出操作表
 */
@Composable
internal fun FolderRow(
    group: FavoriteGroup,
    previewRecords: List<FavoriteRecord>,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val coverUrl = previewRecords.firstOrNull()?.video?.coverUrl
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(AppShapes.Medium)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    AppIcons.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 14.dp)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.favorites_group_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            AppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 某个范围的收藏内容网格（全部 / 未分组 / 指定收藏夹）：
 * 两列卡片、长按进多选、左右滑单删；空结果显示行内空态。
 */
@Composable
internal fun FolderContent(
    filter: FavoritesFilter,
    records: List<FavoriteRecord>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onSwipeDelete: (FavoriteRecord) -> Unit,
    listLayout: Boolean = false
) {
    if (records.isEmpty()) {
        val titleRes: Int
        val subtitleRes: Int
        when (filter) {
            FavoritesFilter.All -> {
                titleRes = R.string.favorites_empty_title
                subtitleRes = R.string.favorites_empty_subtitle
            }
            FavoritesFilter.Ungrouped -> {
                titleRes = R.string.favorites_empty_group_title
                subtitleRes = R.string.favorites_empty_group_subtitle
            }
            is FavoritesFilter.Group -> {
                titleRes = R.string.favorites_empty_group_title
                subtitleRes = R.string.favorites_empty_group_subtitle
            }
        }
        EmptyState(
            icon = AppIcons.Folder,
            title = stringResource(titleRes),
            subtitle = stringResource(subtitleRes),
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (listLayout) 1 else 2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (listLayout) 8.dp else 12.dp)
    ) {
        items(records, key = { it.video.id }) { record ->
            SwipeableFavoriteCard(
                record = record,
                selectionMode = selectionMode,
                selected = record.video.id in selectedIds,
                onClick = {
                    if (selectionMode) onToggleSelection(record.video.id)
                    else onVideoClick(record.video.id)
                },
                onLongClick = { onEnterSelection(record.video.id) },
                onDismiss = { onSwipeDelete(record) },
                listLayout = listLayout
            )
        }
    }
}
