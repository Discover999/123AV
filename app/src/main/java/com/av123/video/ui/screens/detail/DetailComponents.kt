package com.av123.video.ui.screens.detail

import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.AppTypeScale

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.av123.video.LocalAppContainer
import com.av123.video.ui.components.player.VideoPlayer
import com.av123.video.R
import com.av123.video.ui.theme.Motion
import com.av123.video.data.model.Episode
import com.av123.video.data.model.InfoChip
import com.av123.video.data.model.Video
import com.av123.video.data.model.VideoInfoRow
import com.av123.video.data.prefs.ResizeModeOption
import com.av123.video.ui.components.ErrorState
import com.av123.video.ui.components.LoadingState
import com.av123.video.ui.components.VideoGridCard
import com.av123.video.ui.components.appErrorText
import com.av123.video.ui.components.rememberTogglePopScale
import com.av123.video.ui.components.shimmerPlaceholder
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 详情页底部“相关推荐”：titleMedium 标题 + 2 列卡片网格。
 * 用 Column + chunked(2) 的普通 Row 实现，行高 10dp、列距 8dp；
 * 加载中给 2×2 封面+文本骨架；数据到达后骨架消失。
 * 完整池超过一页时，网格下方显示“加载更多”，点击就地追加一页（纯本地展开）。
 */
