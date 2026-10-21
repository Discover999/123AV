package com.av123.video.ui.screens.history

import com.av123.video.ui.icons.AppIcons

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.av123.video.LocalAppContainer
import com.av123.video.LocalBottomBarInset
import com.av123.video.R
import com.av123.video.data.model.WatchRecord
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.shimmerPlaceholder
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.Motion
import com.av123.video.ui.theme.appCardContainerColor
import com.av123.video.ui.util.formatDuration
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HistoryCardShape = AppShapes.Large
private val HistoryClockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val HistoryEarlierFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())

/**
 * 观看历史：日期分组 + 全部/未看完/已看完筛选 + 封面内进度与时长。
 * 不做继续观看大卡，最近一条直接排在「今天」第一条。
 * 长按多选、滑动删除 + 撤销、清空确认都沿用原交互。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onVideoClick: (String) -> Unit,
    onNavigateHome: () -> Unit,
    scrollToTopTick: Int = 0
) {
    val container = LocalAppContainer.current
    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(container.historyRepository) }
    val records by viewModel.records.collectAsStateWithLifecycle()
    val totalPlayMs by viewModel.totalPlayMs.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showClearDialog by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    val today = LocalDate.now()
    val filtered = remember(records, filter) { filterHistory(records, filter) }
    // 多选时展示全部记录，避免筛选后无法选到其他条目
    val visible = if (selectionMode) records else filtered
    val sections = remember(visible, today) { groupHistory(visible, today) }
    val allSelected = visible.isNotEmpty() && selectedIds.size == visible.size

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    // 重复点击底部 Tab：回到顶部（同时退出多选态）
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) {
            exitSelection()
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            Crossfade(
                targetState = selectionMode,
                animationSpec = Motion.FadeThrough,
                label = "historyTopBar"
            ) { selecting ->
                if (selecting) {
                    SelectionTopBar(
                        count = selectedIds.size,
                        allSelected = allSelected,
                        onClose = { exitSelection() },
                        onToggleAll = {
                            selectedIds = if (allSelected) {
                                emptySet()
                            } else {
                                visible.map { it.video.id }.toSet()
                            }
                        },
                        onDelete = {
                            val deleted = visible.filter { it.video.id in selectedIds }
                            viewModel.delete(selectedIds)
                            exitSelection()
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = resources.getString(
                                        R.string.history_deleted_multi, deleted.size
                                    ),
                                    actionLabel = resources.getString(R.string.history_undo),
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
                                text = stringResource(R.string.history_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        actions = {
                            if (records.isNotEmpty()) {
                                IconButton(onClick = { showClearDialog = true }) {
                                    Icon(
                                        AppIcons.DeleteSweep,
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
                icon = AppIcons.HistoryOutline,
                title = stringResource(R.string.history_empty_title),
                subtitle = stringResource(R.string.history_empty_subtitle),
                actionText = stringResource(R.string.action_go_home),
                onAction = onNavigateHome,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 16.dp + LocalBottomBarInset.current
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!selectionMode) {
                    item(key = "summary") {
                        HistorySummary(count = records.size, totalPlayMs = totalPlayMs)
                    }
                    item(key = "filters") {
                        HistoryFilterRow(
                            current = filter,
                            onSelect = { filter = it }
                        )
                    }
                }

                if (visible.isEmpty()) {
                    item(key = "filter-empty") {
                        HistoryFilterEmpty(onShowAll = { filter = HistoryFilter.ALL })
                    }
                } else {
                    sections.forEach { section ->
                        stickyHeader(key = "header-${section.group.name}") {
                            HistoryGroupHeader(
                                group = section.group,
                                count = section.records.size
                            )
                        }
                        items(section.records, key = { it.video.id }) { record ->
                            SwipeableHistoryItem(
                                record = record,
                                today = today,
                                selectionMode = selectionMode,
                                selected = record.video.id in selectedIds,
                                swipeEnabled = !selectionMode,
                                onClick = {
                                    if (selectionMode) {
                                        val id = record.video.id
                                        selectedIds = if (id in selectedIds) {
                                            selectedIds - id
                                        } else {
                                            selectedIds + id
                                        }
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
                                            message = resources.getString(R.string.history_deleted_single),
                                            actionLabel = resources.getString(R.string.history_undo),
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
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear)) },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clear()
                        showClearDialog = false
                        exitSelection()
                        filter = HistoryFilter.ALL
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.history_clear_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.history_clear_confirm_no))
                }
            }
        )
    }
}

@Composable
private fun HistorySummary(count: Int, totalPlayMs: Long) {
    Text(
        text = historySummaryText(count = count, totalPlayMs = totalPlayMs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/** 「共 N 部 · 观看 X」：X 取全部日期累计，不按周截断 */
