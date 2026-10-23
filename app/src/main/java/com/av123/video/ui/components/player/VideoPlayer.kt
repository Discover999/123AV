@file:OptIn(ExperimentalMaterial3Api::class)
package com.av123.video.ui.components.player

import com.av123.video.ui.icons.AppIconSize
import com.av123.video.ui.icons.AppIcons

import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.AppTypeScale

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.av123.video.ui.theme.Motion
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
import com.av123.video.data.model.Episode
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

private const val TAG = "VideoPlayer"
private const val CONTROLLER_AUTO_HIDE_MS = 3_000L
private const val POSITION_POLL_MS = 200L
internal const val ERROR_OVERLAY_ENTER_MS = 160

/** 播放进度落盘间隔：每播放 5 秒回传一次，退出/切后台时再补最后一次 */
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

/** 长按播放器临时倍速（主流 App 的“按住快放、松手恢复”交互） */
private const val LONG_PRESS_SPEED = 2f

/** 横滑 Seek 灵敏度：手指横滑整个画面宽度对应的视频时长占比（拖满整屏 ≈ 80% 时长） */
private const val HORIZONTAL_SEEK_FRACTION = 0.8f

/** 双击左右半屏快退 / 快进的步长（毫秒） */
private const val DOUBLE_TAP_SEEK_STEP_MS = 10_000L

/** 双击中央播放/暂停热区占画面宽度的比例（中间 40%，左右各 30%） */
private const val DOUBLE_TAP_CENTER_ZONE = 0.40f

/** 双击快退/快进反馈浮层的显示时长 */
private const val DOUBLE_TAP_FEEDBACK_MS = 600L

/** 翻转退出：屏幕朝下时重力 z 轴阈值（静止朝下约 -9.8） */
private const val FLIP_TO_EXIT_FACE_DOWN_Z = -7.0f

/** 翻转退出：连续朝下的最短持续时间，避免翻转过程误触 */
private const val FLIP_TO_EXIT_HOLD_MS = 500L

