@file:OptIn(ExperimentalMaterial3Api::class)
package com.av123.video.ui.components.player

import com.av123.video.ui.icons.AppIconSize
import com.av123.video.ui.icons.AppIcons

import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.AppTypeScale
import com.av123.video.ui.util.rememberAppHaptics
import com.av123.video.ui.theme.Motion

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.av123.video.data.model.Episode
import com.av123.video.data.prefs.ResizeModeOption
import com.av123.video.ui.util.AppLog
import com.av123.video.ui.util.findActivity
import com.av123.video.ui.util.formatDuration
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
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
    isEnded: Boolean,
    position: Long,
    duration: Long,
    bufferedPosition: Long,
    selectedSpeed: Float,
    availableTracks: List<VideoTrack>,
    selectedTrack: VideoTrack?,
    episodes: List<Episode>,
    selectedEpisodeIndex: Int,
    onTitleBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekDragStateChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onTrackSelected: (VideoTrack?) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onControlPopupVisibleChange: (Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    onShowPlaybackStats: () -> Unit,
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    flipToExitEnabled: Boolean,
    onFlipToExitChange: (Boolean) -> Unit,
    resizeMode: ResizeModeOption,
    onResizeModeChange: (ResizeModeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
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
    // 全屏改用右侧面板，锚定倍速弹层只在竖屏使用
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) showSpeedSheet = false
    }
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

    LaunchedEffect(showSpeedSheet, showTrackSheet, showSettingsSheet, showVolumePanel) {
        onControlPopupVisibleChange(
            showSpeedSheet || showTrackSheet || showSettingsSheet || showVolumePanel
        )
    }

    // 音量条显示后自动隐藏，拖动改变音量时重置计时
    LaunchedEffect(showVolumePanel, volume) {
        if (showVolumePanel) {
            delay(2_000L.milliseconds)
            showVolumePanel = false
        }
    }

    // 全屏顶栏状态胶囊：时间 / 网络 / 电量
    var timeText by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }

    val connectivityManager = remember(context) {
        context.getSystemService(ConnectivityManager::class.java)
    }
    fun networkLabelOf(caps: NetworkCapabilities?): String = when {
        caps == null -> "离线"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "数据"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线"
        else -> "在线"
    }
    var networkLabel by remember {
        mutableStateOf(
            networkLabelOf(
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            )
        )
    }
    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                networkLabel = networkLabelOf(caps)
            }

            override fun onLost(network: Network) {
                networkLabel = networkLabelOf(
                    connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                )
            }
        }
        runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
    }

    var batteryLevel by remember {
        mutableIntStateOf(
            (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        )
    }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) batteryLevel = level * 100 / scale
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // 设置面板打开时，返回键先关面板，不直接退出全屏
    BackHandler(enabled = showSettingsSheet) {
        showSettingsSheet = false
    }

    DisposableEffect(Unit) {
        onDispose { onControlPopupVisibleChange(false) }
    }

    Box(modifier = modifier) {
        // 顶部渐变遮罩 + 标题/返回
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(
                        // 容器高度与渐变终点一致，底部才会真正过渡到全透明
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.68f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = with(LocalDensity.current) { 84.dp.toPx() }
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onTitleBack()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = exitFullscreenDescription,
                            tint = Color.White,
                            modifier = Modifier.size(AppIconSize.Player)
                        )
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = AppTypeScale.Title,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeText,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = AppTypeScale.Caption,
                            fontWeight = FontWeight.Medium
                        )
                        NetworkPill(label = networkLabel)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BatteryGlyph(level = batteryLevel, charging = charging)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$batteryLevel%",
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = AppTypeScale.Caption
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.tick()
                                showSettingsSheet = true
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Settings,
                                contentDescription = stringResource(R.string.player_settings),
                                tint = Color.White,
                                modifier = Modifier.size(AppIconSize.Inline)
                            )
                        }
                    }
                }
            }
        }

        // 竖屏画面小，不放中心键；全屏常显播放/暂停（播完由居中重播按钮接管）
        if (isFullscreen && !isEnded) {
            CenterPlayButton(
                isPlaying = isPlaying,
                onClick = {
                    haptics.tick()
                    onTogglePlay()
                },
                modifier = Modifier.align(Alignment.Center)
            )
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
                    onClick = {
                        haptics.tick()
                        onTogglePlay()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                        contentDescription = if (isPlaying) pauseDescription else playDescription,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = formatDuration(position),
                    color = Color.White,
                    fontSize = AppTypeScale.Label,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = " / ${formatDuration(duration)}",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = AppTypeScale.Label
                )

                Spacer(modifier = Modifier.weight(1f))

                Box {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            if (isFullscreen) showSettingsSheet = true
                            else showSpeedSheet = true
                        },
                        shape = AppShapes.Large,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = "${selectedSpeed}x",
                            color = Color.White,
                            fontSize = AppTypeScale.Label,
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

                // 选集：只在全屏且确实有多集时出现，紧挨倍速右侧；点开右侧设置面板
                if (isFullscreen && episodes.size > 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            haptics.tick()
                            showSettingsSheet = true
                        },
                        shape = AppShapes.Large,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.player_episodes_n, selectedEpisodeIndex + 1
                            ),
                            color = Color.White,
                            fontSize = AppTypeScale.Label,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (isFullscreen && availableTracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        TextButton(
                            onClick = {
                                haptics.tick()
                                showTrackSheet = true
                            },
                            shape = AppShapes.Large,
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = Color.White.copy(alpha = 0.12f)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = selectedTrack?.label ?: trackAutoLabel,
                                color = Color.White,
                                fontSize = AppTypeScale.Label,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 72.dp)
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
                            haptics.tick()
                            showVolumePanel = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (effectivelyMuted) AppIcons.VolumeOff else AppIcons.VolumeUp,
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
                    onClick = {
                        haptics.tick()
                        onToggleFullscreen()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen)
                            AppIcons.FullscreenExit else AppIcons.Fullscreen,
                        contentDescription = if (isFullscreen) exitFullscreenDescription
                        else enterFullscreenDescription,
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        // 全屏设置：右侧滑出面板 + 可点关闭的遮罩
        AnimatedVisibility(
            visible = showSettingsSheet,
            enter = fadeIn(Motion.Overlay),
            exit = fadeOut(Motion.Overlay)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showSettingsSheet = false }
            )
        }
        AnimatedVisibility(
            visible = showSettingsSheet,
            enter = slideInHorizontally(Motion.overlay<IntOffset>()) { it } +
                fadeIn(Motion.Overlay),
            exit = slideOutHorizontally(Motion.overlay<IntOffset>()) { it } +
                fadeOut(Motion.Overlay),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SettingsSheet(
                resizeMode = resizeMode,
                onResizeModeChange = onResizeModeChange,
                selectedSpeed = selectedSpeed,
                onSpeedChange = onSpeedChange,
                episodes = episodes,
                selectedEpisodeIndex = selectedEpisodeIndex,
                onSelectEpisode = onSelectEpisode,
                autoPlayNext = autoPlayNext,
                onAutoPlayNextChange = onAutoPlayNextChange,
                flipToExitEnabled = flipToExitEnabled,
                onFlipToExitChange = onFlipToExitChange,
                onShowStats = {
                    showSettingsSheet = false
                    onShowPlaybackStats()
                },
                onDismiss = { showSettingsSheet = false }
            )
        }
    }

}

