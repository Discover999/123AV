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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.av123.video.LocalAppContainer
import com.av123.video.LocalBottomBarInset
import com.av123.video.R
import com.av123.video.ui.theme.Motion
import com.av123.video.data.model.FavoriteRecord
import com.av123.video.data.prefs.VideoLayout
import com.av123.video.data.repository.FavoriteGroup
import com.av123.video.data.repository.GroupEditResult
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.VideoGridCard
import com.av123.video.ui.util.rememberAppHaptics
import kotlinx.coroutines.launch

/** 收藏夹新建/重命名编辑弹窗状态 */
internal data class GroupEditorState(
    val renaming: FavoriteGroup?,
    val initialName: String
)

/** 收藏页内部导航位置：文件夹总览 / 某个范围（全部·未分组·指定收藏夹）的内容 */
internal sealed interface FavoritesDestination {
    data object Overview : FavoritesDestination
    data class Content(val filter: FavoritesFilter) : FavoritesDestination
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: FavoritesViewModel = viewModel { FavoritesViewModel(container.favoriteRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appSettings by container.appSettingsState.collectAsStateWithLifecycle()
    val listLayout = appSettings.settings.videoLayout == VideoLayout.LIST
    // 本页有自己的 SnackbarHost：悬浮底栏占位为 0，需要手动补胶囊与手势条高度
    val floatingNavBar = appSettings.settings.floatingNavBar
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val haptics = rememberAppHaptics()
    val resources = LocalResources.current
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
                animationSpec = Motion.FadeThrough,
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
                                    message = resources.getString(
                                        R.string.favorites_deleted_multi, deleted.size
                                    ),
                                    actionLabel = resources.getString(R.string.favorites_undo),
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
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                // 悬浮底栏时让出胶囊与手势条，撤销提示才点得到
                modifier = Modifier.padding(
                    bottom = LocalBottomBarInset.current + if (floatingNavBar) navBarBottom else 0.dp
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                // 进入内容：横向滑入；返回总览：反向滑出（与路由横滑同一方向语言）
                (slideInHorizontally(Motion.pageEnter<IntOffset>()) { it / 4 } +
                    fadeIn(Motion.PageEnter)) togetherWith
                    (slideOutHorizontally(Motion.pageExit<IntOffset>()) { it / 4 } +
                        fadeOut(Motion.PageExit))
            },
            label = "favoritesDestination",
            modifier = Modifier.padding(padding)
        ) { dest ->
            when (dest) {
                FavoritesDestination.Overview ->
                    FavoritesOverview(
                        state = state,
                        onNavigateHome = onNavigateHome,
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
                        listLayout = listLayout,
                        onSwipeDelete = { record ->
                            viewModel.delete(setOf(record.video.id))
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = resources.getString(
                                        R.string.favorites_deleted_single
                                    ),
                                    actionLabel = resources.getString(R.string.favorites_undo),
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
                    haptics.confirm()
                    // 清空先快照全部记录：Snackbar 撤销可按原收藏时间完整恢复
                    val cleared = state.records
                    viewModel.clear()
                    showClearDialog = false
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(
                                R.string.favorites_deleted_multi, cleared.size
                            ),
                            actionLabel = resources.getString(R.string.favorites_undo),
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
                            message = resources.getString(R.string.favorites_added_group, group.name),
                            actionLabel = resources.getString(R.string.favorites_undo),
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
