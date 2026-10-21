@file:OptIn(ExperimentalMaterial3Api::class)
package com.av123.video.ui.components.player

import com.av123.video.ui.icons.AppIcons

import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.AppTypeScale

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import com.av123.video.R
import com.av123.video.ui.theme.Motion
import kotlin.math.max

/**
 * 长按快放提示：顶部居中的紧凑胶囊（快进图标 + “2X”），
 * 半透明深色底，尺寸尽量小，不遮挡画面主体。
 */
@Composable
internal fun LongPressSpeedBadge(speed: Float) {
    Row(
        modifier = Modifier
            .clip(AppShapes.ExtraLarge)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${speed.formatSpeed()}X",
            color = Color.White,
            fontSize = AppTypeScale.Label,
            fontWeight = FontWeight.Bold
        )
        AnimatedFastForwardArrows(modifier = Modifier.size(width = 17.dp, height = 13.dp))
    }
}

/**
 * 快放提示的动态双箭头“»”：两个右箭头错开半个周期，
 * 依次变亮并轻微向右挪动，形成持续“向右冲刺”的速度感。
 * 仅在快放胶囊组合期间存在，松手移除后循环动画自动停止。
 */
@Composable
private fun AnimatedFastForwardArrows(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fastForward")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.PULSE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        repeat(2) { index ->
            // 两个箭头错开 0.5 个周期，保证任意时刻都有一个在“冲刺”
            val phase = (progress + index * 0.5f) % 1f
            // 三角波：周期中段最亮、两端最暗
            val pulse = 1f - kotlin.math.abs(phase * 2f - 1f)
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f + 0.7f * pulse),
                modifier = Modifier
                    .size(13.dp)
                    .offset(x = index.dp * 5f + (phase - 0.5f).dp * 1.5f)
            )
        }
    }
}

/** 倍速文案去尾零：2.0 → "2"，1.5 → "1.5" */
internal fun Float.formatSpeed(): String =
    if (this % 1f == 0f) toInt().toString() else toString()


/**
 * 拖动进度条时跟随小圆点显示目标时间的气泡：
 * 深色圆角底 + 单行白色时间文案，尺寸按文案内容自适应。
 */
@Composable
internal fun SeekTimeBubble(
    timeText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = timeText,
            color = Color.White,
            fontSize = AppTypeScale.Caption,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * 双击左/右半屏快退快进的反馈浮层：与长按 2× 提示同款深色胶囊，
 * 横向排列 10 秒跳转图标与 -10s/+10s，出现时带一次弹性放大动画；
 * 由调用方对齐到画面顶部中央，与长按 2× 提示同位置。
 *
 * @param direction -1=快退（左），1=快进（右）
 */
@Composable
internal fun DoubleSeekFeedback(
    direction: Int,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(direction) {
        scale.snapTo(0.6f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = Motion.PopSpring
        )
    }
    val isRewind = direction < 0
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(AppShapes.ExtraLarge)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (isRewind) AppIcons.Replay10 else AppIcons.Forward10,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isRewind) "-10s" else "+10s",
            color = Color.White,
            fontSize = AppTypeScale.Label,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 播放异常 → 归一化用户文案。不直接展示 [PlaybackException.getLocalizedMessage]
 * （可能包含英文栈信息/CDN 地址），按错误码区间映射到全局统一错误文案。
 * Media3 错误码分段：2xxx=IO/网络、3xxx=解析，其余（解码/DRM 等）走通用失败。
 */
private fun playbackErrorText(context: Context, error: PlaybackException?): String = when {
    error == null -> context.getString(R.string.error_unknown)
    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        context.getString(R.string.error_timeout)
    error.errorCode in 2000..2999 -> context.getString(R.string.error_network)
    error.errorCode in 3000..3999 -> context.getString(R.string.error_parse)
    else -> context.getString(R.string.error_unknown)
}

@Composable
internal fun ErrorOverlay(
    error: PlaybackException?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // 轻遮罩：保留一点画面信息，视觉重心交给卡片
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        // 160ms 淡入 + 轻微放大，避免失败卡片硬切出现
        val visibleState = remember {
            MutableTransitionState(false).apply { targetState = true }
        }
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(ERROR_OVERLAY_ENTER_MS)) +
                scaleIn(tween(ERROR_OVERLAY_ENTER_MS), initialScale = 0.96f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    // 全屏横屏时也保持卡片宽度，不铺满整块画面
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(0.82f)
                    .clip(AppShapes.ExtraLarge)
                    .background(Color(0xFF17181D).copy(alpha = 0.96f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = AppShapes.ExtraLarge
                    )
                    // 详情页播放器高度有限：极端字号下仍可滚动到重试按钮
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.player_error_title),
                    color = Color.White,
                    fontSize = AppTypeScale.OverlayTitle,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playbackErrorText(LocalContext.current, error),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = AppTypeScale.Body,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.player_retry),
                        fontSize = AppTypeScale.Body,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}