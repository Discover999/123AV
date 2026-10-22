package com.av123.video.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.av123.video.ui.icons.AppIcons

/**
 * 站点默认头像是内联 SVG data URI（如 data:image/svg+xml,%3csvg…），
 * Coil 不解码 SVG，直接渲染会留白。这里把 data: 与空串都当作缺失。
 */
internal fun hasRemoteAvatar(url: String): Boolean =
    url.isNotBlank() && !url.startsWith("data:", ignoreCase = true)

/**
 * 女演员头像：没有可用远程头像时显示通用人形兜底，不再是一块空白。
 * 调用方通过 [modifier] 给尺寸；[iconSize] 是兜底图标的视觉大小。
 */
@Composable
fun ActressAvatar(
    url: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize)
        )
        if (hasRemoteAvatar(url)) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
