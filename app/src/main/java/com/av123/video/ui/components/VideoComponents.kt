package com.av123.video.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale as drawScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.graphics.Path
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import coil.compose.AsyncImage
import com.av123.video.R
import com.av123.video.data.model.Video

/**
 * 骨架屏波纹占位：浅灰底 + 循环扫过的高亮微光带，用于封面加载中 / 无封面时的空白占位。
 * 颜色取自主题 onSurface，浅色/深色模式均适配；图片加载完成后会覆盖在其上。
 */
@Composable
fun Modifier.shimmerPlaceholder(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    return this.drawWithContent {
        drawRect(color = baseColor)
        val w = size.width
        // 高光带从左侧外扫入、向右移出
        val startX = -w + progress * w * 2f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlightColor, Color.Transparent),
                start = Offset(startX, 0f),
                end = Offset(startX + w, 0f)
            )
        )
        drawContent()
    }
}

/**
 * 视频封面：有 coverUrl 用 Coil 加载网络图片，加载中/无封面时显示波纹空白占位。
 */
@Composable
fun VideoCover(
    video: Video,
    modifier: Modifier = Modifier,
    ratio: Float = 16f / 9f
) {
    Box(
        modifier = modifier
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .shimmerPlaceholder()
    ) {
        if (video.coverUrl.isNotBlank()) {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (video.durationText.isNotBlank()) {
            DurationBadge(
                text = video.durationText,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }
    }
}

/** 封面上的时长角标（网页列表常见样式） */
@Composable
fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 带按压缩放反馈的卡片外壳；[onLongClick] 非空时长按卡片触发（用于封面预览） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    defaultElevation: Dp = 2.dp,
    pressedElevation: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "cardScale"
    )
    ElevatedCard(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = defaultElevation,
            pressedElevation = pressedElevation
        ),
        content = { content() }
    )
}

private fun Modifier.scale(v: Float): Modifier =
    this.drawScale(scaleX = v, scaleY = v)

/** 网格视频卡片；[onLongClick] 非空时长按封面触发预览 */
@Composable
fun VideoGridCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    PressableCard(onClick = onClick, modifier = modifier, onLongClick = onLongClick) {
        VideoCover(video = video, modifier = Modifier.fillMaxWidth())
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            VideoMetaLine(video)
        }
    }
}

/**
 * 横向列表用的紧凑卡片；[onLongClick] 非空时长按封面触发预览。
 * [progress] 非 null（0f..1f）时在底部显示播放进度条，用于“继续观看”。
 */
@Composable
fun VideoCompactCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 168.dp,
    onLongClick: (() -> Unit)? = null,
    progress: Float? = null
) {
    PressableCard(
        onClick = onClick,
        modifier = modifier.width(width),
        onLongClick = onLongClick
    ) {
        VideoCover(video = video, modifier = Modifier.fillMaxWidth())
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            VideoMetaLine(video)
            if (progress != null && progress > 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoMetaLine(video: Video) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (video.viewsDisplay.isNotBlank()) {
            MetaIcon(Icons.Outlined.Visibility)
            Spacer(Modifier.width(3.dp))
            MetaText(video.viewsDisplay)
        }
        val timeText = video.publishedAgo.ifBlank {
            if (video.year > 0) video.year.toString() else ""
        }
        if (timeText.isNotBlank()) {
            if (video.viewsDisplay.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                MetaText("·")
                Spacer(Modifier.width(6.dp))
            }
            MetaIcon(Icons.Outlined.Schedule)
            Spacer(Modifier.width(3.dp))
            MetaText(timeText, Modifier.weight(1f))
        }
    }
}

/** 副标题行内的小图标：眼睛=观看数，时钟=发布时间，颜色随副标题文字 */
@Composable
private fun MetaIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(14.dp)
    )
}

