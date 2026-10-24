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
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.av123.video.data.playback.MediaCache

/**
 * 持有 [ExoPlayer] 实例，跨 Activity 配置变更（旋转、分屏等）时保持播放状态，
 * 避免切换横竖屏时重新加载 m3u8。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerViewModel : ViewModel() {

    private var player: ExoPlayer? = null

    /** 播放源身份：URL 相同但 Referer/UA 等请求头变化时必须重建播放器 */
    private var initializedKey: MediaKey? = null

    private data class MediaKey(
        val url: String,
        val headers: Map<String, String>
    )

    fun initialize(context: Context, url: String, headers: Map<String, String>): ExoPlayer {
        val key = MediaKey(url, headers.toMap())
        val existing = player
        if (existing != null && initializedKey == key) {
            return existing
        }

        // 地址变化时先释放旧播放器
        releasePlayer()

        val appContext = context.applicationContext
        // 带宽计量器：作为 TransferListener 挂到 HTTP 数据源上估算带宽，
        // 单例在应用内复用，供自适应码率选择使用
        val meter = DefaultBandwidthMeter.getSingletonInstance(appContext)

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
            // 网络抖动/429/5xx 有限重试；403 等签名失效立即失败，交给抓流兜底
            .setLoadErrorHandlingPolicy(RetryLoadErrorHandlingPolicy())

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
        initializedKey = key
        MediaCache.onPlayerCreated()
        return newPlayer
    }

    fun releasePlayer() {
        if (player != null) {
            MediaCache.onPlayerReleased()
        }
        player?.release()
        player = null
        initializedKey = null
    }

    /**
     * 释放指定播放器实例：仅当它仍是当前实例时才真正释放。
     * 换源（缓存命中原地换 URL）时 [initialize] 已先释放旧实例并创建新实例，
     * 旧组合的 onDispose 走到这里时身份不匹配，就不会误释新实例。
     */
    fun releasePlayer(instance: ExoPlayer) {
        if (player === instance) {
            releasePlayer()
        }
    }

    override fun onCleared() {
        releasePlayer()
    }
}
