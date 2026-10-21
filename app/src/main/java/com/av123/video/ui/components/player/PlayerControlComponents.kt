@file:OptIn(ExperimentalMaterial3Api::class)
package com.av123.video.ui.components.player

import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.AppTypeScale

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.av123.video.MainActivity
import com.av123.video.R
import com.av123.video.ui.theme.Motion
import com.av123.video.data.prefs.ResizeModeOption
import com.av123.video.ui.util.AppLog
import com.av123.video.ui.util.findActivity
import com.av123.video.ui.util.formatDuration
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun ControlCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerProgressSlider(
    position: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit,
    onDragStateChange: (Boolean) -> Unit = {}
) {
    val safeDuration = duration.coerceAtLeast(0L)
    val sliderPosition = remember(position, safeDuration) {
        if (safeDuration > 0) position.toFloat() / safeDuration.toFloat() else 0f
    }
    var draggingValue by remember { mutableFloatStateOf(-1f) }
    val isDragging = draggingValue >= 0f
    val currentValue = if (isDragging) draggingValue else sliderPosition
    val interactionSource = remember { MutableInteractionSource() }

    // 拖动状态上抛：父层据此暂停控制层自动隐藏计时
    LaunchedEffect(isDragging) { onDragStateChange(isDragging) }

    // 气泡与进度条之间的间距、小圆点半径（与 thumbSize 14dp 对应）
    val bubbleGap = 6.dp
    val thumbRadius = 7.dp

    // 自定义 Layout：Slider 正常占位测量，时间气泡按自然尺寸测量后浮于小圆点上方，
    // 水平跟随圆点中心，靠近两端时夹紧在进度条内，垂直溢出不影响布局且默认不裁剪
    Layout(
        content = {
            Slider(
                value = currentValue,
                onValueChange = { draggingValue = it },
                interactionSource = interactionSource,
                onValueChangeFinished = {
                    if (safeDuration > 0) {
                        onSeek((draggingValue * safeDuration).toLong())
                    }
                    draggingValue = -1f
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                ),
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        thumbSize = DpSize(14.dp, 14.dp),
                        colors = SliderDefaults.colors(thumbColor = Color.White)
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(4.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )

            AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn(Motion.Overlay),
                exit = fadeOut(Motion.Overlay)
            ) {
                val previewPositionMs =
                    if (safeDuration > 0) (currentValue * safeDuration).toLong() else 0L
                SeekTimeBubble(timeText = formatDuration(previewPositionMs))
            }
        },
        measurePolicy = { measurables, constraints ->
            val sliderPlaceable = measurables[0].measure(constraints)
            val bubblePlaceable = measurables.getOrNull(1)?.measure(Constraints())
            layout(sliderPlaceable.width, sliderPlaceable.height) {
                sliderPlaceable.placeRelative(0, 0)
                bubblePlaceable?.let { bubble ->
                    if (bubble.width > 0 && bubble.height > 0) {
                        // Material3 Slider 轨道两侧内缩一个圆点半径，圆心 = radius + fraction * (w - 2r)
                        val radiusPx = thumbRadius.roundToPx()
                        val centerX = radiusPx +
                            currentValue.coerceIn(0f, 1f) * (sliderPlaceable.width - radiusPx * 2)
                        val maxBubbleX = (sliderPlaceable.width - bubble.width).coerceAtLeast(0)
                        val bubbleX = (centerX.roundToInt() - bubble.width / 2)
                            .coerceIn(0, maxBubbleX)
                        val bubbleY = -bubble.height - bubbleGap.roundToPx()
                        bubble.placeRelative(bubbleX, bubbleY)
                    }
                }
            }
        }
    )
}

@Composable
internal fun VolumePanel(
    volume: Int,
    maxVolume: Int,
    isMuted: Boolean,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(AppShapes.Large)
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* 拦截点击，避免触发底层控制器切换 */ }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        VerticalVolumeBar(
            volume = volume,
            maxVolume = maxVolume,
            isMuted = isMuted,
            onVolumeChange = onVolumeChange
        )
    }
}

/**
 * 垂直音量条：与主流视频软件一致，靠近音量按钮展示，
 * 支持点击定位与上下拖动调节，条内自底部向上填充。
 * 视觉轨道粗细与进度条一致（4dp），外层保留更宽的透明触摸区域。
 */
