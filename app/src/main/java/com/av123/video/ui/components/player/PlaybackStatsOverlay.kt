package com.av123.video.ui.components.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.av123.video.R
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * 播放统计浮层：类似 YouTube “统计信息 (Stats for nerds)”，
 * 展示当前视频/音频轨道的编码、分辨率、帧率、码率、下载网速与缓冲健康度，
 * 每 500ms 刷新一次，通过右上角 X 按钮关闭。
 */
@Composable
internal fun PlaybackStatsOverlay(
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
