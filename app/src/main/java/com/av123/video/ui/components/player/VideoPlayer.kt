@file:OptIn(ExperimentalMaterial3Api::class)
package com.av123.video.ui.components.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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

private const val TAG = "VideoPlayer"
private const val CONTROLLER_AUTO_HIDE_MS = 3_000L
private const val POSITION_POLL_MS = 200L

/** 应用内画面比例枚举 → media3 AspectRatioFrameLayout 常量 */
private fun ResizeModeOption.toMedia3ResizeMode(): Int = when (this) {
    ResizeModeOption.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    ResizeModeOption.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    ResizeModeOption.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}

/** 播放进度落盘间隔：每播放 5 秒回传一次，退出/切后台时再补最后一次 */
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

/** 长按播放器临时倍速（主流 App 的“按住快放、松手恢复”交互） */
private const val LONG_PRESS_SPEED = 2f

/** 横滑 Seek 灵敏度：手指横滑整个画面宽度对应的视频时长占比（拖满整屏 ≈ 80% 时长） */
private const val HORIZONTAL_SEEK_FRACTION = 0.8f

/** 双击左右半屏快退 / 快进的步长（毫秒） */
private const val DOUBLE_TAP_SEEK_STEP_MS = 10_000L

/** 双击中央播放/暂停热区占画面宽度的比例（中间 24%） */
private const val DOUBLE_TAP_CENTER_ZONE = 0.24f