/** 副标题行内的文字片段 */
@Composable
private fun MetaText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 首页精选大图卡片 */
@Composable
fun FeaturedCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PressableCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        defaultElevation = 8.dp,
        pressedElevation = 12.dp
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .shimmerPlaceholder()
            ) {
                if (video.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = video.coverUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            // 底部信息渐变遮罩
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = video.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 区块标题：左侧标题文字；右侧可选操作（对应网页区块右上角的
 * "查看全部 →"链接），[actionText] 非空且 [onActionClick] 非空时才显示。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        if (actionText != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

/**
 * 列表页底部分页栏（M3 风格）：首页 / 页码窗口 / 末页。
 * 仅当列表页解析到 .pager 分页结构时由调用方放置；[enabled] 为 false（翻页加载中）
 * 时所有控件禁用。页码只显示以当前页为中心的 5 页滑动窗口，不固定首尾页；
 * 长按任意页码弹出跳页对话框。整栏可横向滚动，页码位数较多时自动加宽不换行。
 */
@Composable
fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    enabled: Boolean,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showJumpDialog by remember { mutableStateOf(false) }
    val firstPageDescription = stringResource(R.string.pagination_first_page)
    val lastPageDescription = stringResource(R.string.pagination_last_page)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PagerIconButton(
                icon = Icons.Filled.FirstPage,
                description = firstPageDescription,
                enabled = enabled && currentPage > 1,
                onClick = { onPageSelected(1) }
            )
            pageWindow(currentPage, totalPages).forEach { p ->
                PageNumberButton(
                    page = p,
                    selected = p == currentPage,
                    enabled = enabled,
                    onClick = { onPageSelected(p) },
                    onLongClick = { showJumpDialog = true }
                )
            }
            PagerIconButton(
                icon = Icons.AutoMirrored.Filled.LastPage,
                description = lastPageDescription,
                enabled = enabled && currentPage < totalPages,
                onClick = { onPageSelected(totalPages) }
            )
        }
    }

    if (showJumpDialog) {
        JumpPageDialog(
            totalPages = totalPages,
            onDismiss = { showJumpDialog = false },
            onConfirm = { page ->
                showJumpDialog = false
                onPageSelected(page)
            }
        )
    }
}

@Composable
private fun PagerIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageNumberButton(
    page: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val container = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .height(36.dp)
            .widthIn(min = 36.dp)
            .clip(CircleShape)
            .background(container)
            .combinedClickable(
                enabled = enabled,
                onClick = { if (!selected) onClick() },
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = page.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (enabled || selected) contentColor
            else contentColor.copy(alpha = 0.38f),
            maxLines = 1,
            softWrap = false
        )
    }
}

