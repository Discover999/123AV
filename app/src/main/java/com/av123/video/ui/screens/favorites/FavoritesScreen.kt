package com.av123.video.ui.screens.favorites

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.VideoLibrary
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
import kotlinx.coroutines.launch

/** 收藏夹新建/重命名编辑弹窗状态 */
private data class GroupEditorState(
    val renaming: FavoriteGroup?,
    val initialName: String
)

/** 收藏页内部导航位置：文件夹总览 / 某个范围（全部·未分组·指定收藏夹）的内容 */
private sealed interface FavoritesDestination {
    data object Overview : FavoritesDestination
    data class Content(val filter: FavoritesFilter) : FavoritesDestination
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: FavoritesViewModel = viewModel { FavoritesViewModel(container.favoriteRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var destination by remember { mutableStateOf<FavoritesDestination>(FavoritesDestination.Overview) }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    // 新建/重命名弹窗；null 不展示
    var groupEditor by remember { mutableStateOf<GroupEditorState?>(null) }
    // 待确认删除的收藏夹；null 不展示
    var pendingDeleteGroup by remember { mutableStateOf<FavoriteGroup?>(null) }
    // 多选“加入收藏夹”底部抽屉
    var showPicker by remember { mutableStateOf(false) }
    // 长按文件夹弹出的操作表；null 不展示
    var folderActionsFor by remember { mutableStateOf<FavoriteGroup?>(null) }

    val contentFilter = (destination as? FavoritesDestination.Content)?.filter
    val selectedGroup = contentFilter as? FavoritesFilter.Group

    // 当前查看的收藏夹被删除：返回总览
    LaunchedEffect(state.groups, destination) {
        if (selectedGroup != null && state.groups.none { it.id == selectedGroup.groupId }) {
            destination = FavoritesDestination.Overview
        }
    }
    // 位置变化时退出多选
    LaunchedEffect(destination) {
        if (selectionMode) selectionMode = false
        selectedIds = emptySet()
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    // 返回键优先级：多选 → 退出多选；内容页 → 回总览；总览不拦截（离开本页）
    BackHandler(enabled = selectionMode || destination !is FavoritesDestination.Overview) {
        if (selectionMode) exitSelection() else destination = FavoritesDestination.Overview
    }

    val visibleRecords = contentFilter?.let { state.filtered(it) } ?: emptyList()

    Scaffold(
        topBar = {
            // 内容页多选 → 多选操作栏；其余 → 当前位置的普通顶栏
            Crossfade(
                targetState = Triple(destination, selectionMode, selectedGroup),
                label = "favoritesTopBar"
            ) { (dest, selecting, groupFilter) ->
                if (selecting && dest is FavoritesDestination.Content) {
                    SelectionTopBar(
                        count = selectedIds.size,
                        allSelected = visibleRecords.isNotEmpty() &&
                            selectedIds.size == visibleRecords.size,
                        onClose = { exitSelection() },
                        onToggleAll = {
                            selectedIds = if (selectedIds.size == visibleRecords.size) {
                                emptySet()
                            } else {
                                visibleRecords.map { it.video.id }.toSet()
                            }
                        },
                        onDelete = {
                            val deleted = visibleRecords.filter { it.video.id in selectedIds }
                            viewModel.delete(selectedIds)
                            exitSelection()
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(
                                        R.string.favorites_deleted_multi, deleted.size
                                    ),
                                    actionLabel = context.getString(R.string.favorites_undo),
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restore(deleted)
                                }
                            }
                        },
                        onManageGroups = { showPicker = true }
                    )
                } else {
                    FavoritesTopBar(
                        destination = dest,
                        totalCount = state.records.size,
                        onBack = {
                            if (dest is FavoritesDestination.Overview) onBack()
                            else destination = FavoritesDestination.Overview
                        },
                        onNewGroup = {
                            groupEditor = GroupEditorState(renaming = null, initialName = "")
                        },
                        onClear = { showClearDialog = true },
                        onGroupMenu = { group ->
                            folderActionsFor = group
                        },
                        resolveGroup = { id -> state.groups.firstOrNull { it.id == id } }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                // 进入内容：横向滑入；返回总览：反向滑出（与路由横滑同一方向语言）
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it / 4 } + fadeOut())
            },
            label = "favoritesDestination",
            modifier = Modifier.padding(padding)
        ) { dest ->
            when (dest) {
                FavoritesDestination.Overview ->
                    FavoritesOverview(
                        state = state,
                        onOpenFilter = { filter ->
                            destination = FavoritesDestination.Content(filter)
                        },
                        onNewGroup = {
                            groupEditor = GroupEditorState(renaming = null, initialName = "")
                        },
                        onFolderActions = { group -> folderActionsFor = group }
                    )

                is FavoritesDestination.Content ->
                    FolderContent(
                        filter = dest.filter,
                        records = state.filtered(dest.filter),
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggleSelection = { id ->
                            selectedIds =
                                if (id in selectedIds) selectedIds - id else selectedIds + id
                        },
                        onEnterSelection = { id ->
                            selectionMode = true
                            selectedIds = setOf(id)
                        },
                        onVideoClick = onVideoClick,
                        onSwipeDelete = { record ->
                            viewModel.delete(setOf(record.video.id))
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(
                                        R.string.favorites_deleted_single
                                    ),
                                    actionLabel = context.getString(R.string.favorites_undo),
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restore(listOf(record))
                                }
                            }
                        }
                    )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.favorites_clear)) },
            text = { Text(stringResource(R.string.favorites_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    // 清空先快照全部记录：Snackbar 撤销可按原收藏时间完整恢复
                    val cleared = state.records
                    viewModel.clear()
                    showClearDialog = false
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(
                                R.string.favorites_deleted_multi, cleared.size
                            ),
                            actionLabel = context.getString(R.string.favorites_undo),
                            withDismissAction = true,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restore(cleared)
                        }
                    }
                }) { Text(stringResource(R.string.favorites_clear_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.favorites_clear_confirm_no))
                }
            }
        )
    }

    // 新建/重命名收藏夹命名弹窗
    groupEditor?.let { editor ->
        GroupNameDialog(
            title = if (editor.renaming != null) {
                stringResource(R.string.favorites_rename_group)
            } else {
                stringResource(R.string.favorites_new_group)
            },
            initialName = editor.initialName,
            onDismiss = { groupEditor = null },
            onSubmit = { name ->
                val renaming = editor.renaming
                if (renaming == null) {
                    viewModel.createGroupAndWait(name)
                } else {
                    viewModel.renameGroupAndWait(renaming.id, name)
                }.also { if (it is GroupEditResult.Success) groupEditor = null }
            }
        )
    }

    // 删除收藏夹确认
    pendingDeleteGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { pendingDeleteGroup = null },
            title = { Text(stringResource(R.string.favorites_delete_group)) },
            text = { Text(stringResource(R.string.favorites_delete_group_confirm, group.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(group.id)
                    pendingDeleteGroup = null
                }) { Text(stringResource(R.string.favorites_delete_group_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteGroup = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 多选：单选文件夹选择器
    if (showPicker) {
        FolderPickerSheet(
            groups = state.groups,
            memberships = state.memberships,
            selectedVideoIds = selectedIds,
            createGroup = { viewModel.createGroupAndWait(it) },
            onDismiss = { showPicker = false },
            onConfirm = { group, toAdd ->
                showPicker = false
                if (toAdd.isNotEmpty()) {
                    viewModel.addToGroup(group.id, toAdd)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.favorites_added_group, group.name),
                            actionLabel = context.getString(R.string.favorites_undo),
                            withDismissAction = true,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.removeFromGroup(group.id, toAdd)
                        }
                    }
                }
            }
        )
    }

    // 长按文件夹：操作表
    folderActionsFor?.let { group ->
        FolderActionsSheet(
            group = group,
            onDismiss = { folderActionsFor = null },
            onRename = {
                folderActionsFor = null
                groupEditor = GroupEditorState(renaming = group, initialName = group.name)
            },
            onDelete = {
                folderActionsFor = null
                pendingDeleteGroup = group
            }
        )
    }
}

