package com.av123.video.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.av123.video.R
import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.util.rememberAppHaptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 左滑露出的删除区宽度；与图标、点击热区共用 */
private val RevealWidth = 88.dp

/**
 * 左滑露出删除图标的行容器（历史 / 收藏共用）。
 *
 * 与「滑到阈值即删除」不同：越过阈值后吸附停在露出态，行不消失；
 * 只有点右侧的删除图标才真正删除。两个动作各给一次触感——
 * 吸附露出时 tick，点删除时 confirm。展开状态下点内容区域先收回。
 */
@Composable
fun SwipeRevealRow(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AppShapes.Large,
    content: @Composable () -> Unit
) {
    val haptics = rememberAppHaptics()
    val deleteLabel = stringResource(R.string.common_delete)
    val scope = rememberCoroutineScope()
    val revealPx = with(LocalDensity.current) { RevealWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scheme = MaterialTheme.colorScheme
    // 浅色用实心 error（#BA1A1A），深色用 errorContainer（#93000A）：
    // errorContainer 在浅色下是淡粉，正是要避开的那个颜色
    val dark = scheme.background.luminance() < 0.5f
    val containerColor = if (dark) scheme.errorContainer else scheme.error
    val contentColor = if (dark) scheme.onErrorContainer else scheme.onError

    fun settle(open: Boolean) {
        scope.launch {
            offset.animateTo(
                targetValue = if (open) -revealPx else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // 外部状态变化（例如另一行滑开、进入多选）时跟着收放
    LaunchedEffect(revealed, revealPx) {
        offset.animateTo(
            targetValue = if (revealed) -revealPx else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    // 只有真正在滑动/展开时才铺红色删除底：
    // 常驻的话，长按卡片缩放到 0.97 时四周（尤其底部）会漏出红色
    val showDeleteBackground by remember(enabled, revealed) {
        derivedStateOf { enabled && (revealed || offset.value < -0.5f) }
    }

    Box(modifier = modifier.clip(shape)) {
        if (showDeleteBackground) {
            // 底层：整条删除底色，右侧露出区可点
            Box(Modifier.matchParentSize().background(containerColor)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(RevealWidth)
                        .clickable(enabled = revealed) {
                            haptics.confirm()
                            onDelete()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = deleteLabel,
                        tint = contentColor
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .then(
                    if (!enabled) {
                        Modifier
                    } else {
                        Modifier.pointerInput(revealPx) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val open = offset.value < -revealPx * 0.5f
                                    if (open && !revealed) haptics.tick()
                                    if (open != revealed) onRevealedChange(open)
                                    settle(open)
                                },
                                onDragCancel = { settle(revealed) }
                            ) { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offset.snapTo(
                                        (offset.value + dragAmount).coerceIn(-revealPx, 0f)
                                    )
                                }
                            }
                        }
                    }
                )
        ) {
            content()
            // 展开时盖一层透明点击区：点内容只收回，不跳转
            if (revealed) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { onRevealedChange(false) }
                )
            }
        }
    }
}
