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

/** 多选模式下的顶部操作栏：退出 + 已选数量 + 加入收藏夹 + 全选 + 删除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
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
internal fun SwipeableFavoriteCard(
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
internal fun SelectableFavoriteCard(
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