/** 跳页对话框：数字输入框，校验 1..totalPages 后回调 */
@Composable
private fun JumpPageDialog(
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val page = input.trim().toIntOrNull()
    val valid = page != null && page in 1..totalPages
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pagination_jump_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { v -> input = v.filter { it.isDigit() }.take(7) },
                label = { Text(stringResource(R.string.pagination_jump_hint, totalPages)) },
                singleLine = true,
                isError = input.isNotBlank() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(page!!) }, enabled = valid) {
                Text(stringResource(R.string.pagination_jump_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/**
 * 页码窗口：总页数 ≤ 5 时全部展示；否则显示以当前页为中心的 5 页连续页码，
 * 滑到边界时整体贴边（如第 1 页显示 1..5，末页显示 total-4..total），
 * 不固定首尾页、不显示省略号。
 */
private fun pageWindow(current: Int, total: Int, windowSize: Int = 5): List<Int> {
    if (total <= windowSize) return (1..total).toList()
    var start = (current - windowSize / 2).coerceAtLeast(1)
    val end = (start + windowSize - 1).coerceAtMost(total)
    start = (end - windowSize + 1).coerceAtLeast(1)
    return (start..end).toList()
}

/**
 * 封面长按预览弹窗：静音、循环自动播放网页 data-preview 指向的预览视频
 * （主流视频站 hover 预览的同款交互）。
 *
 * 注意：预览地址虽以 .png 结尾，服务器实际返回的是 mp4 内容（隐式 mp4），
 * 因此 MediaItem 显式声明 [MimeTypes.VIDEO_MP4]，强制走 progressive 提取器，
 * 避免按扩展名推断异常。播放器为弹窗内独立实例，关闭即释放，
 * 与详情页的共享播放器互不影响。
 *
 * 交互：点击遮罩外区域关闭弹窗；点击预览视频本身通过 [onVideoClick]
 * 进入对应播放页。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewDialog(
    video: Video,
    onDismiss: () -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(video.previewUrl) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            // 地址以 .png 结尾但实为 mp4 内容（隐式 mp4），显式声明 MIME 走 progressive
            val mediaItem = androidx.media3.common.MediaItem.Builder()
                .setUri(video.previewUrl)
                .setMimeType(MimeTypes.VIDEO_MP4)
                .build()
            setMediaItem(mediaItem)
            volume = 0f // 预览静音自动播放
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    // 生命周期联动：切后台暂停，回前台恢复；关闭弹窗时释放播放器
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> player.playWhenReady = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var playerError by remember { mutableStateOf<PlaybackException?>(player.playerError) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onPlayerErrorChanged(e: PlaybackException?) {
                playerError = e
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    // 拦截卡片区域点击，避免点到内容时关闭弹窗
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 消费点击 */ }
            ) {
                Column {
                    // 预览播放区：16:9，裁剪填充（与封面一致）；
                    // 顶部两角随卡片圆角裁剪，否则视频黑底直角会顶出圆角。
                    // 点击视频本身进入对应播放页（父级 Surface 仅消费点击防关闭）。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(Color.Black)
                            .clickable { onVideoClick(video) }
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                // 从 XML inflate：surface_type 只能通过布局属性设置，
                                // TextureView 保证圆角 outline 裁剪在 Android 12 以下也生效
                                (LayoutInflater.from(ctx)
                                    .inflate(R.layout.preview_player_view, null) as PlayerView)
                                    .apply {
                                        // Compose 的 Modifier.clip 裁剪不到原生 PlayerView 画面，
                                        // 必须直接在 view 上按顶部圆角 outline 裁剪（只圆顶部两角）
                                        outlineProvider = object : ViewOutlineProvider() {
                                            override fun getOutline(view: View, viewOutline: android.graphics.Outline) {
                                                val radius = view.resources.displayMetrics.density * 20f
                                                val path = Path().apply {
                                                    addRoundRect(
                                                        RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()),
                                                        // addRoundRect 八元组顺序：左上、右上、右下、左下（各 x,y）
                                                        floatArrayOf(
                                                            radius, radius,
                                                            radius, radius,
                                                            0f, 0f,
                                                            0f, 0f
                                                        ),
                                                        Path.Direction.CW
                                                    )
                                                }
                                                viewOutline.setConvexPath(path)
                                            }
                                        }
                                        clipToOutline = true
                                        this.player = player
                                    }
                            },
                            onRelease = { it.player = null },
                            modifier = Modifier.fillMaxSize()
                        )

                        // 加载中：缓冲转圈
                        if (playerError == null &&
                            (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(36.dp)
                            )
                        }

                        // 播放失败：回退展示封面
                        if (playerError != null) {
                            if (video.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = video.coverUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = stringResource(R.string.video_preview_unavailable),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        // “预览”角标
                        Text(
                            text = stringResource(R.string.video_preview),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }

                    // 标题与元信息
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = buildList {
                                    if (video.code.isNotBlank()) add(video.code)
                                    if (video.durationText.isNotBlank()) add(video.durationText)
                                    if (video.viewsDisplay.isNotBlank()) add(video.viewsDisplay)
                                }.joinToString("  ·  "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 收藏 / 关注类“点亮”动效：当 [active] 由 false 变为 true 时，
 * 图标先收缩到 0.4 再冲到 1.3，最后带回弹地落回 1.0（点赞式 pop）；
 * 变为 false 时立即复位，不做动画。
 *
 * 返回当前缩放值，调用方挂在图标上即可：
 * ```
 * val scale = rememberTogglePopScale(active = isFavorite)
 * Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
 * ```
 * 初始即 active（如进页面时该内容已收藏）不会弹，只在状态翻转时触发。
 */
@Composable
fun rememberTogglePopScale(active: Boolean): Float {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            scale.snapTo(0.4f)
            scale.animateTo(1.3f, animationSpec = tween(durationMillis = 150))
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            scale.snapTo(1f)
        }
    }
    return scale.value
}