/**
 * 现代风格流媒体播放器。
 *
 * - 基于 Media3 ExoPlayer + HLS，[PlayerViewModel] 持有 ExoPlayer 实例，
 *   横竖屏切换/Activity 重建时不会重新加载 m3u8。
 * - 自定义深色半透明控制层，支持播放/暂停、±10s 跳转、进度拖拽、倍速、音量、全屏。
 * - 长按画面临时 2 倍速播放（按住快放、松手恢复菜单所选倍速），带触感与浮层提示。
 * - 双击画面播放/暂停；单击切换控制层显示（注册双击后单击有约 200ms 判定延迟）。
 * - 自动隐藏控制器，点击视频切换显示。
 * - 加载、缓冲、错误状态均有对应 UI。
 *
 * @param url 播放地址（m3u8）
 * @param headers 请求头（Referer、User-Agent 等）
 * @param title 视频标题，全屏时显示在顶部
 * @param isFullscreen 当前是否全屏
 * @param onFullscreenChange 全屏状态变化回调，由调用方控制 Activity 方向与系统栏
 * @param onPlaybackReady 播放器进入 READY（地址验证可用）时回调，可用于缓存播放地址
 * @param onPlaybackError 播放器发生错误时回调，可用于剔除失效的缓存地址
 * @param startPositionMs 起播位置（ms），来自观看历史；大于 0 时 seek 到该位置续播
 * @param episodeIndex 本播放器实例对应的选集下标（0 起），随进度回调原样上抛，
 *   避免切集瞬间旧实例 onDispose 把旧集进度写到新集名下
 * @param onPlaybackProgress 播放进度回调（选集下标、当前位置、总时长），
 *   周期/退出/切后台时触发，用于持久化观看历史；播放结束时位置回传 0
 * @param onPlayingElapsed 播放墙钟增量回调（ms）：仅 isPlaying 且无错误期间每个轮询 tick
 *   触发，暂停/缓冲/seek 不计；供观看时长统计累计
 * @param onEpisodeEnded 本集播放结束回调：调用方可据此自动连播下一集
 * @param autoPlayNext 片尾自动连播开关（与“我的-播放设置”共用同一份偏好）
 * @param onAutoPlayNextChange 自动连播开关变化回调
 * @param flipToExitEnabled 全屏播放时屏幕朝下是否自动停止并结束应用
 * @param onFlipToExitChange 翻转退出开关变化回调
 * @param pipEnabled 是否允许画中画（用户设置开关）；关闭时 Home/手势回桌面不进入小窗
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    headers: Map<String, String>,
    title: String,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onPlaybackReady: () -> Unit = {},
    onPlaybackError: () -> Unit = {},
    startPositionMs: Long = 0L,
    episodeIndex: Int = 0,
    episodes: List<Episode> = emptyList(),
    onSelectEpisode: (Int) -> Unit = {},
    onPlaybackProgress: (episodeIndex: Int, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
    onPlayingElapsed: (deltaMs: Long) -> Unit = {},
    onEpisodeEnded: () -> Unit = {},
    pipEnabled: Boolean = true,
    autoPlayNext: Boolean = true,
    onAutoPlayNextChange: (Boolean) -> Unit = {},
    flipToExitEnabled: Boolean = false,
    onFlipToExitChange: (Boolean) -> Unit = {},
    resizeMode: ResizeModeOption = ResizeModeOption.FIT,
    onResizeModeChange: (ResizeModeOption) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticFeedback = LocalHapticFeedback.current
    val playerViewModel: PlayerViewModel = viewModel()

    // 初始化/复用 ExoPlayer；ViewModel 保证实例跨配置变更存活
    val player = remember(url, headers) {
        playerViewModel.initialize(context.applicationContext, url, headers)
    }

    // PiP 源区域：AndroidView factory 创建后持有 PlayerView 引用，
    // 供 MainActivity 计算小窗动画的起止矩形（不参与重组，用普通数组做持有器）
    val playerViewHolder = remember { arrayOfNulls<PlayerView>(1) }

    // 用 rememberUpdatedState 持有最新进度回调，周期协程不必因回调变化重启
    val progressCallback by rememberUpdatedState(onPlaybackProgress)
    // 观看时长增量回调同理：随播放/暂停重启的轮询协程始终拿到最新引用
    val playingElapsedCallback by rememberUpdatedState(onPlayingElapsed)

    // 上报当前播放进度；总时长未知（未 READY）时跳过，
    // 播放结束（STATE_ENDED）时位置回传 0，下次进入从头播放
    fun reportProgress() {
        val dur = player.duration.coerceAtLeast(0L)
        if (dur <= 0L) return
        if (player.playbackState == Player.STATE_READY ||
            player.playbackState == Player.STATE_ENDED
        ) {
            val pos = if (player.playbackState == Player.STATE_ENDED) 0L
            else player.currentPosition
            // episodeIndex 取本播放器实例创建时的快照：切集后旧实例 onDispose
            // 仍上报旧集下标，不会把旧集进度写到新集
            progressCallback(episodeIndex, pos, dur)
        }
    }

    // Activity 引用（方向/系统栏/画中画均需要），提前到生命周期监听之前
    val activity = remember(context) { context.findActivity() }

    // 生命周期联动：切后台暂停，返回前台恢复（仅当之前正在播放）
    var wasPlayingOnPause by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // 暂停前先落盘一次进度
                    reportProgress()
                    // 画中画窗口仍可见，保持播放、不记录“待恢复”状态
                    if (activity?.isInPictureInPictureMode == true) return@LifecycleEventObserver
                    wasPlayingOnPause = player.playWhenReady
                    player.playWhenReady = false
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingOnPause) player.playWhenReady = true
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 播放器状态聚合
    // 全部以 player 为 key：换源会换一个播放器实例，旧实例的读数不能残留
    var isPlaying by remember(player) { mutableStateOf(player.playWhenReady) }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var duration by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    // 以 player 为 key：缓存地址失效后重新抓流会换播放器实例，
    // 旧实例上的错误状态不能残留到新实例的错误层上
    var error by remember(player) { mutableStateOf<PlaybackException?>(player.playerError) }
    var bufferedPosition by remember(player) { mutableLongStateOf(player.bufferedPosition) }
    var isControllerVisible by remember { mutableStateOf(true) }
    // 控制层任意触摸都重置 3s 自动隐藏计时
    var controlInteractionTick by remember { mutableIntStateOf(0) }
    var isControlPopupVisible by remember { mutableStateOf(false) }
    // 拖动进度条期间禁止控制层自动隐藏（小圆点离手后再重新计时）
    var isSeeking by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableFloatStateOf(1f) }
    // 是否处于画中画：PiP 中保持播放并隐藏全部控件/手势
    var isInPip by remember { mutableStateOf(false) }
    // 长按画面快放中：临时强制 2x，松手恢复 selectedSpeed。
    // 以 player 为 key：重新抓流更换播放器实例时状态一并复位。
    var isLongPressSpeed by remember(player) { mutableStateOf(false) }
    var availableTracks by remember { mutableStateOf<List<VideoTrack>>(emptyList()) }
    var selectedTrack by remember { mutableStateOf<VideoTrack?>(null) }
    // 播放统计浮层（类似 YouTube “统计信息”）
    var showPlaybackStats by remember { mutableStateOf(false) }
    // 横滑 Seek：松手将跳转到的目标位置（null=未拖动）；以 player 为 key，
    // 重新抓流更换播放器实例时拖动状态一并复位
    var seekPreviewTargetMs by remember(player) { mutableStateOf<Long?>(null) }
    // 横滑起点位置：气泡据此显示快进/快退方向与增量
    var seekPreviewStartMs by remember(player) { mutableLongStateOf(0L) }
    // 双击左右半屏快退/快进反馈：first=方向(-1 左退 / 1 右进)，second=触发时间戳
    // （每次双击都是新 Pair，驱动反馈动画重播）
    var doubleSeekFeedback by remember(player) { mutableStateOf<Pair<Int, Long>?>(null) }
    // 全屏锁定：屏蔽画面手势与控件，只留解锁键
    var isLocked by remember(player) { mutableStateOf(false) }
    // 锁定态解锁键独立计时：锁上后短暂显示，超时隐藏；点画面重新唤出
    var isLockControlVisible by remember(player) { mutableStateOf(false) }
    var lockControlInteractionTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            isLocked = false
            isLockControlVisible = false
        }
    }

    val mainActivity = activity as? MainActivity

    // 画中画：向 Activity 注册准入判断与模式变化监听（受用户设置开关控制）
    DisposableEffect(mainActivity) {
        if (mainActivity != null) {
            mainActivity.pipModeChangedListener = { inPip ->
                isInPip = inPip
                if (inPip) {
                    // 进入小窗：收起所有浮层，只保留画面
                    isControllerVisible = false
                    isControlPopupVisible = false
                    showPlaybackStats = false
                }
            }
            mainActivity.pipEligibilityProvider = {
                pipEnabled && isFullscreen && error == null &&
                    playbackState != Player.STATE_ENDED
            }
            mainActivity.pipSourceRectProvider = {
                val view = playerViewHolder[0]
                if (view == null || !view.isAttachedToWindow) {
                    null
                } else {
                    Rect().takeIf { view.getGlobalVisibleRect(it) }
                }
            }
        }
        onDispose {
            mainActivity?.let {
                it.pipModeChangedListener = null
                it.pipEligibilityProvider = { false }
                it.pipSourceRectProvider = null
                it.updatePipParams(false)
            }
        }
    }

    // 全屏播放态/开关变化时同步 PiP 自动进入参数（Android 12+ 手势 Home）
    LaunchedEffect(isFullscreen, error, playbackState, pipEnabled) {
        mainActivity?.updatePipParams(
            pipEnabled && isFullscreen && error == null && playbackState != Player.STATE_ENDED
        )
    }

    // onEpisodeEnded 用最新引用，避免播放器实例监听器持有旧回调
    val episodeEndedCallback by rememberUpdatedState(onEpisodeEnded)

    // 监听播放器事件
    DisposableEffect(player) {
        // 每个播放器实例只在首次进入 ENDED 时回调一次自动连播，
        // 避免状态抖动重复触发；重新 READY（用户重播/重新缓冲）后复位
        var endedNotified = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                duration = player.duration.coerceAtLeast(0L)
                bufferedPosition = player.bufferedPosition
                // READY 表示主播放列表/分片已可正常加载，地址验证可用
                if (state == Player.STATE_READY) {
                    endedNotified = false
                    onPlaybackReady()
                }
                // 本集播放结束：交由调用方决定自动连播下一集（末集不动作，显示重播）
                if (state == Player.STATE_ENDED && !endedNotified) {
                    endedNotified = true
                    episodeEndedCallback()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                position = player.currentPosition
            }

            override fun onPlayerErrorChanged(e: PlaybackException?) {
                error = e
                AppLog.e(TAG, "播放失败: ${e?.errorCodeName} ${e?.message}")
                if (e != null) onPlaybackError()
            }

            override fun onTracksChanged(tracks: Tracks) {
                availableTracks = parseVideoTracks(tracks)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 轮询播放位置；同时以墙钟口径累计“真正播放中”的时长增量（观看统计用）。
    // 协程以 isPlaying/playbackState 为 key 重启：暂停/缓冲/结束期间协程取消，
    // lastTick 复位，恢复播放后不会把暂停期间补偿计入；单次增量钳制为 2 个轮询间隔，
    // 防止主线程卡顿或 tick 漂移造成虚高。
    // player 也是 key：换源后必须重启轮询，否则协程还在读已 release 的旧实例，
    // 进度条与时间会一直停在切换前（2026-10-23 换集后进度条卡住）
    LaunchedEffect(player, isPlaying, playbackState) {
        var lastTickMs = SystemClock.elapsedRealtime()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (isPlaying && error == null) {
                val elapsedMs = (now - lastTickMs).coerceIn(0L, POSITION_POLL_MS * 2L)
                if (elapsedMs > 0L) playingElapsedCallback(elapsedMs)
            }
            lastTickMs = now
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            bufferedPosition = player.bufferedPosition
            delay(POSITION_POLL_MS.milliseconds)
        }
    }

    // 续播：历史位置异步就绪或播放器实例更换（重新抓流）后 seek 到上次位置
    LaunchedEffect(player, startPositionMs) {
        if (startPositionMs > 0L) player.seekTo(startPositionMs)
    }

    // 周期性落盘播放进度
    LaunchedEffect(player) {
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            reportProgress()
        }
    }

    // 离开页面 / 播放器实例被替换：补存最后一次进度
    DisposableEffect(player) {
        onDispose { reportProgress() }
    }

    // 控制器自动隐藏（弹窗打开或拖动进度条期间保持常显）
    LaunchedEffect(
        isControllerVisible,
        isControlPopupVisible,
        isPlaying,
        playbackState,
        isSeeking,
        controlInteractionTick
    ) {
        if (isControllerVisible && !isControlPopupVisible && !isSeeking &&
            isPlaying && playbackState == Player.STATE_READY
        ) {
            delay(CONTROLLER_AUTO_HIDE_MS.milliseconds)
            if (!isControlPopupVisible && !isSeeking) {
                isControllerVisible = false
            }
        }
    }

    // 锁定态解锁键也在超时后隐藏，避免一直压在画面上
    LaunchedEffect(isLocked, isLockControlVisible, lockControlInteractionTick) {
        if (isLocked && isLockControlVisible) {
            delay(CONTROLLER_AUTO_HIDE_MS.milliseconds)
            if (isLocked) isLockControlVisible = false
        }
    }

    // 隐私：全屏播放时屏幕朝下持续一段时间，立即暂停并结束任务
    DisposableEffect(flipToExitEnabled, isFullscreen, isInPip, error, player) {
        val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val hostActivity = activity
        if (!flipToExitEnabled || !isFullscreen || isInPip || error != null ||
            sensorManager == null || gravitySensor == null || hostActivity == null
        ) {
            onDispose { }
        } else {
            var faceDownSinceMs = 0L
            var triggered = false
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (triggered) return
                    val activelyPlaying = player.playWhenReady &&
                        player.playbackState != Player.STATE_ENDED
                    if (!activelyPlaying) {
                        faceDownSinceMs = 0L
                        return
                    }
                    if (event.values[2] < FLIP_TO_EXIT_FACE_DOWN_Z) {
                        val now = SystemClock.elapsedRealtime()
                        if (faceDownSinceMs == 0L) faceDownSinceMs = now
                        if (now - faceDownSinceMs >= FLIP_TO_EXIT_HOLD_MS) {
                            triggered = true
                            player.pause()
                            hostActivity.finishAndRemoveTask()
                        }
                    } else {
                        faceDownSinceMs = 0L
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(
                listener,
                gravitySensor,
                SensorManager.SENSOR_DELAY_UI,
                Handler(Looper.getMainLooper())
            )
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    // 长按快放：按住期间强制 2x；松手或播放出错时恢复倍速菜单所选速度
    LaunchedEffect(isLongPressSpeed, error) {
        if (isLongPressSpeed && error == null) {
            player.setPlaybackSpeed(LONG_PRESS_SPEED)
        } else {
            player.setPlaybackSpeed(selectedSpeed)
        }
    }

    // 返回键：锁定态先解锁，未锁定再退出全屏
    BackHandler(enabled = isFullscreen) {
        if (isLocked) {
            isLocked = false
            isLockControlVisible = false
            isControllerVisible = true
        } else {
            onFullscreenChange(false)
        }
    }

    Box(
        modifier = modifier
            // 自动隐藏计时挂在播放器根部：控件层若铺满 pointerInput，
            // 会盖住下面的单击/长按/横滑手势层
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    controlInteractionTick++
                }
            }
    ) {
        // 渲染层
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    // 参数名 resizeMode 与 PlayerView 属性同名，显式 this 消歧
                    this.resizeMode = resizeMode.toMedia3ResizeMode()
                    this.player = player
                }.also { playerViewHolder[0] = it }
            },
            // update 只改属性、不重建 factory（避免 SurfaceView 重建黑帧），
            // 但 URL 变化时 remember 会换一个新的 ExoPlayer 实例，必须重新绑到同一个视图上：
            // 否则 PlayerView 仍指向已 release 的旧实例，画面黑、无声，
            // 而新实例照样回调 READY——换回播过的集会一直卡住（2026-10-23）
            update = { playerView ->
                playerView.resizeMode = resizeMode.toMedia3ResizeMode()
                if (playerView.player !== player) playerView.player = player
            },
            // 离开组合（如全屏/竖屏切换导致分支重建）时解绑 player，
            // 避免销毁的 PlayerView 继续持有 surface；重建时由 factory 重新绑定
            onRelease = {
                playerViewHolder[0] = null
                it.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // 播放/暂停；已播放结束（末集播完）时改为从头重播
        fun togglePlayOrReplay() {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
                player.playWhenReady = true
            } else {
                player.playWhenReady = !player.playWhenReady
            }
        }

        // 手势层：单击切换控制器；双击左/右半屏快退/快进 10 秒（中央区域播放/暂停，
        // 末集播完时双击中央为重播）；横滑画面 Seek 并显示时间预览气泡；
        // 长按画面临时 2 倍速，松手恢复。画中画小窗 / 出错层显示时不响应手势。
        // 单击/双击/长按与横滑分别挂在独立 pointerInput 上，Compose 手势系统可并行分发。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isInPip, isLocked) {
                    if (isInPip) return@pointerInput
                    if (isLocked) {
                        detectTapGestures(
                            onTap = {
                                isLockControlVisible = true
                                lockControlInteractionTick++
                            }
                        )
                        return@pointerInput
                    }
                    detectTapGestures(
                        onTap = { isControllerVisible = !isControllerVisible },
                        onDoubleTap = { tapOffset ->
                            // 出错时双击不做播放切换（错误层有独立重试按钮）
                            if (error == null) {
                                val widthPx = size.width.toFloat()
                                val x = tapOffset.x.coerceIn(0f, widthPx)
                                val leftBoundary = widthPx * (0.5f - DOUBLE_TAP_CENTER_ZONE / 2f)
                                val rightBoundary = widthPx * (0.5f + DOUBLE_TAP_CENTER_ZONE / 2f)
                                when {
                                    // 左半屏：快退 10 秒（已到开头则停在 0）
                                    duration > 0L && x < leftBoundary -> {
                                        val target = (position - DOUBLE_TAP_SEEK_STEP_MS)
                                            .coerceAtLeast(0L)
                                        player.seekTo(target)
                                        position = target
                                        doubleSeekFeedback = -1 to System.currentTimeMillis()
                                    }
                                    // 右半屏：快进 10 秒（已到结尾则停在 duration）
                                    duration > 0L && x > rightBoundary -> {
                                        val target = (position + DOUBLE_TAP_SEEK_STEP_MS)
                                            .coerceAtMost(duration)
                                        player.seekTo(target)
                                        position = target
                                        doubleSeekFeedback = 1 to System.currentTimeMillis()
                                    }
                                    // 中央：播放/暂停（末集播完为重播），显示控制层给即时反馈
                                    else -> {
                                        togglePlayOrReplay()
                                        isControllerVisible = true
                                    }
                                }
                            }
                        },
                        onLongPress = {
                            // 仅正常播放中启用快放；暂停/出错时长按不响应，避免误导
                            if (error == null && isPlaying) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                isLongPressSpeed = true
                            }
                        },
                        onPress = {
                            // 等待手指松开（或手势被取消，如移出热区）；
                            // 长按触发的快放统一在此恢复，保证任何路径下速度都能复位
                            tryAwaitRelease()
                            if (isLongPressSpeed) isLongPressSpeed = false
                        }
                    )
                }
                .pointerInput(isLocked) {
                    // 横滑 Seek：累积横向位移换算成目标时间，拖动中只显示预览气泡，
                    // 抬手才真正 seekTo（主流 App 交互，避免拖动中途频繁跳转）
                    var accumulatedDx = 0f
                    var startPositionMs = 0L
                    var active = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulatedDx = 0f
                            startPositionMs = position
                            active = false
                        },
                        onDragEnd = {
                            if (active) {
                                seekPreviewTargetMs?.let { target ->
                                    player.seekTo(target)
                                    position = target
                                }
                                isSeeking = false
                            }
                            seekPreviewTargetMs = null
                            active = false
                        },
                        onDragCancel = {
                            if (active) isSeeking = false
                            seekPreviewTargetMs = null
                            active = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (isInPip || error != null || isLocked ||
                                duration <= 0L
                            ) {
                                return@detectHorizontalDragGestures
                            }
                            if (!active) {
                                active = true
                                // 拖动期间禁止控制层自动隐藏（复用进度条拖动标记）
                                isSeeking = true
                                seekPreviewStartMs = startPositionMs
                            }
                            accumulatedDx += dragAmount
                            val widthPx = size.width.toFloat().coerceAtLeast(1f)
                            val deltaMs =
                                accumulatedDx / widthPx * duration * HORIZONTAL_SEEK_FRACTION
                            seekPreviewTargetMs =
                                (startPositionMs + deltaMs.toLong()).coerceIn(0L, duration)
                        }
                    )
                }
        )

        // 横滑 Seek 读数：小胶囊，贴在进度条上方并跟随拖动位置
        val seekTarget = seekPreviewTargetMs
        if (seekTarget != null && duration > 0L) {
            val fraction = (seekTarget.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            Layout(
                content = { SeekTimeBubble(timeText = formatDuration(seekTarget)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = if (isControllerVisible) 84.dp else 46.dp
                    ),
                measurePolicy = { measurables, constraints ->
                    val bubble = measurables.first().measure(Constraints())
                    layout(constraints.maxWidth, bubble.height) {
                        val centerX = (constraints.maxWidth * fraction).roundToInt()
                        val x = (centerX - bubble.width / 2)
                            .coerceIn(0, (constraints.maxWidth - bubble.width).coerceAtLeast(0))
                        bubble.placeRelative(x, 0)
                    }
                }
            )
        }

        // 全屏且控件隐藏时横滑：只在底部浮出一条细进度条，不展开整个底栏
        AnimatedVisibility(
            visible = isFullscreen && isSeeking && !isControllerVisible && duration > 0L,
            enter = fadeIn(Motion.Overlay),
            exit = fadeOut(Motion.Overlay),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                MiniSeekBar(
                    position = seekPreviewTargetMs ?: position,
                    duration = duration,
                    bufferedPosition = bufferedPosition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                )
            }
        }

        // 双击左右半屏快退/快进的动画反馈（顶部居中，和长按 2× 提示同位置，避免被手指遮挡）
        val feedback = doubleSeekFeedback
        if (feedback != null) {
            LaunchedEffect(feedback) {
                delay(DOUBLE_TAP_FEEDBACK_MS.milliseconds)
                doubleSeekFeedback = null
            }
            DoubleSeekFeedback(
                direction = feedback.first,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isFullscreen) 56.dp else 12.dp)
            )
        }

        // 加载动画：初始准备中；出错后 state 会回到 IDLE，必须显式排除错误态
        if (error == null && (
                playbackState == Player.STATE_IDLE ||
                    (playbackState == Player.STATE_BUFFERING && position <= 0L)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // 缓冲中提示（错误层显示时不再转圈）
        AnimatedVisibility(
            visible = error == null &&
                playbackState == Player.STATE_BUFFERING && position > 0L,
            enter = fadeIn(Motion.Overlay),
            exit = fadeOut(Motion.Overlay),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
        }

        // 错误层
        if (error != null) {
            ErrorOverlay(
                error = error,
                onRetry = {
                    error = null
                    player.prepare()
                    player.playWhenReady = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 控制层（长按快放/画中画期间隐藏，避免按钮与浮层重叠）
        AnimatedVisibility(
            visible = isControllerVisible && error == null && !isLongPressSpeed &&
                !isInPip && !isLocked,
            enter = fadeIn(Motion.Overlay) + slideInVertically(Motion.overlay<IntOffset>()) { 8 },
            exit = fadeOut(Motion.Overlay) + slideOutVertically(Motion.overlay<IntOffset>()) { 8 },
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControls(
                title = title,
                isFullscreen = isFullscreen,
                isPlaying = isPlaying,
                position = seekPreviewTargetMs ?: position,
                duration = duration,
                bufferedPosition = bufferedPosition,
                selectedSpeed = selectedSpeed,
                availableTracks = availableTracks,
                selectedTrack = selectedTrack,
                episodes = episodes,
                selectedEpisodeIndex = episodeIndex,
                onSelectEpisode = onSelectEpisode,
                onTitleBack = { onFullscreenChange(false) },
                onTogglePlay = {
                    togglePlayOrReplay()
                    if (!isControllerVisible) isControllerVisible = true
                },
                onSeekBack = { player.seekTo(max(0L, position - 10_000L)) },
                onSeekForward = { player.seekTo(min(duration, position + 10_000L)) },
                onSeek = { player.seekTo(it) },
                onSeekDragStateChange = { isSeeking = it },
                onSpeedChange = {
                    selectedSpeed = it
                    player.setPlaybackSpeed(it)
                },
                onTrackSelected = { track ->
                    selectedTrack = track
                    applyTrackSelection(player, track)
                },
                onControlPopupVisibleChange = { isControlPopupVisible = it },
                onToggleFullscreen = { onFullscreenChange(!isFullscreen) },
                onShowPlaybackStats = { showPlaybackStats = true },
                autoPlayNext = autoPlayNext,
                onAutoPlayNextChange = onAutoPlayNextChange,
                flipToExitEnabled = flipToExitEnabled,
                onFlipToExitChange = onFlipToExitChange,
                resizeMode = resizeMode,
                onResizeModeChange = onResizeModeChange,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 全屏锁定键：左右各放一个，任一侧被挖孔/刘海遮住时仍可操作；
        // 锁定态下解锁键也超时隐藏，点画面重新唤出
        val showLockControl = if (isLocked) isLockControlVisible else isControllerVisible
        if (isFullscreen && showLockControl && !isControlPopupVisible) {
            val onLockClick: () -> Unit = {
                if (isLocked) {
                    isLocked = false
                    isLockControlVisible = false
                    isControllerVisible = true
                } else {
                    isLocked = true
                    isLockControlVisible = true
                    lockControlInteractionTick++
                }
            }
            LockButton(
                locked = isLocked,
                onClick = onLockClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
            )
            LockButton(
                locked = isLocked,
                onClick = onLockClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
            )
        }

        // 播放统计信息浮层（类似 YouTube Stats for nerds），点击 X 关闭
        if (showPlaybackStats && error == null && !isInPip) {
            PlaybackStatsOverlay(
                player = player,
                onDismiss = { showPlaybackStats = false },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = if (isFullscreen) 56.dp else 12.dp
                    )
            )
        }

        // 长按快放提示：顶部居中的紧凑小胶囊（不遮暗画面），松手即消失
        AnimatedVisibility(
            visible = isLongPressSpeed && error == null,
            enter = fadeIn(Motion.Overlay),
            exit = fadeOut(Motion.Overlay),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isFullscreen) 56.dp else 12.dp)
        ) {
            LongPressSpeedBadge(speed = LONG_PRESS_SPEED)
        }
    }
}

/** 全屏且控件隐藏时，拖动只在底部显示的细进度条（不可交互，跟随预览位置） */
@Composable
private fun MiniSeekBar(
    position: Long,
    duration: Long,
    bufferedPosition: Long,
    modifier: Modifier = Modifier
) {
    val safeDuration = duration.coerceAtLeast(1L)
    val fraction = (position.toFloat() / safeDuration).coerceIn(0f, 1f)
    val buffered = (bufferedPosition.toFloat() / safeDuration).coerceIn(0f, 1f)
    BoxWithConstraints(modifier = modifier.height(24.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.28f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(buffered)
                    .background(Color.White.copy(alpha = 0.38f))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(
                    x = (maxWidth * fraction - 7.dp)
                        .coerceIn(0.dp, maxWidth - 14.dp)
                )
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** 全屏锁定键：左右各一枚，锁定时是唯一可点目标，点一下解锁 */
@Composable
private fun LockButton(
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (locked) AppIcons.Lock else AppIcons.LockOpen,
            contentDescription = stringResource(
                if (locked) R.string.player_unlock else R.string.player_lock
            ),
            tint = Color.White,
            modifier = Modifier.size(AppIconSize.Inline)
        )
    }
}
