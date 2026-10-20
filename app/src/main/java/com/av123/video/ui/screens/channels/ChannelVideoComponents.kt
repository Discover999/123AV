package com.av123.video.ui.screens.channels

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.av123.video.LocalAppContainer
import com.av123.video.data.repository.FollowedActress
import com.av123.video.R
import com.av123.video.ui.theme.Motion
import com.av123.video.data.model.ActressProfile
import com.av123.video.data.model.FilterGroup
import com.av123.video.data.model.FilterOption
import com.av123.video.data.model.IndexEntry
import com.av123.video.data.model.Video
import com.av123.video.data.repository.CategoryPageState
import com.av123.video.data.repository.VideoRepository
import com.av123.video.ui.components.EmptyState
import com.av123.video.ui.components.ErrorState
import com.av123.video.ui.components.LoadingState
import com.av123.video.ui.components.appErrorText
import com.av123.video.ui.components.PaginationBar
import com.av123.video.ui.components.rememberTogglePopScale
import com.av123.video.ui.components.VideoGridCard
import com.av123.video.ui.components.VideoPreviewDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 女演员个人页头部（对应网页 .ahero）：
 * 圆形头像 + 姓名/属性行（年龄·身高·三围·罩杯），右侧关注数胶囊（对应 .ahero__follow），
 * 下方视频/观看/关注者/出道四项统计。
 *
 * 底部“关注/已关注”为纯本地关注（不依赖站点账号）：关注后可在“我的-我的关注”里
 * 聚合查看新片。[actressPath] 为演员个人页站点路径（关注身份主键），为空时不显示按钮。
 */
@Composable
internal fun ActressHero(
    profile: ActressProfile,
    actressPath: String?
) {
    val container = LocalAppContainer.current
    val followScope = rememberCoroutineScope()
    // 关注身份只认演员主页基础路径，不带筛选/排序查询串
    val followPath = actressPath?.substringBefore('?')
    val followedState = followPath?.let {
        container.followedActressRepository.isFollowed(it)
            .collectAsStateWithLifecycle(initialValue = false)
    }
    val isFollowed = followedState?.value == true
    val colorScheme = MaterialTheme.colorScheme

    // 按钮底色/内容色随关注态平滑过渡，而非瞬切
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isFollowed) {
            colorScheme.primary
        } else {
            colorScheme.secondaryContainer
        },
        animationSpec = Motion.MorphColorSpring,
        label = "followButtonContainer"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = if (isFollowed) {
            colorScheme.onPrimary
        } else {
            colorScheme.onSecondaryContainer
        },
        animationSpec = Motion.MorphColorSpring,
        label = "followButtonContent"
    )

    // 爱心“点赞弹出”缩放（关注瞬间收缩回弹；取关立即复位）
    val heartScale = rememberTogglePopScale(active = isFollowed)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                )
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile.meta.isNotBlank()) {
                        Text(
                            text = profile.meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                // 关注数胶囊：贴信息行右侧（对应网页 .ahero__follow 位于 .ahero__info 内）
                if (profile.followersLabel.isNotBlank()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(R.string.actress_follow),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = profile.followersLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            // 视频 / 观看 / 关注者 / 出道：等宽四列，某项网页缺失则只留出空位
            val stats = listOf(
                profile.videos to stringResource(R.string.actress_stat_videos),
                profile.views to stringResource(R.string.actress_stat_views),
                profile.followers to stringResource(R.string.actress_stat_followers),
                profile.debut to stringResource(R.string.actress_stat_debut)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stats.forEach { (value, label) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            // 本地关注/已关注：资料写入本机，个人中心“我的关注”聚合新片。
            // 底色/文字/爱心均随关注态做动画：颜色平滑过渡、爱心弹出回弹、文案淡入切换。
            if (!followPath.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = {
                        followScope.launch {
                            if (isFollowed) {
                                container.followedActressRepository.unfollow(followPath)
                            } else {
                                container.followedActressRepository.follow(
                                    FollowedActress(
                                        path = followPath,
                                        name = profile.name,
                                        avatarUrl = profile.avatarUrl,
                                        meta = profile.meta
                                    )
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = animatedContainerColor,
                        contentColor = animatedContentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isFollowed) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Outlined.FavoriteBorder
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                    )
                    Spacer(Modifier.width(8.dp))
                    AnimatedContent(
                        targetState = isFollowed,
                        transitionSpec = {
                            fadeIn(animationSpec = Motion.FadeThrough) togetherWith
                                fadeOut(animationSpec = Motion.Overlay)
                        },
                        label = "followLabel"
                    ) { followed ->
                        Text(
                            stringResource(
                                if (followed) R.string.actress_followed
                                else R.string.actress_follow
                            )
                        )
                    }
                }
            }
        }
    }
}

/** 视频流页：双列卡片网格 + 顶部女演员资料头（仅演员页）+ 分页进度条 + 页脚换页 */
@Composable
internal fun VideosGrid(
    state: CategoryPageState,
    onVideoClick: (String) -> Unit,
    onPreview: (Video) -> Unit,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    actressPath: String? = null
) {
    val gridState = rememberLazyGridState()
    // 翻页后回到列表顶部
    LaunchedEffect(state.page) { gridState.scrollToItem(0) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 女演员个人页头部资料（.ahero）：始终置顶，翻页加载时也不消失
        state.actressProfile?.let { profile ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                ActressHero(profile = profile, actressPath = actressPath)
            }
        }
        // 翻页加载中：顶部进度条（列表不消失，避免整屏闪烁）
        if (state.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
            }
        }
        items(state.videos, key = { it.id }) { video ->
            VideoGridCard(
                video = video,
                onClick = { onVideoClick(video.id) },
                onLongClick = {
                    if (video.previewUrl.isNotBlank()) onPreview(video)
                }
            )
        }
        // 仅当网页页脚解析到 .pager 结构时显示换页组件
        state.pagination?.let { pager ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                PaginationBar(
                    currentPage = state.page,
                    totalPages = pager.totalPages,
                    enabled = !state.isLoading,
                    onPageSelected = onPageSelected
                )
            }
        }
    }
}