@Composable
private fun historySummaryText(count: Int, totalPlayMs: Long): String {
    val totalMinutes = (totalPlayMs / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val duration = when {
        hours > 0 -> stringResource(R.string.history_total_hours_minutes, hours, minutes)
        totalMinutes > 0 -> stringResource(R.string.history_total_minutes, totalMinutes)
        totalPlayMs > 0L -> stringResource(R.string.history_total_less_than_minute)
        else -> null
    }
    return if (duration == null) {
        stringResource(R.string.history_summary_short, count)
    } else {
        stringResource(R.string.history_summary, count, duration)
    }
}

@Composable
private fun HistoryFilterRow(current: HistoryFilter, onSelect: (HistoryFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryFilter.entries.forEach { item ->
            val selected = item == current
            val background by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                animationSpec = tween(180),
                label = "historyFilterBg"
            )
            val foreground by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(180),
                label = "historyFilterFg"
            )
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.label(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = foreground
                )
            }
        }
    }
}

@Composable
private fun HistoryGroupHeader(group: HistoryGroup, count: Int) {
    Text(
        text = stringResource(R.string.history_group_count, group.label(), count),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun HistoryFilterEmpty(onShowAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.history_filter_empty_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onShowAll) {
            Text(stringResource(R.string.history_filter_empty_action))
        }
    }
}

@Composable
private fun HistoryFilter.label(): String = when (this) {
    HistoryFilter.ALL -> stringResource(R.string.history_filter_all)
    HistoryFilter.UNFINISHED -> stringResource(R.string.history_filter_unfinished)
    HistoryFilter.FINISHED -> stringResource(R.string.history_filter_finished)
}

@Composable
private fun HistoryGroup.label(): String = when (this) {
    HistoryGroup.TODAY -> stringResource(R.string.history_group_today)
    HistoryGroup.YESTERDAY -> stringResource(R.string.history_group_yesterday)
    HistoryGroup.EARLIER -> stringResource(R.string.history_group_earlier)
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
                    AppIcons.Close,
                    contentDescription = stringResource(R.string.history_exit_selection)
                )
            }
        },
        title = { Text(stringResource(R.string.history_selected_count, count)) },
        actions = {
            IconButton(onClick = onToggleAll) {
                Icon(
                    if (allSelected) AppIcons.Deselect else AppIcons.SelectAll,
                    contentDescription = stringResource(
                        if (allSelected) R.string.history_deselect_all
                        else R.string.history_select_all
                    )
                )
            }
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(
                    AppIcons.Delete,
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
    today: LocalDate,
    selectionMode: Boolean,
    selected: Boolean,
    swipeEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        // 滑动只作为删除手势，不真正停在滑出位置；删除与撤销交给 ViewModel + Snackbar
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
            }
            false
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
                    .clip(HistoryCardShape)
                    .background(colorScheme.errorContainer)
                    .padding(horizontal = 28.dp),
                contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = null,
                    tint = colorScheme.onErrorContainer
                )
            }
        }
    ) {
        HistoryItem(
            record = record,
            today = today,
            selectionMode = selectionMode,
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(
    record: WatchRecord,
    today: LocalDate,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val finished = record.isFinished()
    val durationText = when {
        record.durationMs > 0L -> formatDuration(record.durationMs)
        record.video.durationText.isNotBlank() -> record.video.durationText
        else -> ""
    }
    val episodeText = if (record.video.episodeCount > 1) {
        stringResource(R.string.history_episode_badge, record.episodeIndex + 1)
    } else {
        ""
    }
    val instant = Instant.ofEpochMilli(record.watchedAt).atZone(ZoneId.systemDefault())
    val date = instant.toLocalDate()
    val clock = instant.format(HistoryClockFormat)
    val timeText = when {
        date >= today -> stringResource(R.string.history_time_today, clock)
        date == today.minusDays(1) -> stringResource(R.string.history_time_yesterday, clock)
        else -> instant.format(HistoryEarlierFormat)
    }
    val metaText = if (record.video.category.isBlank()) {
        timeText
    } else {
        "${record.video.category} · $timeText"
    }
    val watchedText = when {
        finished -> stringResource(R.string.history_finished)
        record.positionMs > 0L -> stringResource(
            R.string.history_watched_at,
            formatDuration(record.positionMs)
        )
        else -> stringResource(R.string.history_not_started)
    }
    val showProgress = record.positionMs > 0L && record.durationMs > 0L && !finished

    ElevatedCard(
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, colorScheme.primary.copy(alpha = 0.7f), HistoryCardShape)
                } else {
                    Modifier
                }
            )
            .clip(HistoryCardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = HistoryCardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) {
                colorScheme.primaryContainer
            } else {
                appCardContainerColor()
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(124.dp)
                    .height(70.dp)
                    .clip(AppShapes.Compact)
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
                if (episodeText.isNotEmpty()) {
                    Text(
                        text = episodeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(AppShapes.Small)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (durationText.isNotEmpty()) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .clip(AppShapes.Small)
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (showProgress) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(record.progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(colorScheme.primary)
                        )
                    }
                }
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) colorScheme.primary
                                else Color.Black.copy(alpha = 0.22f)
                            )
                            .border(
                                1.5.dp,
                                if (selected) colorScheme.primary else Color.White.copy(alpha = 0.9f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                AppIcons.Check,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = watchedText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
