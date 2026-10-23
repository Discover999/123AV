package com.av123.video.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.av123.video.ui.icons.AppIcons
import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.AppTypeScale

/** 提示语义：决定左侧图标与颜色，不影响容器形状 */
enum class SnackbarTone { Neutral, Success, Removal, Error }

class AppSnackbarVisuals(
    override val message: String,
    val tone: SnackbarTone = SnackbarTone.Neutral,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short
) : SnackbarVisuals

/**
 * 统一入口：页面不再直接调 showSnackbar，避免各写一处文案与样式。
 *
 * 后进先显示：M3 的 [SnackbarHostState.showSnackbar] 是排队语义，新提示会挂起到当前这条
 * 超时才轮到它。这里先关掉正在显示的那条，新提示立刻占位；被顶掉的调用拿到
 * [SnackbarResult.Dismissed]，所以「撤销」类回调不会因为被顶掉而误触发。
 */
suspend fun SnackbarHostState.showAppSnackbar(
    message: String,
    tone: SnackbarTone = SnackbarTone.Neutral,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = SnackbarDuration.Short
): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(
        AppSnackbarVisuals(
            message = message,
            tone = tone,
            actionLabel = actionLabel,
            withDismissAction = withDismissAction,
            duration = duration
        )
    )
}

/** 胶囊与屏幕底 / 底栏之间固定留出的呼吸空间 */
private val SnackbarBottomGap = 48.dp

/**
 * 全 App 共用的底部轻提示：胶囊容器 + 左侧状态图标，浮在悬浮底栏上缘之上。
 * 各页面只负责让出底栏与手势条，这一层统一再抬高 48dp，避免任何页面贴底。
 * 未标记语义的普通消息（旧调用点）按中性处理，不显示图标。
 */
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState,
        modifier = modifier.padding(bottom = SnackbarBottomGap)
    ) { data ->
        AppSnackbar(data)
    }
}

@Composable
private fun AppSnackbar(data: SnackbarData) {
    val scheme = MaterialTheme.colorScheme
    val visuals = data.visuals
    val tone = (visuals as? AppSnackbarVisuals)?.tone ?: SnackbarTone.Neutral
    val icon = when (tone) {
        SnackbarTone.Error -> AppIcons.ErrorOutline
        SnackbarTone.Success -> AppIcons.Check
        SnackbarTone.Removal -> AppIcons.Close
        SnackbarTone.Neutral -> null
    }
    val iconTint = when (tone) {
        SnackbarTone.Error, SnackbarTone.Removal -> scheme.error
        SnackbarTone.Success -> scheme.primary
        SnackbarTone.Neutral -> scheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = AppShapes.Pill,
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        start = 14.dp,
                        end = if (visuals.actionLabel != null) 6.dp else 14.dp,
                        top = 9.dp,
                        bottom = 9.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = visuals.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = AppTypeScale.BodyLarge,
                        lineHeight = 20.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 272.dp)
                )
                visuals.actionLabel?.let { label ->
                    Box(
                        modifier = Modifier
                            .clip(AppShapes.Pill)
                            .clickable { data.performAction() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = AppTypeScale.Body,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = scheme.primary
                        )
                    }
                }
            }
        }
    }
}
