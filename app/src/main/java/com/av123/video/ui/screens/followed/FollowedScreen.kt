package com.av123.video.ui.screens.followed

import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.util.rememberAppHaptics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.repository.FollowedActress
import com.av123.video.ui.components.ActressAvatar
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.VideoCompactCard
import com.av123.video.ui.components.shimmerPlaceholder
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.appCardContainerColor

/**
 * “我的关注”页：本地关注女演员聚合。
 * 顶部为关注演员头像横条（点击进入其个人页），下方只展示有新片的演员分区；
 * 已加载但没有新片的演员不占位，只在顶部汇总里给出数量。
 * 全部数据来自本机关注表 + 实时抓取第一页，不依赖站点账号。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowedScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenChannel: (title: String, path: String) -> Unit,
    onOpenChannels: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: FollowedViewModel = viewModel {
        FollowedViewModel(container.followedActressRepository, container.dataSource)
    }
    val actresses by viewModel.actresses.collectAsStateWithLifecycle()
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.followed_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onBack()
                        }
                    ) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.followed_back)
                        )
                    }
                },
                actions = {
                    if (actresses.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                haptics.confirm()
                                viewModel.refresh()
                            }
                        ) {
                            Icon(
                                AppIcons.Refresh,
                                contentDescription = stringResource(R.string.followed_refresh)
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (actresses.isEmpty()) {
            EmptyState(
                icon = AppIcons.PersonAddAltOutline,
                title = stringResource(R.string.followed_empty_title),
                subtitle = stringResource(R.string.followed_empty_subtitle),
                actionText = stringResource(R.string.followed_empty_action),
                onAction = onOpenChannels,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
            return@Scaffold
        }

        // 已加载完成且没有新片的演员：不渲染分区，只在顶部汇总计数
        val emptyCount = actresses.count { feeds[it.path].isLoadedEmpty() }
        val sections = actresses.filter { actress ->
            val feed = feeds[actress.path] ?: return@filter true
            feed.loading || feed.error || feed.videos.isNotEmpty()
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 关注演员头像横条：无新片的头像做弱化，不额外加文字
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(actresses, key = { it.path }) { actress ->
                        FollowedActressChip(
                            actress = actress,
                            dimmed = feeds[actress.path].isLoadedEmpty(),
                            onClick = { onOpenChannel(actress.name, actress.path) }
                        )
                    }
                }
            }

            if (emptyCount > 0) {
                item {
                    Text(
                        text = stringResource(
                            R.string.followed_summary,
                            actresses.size,
                            emptyCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 有新片、加载中或加载失败的演员各占一个分区
            items(sections, key = { "section-${it.path}" }) { actress ->
                ActressFeedSection(
                    actress = actress,
                    feed = feeds[actress.path],
                    onOpenChannel = { onOpenChannel(actress.name, actress.path) },
                    onRetry = {
                        haptics.tick()
                        viewModel.retry(actress.path)
                    },
                    onVideoClick = onVideoClick
                )
            }
        }
    }
}

/** 已加载完成、没有报错且没有影片 */
private fun ActressFeedState?.isLoadedEmpty(): Boolean =
    this != null && !loading && !error && videos.isEmpty()

/** 顶部关注演员头像 + 姓名的圆形入口 */
@Composable
private fun FollowedActressChip(
    actress: FollowedActress,
    dimmed: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
    ) {
        ActressAvatar(
            url = actress.avatarUrl,
            modifier = Modifier
                .size(56.dp)
                .alpha(if (dimmed) 0.42f else 1f),
            iconSize = 24.dp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = actress.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 单个演员分区：标题行 + 横向最新影片（加载/失败/空态） */
@Composable
private fun ActressFeedSection(
    actress: FollowedActress,
    feed: ActressFeedState?,
    onOpenChannel: () -> Unit,
    onRetry: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    Column {
        // 分区标题：头像 + 姓名 + “查看全部”，整行进入她的个人页
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenChannel)
                .padding(horizontal = 16.dp)
        ) {
            ActressAvatar(
                url = actress.avatarUrl,
                modifier = Modifier.size(32.dp),
                iconSize = 16.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = actress.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.followed_view_all),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                AppIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            !feed?.videos.isNullOrEmpty() ->
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(feed.videos, key = { it.id }) { video ->
                        VideoCompactCard(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            width = 168.dp
                        )
                    }
                }

            feed?.error == true -> FeedErrorRow(onRetry = onRetry)

            else -> FeedSkeletonRow()
        }
    }
}

/** 分区内的加载骨架：与影片卡同宽同圆角，避免整页结构跳动 */
@Composable
private fun FeedSkeletonRow() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(3) { FeedCardSkeleton() }
    }
}

@Composable
private fun FeedCardSkeleton() {
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(AppShapes.Compact)
            .background(appCardContainerColor())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shimmerPlaceholder()
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .fillMaxWidth(0.72f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerPlaceholder()
        )
    }
}

/** 单个演员新片加载失败：行内提示 + 重试，不影响其他分区 */
@Composable
private fun FeedErrorRow(onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(AppShapes.Small)
            .background(appCardContainerColor())
            .padding(start = 12.dp, end = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.followed_section_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text(stringResource(R.string.common_retry))
        }
    }
}
