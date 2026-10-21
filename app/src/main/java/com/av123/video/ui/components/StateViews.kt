package com.av123.video.ui.components

import com.av123.video.ui.icons.AppIcons

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Context
import com.av123.video.R
import com.av123.video.data.net.AppError
import com.av123.video.data.net.AppErrorKind

/** 骨架屏样式：列表页与详情页各一套，加载完成后不发生整页重排 */
enum class LoadingSkeleton { List, Detail }

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    variant: LoadingSkeleton = LoadingSkeleton.List
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (variant) {
            LoadingSkeleton.List -> ListSkeleton()
            LoadingSkeleton.Detail -> DetailSkeleton()
        }
    }
}

/** 列表骨架：16:9 头部 + 胶囊行 + 两列卡片，与首页/分类页的真实布局一致 */
@Composable
private fun ListSkeleton() {
    SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f), 20)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) { SkeletonBlock(Modifier.width(64.dp).height(28.dp), 14) }
    }
    repeat(2) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f), 16)
                    SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(14.dp), 4)
                    SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(12.dp), 4)
                }
            }
        }
    }
}

/** 详情骨架：hero + 标题 + 信息胶囊 + 简介行 */
@Composable
private fun DetailSkeleton() {
    SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f), 20)
    SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(26.dp), 8)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { SkeletonBlock(Modifier.width(72.dp).height(28.dp), 14) }
    }
    repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(14.dp), 7) }
}

/** 与封面同一套扫光语言，深色/浅色均取自主题 onSurface */
@Composable
private fun SkeletonBlock(modifier: Modifier, radius: Int) {
    Box(modifier.clip(RoundedCornerShape(radius.dp)).shimmerPlaceholder())
}

/**
 * 归一化错误 → 用户可读中文文案。整屏错误态与全局 Snackbar 共用同一套口径，
 * 数据层只给 [AppError]，文案统一在这里解析。
 */
fun appErrorText(context: Context, error: AppError): String = when (error.kind) {
    AppErrorKind.DNS -> context.getString(R.string.error_dns)
    AppErrorKind.TIMEOUT -> context.getString(R.string.error_timeout)
    AppErrorKind.SSL -> context.getString(R.string.error_ssl)
    AppErrorKind.NETWORK -> context.getString(R.string.error_network)
    AppErrorKind.HTTP_CLIENT ->
        context.getString(R.string.error_http_client, error.httpCode ?: 0)
    AppErrorKind.HTTP_SERVER ->
        context.getString(R.string.error_http_server, error.httpCode ?: 0)
    AppErrorKind.PARSE -> context.getString(R.string.error_parse)
    AppErrorKind.UNKNOWN -> context.getString(R.string.error_unknown)
}

/** Composable 场景下的文案解析（整屏错误态用） */
@Composable
fun appErrorText(error: AppError): String =
    appErrorText(LocalContext.current, error)

/** 错误态（错误图标 + 归一化文案 + 重试） */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = AppIcons.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}


/** 空态 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}