@Composable
internal fun VerticalVolumeBar(
    volume: Int,
    maxVolume: Int,
    isMuted: Boolean,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackHeight = 120.dp
    val trackWidth = 4.dp
    val touchWidth = 28.dp
    val thumbSize = 14.dp
    val fraction = if (maxVolume > 0) volume.toFloat() / maxVolume.toFloat() else 0f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isMuted) stringResource(R.string.player_mute)
            else "${(fraction * 100).toInt()}%",
            color = Color.White,
            fontSize = AppTypeScale.Caption,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(touchWidth)
                .height(trackHeight)
                .pointerInput(maxVolume) {
                    val trackHeightPx = size.height.toFloat()
                    detectTapGestures { offset ->
                        val f = 1f - (offset.y / trackHeightPx).coerceIn(0f, 1f)
                        onVolumeChange((f * maxVolume).roundToInt().coerceIn(0, maxVolume))
                    }
                }
                .pointerInput(maxVolume) {
                    val trackHeightPx = size.height.toFloat()
                    detectVerticalDragGestures { change, _ ->
                        val f = 1f - (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                        onVolumeChange((f * maxVolume).roundToInt().coerceIn(0, maxVolume))
                    }
                }
        ) {
            // 背景轨道
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(trackWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
            )
            // 已激活音量填充，自底部向上
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(trackWidth)
                    .fillMaxHeight(fraction)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            // 滑块圆点，中心对齐填充顶部
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(trackHeight * fraction) + thumbSize / 2)
                    .size(thumbSize)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
internal fun SpeedPopupMenu(
    expanded: Boolean,
    selected: Float,
    onSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 1f, 1.25f, 1.5f, 2f, 3f)
    val selectedColor = MaterialTheme.colorScheme.primary

    CapsulePopupMenu(expanded = expanded, onDismiss = onDismiss) {
        speeds.forEach { speed ->
            val isSelected = speed == selected
            DropdownMenuItem(
                text = {
                    Text(
                        text = "${speed}x",
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) selectedColor else Color.White.copy(alpha = 0.78f),
                        fontSize = AppTypeScale.Body
                    )
                },
                onClick = { onSelected(speed) }
            )
        }
    }
}

@Composable
internal fun TrackPopupMenu(
    expanded: Boolean,
    tracks: List<VideoTrack>,
    selected: VideoTrack?,
    onSelected: (VideoTrack?) -> Unit,
    onDismiss: () -> Unit
) {
    val autoLabel = stringResource(R.string.player_track_auto)
    CapsulePopupMenu(expanded = expanded, onDismiss = onDismiss) {
        DropdownMenuItem(
            text = {
                Text(
                    text = autoLabel,
                    fontWeight = if (selected == null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected == null) Color.White else Color.White.copy(alpha = 0.78f),
                    fontSize = AppTypeScale.Body
                )
            },
            onClick = { onSelected(null) }
        )
        tracks.forEach { track ->
            val isSelected = track == selected
            DropdownMenuItem(
                text = {
                    Text(
                        text = track.label,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.78f),
                        fontSize = AppTypeScale.Body
                    )
                },
                onClick = { onSelected(track) }
            )
        }
    }
}

/**
 * 播放器右上角设置齿轮：点击在齿轮正左侧弹出深色胶囊菜单，
 * 避免向下展开超出播放器区域。菜单内含“播放详情/播放地址”等入口，
 * “播放地址”二级弹窗同样锚定在齿轮左侧。
 */