/**
 * 收藏页普通顶栏：
 * - 总览：标题“我的收藏(N)” + 新建收藏夹（有收藏时显示清空）；
 * - 内容：返回总览 + 范围标题；指定收藏夹时可从溢出位置打开操作表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesTopBar(
    destination: FavoritesDestination,
    totalCount: Int,
    onBack: () -> Unit,
    onNewGroup: () -> Unit,
    onClear: () -> Unit,
    onGroupMenu: (FavoriteGroup) -> Unit,
    resolveGroup: (Long) -> FavoriteGroup?
) {
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
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.favorites_back)
                )
            }
        },
        actions = {
            val filter = (destination as? FavoritesDestination.Content)?.filter
            val groupFilter = filter as? FavoritesFilter.Group
            if (groupFilter != null) {
                IconButton(onClick = {
                    resolveGroup(groupFilter.groupId)?.let(onGroupMenu)
                }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.favorites_folder_actions)
                    )
                }
            } else {
                IconButton(onClick = onNewGroup) {
                    Icon(
                        Icons.Rounded.CreateNewFolder,
                        contentDescription = stringResource(R.string.favorites_new_group)
                    )
                }
                // “清空”只在全部视频范围提供，未分组/空列表不展示
                if (filter == FavoritesFilter.All && totalCount > 0) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
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
private fun FavoritesOverview(
    state: FavoritesUiState,
    onOpenFilter: (FavoritesFilter) -> Unit,
    onNewGroup: () -> Unit,
    onFolderActions: (FavoriteGroup) -> Unit
) {
    if (state.records.isEmpty() && state.groups.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.VideoLibrary,
            title = stringResource(R.string.favorites_empty_title),
            subtitle = stringResource(R.string.favorites_empty_subtitle),
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
                    icon = Icons.Rounded.VideoLibrary,
                    label = stringResource(R.string.favorites_all_videos),
                    count = state.records.size,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onOpenFilter(FavoritesFilter.All) },
                    modifier = Modifier.weight(1f)
                )
                ShortcutCard(
                    icon = Icons.Rounded.Inbox,
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
                Icon(Icons.Rounded.Add, contentDescription = null)
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
private fun ShortcutCard(
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
            .clip(RoundedCornerShape(20.dp))
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
private fun FolderRow(
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val coverUrl = previewRecords.firstOrNull()?.video?.coverUrl
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
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
                    Icons.Rounded.Folder,
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
            Icons.Rounded.ChevronRight,
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
private fun FolderContent(
    filter: FavoritesFilter,
    records: List<FavoriteRecord>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onSwipeDelete: (FavoriteRecord) -> Unit
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
            icon = Icons.Rounded.Folder,
            title = stringResource(titleRes),
            subtitle = stringResource(subtitleRes),
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                onDismiss = { onSwipeDelete(record) }
            )
        }
    }
}

/**
 * “加入收藏夹”单选选择器（主流做法）：
 * 文件夹逐行列出，点选高亮但不关闭；可就地新建收藏夹；底部“完成”统一确认。
 * 已在所选文件夹内的视频不会重复添加，撤销也只移除本次新增的关系。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerSheet(
    groups: List<FavoriteGroup>,
    memberships: Map<String, Set<Long>>,
    selectedVideoIds: Set<String>,
    createGroup: suspend (String) -> GroupEditResult,
    onDismiss: () -> Unit,
    onConfirm: (group: FavoriteGroup, toAdd: List<String>) -> Unit
) {
    // 预选：所有选中视频同属且唯一共有的收藏夹
    val preselected = remember(groups, selectedVideoIds, memberships) {
        commonSingleGroup(selectedVideoIds.map { memberships[it].orEmpty() })
    }
    var selectedGroupId by remember { mutableStateOf(preselected) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var duplicate by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.favorites_picker_title, selectedVideoIds.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            if (creating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it.take(20)
                            duplicate = false
                        },
                        singleLine = true,
                        isError = duplicate,
                        supportingText = if (duplicate) {
                            { Text(stringResource(R.string.favorites_group_duplicate)) }
                        } else {
                            null
                        },
                        label = { Text(stringResource(R.string.favorites_group_name_hint)) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        enabled = newName.isNotBlank() && !submitting,
                        onClick = {
                            submitting = true
                            scope.launch {
                                when (val result = createGroup(newName.trim())) {
                                    is GroupEditResult.Success -> {
                                        selectedGroupId = result.group.id
                                        creating = false
                                        newName = ""
                                    }
                                    GroupEditResult.DuplicateName -> duplicate = true
                                }
                                submitting = false
                            }
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.favorites_dialog_done),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        creating = false
                        newName = ""
                        duplicate = false
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { creating = true }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.favorites_new_group),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_no_groups_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            groups.forEach { group ->
                val selected = selectedGroupId == group.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedGroupId = group.id }
                        .padding(vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.background(MaterialTheme.colorScheme.primary)
                                } else {
                                    Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    )
                }
            }
            Button(
                onClick = {
                    val group = groups.firstOrNull { it.id == selectedGroupId } ?: return@Button
                    val toAdd = selectedVideoIds
                        .filterNot { memberships[it]?.contains(group.id) == true }
                    onConfirm(group, toAdd)
                },
                enabled = selectedGroupId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.favorites_picker_done))
            }
        }
    }
}

/** 文件夹操作表：重命名 / 删除收藏夹 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderActionsSheet(
    group: FavoriteGroup,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
            )
            SheetActionRow(
                icon = Icons.Rounded.DriveFileRenameOutline,
                label = stringResource(R.string.favorites_rename_group),
                onClick = onRename
            )
            SheetActionRow(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.favorites_delete_group),
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }
}

/** 操作表内的可点行 */
@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

