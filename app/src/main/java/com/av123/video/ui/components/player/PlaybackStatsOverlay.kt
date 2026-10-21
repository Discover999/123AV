package com.av123.video.ui.components.player

import com.av123.video.ui.theme.AppShapes
import com.av123.video.ui.theme.AppTypeScale

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import com.av123.video.R
import com.av123.video.data.playback.MediaCache
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * 播放统计浮层：类似 YouTube “统计信息 (Stats for nerds)”，
 * 展示当前视频/音频轨道的编码、分辨率、帧率、码率、缓存速度与缓冲健康度，
 * 每 500ms 刷新一次，通过右上角 X 按钮关闭。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlaybackStatsOverlay(
    player: Player,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val frameRateMeter = remember(player) { FrameRateMeter() }
    val cacheSpeedMeter = remember(player) { CacheSpeedMeter() }
    var stats by remember(player) {
        mutableStateOf(readPlaybackStats(context, player, cacheSpeedMBps = null, measuredFrameRate = 0f))
    }

    // HLS 清单通常不声明 FRAME-RATE，Format.frameRate 为 NO_VALUE；
    // 挂到渲染帧回调上按呈现时间实测，作为声明值缺失时的兜底。
    DisposableEffect(player) {
        val exoPlayer = player as? ExoPlayer
        if (exoPlayer == null) {
            onDispose { }
        } else {
            val listener = VideoFrameMetadataListener { presentationTimeUs, _, _, _ ->
                frameRateMeter.onFrame(presentationTimeUs)
            }
            exoPlayer.setVideoFrameMetadataListener(listener)
            onDispose { exoPlayer.clearVideoFrameMetadataListener(listener) }
        }
    }

    LaunchedEffect(player) {
        var cacheSpeedMBps: Float? = null
        while (true) {
            delay(500L.milliseconds)
            cacheSpeedMBps = cacheSpeedMeter.sample()
            stats = readPlaybackStats(
                context = context,
                player = player,
                cacheSpeedMBps = cacheSpeedMBps,
                measuredFrameRate = frameRateMeter.fps
            )
        }
    }

    val videoCells = listOf(
        stringResource(R.string.player_stat_video_codec) to stats.videoCodec,
        stringResource(R.string.player_stat_resolution) to stats.resolution,
        stringResource(R.string.player_stat_frame_rate) to stats.frameRate,
        stringResource(R.string.player_stat_video_bitrate) to stats.videoBitrate
    )
    val audioCells = listOf(
        stringResource(R.string.player_stat_audio_codec) to stats.audioCodec,
        stringResource(R.string.player_stat_audio_sample_rate) to stats.audioSampleRate,
        stringResource(R.string.player_stat_audio_channels) to stats.audioChannels
    )
    val playbackCells = listOf(
        stringResource(R.string.player_stat_cache_speed) to stats.cacheSpeed,
        stringResource(R.string.player_stat_buffer_health) to stats.bufferHealth,
        stringResource(R.string.player_stat_speed) to stats.speed,
        stringResource(R.string.player_stat_state) to stats.state
    )

    // 固定宽度 + 最大高度：底部永远不会越过屏幕，内容多时在卡片内滚动
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .heightIn(max = maxHeight)
                .clip(AppShapes.Medium)
                .background(Color(0xF2171214))
                .border(1.dp, Color.White.copy(alpha = 0.08f), AppShapes.Medium)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.player_stats_title),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = AppTypeScale.Label,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                PopupCloseButton(onClick = onDismiss)
            }
            Spacer(modifier = Modifier.height(6.dp))
            StatGroup(
                title = stringResource(R.string.player_stats_group_video),
                cells = videoCells
            )
            Spacer(modifier = Modifier.height(5.dp))
            StatDivider()
            Spacer(modifier = Modifier.height(5.dp))
            StatGroup(
                title = stringResource(R.string.player_stats_group_audio),
                cells = audioCells
            )
            Spacer(modifier = Modifier.height(5.dp))
            StatDivider()
            Spacer(modifier = Modifier.height(5.dp))
            StatGroup(
                title = stringResource(R.string.player_stats_group_playback),
                cells = playbackCells
            )
        }
    }
}

@Composable
private fun StatGroup(title: String, cells: List<Pair<String, String>>) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium
    )
    Spacer(modifier = Modifier.height(2.dp))
    cells.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row.forEach { (label, value) ->
                StatCell(
                    label = label,
                    value = value,
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(2 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 1.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = AppTypeScale.Caption,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

private data class PlaybackStatsData(
    val videoCodec: String,
    val resolution: String,
    val frameRate: String,
    val videoBitrate: String,
    val cacheSpeed: String,
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
    cacheSpeedMBps: Float?,
    measuredFrameRate: Float
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
    // HLS 清单常不声明 FRAME-RATE，Format.frameRate 为 NO_VALUE，此时用渲染帧实测值兜底
    val frameRate = video?.frameRate?.takeIf { it > 0f }
        ?: measuredFrameRate.takeIf { it > 0.5f }
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
        frameRate = frameRate?.let { "${it.roundToInt()} fps" } ?: "—",
        videoBitrate = video?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "—",
        cacheSpeed = cacheSpeedMBps?.let { String.format(Locale.US, "%.1f M/s", it) } ?: "—",
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

/**
 * 按渲染帧的呈现时间估算内容帧率。
 * 用 presentationTimeUs 而不是墙钟，倍速播放时不会把 2× 误算成两倍帧率。
 */
private class FrameRateMeter {
    private var windowStartUs = 0L
    private var frameCount = 0

    @Volatile
    var fps: Float = 0f
        private set

    fun onFrame(presentationTimeUs: Long) {
        if (windowStartUs == 0L) {
            windowStartUs = presentationTimeUs
            return
        }
        val elapsedUs = presentationTimeUs - windowStartUs
        // 跳转/切换轨道会让呈现时间突跳，丢弃这一窗重新开始
        if (elapsedUs <= 0L || elapsedUs > 2_000_000L) {
            windowStartUs = presentationTimeUs
            frameCount = 0
            return
        }
        frameCount++
        if (elapsedUs >= 1_000_000L) {
            fps = frameCount * 1_000_000f / elapsedUs
            windowStartUs = presentationTimeUs
            frameCount = 0
        }
    }
}

/** 以 1 秒窗口采样缓存写入字节数，避免 500ms 轮询让读数来回跳。 */
@androidx.annotation.OptIn(UnstableApi::class)
private class CacheSpeedMeter {
    private var lastBytes = MediaCache.cacheBytesWritten
    private var lastSampleAtMs = SystemClock.elapsedRealtime()
    private var speedMBps: Float? = null

    fun sample(): Float? {
        val now = SystemClock.elapsedRealtime()
        val elapsedMs = now - lastSampleAtMs
        if (elapsedMs >= 1_000L) {
            val bytes = MediaCache.cacheBytesWritten
            val delta = (bytes - lastBytes).coerceAtLeast(0L)
            speedMBps = delta * 1000f / elapsedMs / 1_048_576f
            lastBytes = bytes
            lastSampleAtMs = now
        }
        return speedMBps
    }
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