@Composable
internal fun PlayerSettingsButton(
    settingsExpanded: Boolean,
    urlSheetExpanded: Boolean,
    playbackUrl: String,
    resizeMode: ResizeModeOption,
    onResizeModeChange: (ResizeModeOption) -> Unit,
    onSettingsExpandedChange: (Boolean) -> Unit,
    onUrlSheetExpandedChange: (Boolean) -> Unit,
    onShowStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(onClick = { onSettingsExpandedChange(!settingsExpanded) }) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.player_settings),
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        // 锚点为 48dp 图标按钮，左移 56dp 使弹窗右缘对齐齿轮左缘并留 8dp 间距
        val offsetXPx = with(LocalDensity.current) { (-56).dp.roundToPx() }

        if (settingsExpanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(offsetXPx, 0),
                onDismissRequest = { onSettingsExpandedChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                SettingsMenuContent(
                    resizeMode = resizeMode,
                    onResizeModeChange = onResizeModeChange,
                    onShowStats = {
                        onSettingsExpandedChange(false)
                        onShowStats()
                    },
                    onShowUrl = {
                        onSettingsExpandedChange(false)
                        onUrlSheetExpandedChange(true)
                    }
                )
            }
        }

        if (urlSheetExpanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(offsetXPx, 0),
                onDismissRequest = { onUrlSheetExpandedChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                PlaybackUrlContent(
                    url = playbackUrl,
                    onDismiss = { onUrlSheetExpandedChange(false) }
                )
            }
        }
    }
}

@Composable
internal fun SettingsMenuContent(
    resizeMode: ResizeModeOption,
    onResizeModeChange: (ResizeModeOption) -> Unit,
    onShowStats: () -> Unit,
    onShowUrl: () -> Unit
) {
    // 二级菜单：画面比例选项在同一胶囊容器内切换展示，避免弹层套弹层定位
    var showResizeMenu by remember { mutableStateOf(false) }

    val resizeLabel = when (resizeMode) {
        ResizeModeOption.FIT -> stringResource(R.string.player_resize_fit)
        ResizeModeOption.FILL -> stringResource(R.string.player_resize_fill)
        ResizeModeOption.ZOOM -> stringResource(R.string.player_resize_zoom)
    }

    Box(
        modifier = Modifier
            .clip(AppShapes.Large)
            .background(Color.Black.copy(alpha = 0.86f))
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            if (!showResizeMenu) {
                // 一级：画面比例（右侧显示当前值）/ 播放详情 / 播放链接
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showResizeMenu = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.player_resize_title),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = AppTypeScale.Body,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = resizeLabel,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = AppTypeScale.Label
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                SettingsMenuRow(stringResource(R.string.player_menu_stats), onShowStats)
                SettingsMenuRow(stringResource(R.string.player_menu_url), onShowUrl)
            } else {
                // 二级：标题 + 返回，下方三个画面比例选项，当前项打勾
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showResizeMenu = false }
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.player_resize_title),
                        color = Color.White,
                        fontSize = AppTypeScale.Body,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                ResizeModeOption.entries.forEach { option ->
                    val label = when (option) {
                        ResizeModeOption.FIT -> R.string.player_resize_fit
                        ResizeModeOption.FILL -> R.string.player_resize_fill
                        ResizeModeOption.ZOOM -> R.string.player_resize_zoom
                    }
                    val selected = option == resizeMode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResizeModeChange(option) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(label),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
                            fontSize = AppTypeScale.Body,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsMenuRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = AppTypeScale.Body
        )
    }
}

/**
 * 当前播放链接（m3u8）弹窗：链接为可点击超链接（拉起浏览器），
 * 支持一键复制，右上角 X 关闭。
 */
@Composable
internal fun PlaybackUrlContent(
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500L.milliseconds)
            copied = false
        }
    }

    fun openInBrowser() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Box(
        modifier = Modifier
            .width(280.dp)
            .clip(AppShapes.Large)
            .background(Color.Black.copy(alpha = 0.86f))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.player_menu_url),
                    color = Color.White,
                    fontSize = AppTypeScale.Body,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                PopupCloseButton(onClick = onDismiss)
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 超链接样式（浅蓝 + 下划线），点击拉起浏览器播放
            Text(
                text = url,
                color = Color(0xFF8AB4F8),
                fontSize = AppTypeScale.Caption,
                fontFamily = FontFamily.Monospace,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openInBrowser() }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(url))
                        copied = true
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (copied) stringResource(R.string.player_url_copied)
                        else stringResource(R.string.player_url_copy),
                        color = Color.White,
                        fontSize = AppTypeScale.Label
                    )
                }
            }
        }
    }
}

/** 弹窗右上角通用关闭按钮 */
@Composable
internal fun PopupCloseButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.common_close),
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
    }
}


@Composable
internal fun CapsulePopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = AppShapes.Large,
        containerColor = Color.Black.copy(alpha = 0.86f)
    ) {
        content()
    }
}