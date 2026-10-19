@file:OptIn(ExperimentalMaterial3Api::class)
package com.av123.video.ui.components.player

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

/**
 * 播放器控制层：顶部返回/标题 + 中央播放按钮 + 底部进度/倍速/音量/全屏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerControls(
    title: String,
    isFullscreen: Boolean,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    bufferedPosition: Long,
    playbackUrl: String,
    selectedSpeed: Float,
    availableTracks: List<VideoTrack>,
    selectedTrack: VideoTrack?,
    onTitleBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekDragStateChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onTrackSelected: (VideoTrack?) -> Unit,
    onControlPopupVisibleChange: (Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    onShowPlaybackStats: () -> Unit,
    resizeMode: ResizeModeOption,
    onResizeModeChange: (ResizeModeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playDescription = stringResource(R.string.common_play)
    val pauseDescription = stringResource(R.string.common_pause)
    val exitFullscreenDescription = stringResource(R.string.player_exit_fullscreen)
    val enterFullscreenDescription = stringResource(R.string.player_enter_fullscreen)
    val seekBackDescription = stringResource(R.string.player_seek_back)
    val seekForwardDescription = stringResource(R.string.player_seek_forward)
    val muteDescription = stringResource(R.string.player_mute)
    val unmuteDescription = stringResource(R.string.player_unmute)
    val trackAutoLabel = stringResource(R.string.player_track_auto)
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showUrlSheet by remember { mutableStateOf(false) }
    var showVolumePanel by remember { mutableStateOf(false) }
    var volume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    var isMuted by remember {
        mutableStateOf(audioManager.isStreamMute(AudioManager.STREAM_MUSIC))
    }

    // 监听系统音量变化（物理音量键、其他应用调整等），保证图标与音量条实时同步
    DisposableEffect(audioManager, context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
            }
        }
        val uri = Settings.System.CONTENT_URI
        context.contentResolver.registerContentObserver(uri, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val effectivelyMuted = isMuted || volume == 0

    LaunchedEffect(showSpeedSheet, showTrackSheet, showSettingsSheet, showUrlSheet, showVolumePanel) {
        onControlPopupVisibleChange(
            showSpeedSheet || showTrackSheet || showSettingsSheet || showUrlSheet || showVolumePanel
        )
    }

    // 音量条显示后自动隐藏，拖动改变音量时重置计时
    LaunchedEffect(showVolumePanel, volume) {
        if (showVolumePanel) {
            delay(2_000L.milliseconds)
            showVolumePanel = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { onControlPopupVisibleChange(false) }
    }

    Box(modifier = modifier) {
        // 顶部渐变遮罩 + 标题/返回
        if (isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                            startY = 0f,
                            endY = 120f
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTitleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = exitFullscreenDescription,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    PlayerSettingsButton(
                        settingsExpanded = showSettingsSheet,
                        onSettingsExpandedChange = { showSettingsSheet = it },
                        urlSheetExpanded = showUrlSheet,
                        onUrlSheetExpandedChange = { showUrlSheet = it },
                        playbackUrl = playbackUrl,
                        resizeMode = resizeMode,
                        onResizeModeChange = onResizeModeChange,
                        onShowStats = onShowPlaybackStats
                    )
                }
            }
        }

        // 竖屏时顶部没有标题栏，在播放器右上角悬浮设置齿轮
        if (!isFullscreen) {
            PlayerSettingsButton(
                settingsExpanded = showSettingsSheet,
                onSettingsExpandedChange = { showSettingsSheet = it },
                urlSheetExpanded = showUrlSheet,
                onUrlSheetExpandedChange = { showUrlSheet = it },
                playbackUrl = playbackUrl,
                resizeMode = resizeMode,
                onResizeModeChange = onResizeModeChange,
                onShowStats = onShowPlaybackStats,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // 中央快捷控制：相对播放器整体垂直居中，与加载/缓冲动画处于同一位置，
        // 避免状态切换时视觉跳动；底部渐变顶部保留透明带，不会遮暗按钮
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            ControlCircleButton(onClick = onSeekBack, size = 46.dp) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = seekBackDescription,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            ControlCircleButton(onClick = onTogglePlay, size = 64.dp) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) pauseDescription else playDescription,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            ControlCircleButton(onClick = onSeekForward, size = 46.dp) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = seekForwardDescription,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // 底部控制栏：进度条置顶，按钮紧凑排列。
        // 渐变顶部保留约 40dp 透明带：居中的中央按钮即使与控制栏区域重叠，
        // 落在透明带内也不会被遮暗；深色段集中在进度条与按钮行所在的下半部分
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.34f to Color.Black.copy(alpha = 0.12f),
                            0.67f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.85f)
                        ),
                        startY = 0f,
                        endY = with(LocalDensity.current) { 120.dp.toPx() }
                    )
                )
                .padding(start = 12.dp, top = 36.dp, end = 12.dp, bottom = 8.dp)
        ) {
            PlayerProgressSlider(
                position = position,
                duration = duration,
                bufferedPosition = bufferedPosition,
                onSeek = onSeek,
                onDragStateChange = onSeekDragStateChange
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) pauseDescription else playDescription,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = formatDuration(position),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = " / ${formatDuration(duration)}",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                Box {
                    TextButton(
                        onClick = { showSpeedSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = "${selectedSpeed}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    SpeedPopupMenu(
                        expanded = showSpeedSheet,
                        selected = selectedSpeed,
                        onSelected = {
                            onSpeedChange(it)
                            showSpeedSheet = false
                        },
                        onDismiss = { showSpeedSheet = false }
                    )
                }

                if (availableTracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        TextButton(
                            onClick = { showTrackSheet = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = Color.White.copy(alpha = 0.12f)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = selectedTrack?.label ?: trackAutoLabel,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TrackPopupMenu(
                            expanded = showTrackSheet,
                            tracks = availableTracks,
                            selected = selectedTrack,
                            onSelected = {
                                onTrackSelected(it)
                                showTrackSheet = false
                            },
                            onDismiss = { showTrackSheet = false }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box {
                    IconButton(
                        onClick = {
                            // 点击音量按钮仅弹出音量调节面板，不直接静音；
                            // 静音/音量通过弹出的音量条操作
                            showVolumePanel = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (effectivelyMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (effectivelyMuted) unmuteDescription else muteDescription,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 音量条：以音量按钮为锚点弹出，水平居中于图标、浮于控制栏上方
                    if (showVolumePanel) {
                        val panelOffsetY = with(LocalDensity.current) { (-52).dp.roundToPx() }
                        Popup(
                            alignment = Alignment.BottomCenter,
                            offset = IntOffset(0, panelOffsetY),
                            onDismissRequest = { showVolumePanel = false },
                            properties = PopupProperties(focusable = false)
                        ) {
                            VolumePanel(
                                volume = volume,
                                maxVolume = maxVolume,
                                isMuted = effectivelyMuted,
                                onVolumeChange = { newVolume ->
                                    volume = newVolume
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                    if (newVolume > 0 && isMuted) {
                                        audioManager.adjustStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            AudioManager.ADJUST_UNMUTE,
                                            0
                                        )
                                        isMuted = false
                                    }
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen)
                            Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) exitFullscreenDescription
                        else enterFullscreenDescription,
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }

}
