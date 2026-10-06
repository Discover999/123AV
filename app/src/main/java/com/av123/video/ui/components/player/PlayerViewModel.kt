package com.av123.video.ui.components.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.av123.video.data.playback.MediaCache

/**
 * 持有 [ExoPlayer] 实例，跨 Activity 配置变更（旋转、分屏等）时保持播放状态，
 * 避免切换横竖屏时重新加载 m3u8。
 */
class PlayerViewModel : ViewModel() {

    private var player: ExoPlayer? = null
    private var initializedUrl: String? = null
    private var bandwidthMeter: BandwidthMeter? = null

    /** 当前带宽估算（bit/s），<=0 表示尚无估算；用于播放器左上角网速显示 */
    val bitrateEstimate: Long
        @UnstableApi
        get() = bandwidthMeter?.bitrateEstimate ?: Long.MIN_VALUE

    @OptIn(UnstableApi::class)
    fun initialize(context: Context, url: String, headers: Map<String, String>): ExoPlayer {
        val existing = player
        if (existing != null && initializedUrl == url) {
            return existing
        }

        // 地址变化时先释放旧播放器
        releasePlayer()

        val appContext = context.applicationContext
        // 带宽计量器：同时作为 TransferListener 挂到 HTTP 数据源上统计下载字节，
        // 单例在应用内复用，估算值随播放持续收敛
        val meter = DefaultBandwidthMeter.getSingletonInstance(appContext)
        bandwidthMeter = meter

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setTransferListener(meter)
            .setDefaultRequestProperties(headers)
        // 先读本地分片缓存（LRU 512MB），未命中回源并写回；
        // 重复观看/ seek 已缓存区间时不再消耗流量，坏缓存自动忽略回源
        val dataSourceFactory = MediaCache.cacheDataSourceFactory(
            context = appContext,
            upstreamFactory = httpDataSourceFactory
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(dataSourceFactory)

        // 媒体播放音频属性：交由 ExoPlayer 管理音频焦点
        // （与其他音频 App 并发时自动暂停/降低音量、焦点恢复后续播），
        // 并在耳机拔出/蓝牙断开（ACTION_AUDIO_BECOMING_NOISY）时自动暂停
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val newPlayer = ExoPlayer.Builder(appContext)
            .setBandwidthMeter(meter)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
                setHandleAudioBecomingNoisy(true)
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }

        player = newPlayer
        initializedUrl = url
        return newPlayer
    }

    fun releasePlayer() {
        player?.release()
        player = null
        initializedUrl = null
        bandwidthMeter = null
    }

    override fun onCleared() {
        releasePlayer()
    }
}
