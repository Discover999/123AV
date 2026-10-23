package com.av123.video.ui.screens.favorites

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.av123.video.R
import com.av123.video.data.model.FavoriteRecord
import com.av123.video.data.repository.FavoriteGroup
import com.av123.video.data.repository.MAX_FAVORITE_GROUPS
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.appCardContainerColor
import com.av123.video.ui.util.relativeTime
import com.av123.video.ui.util.rememberAppHaptics

/**
 * 收藏总览：收藏夹优先。
 * 顶部「全部收藏」大卡 + 未分组快捷行 + 两列收藏夹卡（2×2 封面拼贴）+ 虚线新建卡。
 */
@Composable
internal fun FavoritesOverview(
    state: FavoritesUiState,
    onOpenFilter: (FavoritesFilter) -> Unit,
    onNewGroup: () -> Unit,
    onFolderActions: (FavoriteGroup) -> Unit
) {
    if (state.records.isEmpty() && state.groups.isEmpty()) {
        EmptyState(
            icon = AppIcons.VideoLibrary,
            title = stringResource(R.string.favorites_empty_title),
            subtitle = stringResource(R.string.favorites_empty_subtitle),
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FavoritesHero(
                count = state.records.size,
                previews = state.records.take(4),
                lastFavoritedAt = state.totalLastFavoritedAt,
                onClick = { onOpenFilter(FavoritesFilter.All) }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            UngroupedRow(
                count = state.ungroupedCount,
                onClick = { onOpenFilter(FavoritesFilter.Ungrouped) }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = stringResource(R.string.favorites_section_groups),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.favorites_group_total, state.groups.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(state.groups, key = { it.id }) { group ->
            FolderCard(
                group = group,
                previews = state.groupPreview(group.id),
                count = state.groupCount(group.id),
                onClick = { onOpenFilter(FavoritesFilter.Group(group.id)) },
                onLongClick = { onFolderActions(group) }
            )
        }
        item {
            NewFolderCard(
                enabled = state.groups.size < MAX_FAVORITE_GROUPS,
                onClick = onNewGroup
            )
        }
    }
}

/** 顶部「全部收藏」大卡：左侧 2×2 拼贴 + 总数 + 最近收藏时间 */
@Composable
private fun FavoritesHero(
    count: Int,
    previews: List<FavoriteRecord>,
    lastFavoritedAt: Long?,
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Large)
            .background(appCardContainerColor())
            .clickable {
                haptics.tick()
                onClick()
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverCollage(previews, Modifier.size(88.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.favorites_all_videos),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.favorites_group_count, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (lastFavoritedAt != null) {
                Text(
                    text = stringResource(
                        R.string.favorites_total_meta,
                        relativeTime(lastFavoritedAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
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

/** 未分组：一条 48dp 快捷行，不再与「全部收藏」平分两列 */
@Composable
private fun UngroupedRow(count: Int, onClick: () -> Unit) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Medium)
            .background(appCardContainerColor())
            .clickable {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(AppShapes.Small)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = stringResource(R.string.favorites_ungrouped_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
        Text(
            text = stringResource(R.string.favorites_group_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 收藏夹卡：2×2 拼贴 + 名称 + 数量/最近添加 */
@Composable
private fun FolderCard(
    group: FavoriteGroup,
    previews: List<FavoriteRecord>,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = rememberAppHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Large)
            .background(appCardContainerColor())
            .combinedClickable(
                onClick = {
                    haptics.tick()
                    onClick()
                },
                onLongClick = {
                    haptics.confirm()
                    onLongClick()
                }
            )
    ) {
        CoverCollage(
            records = previews,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        )
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.favorites_group_item_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** 新建收藏夹：与文件夹同尺寸的虚线卡；到上限时变灰并显示「已达上限」 */
@Composable
private fun NewFolderCard(enabled: Boolean, onClick: () -> Unit) {
    val haptics = rememberAppHaptics()
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .clip(AppShapes.Large)
            .border(
                width = 1.5.dp,
                color = tint.copy(alpha = 0.35f),
                shape = AppShapes.Large
            )
            .then(
                if (enabled) {
                    Modifier.clickable {
                        haptics.tick()
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Add,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = if (enabled) {
                stringResource(R.string.favorites_new_group)
            } else {
                stringResource(R.string.favorites_group_limit_card, MAX_FAVORITE_GROUPS)
            },
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** 2×2 封面拼贴；不足四张的格子留主题底，不拉伸、不重复 */
@Composable
private fun CoverCollage(records: List<FavoriteRecord>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(AppShapes.Medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        repeat(2) { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(2) { col ->
                    val record = records.getOrNull(row * 2 + col)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (record != null && record.video.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = record.video.coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
