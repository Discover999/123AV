package com.av123.video.ui.components

import com.av123.video.ui.icons.AppIcons

import com.av123.video.ui.theme.AppShapes

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
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
import android.os.Build
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.view.View
import android.view.ViewOutlineProvider
import coil.compose.AsyncImage
import com.av123.video.R
import com.av123.video.data.model.Video
import com.av123.video.ui.theme.Motion
import com.av123.video.ui.theme.appCardContainerColor
import com.av123.video.ui.util.rememberAppHaptics

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
            animation = tween(durationMillis = Motion.SKELETON_MS, easing = LinearEasing),
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
            .clip(AppShapes.Small)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 带按压缩放反馈的卡片外壳；[onLongClick] 非空时长按卡片触发（用于封面预览） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = AppShapes.Compact,
    defaultElevation: Dp = 2.dp,
    pressedElevation: Dp = 6.dp,
    /** false = 内容直接落在页面底色上（视频卡），不画卡片底与阴影 */
    surface: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = rememberAppHaptics()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.PressSpring,
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
                // 长按封面打开预览属有后果操作；普通点卡进详情保持静默
                onLongClick = onLongClick?.let { callback ->
                    {
                        haptics.confirm()
                        callback()
                    }
                }
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (surface) appCardContainerColor() else Color.Transparent
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (surface) defaultElevation else 0.dp,
            pressedElevation = if (surface) pressedElevation else 0.dp
        ),
        content = { content() }
    )
}

internal fun Modifier.scale(v: Float): Modifier =
    this.drawScale(scaleX = v, scaleY = v)

/** 网格视频卡片；[onLongClick] 非空时长按封面触发预览 */
@Composable
fun VideoGridCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    PressableCard(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick
    ) {
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
 * 单列行（128dp 封面在左）：标题两行、下面接同一行观看数/发布时间。
 * 外壳与网格卡共用 [PressableCard]，圆角、按压反馈、卡片底一致。
 */
@Composable
fun VideoListRow(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    PressableCard(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
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
                            .padding(4.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    // 3 行：72dp 封面旁边可容纳 3×18sp 标题 + 元信息行，
                    // 行高由文字撑到约 92dp，封面仍顶部对齐
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                VideoMetaLine(video)
            }
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
internal fun VideoMetaLine(video: Video) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (video.viewsDisplay.isNotBlank()) {
            MetaIcon(AppIcons.VisibilityOutline)
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
            MetaIcon(AppIcons.ScheduleOutline)
            Spacer(Modifier.width(3.dp))
            MetaText(timeText, Modifier.weight(1f))
        }
    }
}

/** 副标题行内的小图标：眼睛=观看数，时钟=发布时间，颜色随副标题文字 */
@Composable
internal fun MetaIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(14.dp)
    )
}

/** 副标题行内的文字片段 */
@Composable
internal fun MetaText(text: String, modifier: Modifier = Modifier) {
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
        shape = AppShapes.ExtraLarge,
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
                    imageVector = AppIcons.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(16.dp)
                )
            }
        }
    }
}