/** 新建/重命名收藏夹命名弹窗；重名时就地提示且不关闭 */
@Composable
private fun GroupNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> GroupEditResult
) {
    var name by remember { mutableStateOf(initialName) }
    var duplicate by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(20)
                    duplicate = false
                },
                singleLine = true,
                isError = duplicate,
                supportingText = if (duplicate) {
                    { Text(stringResource(R.string.favorites_group_duplicate)) }
                } else {
                    null
                },
                label = { Text(stringResource(R.string.favorites_group_name_hint)) }
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !submitting,
                onClick = {
                    submitting = true
                    scope.launch {
                        val result = onSubmit(name.trim())
                        duplicate = result is GroupEditResult.DuplicateName
                        submitting = false
                    }
                }
            ) { Text(stringResource(R.string.favorites_dialog_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/** 多选模式下的顶部操作栏：退出 + 已选数量 + 加入收藏夹 + 全选 + 删除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    onManageGroups: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.favorites_exit_selection)
                )
            }
        },
        title = { Text(stringResource(R.string.favorites_selected_count, count)) },
        actions = {
            IconButton(onClick = onManageGroups, enabled = count > 0) {
                Icon(
                    Icons.AutoMirrored.Rounded.DriveFileMove,
                    contentDescription = stringResource(R.string.favorites_manage_groups)
                )
            }
            IconButton(onClick = onToggleAll) {
                Icon(
                    if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                    contentDescription = stringResource(
                        if (allSelected) R.string.favorites_deselect_all
                        else R.string.favorites_select_all
                    )
                )
            }
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.favorites_delete_selected)
                )
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

/**
 * 可左右滑动删除的收藏卡（与历史页同口径）：
 * 滑出阈值即触发 onDismiss，背景为 errorContainer + 删除图标；
 * 多选模式下禁用滑动手势，避免与批量管理冲突。
 */
@Composable
private fun SwipeableFavoriteCard(
    record: FavoriteRecord,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )
    val colorScheme = MaterialTheme.colorScheme
    val alignStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
    SwipeToDismissBox(
        state = dismissState,
        gesturesEnabled = !selectionMode,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = colorScheme.onErrorContainer
                )
            }
        }
    ) {
        SelectableFavoriteCard(
            record = record,
            selectionMode = selectionMode,
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}

/** 收藏网格卡片：复用 [VideoGridCard]，多选模式下叠加勾选标记与主题色描边 */
@Composable
private fun SelectableFavoriteCard(
    record: FavoriteRecord,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(16.dp)
    Box {
        VideoGridCard(
            video = record.video,
            onClick = onClick,
            onLongClick = onLongClick
        )
        if (selectionMode) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(cardShape)
                        .background(colorScheme.primary.copy(alpha = 0.12f))
                        .border(1.5.dp, colorScheme.primary.copy(alpha = 0.7f), cardShape)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(colorScheme.primary)
                        } else {
                            Modifier.background(Color.White.copy(alpha = 0.25f))
                                .border(
                                    1.5.dp,
                                    Color.White.copy(alpha = 0.9f),
                                    CircleShape
                                )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * 文件夹选择器预选：给定每个选中视频各自所属的收藏夹 id 集合，
 * 返回所有视频共有的收藏夹——仅当共有集合恰好一个时返回（多个/零个均不预选）。
 * 首项为空时以第一组为初始交集；纯函数便于单测。
 */
internal fun commonSingleGroup(memberGroups: List<Set<Long>>): Long? {
    if (memberGroups.isEmpty()) return null
    val common = memberGroups.fold<Set<Long>, Set<Long>?>(null) { acc, set ->
        if (acc == null) set else acc.intersect(set)
    }.orEmpty()
    return common.singleOrNull()
}