private val SETTINGS_SPEED_OPTIONS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f, 3f)

/** 全屏设置：右侧滑出面板，B 站式标题 + 行式配置 */
@Composable
private fun SettingsSheet(
    resizeMode: ResizeModeOption,
    onResizeModeChange: (ResizeModeOption) -> Unit,
    selectedSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    episodes: List<Episode>,
    selectedEpisodeIndex: Int,
    onSelectEpisode: (Int) -> Unit,
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    flipToExitEnabled: Boolean,
    onFlipToExitChange: (Boolean) -> Unit,
    onShowStats: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(Color(0xFF171214))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.player_settings_title),
                color = Color.White,
                fontSize = AppTypeScale.BodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.player_resize_title),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = AppTypeScale.Label
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResizeModeOption.entries.forEach { option ->
                val selected = option == resizeMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(AppShapes.Small)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .clickable { onResizeModeChange(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (option) {
                            ResizeModeOption.FIT -> stringResource(R.string.player_resize_fit)
                            ResizeModeOption.FILL -> stringResource(R.string.player_resize_fill)
                            ResizeModeOption.ZOOM -> stringResource(R.string.player_resize_zoom)
                        },
                        color = if (selected) Color.White
                        else Color.White.copy(alpha = 0.75f),
                        fontSize = AppTypeScale.Label,
                        fontWeight = if (selected) FontWeight.SemiBold
                        else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
        // 选集：画面比例下方，只在多集时出现；点一集即切换并收起面板
        if (episodes.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.player_episodes),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = AppTypeScale.Label
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                episodes.indices.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { index ->
                            val selected = index == selectedEpisodeIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(AppShapes.Small)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable {
                                        onSelectEpisode(index)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = episodes[index].name.ifBlank {
                                        stringResource(R.string.detail_episode_n, index + 1)
                                    },
                                    color = if (selected) Color.White
                                    else Color.White.copy(alpha = 0.75f),
                                    fontSize = AppTypeScale.Label,
                                    fontWeight = if (selected) FontWeight.SemiBold
                                    else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // 最后一行不足 4 个时用空位撑住，避免格子被拉宽
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.player_speed_title),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = AppTypeScale.Label
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SETTINGS_SPEED_OPTIONS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { speed ->
                        val selected = speed == selectedSpeed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(AppShapes.Small)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.08f)
                                )
                                .clickable { onSpeedChange(speed) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${speed}x",
                                color = if (selected) Color.White
                                else Color.White.copy(alpha = 0.75f),
                                fontSize = AppTypeScale.Label,
                                fontWeight = if (selected) FontWeight.SemiBold
                                else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        SettingsSheetDivider()
        SettingsSheetSwitchRow(
            label = stringResource(R.string.profile_auto_next),
            checked = autoPlayNext,
            onCheckedChange = onAutoPlayNextChange
        )
        SettingsSheetDivider()
        SettingsSheetSwitchRow(
            label = stringResource(R.string.profile_flip_to_exit),
            checked = flipToExitEnabled,
            onCheckedChange = onFlipToExitChange
        )
        SettingsSheetDivider()
        SettingsSheetRow(
            label = stringResource(R.string.player_menu_stats),
            onClick = onShowStats
        )
    }
}

@Composable
private fun SettingsSheetSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = AppTypeScale.Body,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
private fun SettingsSheetRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = AppTypeScale.Body,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = AppTypeScale.Label
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsSheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

/** 只有网络状态用胶囊，时间和电量保持裸文字/图标 */
@Composable
private fun NetworkPill(label: String) {
    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = AppTypeScale.Caption,
            maxLines = 1
        )
    }
}