/** 双击快退/快进反馈浮层的显示时长 */
private const val DOUBLE_TAP_FEEDBACK_MS = 600L

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
 * @param pipEnabled 是否允许画中画（用户设置开关）；关闭时 Home/手势回桌面不进入小窗
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    headers: Map<String, String>,
    title: String,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onPlaybackReady: () -> Unit = {},
    onPlaybackError: () -> Unit = {},
    startPositionMs: Long = 0L,
    episodeIndex: Int = 0,
    onPlaybackProgress: (episodeIndex: Int, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
    onPlayingElapsed: (deltaMs: Long) -> Unit = {},
    onEpisodeEnded: () -> Unit = {},
    pipEnabled: Boolean = true,
    resizeMode: ResizeModeOption = ResizeModeOption.FIT,
    onResizeModeChange: (ResizeModeOption) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticFeedback = LocalHapticFeedback.current
    val playerViewModel: PlayerViewModel = viewModel()

    // 初始化/复用 ExoPlayer；ViewModel 保证实例跨配置变更存活
    val player = remember(url, headers) {
        playerViewModel.initialize(context.applicationContext, url, headers)
    }

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
    var isPlaying by remember { mutableStateOf(player.playWhenReady) }
    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    // 以 player 为 key：缓存地址失效后重新抓流会换播放器实例，
    // 旧实例上的错误状态不能残留到新实例的错误层上
    var error by remember(player) { mutableStateOf<PlaybackException?>(player.playerError) }
    var bufferedPosition by remember { mutableLongStateOf(player.bufferedPosition) }
    var isControllerVisible by remember { mutableStateOf(true) }
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
        }
        onDispose {
            mainActivity?.let {
                it.pipModeChangedListener = null
                it.pipEligibilityProvider = { false }
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
    LaunchedEffect(isPlaying, playbackState) {
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
    LaunchedEffect(isControllerVisible, isControlPopupVisible, isPlaying, playbackState, isSeeking) {
        if (isControllerVisible && !isControlPopupVisible && !isSeeking &&
            isPlaying && playbackState == Player.STATE_READY
        ) {
            delay(CONTROLLER_AUTO_HIDE_MS.milliseconds)
            if (!isControlPopupVisible && !isSeeking) {
                isControllerVisible = false
            }
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

    // 全屏/退出全屏时同步系统 UI
    FullscreenSystemUiSync(isFullscreen)

    // 返回键：全屏时退出全屏
    BackHandler(enabled = isFullscreen) {
        onFullscreenChange(false)
    }

    Box(modifier = modifier) {
        // 渲染层
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    // 参数名 resizeMode 与 PlayerView 属性同名，显式 this 消歧
                    this.resizeMode = resizeMode.toMedia3ResizeMode()
                    this.player = player
                }
            },
            // 切换画面比例只走 update：同一 PlayerView 改 resizeMode，
            // 绝不重新 factory 或重绑 player（防止 SurfaceView 重建黑帧）
            update = { playerView ->
                playerView.resizeMode = resizeMode.toMedia3ResizeMode()
            },
            // 离开组合（如全屏/竖屏切换导致分支重建）时解绑 player，
            // 避免销毁的 PlayerView 继续持有 surface；重建时由 factory 重新绑定
            onRelease = { it.player = null },
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
                .pointerInput(isInPip) {
                    if (isInPip) return@pointerInput
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
                .pointerInput(Unit) {
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
                            if (isInPip || error != null || duration <= 0L) return@detectHorizontalDragGestures
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

        // 横滑 Seek 时间预览气泡：方向图标 + 目标时间 + 增量/总时长
        val seekTarget = seekPreviewTargetMs
        if (seekTarget != null) {
            val isForward = seekTarget >= seekPreviewStartMs
            val deltaMs = kotlin.math.abs(seekTarget - seekPreviewStartMs)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isForward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
                    contentDescription = stringResource(R.string.player_seek_preview),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatDuration(seekTarget),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${if (isForward) "+" else "-"}${formatDuration(deltaMs)}" +
                        " / ${formatDuration(duration)}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }

        // 双击左右半屏快退/快进的动画反馈（显示在被点击一侧）
        val feedback = doubleSeekFeedback
        if (feedback != null) {
            LaunchedEffect(feedback) {
                delay(DOUBLE_TAP_FEEDBACK_MS.milliseconds)
                doubleSeekFeedback = null
            }
            DoubleSeekFeedback(
                direction = feedback.first,
                modifier = Modifier.align(
                    if (feedback.first < 0) Alignment.CenterStart else Alignment.CenterEnd
                )
            )
        }

        // 加载动画：初始准备中
        if (playbackState == Player.STATE_IDLE ||
            (playbackState == Player.STATE_BUFFERING && position <= 0L)
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

        // 缓冲中提示
        AnimatedVisibility(
            visible = playbackState == Player.STATE_BUFFERING && position > 0L,
            enter = fadeIn(),
            exit = fadeOut(),
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
            visible = isControllerVisible && error == null && !isLongPressSpeed && !isInPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControls(
                title = title,
                isFullscreen = isFullscreen,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                bufferedPosition = bufferedPosition,
                playbackUrl = url,
                selectedSpeed = selectedSpeed,
                availableTracks = availableTracks,
                selectedTrack = selectedTrack,
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
                resizeMode = resizeMode,
                onResizeModeChange = onResizeModeChange,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 播放统计信息浮层（类似 YouTube Stats for nerds），点击 X 关闭
        if (showPlaybackStats && error == null && !isInPip) {
            PlaybackStatsOverlay(
                player = player,
                bitrateEstimateProvider = { playerViewModel.bitrateEstimate },
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
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isFullscreen) 56.dp else 12.dp)
        ) {
            LongPressSpeedBadge(speed = LONG_PRESS_SPEED)
        }
    }
}

/**
 * 长按快放提示：顶部居中的紧凑胶囊（快进图标 + “2X”），
 * 半透明深色底，尺寸尽量小，不遮挡画面主体。
 */
@Composable
private fun LongPressSpeedBadge(speed: Float) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${speed.formatSpeed()}X",
            color = Color.White,
            fontSize = 12.sp,
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
            animation = tween(durationMillis = 700, easing = LinearEasing),
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
                imageVector = Icons.Filled.ChevronRight,
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
private fun Float.formatSpeed(): String =
    if (this % 1f == 0f) toInt().toString() else toString()

/**
 * 播放器控制层：顶部返回/标题 + 中央播放按钮 + 底部进度/倍速/音量/全屏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControls(
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

@Composable
private fun ControlCircleButton(
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
private fun PlayerProgressSlider(
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
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120))
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

/**
 * 拖动进度条时跟随小圆点显示目标时间的气泡：
 * 深色圆角底 + 单行白色时间文案，尺寸按文案内容自适应。
 */
@Composable
private fun SeekTimeBubble(
    timeText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = timeText,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * 双击左/右半屏快退快进的反馈浮层：圆形深色底 + 10 秒跳转图标，
 * 出现时带一次弹性放大动画；由调用方对齐到画面左侧 / 右侧。
 *
 * @param direction -1=快退（左），1=快进（右）
 */
@Composable
private fun DoubleSeekFeedback(
    direction: Int,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(direction) {
        scale.snapTo(0.6f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    val isRewind = direction < 0
    Box(
        modifier = modifier
            .padding(horizontal = 28.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isRewind) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "10 秒",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
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
private fun ErrorOverlay(
    error: PlaybackException?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Replay5,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.player_error_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = playbackErrorText(LocalContext.current, error),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

@Composable
private fun VolumePanel(
    volume: Int,
    maxVolume: Int,
    isMuted: Boolean,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
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
private fun VerticalVolumeBar(
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
            fontSize = 11.sp,
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
private fun SpeedPopupMenu(
    expanded: Boolean,
    selected: Float,
    onSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
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
                        fontSize = 13.sp
                    )
                },
                onClick = { onSelected(speed) }
            )
        }
    }
}

@Composable
private fun TrackPopupMenu(
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
                    fontSize = 13.sp
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
                        fontSize = 13.sp
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
private fun PlayerSettingsButton(
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
private fun SettingsMenuContent(
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
            .clip(RoundedCornerShape(16.dp))
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
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = resizeLabel,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
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
                        fontSize = 13.sp,
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
                            fontSize = 13.sp,
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
private fun SettingsMenuRow(label: String, onClick: () -> Unit) {
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
            fontSize = 13.sp
        )
    }
}

/**
 * 当前播放链接（m3u8）弹窗：链接为可点击超链接（拉起浏览器），
 * 支持一键复制，右上角 X 关闭。
 */
@Composable
private fun PlaybackUrlContent(
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
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Box(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.86f))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.player_menu_url),
                    color = Color.White,
                    fontSize = 13.sp,
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
                fontSize = 11.sp,
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
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/** 弹窗右上角通用关闭按钮 */
@Composable
private fun PopupCloseButton(onClick: () -> Unit) {
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

/**
 * 播放统计浮层：类似 YouTube “统计信息 (Stats for nerds)”，
 * 展示当前视频/音频轨道的编码、分辨率、帧率、码率、下载网速与缓冲健康度，
 * 每 500ms 刷新一次，通过右上角 X 按钮关闭。
 */
@Composable
private fun PlaybackStatsOverlay(
    player: Player,
    bitrateEstimateProvider: () -> Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var stats by remember {
        mutableStateOf(readPlaybackStats(context, player, bitrateEstimateProvider()))
    }
    LaunchedEffect(player) {
        while (true) {
            stats = readPlaybackStats(context, player, bitrateEstimateProvider())
            delay(500L.milliseconds)
        }
    }

    val rows = listOf(
        stringResource(R.string.player_stat_video_codec) to stats.videoCodec,
        stringResource(R.string.player_stat_resolution) to stats.resolution,
        stringResource(R.string.player_stat_frame_rate) to stats.frameRate,
        stringResource(R.string.player_stat_video_bitrate) to stats.videoBitrate,
        stringResource(R.string.player_stat_download_speed) to stats.downloadSpeed,
        stringResource(R.string.player_stat_audio_codec) to stats.audioCodec,
        stringResource(R.string.player_stat_audio_sample_rate) to stats.audioSampleRate,
        stringResource(R.string.player_stat_audio_channels) to stats.audioChannels,
        stringResource(R.string.player_stat_buffer_health) to stats.bufferHealth,
        stringResource(R.string.player_stat_speed) to stats.speed,
        stringResource(R.string.player_stat_state) to stats.state
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // 宽度由最宽的统计行决定（IntrinsicSize.Max），避免标题行 weight(1f)
        // 在包裹型父布局中被拉伸到播放器全宽，导致背景铺满整屏
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.player_stats_title),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                PopupCloseButton(onClick = onDismiss)
            }
            Spacer(modifier = Modifier.height(4.dp))
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = label,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(76.dp)
                    )
                    Text(
                        text = value,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private data class PlaybackStatsData(
    val videoCodec: String,
    val resolution: String,
    val frameRate: String,
    val videoBitrate: String,
    val downloadSpeed: String,
    val audioCodec: String,
    val audioSampleRate: String,
    val audioChannels: String,
    val bufferHealth: String,
    val speed: String,
    val state: String
)

@androidx.annotation.OptIn(UnstableApi::class)
private fun readPlaybackStats(
    context: Context,
    player: Player,
    bitrateEstimate: Long
): PlaybackStatsData {
    val video = selectedTrackFormat(player, C.TRACK_TYPE_VIDEO)
    val audio = selectedTrackFormat(player, C.TRACK_TYPE_AUDIO)
    val videoSize = player.videoSize

    val resolution = when {
        videoSize.width > 0 && videoSize.height > 0 -> "${videoSize.width}×${videoSize.height}"
        video != null && video.width > 0 -> "${video.width}×${video.height}"
        else -> "—"
    }
    val bufferMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
    // 带宽估算单位为 bit/s，换算为 MB/s（÷8÷1024÷1024）
    val speedMBps = if (bitrateEstimate > 0L) bitrateEstimate / 8f / 1_048_576f else 0f
    val stateText = when {
        player.isPlaying -> context.getString(R.string.player_state_playing)
        player.playbackState == Player.STATE_BUFFERING ->
            context.getString(R.string.player_state_buffering)
        player.playbackState == Player.STATE_ENDED ->
            context.getString(R.string.player_state_ended)
        player.playbackState == Player.STATE_IDLE ->
            context.getString(R.string.player_state_idle)
        else -> context.getString(R.string.player_state_paused)
    }

    return PlaybackStatsData(
        videoCodec = video?.codecs ?: video?.sampleMimeType?.substringAfterLast('/') ?: "—",
        resolution = resolution,
        frameRate = video?.frameRate?.takeIf { it > 0f }?.let { "${it.roundToInt()} fps" } ?: "—",
        videoBitrate = video?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "—",
        downloadSpeed = String.format(Locale.US, "%.1f M/s", speedMBps),
        audioCodec = audio?.codecs ?: audio?.sampleMimeType?.substringAfterLast('/') ?: "—",
        audioSampleRate = audio?.sampleRate?.takeIf { it > 0 }?.let {
            String.format(Locale.US, "%.1f kHz", it / 1000f)
        } ?: "—",
        audioChannels = audio?.channelCount?.takeIf { it > 0 }?.let {
            context.getString(R.string.player_audio_channels_fmt, it)
        } ?: "—",
        bufferHealth = String.format(Locale.US, "%.1f s", bufferMs / 1000f),
        speed = "${player.playbackParameters.speed}x",
        state = stateText
    )
}

private fun selectedTrackFormat(player: Player, trackType: Int): Format? {
    player.currentTracks.groups.forEach { group ->
        if (group.type != trackType) return@forEach
        for (i in 0 until group.length) {
            if (group.isTrackSelected(i)) return group.getTrackFormat(i)
        }
    }
    return null
}

@Composable
private fun CapsulePopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.Black.copy(alpha = 0.86f)
    ) {
        content()
    }
}

/**
 * 根据全屏状态与屏幕方向同步 Activity 方向与系统栏。
 */
@Composable
private fun FullscreenSystemUiSync(isFullscreen: Boolean) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }

    // 全屏状态或屏幕方向变化时，重新同步系统栏与 Activity 方向
    LaunchedEffect(isFullscreen, configuration.orientation) {
        activity?.let { setFullscreen(it, isFullscreen) }
    }

    // 播放器离开组合（退出页面）时恢复竖屏与系统栏，避免状态残留
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { setFullscreen(it, false) }
        }
    }
}

private fun setFullscreen(activity: Activity, fullscreen: Boolean) {
    val window = activity.window
    val controller = WindowCompat.getInsetsController(window, window.decorView)

    if (fullscreen) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    } else {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        controller.show(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
    }
}

// ------------------------------------------------------------------------------------------------
// 清晰度轨道选择
// ------------------------------------------------------------------------------------------------

private data class VideoTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val height: Int,
    val bitrate: Int,
    val label: String
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
private fun parseVideoTracks(tracks: Tracks): List<VideoTrack> {
    val result = mutableListOf<VideoTrack>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_VIDEO) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            val height = format.height
            if (height <= 0) continue
            val bitrate = format.bitrate
            val label = buildString {
                append(height)
                append("P")
                if (bitrate > 0) {
                    append(" ")
                    append(bitrate / 1_000_000)
                    append("Mbps")
                }
            }
            result.add(VideoTrack(groupIndex, trackIndex, height, bitrate, label))
        }
    }
    // 按清晰度从高到低排列
    return result.sortedByDescending { it.height }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
private fun applyTrackSelection(player: ExoPlayer, track: VideoTrack?) {
    val trackSelector = player.trackSelector as? DefaultTrackSelector ?: return
    val builder = trackSelector.parameters.buildUpon()
    if (track == null) {
        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    } else {
        val groups = trackSelector.currentMappedTrackInfo?.getTrackGroups(C.TRACK_TYPE_VIDEO)
        val group = groups?.get(track.groupIndex) ?: return
        val override = TrackSelectionOverride(group, listOf(track.trackIndex))
        builder.setOverrideForType(override)
    }
    trackSelector.parameters = builder.build()
}