@Composable
internal fun RelatedSection(
    videos: List<Video>,
    loading: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.detail_related_title),
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(12.dp))

    if (videos.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            videos.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { item ->
                        VideoGridCard(
                            video = item,
                            onClick = { onVideoClick(item.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 奇数个时第二格留空占位，保持两列宽度一致
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (canLoadMore) {
            Spacer(Modifier.height(12.dp))
            // 整行描边按钮：向下箭头 + 文案，展开后剩余条数不足一页时隐藏
            TextButton(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text(text = stringResource(R.string.related_load_more))
            }
        }
    } else if (loading) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) {
                        RelatedCardSkeleton(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 相关推荐卡片骨架：封面比例块 + 两行文字 */
@Composable
internal fun RelatedCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .shimmerPlaceholder()
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .clip(AppShapes.Small)
                .shimmerPlaceholder()
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(11.dp)
                .clip(AppShapes.Small)
                .shimmerPlaceholder()
        )
    }
}

/**
 * 选集按钮组：“选集”标题下方一条横向滑动的圆角按钮带。
 * - 选中集主题色实心填充 + onPrimary 文字 SemiBold，未选集 surfaceVariant 底；
 *   颜色用 [animateColorAsState] 平滑过渡
 * - 切集（含进入详情自动恢复上次选集）时自动横向滚动到选中项，避免其停在视口外
 * - 集名为空时兜底“第N集”；LazyRow 嵌在竖屏 LazyColumn 单 item 内（仅横向滚动，
 *   与外层方向正交，无同方向嵌套冲突）
 */
@Composable
internal fun EpisodeSelector(
    episodes: List<Episode>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    // 选中项变化（手动切集/历史续播恢复）时滚动到可见位置
    LaunchedEffect(selectedIndex, episodes.size) {
        if (selectedIndex in episodes.indices) listState.animateScrollToItem(selectedIndex)
    }
    Column {
        Text(
            text = stringResource(R.string.detail_episodes),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(episodes) { index, episode ->
                val selected = index == selectedIndex
                val accent = MaterialTheme.colorScheme.primary
                val container by animateColorAsState(
                    targetValue = if (selected) accent
                    else MaterialTheme.colorScheme.surfaceVariant,
                    animationSpec = Motion.MorphColorSpring,
                    label = "episodeContainer"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = Motion.MorphColorSpring,
                    label = "episodeContent"
                )
                Box(
                    modifier = Modifier
                        .height(42.dp)
                        .widthIn(min = 54.dp)
                        .clip(AppShapes.Medium)
                        .background(container)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = episode.name.ifBlank {
                            stringResource(R.string.detail_episode_n, index + 1)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold
                        else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 详情标签按钮，对标网页 .chip：全圆角胶囊、表面色底、灰字、13px 中号字；
 * 按下时平滑切换为主题 primary 半透明底 + primary 文字
 *（动态取色跟随壁纸，关闭时为品牌粉）。
 */
@Composable
internal fun MetaChip(
    text: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = Motion.PressSpring,
        label = "chipScale"
    )
    val accent = MaterialTheme.colorScheme.primary
    val container by animateColorAsState(
        targetValue = if (pressed) accent.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        animationSpec = Motion.MorphColorSpring,
        label = "chipContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (pressed) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.MorphColorSpring,
        label = "chipContent"
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 13.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = AppTypeScale.Body,
                fontWeight = FontWeight.Medium
            ),
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * 详情信息行：左侧固定宽度字段名，右侧为纯文本值或可点击胶囊按钮组。
 * chips 为空（代码/发布日期等纯文本行）显示文本；
 * 有 chips（类型/演员/制作商/系列/类别/标签）显示胶囊，点击携带 href 路径跳转对应视频流。
 * 字段名与右侧内容整体垂直居中对齐，胶囊换行时标签在区块上下居中。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailInfoRow(
    row: VideoInfoRow,
    onChipClick: (InfoChip) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(72.dp)
                .align(Alignment.CenterVertically)
        )
        if (row.chips.isEmpty()) {
            Text(
                text = row.value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                row.chips.forEach { chip ->
                    MetaChip(text = chip.text, onClick = { onChipClick(chip) })
                }
            }
        }
    }
}

/**
 * 影片标题，交互对齐主流视频客户端（YouTube/NewPipe/B站）的通用做法：
 * - 折叠态最多两行并自然省略；确认溢出后第二行末尾内联主题色“…更多”，
 *   **整个标题区域可点**（带水波纹 + Button 语义），不再是只能点小箭头；
 * - 展开态显示全文，文末内联主题色“收起”，再次点整段折回；
 * - 短标题（两行内放得下）不渲染提示、整段不可点；
 * - 高度变化由 [animateContentSize] 平滑过渡。
 *
 * 截断点不做逐字符二分：先让 Text 用自然省略号渲染全文，onTextLayout 拿到
 * 第二行右端，再按“…更多”的[TextMeasurer]实测像素宽度用 getOffsetForHorizontal
 * 反查切点——对中英日文/全角半角混排都准确，且只测量一次。
 */
@Composable
internal fun ExpandableTitle(text: String) {
    // 详情从缓存条目补全为完整标题时全部重置，避免沿用上一个标题的展开/截断状态
    var expanded by remember(text) { mutableStateOf(false) }
    var overflowing by remember(text) { mutableStateOf(false) }
    // 折叠态保留的正文字符数；-1 = 尚未测量，首帧先渲染全文参与溢出检测
    var cutIndex by remember(text) { mutableIntStateOf(-1) }

    val titleStyle = MaterialTheme.typography.headlineSmall
    val primaryColor = MaterialTheme.colorScheme.primary
    val expandLabel = stringResource(R.string.title_expand)
    val collapseLabel = stringResource(R.string.detail_collapse)
    val toggleStyle = SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)
    val textMeasurer = rememberTextMeasurer()

    // “…更多”实测宽度，用于从第二行右端反推切点
    val suffix = "…$expandLabel"
    val suffixWidthPx = remember(textMeasurer, titleStyle, suffix) {
        textMeasurer.measure(text = AnnotatedString(suffix), style = titleStyle).size.width
    }

    val displayText = buildAnnotatedString {
        when {
            expanded -> {
                append(text)
                append(' ')
                withStyle(toggleStyle) { append(collapseLabel) }
            }
            cutIndex >= 0 -> {
                append(text, 0, cutIndex.coerceIn(0, text.length))
                withStyle(toggleStyle) { append(suffix) }
            }
            else -> append(text)
        }
    }

    Text(
        text = displayText,
        style = titleStyle,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (expanded || overflowing) return@Text
            if (!result.hasVisualOverflow) return@Text
            overflowing = true
            // 从第二行右边界减去“…更多”宽度反查字符偏移
            val targetX = (result.size.width - suffixWidthPx).toFloat()
            val centerY = (result.getLineTop(1) + result.getLineBottom(1)) / 2f
            var cut = result.getOffsetForPosition(Offset(targetX, centerY))
            // getOffsetForPosition 对 CJK 可能向上取整到下一字符，逐字符回退到
            // “切点实际水平位置 + 提示宽度 <= 行宽”为止，既贴满行尾又不溢到第三行
            while (cut > 0 &&
                result.getHorizontalPosition(cut, true) + suffixWidthPx > result.size.width
            ) {
                cut--
            }
            cutIndex = cut.coerceIn(0, text.length)
        },
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(
                enabled = overflowing,
                role = Role.Button,
                onClickLabel = if (expanded) collapseLabel else expandLabel
            ) { expanded = !expanded }
    )
}

/**
 * 影片简介：默认折叠为 3 行，内容超出时显示“查看更多”，展开后可“收起”。
 * 是否溢出由文本布局结果判定（与网页 watch__more 的 overflowing 逻辑一致）。
 */
@Composable
internal fun ExpandableSynopsis(text: String) {
    var expanded by remember { mutableStateOf(false) }
    var overflowing by remember { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (!expanded && result.hasVisualOverflow) overflowing = true
        }
    )
    if (overflowing) {
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.detail_collapse else R.string.detail_expand
                ),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.size(2.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }
    }
}

/**
 * 播放区（item 内容）：
 * - 已开始播放且存在播放地址时，用自定义 Media3/ExoPlayer 播放器接管渲染与控制
 * - 未开始播放时显示占位封面，中央圆形按钮随解析状态切换：
 *   解析中=加载转圈、解析失败=重试图标+重试入口、已就绪=播放三角
 */
@Composable
internal fun PlayerArea(
    video: Video,
    playbackUrl: String,
    playbackHeaders: Map<String, String>,
    playbackStarted: Boolean,
    resolveFailed: Boolean,
    isFullscreen: Boolean,
    selectedEpisodeIndex: Int,
    onFullscreenChange: (Boolean) -> Unit,
    onPlayClick: () -> Unit,
    onRetry: () -> Unit,
    onPlaybackReady: () -> Unit,
    onPlaybackError: () -> Unit,
    startPositionMs: Long,
    onPlaybackProgress: (Int, Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    onPlayingElapsed: (Long) -> Unit = {},
    pipEnabled: Boolean = true,
    autoPlayNext: Boolean = true,
    onAutoPlayNextChange: (Boolean) -> Unit = {},
    flipToExitEnabled: Boolean = false,
    onFlipToExitChange: (Boolean) -> Unit = {},
    resizeMode: ResizeModeOption = ResizeModeOption.FIT,
    onResizeModeChange: (ResizeModeOption) -> Unit = {},
    onEpisodeEnded: () -> Unit = {}
) {
    val detailCode = video.detailCodeFromInfoRows()

    if (playbackStarted && playbackUrl.isNotBlank()) {
        // 尺寸（竖屏 16:9 / 全屏 fillMaxSize）由调用方通过 modifier 显式给定，
        // 内部不再叠加，保证全屏切换时 PlayerView 只做同一节点的尺寸变化
        VideoPlayer(
            url = playbackUrl,
            headers = playbackHeaders,
            title = detailCode,
            isFullscreen = isFullscreen,
            onFullscreenChange = onFullscreenChange,
            onPlaybackReady = onPlaybackReady,
            onPlaybackError = onPlaybackError,
            startPositionMs = startPositionMs,
            episodeIndex = selectedEpisodeIndex,
            onPlaybackProgress = onPlaybackProgress,
            onPlayingElapsed = onPlayingElapsed,
            pipEnabled = pipEnabled,
            autoPlayNext = autoPlayNext,
            onAutoPlayNextChange = onAutoPlayNextChange,
            flipToExitEnabled = flipToExitEnabled,
            onFlipToExitChange = onFlipToExitChange,
            resizeMode = resizeMode,
            onResizeModeChange = onResizeModeChange,
            onEpisodeEnded = onEpisodeEnded,
            modifier = modifier.background(Color.Black)
        )
    } else {
        // 解析三态：解析中（转圈）/ 解析失败（重试）/ 已就绪（播放三角）
        val isResolving = playbackUrl.isBlank() && !resolveFailed
        Box(
            modifier = modifier
                .shimmerPlaceholder()
                // 解析中点击无意义，禁用点击；失败态点击任意处重试，就绪态点击播放
                .clickable(enabled = !isResolving) {
                    if (resolveFailed) onRetry() else onPlayClick()
                },
            contentAlignment = Alignment.Center
        ) {
            // 与首页列表流一致的封面图；加载中/无封面时显示波纹空白占位
            if (video.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = video.coverUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            // 底部暗色渐变遮罩，保证播放按钮与提示文字可读
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.55f)
                        )
                    )
            )
            // 中央圆形按钮：独立居中，位置不随下方提示出现/消失而移动，
            // 避免“正在解析”提示消失时按钮突然下坠的跳动
            // 流行播放按钮样式：半透明深色圆底 + 白色描边 + 柔光；
            // 内部图标随解析状态切换为 转圈/重试/播放三角
            Box(contentAlignment = Alignment.Center) {
                // 柔光用径向渐变手绘，不用 shadowElevation：
                // Android 硬件阴影对圆形 Outline 只做凸多边形近似，投影会呈八边形，
                // 按钮半透明、叠在明亮封面上时该八边形阴影隐约可见
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Black.copy(alpha = 0f)
                                    )
                                )
                            )
                        }
                )
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f)),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            isResolving -> CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )

                            resolveFailed -> Icon(
                                imageVector = Icons.Filled.Replay,
                                contentDescription = stringResource(R.string.detail_retry),
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )

                            else -> Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.detail_play),
                                tint = Color.White,
                                modifier = Modifier
                                    .size(46.dp)
                                    // 三角形视觉重心偏左，右移 2dp 做光学居中
                                    .offset(x = 2.dp)
                            )
                        }
                    }
                }
            }

            // 状态提示：锚定在播放区底部，与居中按钮解耦，
            // 出现/消失只在底部区域变化，不会推动中央按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // 解析中：半透明胶囊提示，弱化干扰
                    isResolving -> Text(
                        text = stringResource(R.string.detail_resolving),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    // 失败：失败原因胶囊 + 明确的重试按钮
                    resolveFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.detail_resolve_failed),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        TextButton(
                            onClick = onRetry,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.detail_retry),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}