@Composable
private fun BatteryGlyph(level: Int, charging: Boolean) {
    val icon = when {
        charging -> AppIcons.BatteryChargingFull
        level >= 95 -> AppIcons.BatteryFull
        level >= 80 -> AppIcons.Battery6Bar
        level >= 65 -> AppIcons.Battery5Bar
        level >= 50 -> AppIcons.Battery4Bar
        level >= 35 -> AppIcons.Battery3Bar
        level >= 20 -> AppIcons.Battery2Bar
        level >= 10 -> AppIcons.Battery1Bar
        else -> AppIcons.Battery0Bar
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = when {
            charging -> Color(0xFF6EDC8A)
            level <= 15 -> Color(0xFFFF6B6B)
            else -> Color.White.copy(alpha = 0.9f)
        },
        modifier = Modifier.size(AppIconSize.Compact)
    )
}

/** 播放器玻璃圆钮：半透明玻璃底 + 细描边 + 手绘柔光，按压轻微回弹 */
@Composable
private fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = Motion.PressSpring,
        label = "glassCircleScale"
    )
    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.Center
    ) {
        // 手绘径向柔光：不用 shadow，圆形 Outline 的硬件阴影会露出八边形
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
                }
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.52f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(AppIconSize.Hero)
            )
        }
    }
}

/** 居中的播放/暂停键 */
@Composable
private fun CenterPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(
        if (isPlaying) R.string.common_pause else R.string.common_play
    )
    GlassCircleButton(
        icon = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
        contentDescription = description,
        onClick = onClick,
        modifier = modifier
    )
}

/** 播完（最后一集/关闭自动连播）的居中重播按钮：玻璃圆钮 + 重播文字 */
@Composable
internal fun ReplayEndedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val replayDescription = stringResource(R.string.player_replay)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCircleButton(
            icon = AppIcons.Replay,
            contentDescription = replayDescription,
            onClick = onClick
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = replayDescription,
            color = Color.White,
            fontSize = AppTypeScale.Label,
            fontWeight = FontWeight.Medium
        )
    }
}
