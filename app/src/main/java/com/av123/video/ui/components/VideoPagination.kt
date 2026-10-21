package com.av123.video.ui.components

import com.av123.video.ui.icons.AppIcons

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
import android.os.Build
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.view.View
import android.view.ViewOutlineProvider
import coil.compose.AsyncImage
import com.av123.video.R
import com.av123.video.data.model.Video

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
                icon = AppIcons.FirstPage,
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
                icon = AppIcons.LastPage,
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
internal fun PagerIconButton(
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
internal fun PageNumberButton(
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
internal fun JumpPageDialog(
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
internal fun pageWindow(current: Int, total: Int, windowSize: Int = 5): List<Int> {
    if (total <= windowSize) return (1..total).toList()
    var start = (current - windowSize / 2).coerceAtLeast(1)
    val end = (start + windowSize - 1).coerceAtMost(total)
    start = (end - windowSize + 1).coerceAtLeast(1)
    return (start..end).toList()
}
