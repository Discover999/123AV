package com.av123.video.ui.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.av123.video.R
import com.av123.video.data.model.FavoriteRecord
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.util.relativeTime
import com.av123.video.ui.util.rememberAppHaptics

/**
 * 某个范围的收藏内容：范围 meta 行 + 排序胶囊 + 可选搜索 + 网格/单列。
 * 空态按范围区分：全部收藏空 → 回首页；某个收藏夹空 → 去全部收藏。
 */
@Composable
internal fun FavoritesContent(
    filter: FavoritesFilter,
    records: List<FavoriteRecord>,
    totalCount: Int,
    lastAddedAt: Long?,
    sort: FavoritesSort,
    onSortChange: (FavoritesSort) -> Unit,
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onSwipeDelete: (FavoriteRecord) -> Unit,
    listLayout: Boolean
) {
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) runCatching { searchFocus.requestFocus() }
    }

    if (records.isEmpty() && query.isBlank()) {
        val isAll = filter == FavoritesFilter.All
        // 空收藏夹只留一句标题；全部收藏为空才给副标题与「去首页」
        EmptyState(
            icon = if (isAll) AppIcons.VideoLibrary else AppIcons.Folder,
            title = stringResource(
                if (isAll) R.string.favorites_empty_title
                else R.string.favorites_empty_group_title
            ),
            subtitle = if (isAll) {
                stringResource(R.string.favorites_empty_subtitle)
            } else {
                null
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (listLayout) 1 else 2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (listLayout) 8.dp else 12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                if (searchActive) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocus),
                        placeholder = { Text(stringResource(R.string.favorites_search_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = AppIcons.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = stringResource(R.string.favorites_search_clear),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = AppShapes.Pill
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.favorites_group_count, totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lastAddedAt != null) {
                        Text(
                            text = " · " + stringResource(
                                if (filter == FavoritesFilter.All) {
                                    R.string.favorites_recently_favorited
                                } else {
                                    R.string.favorites_last_added
                                },
                                relativeTime(lastAddedAt)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortChip(
                        label = stringResource(R.string.favorites_sort_recent),
                        selected = sort == FavoritesSort.RECENT,
                        onClick = { onSortChange(FavoritesSort.RECENT) }
                    )
                    SortChip(
                        label = stringResource(R.string.favorites_sort_title),
                        selected = sort == FavoritesSort.TITLE,
                        onClick = { onSortChange(FavoritesSort.TITLE) }
                    )
                }
            }
        }

        if (records.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = stringResource(R.string.favorites_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
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
                    listLayout = listLayout,
                    // 卡片网格不做滑动删除：只在单列行形态启用
                    swipeEnabled = listLayout
                )
            }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberAppHaptics()
    FilterChip(
        selected = selected,
        onClick = {
            haptics.tick()
            onClick()
        },
        label = { Text(label) },
        shape = AppShapes.Pill
    )
}
