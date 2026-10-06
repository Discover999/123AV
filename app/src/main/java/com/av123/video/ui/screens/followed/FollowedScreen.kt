package com.av123.video.ui.screens.followed

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.av123.video.LocalAppContainer
import com.av123.video.R
import com.av123.video.data.repository.FollowedActress
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.VideoCompactCard

/**
 * “我的关注”页：本地关注女演员聚合。
 * 顶部为关注演员头像横条（点击进入其个人页），下方按演员分区展示各自最新影片，
 * 全部数据来自本机关注表 + 实时抓取第一页，不依赖站点账号。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowedScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenChannel: (title: String, path: String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: FollowedViewModel = viewModel {
        FollowedViewModel(container.followedActressRepository, container.dataSource)
    }
    val actresses by viewModel.actresses.collectAsStateWithLifecycle()
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.followed_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.followed_back)
                        )
                    }
                },
                actions = {
                    if (actresses.isNotEmpty()) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(
                                Icons.Rounded.Refresh,
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
                icon = Icons.Outlined.PersonAddAlt,
                title = stringResource(R.string.followed_empty_title),
                subtitle = stringResource(R.string.followed_empty_subtitle),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 关注演员头像横条
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(actresses, key = { it.path }) { actress ->
                        FollowedActressChip(
                            actress = actress,
                            onClick = { onOpenChannel(actress.name, actress.path) }
                        )
                    }
                }
            }

            // 每位演员一个“新片”分区
            items(actresses, key = { "section-${it.path}" }) { actress ->
                ActressFeedSection(
                    actress = actress,
                    feed = feeds[actress.path] ?: ActressFeedState(),
                    onOpenChannel = { onOpenChannel(actress.name, actress.path) },
                    onRetry = { viewModel.retry(actress.path) },
                    onVideoClick = onVideoClick
                )
            }
        }
    }
}

/** 顶部关注演员头像 + 姓名的圆形入口 */
@Composable
private fun FollowedActressChip(
    actress: FollowedActress,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = actress.avatarUrl,
            contentDescription = actress.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
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
    feed: ActressFeedState,
    onOpenChannel: () -> Unit,
    onRetry: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    Column {
        // 分区标题：头像 + 姓名，点击进入她的个人页
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenChannel)
                .padding(horizontal = 16.dp)
        ) {
            AsyncImage(
                model = actress.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = actress.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            feed.loading && feed.videos.isEmpty() ->
                Box(
                    modifier = Modifier
                        .height(140.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                }

            feed.error ->
                Box(
                    modifier = Modifier
                        .height(80.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.followed_retry))
                    }
                }

            feed.videos.isEmpty() ->
                Text(
                    text = stringResource(R.string.followed_no_videos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

            else ->
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
        }
    }
}
