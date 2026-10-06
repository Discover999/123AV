package com.av123.video.ui.screens.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.av123.video.LocalAppContainer
import com.av123.video.R
import coil.compose.AsyncImage
import com.av123.video.data.model.WatchRecord
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.shimmerPlaceholder
import com.av123.video.ui.util.formatDuration
import com.av123.video.ui.util.relativeTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onVideoClick: (String) -> Unit,
    scrollToTopTick: Int = 0
) {
    val container = LocalAppContainer.current
    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(container.historyRepository) }
    val records by viewModel.records.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showClearDialog by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    // 多选模式下返回键先退出多选，而不是离开页面
    BackHandler(enabled = selectionMode) { exitSelection() }

    Scaffold(
        topBar = {
            Crossfade(targetState = selectionMode, label = "historyTopBar") { selecting ->
                if (selecting) {
                    SelectionTopBar(
                        count = selectedIds.size,
                        allSelected = records.isNotEmpty() && selectedIds.size == records.size,
                        onClose = { exitSelection() },
                        onToggleAll = {
                            selectedIds = if (selectedIds.size == records.size) {
                                emptySet()
                            } else {
                                records.map { it.video.id }.toSet()
                            }
                        },
                        onDelete = {
                            val deleted = records.filter { it.video.id in selectedIds }
                            viewModel.delete(selectedIds)
                            exitSelection()
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(
                                        R.string.history_deleted_multi, deleted.size
                                    ),
                                    actionLabel = context.getString(R.string.history_undo),
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restore(deleted)
                                }
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (records.isNotEmpty()) {
                                    stringResource(R.string.history_title_count, records.size)
                                } else {
                                    stringResource(R.string.history_title)
                                },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        actions = {
                            if (records.isNotEmpty()) {
                                IconButton(onClick = { showClearDialog = true }) {
                                    Icon(
                                        Icons.Rounded.DeleteSweep,
                                        contentDescription = stringResource(R.string.history_clear)
                                    )
                                }
                            }
                        },
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (records.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.history_empty_title),
                subtitle = stringResource(R.string.history_empty_subtitle),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            val listState = rememberLazyListState()
            // 重复点击底部 Tab：回到顶部（同时退出多选态）
            LaunchedEffect(scrollToTopTick) {
                if (scrollToTopTick > 0) {
                    exitSelection()
                    listState.scrollToItem(0)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records, key = { it.video.id }) { record ->
                    SwipeableHistoryItem(
                        record = record,
                        selectionMode = selectionMode,
                        selected = record.video.id in selectedIds,
                        swipeEnabled = !selectionMode,
                        onClick = {
                            if (selectionMode) {
                                val id = record.video.id
                                selectedIds =
                                    if (id in selectedIds) selectedIds - id else selectedIds + id
                            } else {
                                onVideoClick(record.video.id)
                            }
                        },
                        onLongClick = {
                            selectionMode = true
                            selectedIds = setOf(record.video.id)
                        },
                        onDismiss = {
                            viewModel.delete(record.video.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.history_deleted_single),
                                    actionLabel = context.getString(R.string.history_undo),
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
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear)) },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    showClearDialog = false
                }) { Text(stringResource(R.string.history_clear_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.history_clear_confirm_no))
                }
            }
        )
    }
}

/** 多选模式下的顶部操作栏：退出 + 已选数量 + 全选 + 删除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.history_exit_selection)
                )
            }
        },
        title = { Text(stringResource(R.string.history_selected_count, count)) },
        actions = {
            IconButton(onClick = onToggleAll) {
                Icon(
                    if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                    contentDescription = stringResource(
                        if (allSelected) R.string.history_deselect_all
                        else R.string.history_select_all
                    )
                )
            }
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.history_delete_selected)
                )
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

/** 包裹滑动删除手势的历史条目 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHistoryItem(
    record: WatchRecord,
    selectionMode: Boolean,
    selected: Boolean,
    swipeEnabled: Boolean,
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
        gesturesEnabled = swipeEnabled,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.errorContainer)
                    .padding(horizontal = 28.dp),
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
        HistoryItem(
            record = record,
            selectionMode = selectionMode,
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(
    record: WatchRecord,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(12.dp)
    ElevatedCard(
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, colorScheme.primary.copy(alpha = 0.7f), cardShape)
                } else {
                    Modifier
                }
            )
            .clip(cardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = cardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) {
                colorScheme.primaryContainer
            } else {
                colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // 迷你封面：有封面地址加载网络图，加载中/无封面时显示波纹空白占位
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .shimmerPlaceholder()
            ) {
                if (record.video.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = record.video.coverUrl,
                        contentDescription = record.video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // 多选模式下的选中标记（类似相册的右上角勾选圆钮）
                if (selectionMode) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${record.video.category} · ${relativeTime(record.watchedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // 仅在有真实播放进度时展示进度条（刚进入详情未播放/已看完复位均不显示）
                if (record.positionMs > 0L && record.durationMs > 0L) {
                    LinearProgressIndicator(
                        progress = { record.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.history_watched_at,
                            formatDuration(record.positionMs)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
