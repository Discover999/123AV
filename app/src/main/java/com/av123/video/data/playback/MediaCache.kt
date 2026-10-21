package com.av123.video.data.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * ExoPlayer 媒体分片缓存（m3u8 的 ts/fmp4 分片与播放列表）。
 *
 * - [SimpleCache] 要求同一缓存目录在进程内只有一个实例（文件锁），故用进程级单例，
 *   应用存活期间不释放；
 * - LRU 淘汰，上限 [MAX_CACHE_BYTES]（512MB），索引落在 Room 之外的独立 SQLite 库
 *   （media3 提供的 [StandaloneDatabaseProvider]，目录 cacheDir/exo-media）；
 * - m3u8 地址常带时效签名，但分片本身一般可复用；缓存读取失败时通过
 *   [CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR] 自动回源，坏缓存不影响播放。
 */
@UnstableApi
object MediaCache {

    private const val CACHE_DIR_NAME = "exo-media"

    /** 预算下发前的初始视频分片上限；1 GB 总额由 CacheManager 启动时下发 */
    const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
    private const val MIN_CACHE_BYTES = 1L * 1024L * 1024L

    @Volatile
    private var cache: SimpleCache? = null

    /** 当前生效的 LRU 上限；由存储空间的「缓存上限」按总额预算动态下发 */
    @Volatile
    private var maxBytes: Long = MAX_CACHE_BYTES

    /** 播放中收到新上限时先记下，等播放器全部释放再重建 */
    private var pendingRebuild = false

    /** 活跃播放器计数：播放/画中画期间禁止释放 SimpleCache */
    private val activePlayers = AtomicInteger(0)

    /** 累计写入缓存的媒体字节数；只增不减，供播放统计按采样窗口求写入速度 */
    private val cacheWriteBytes = AtomicLong(0L)

    val cacheBytesWritten: Long
        get() = cacheWriteBytes.get()

    /** 有播放器持有缓存时不能释放 SimpleCache，存储空间页据此把视频分区标为暂不可清 */
    val isPlaybackActive: Boolean
        get() = activePlayers.get() > 0

    /** 调整视频分片 LRU 上限；SimpleCache 的上限在构造时固定，因此空闲时重建实例 */
    @Synchronized
    fun setMaxBytes(bytes: Long) {
        val target = bytes.coerceAtLeast(MIN_CACHE_BYTES)
        if (target == maxBytes) return
        maxBytes = target
        if (activePlayers.get() > 0) {
            pendingRebuild = true
            return
        }
        releaseLocked()
    }

    fun onPlayerCreated() {
        activePlayers.incrementAndGet()
    }

    fun onPlayerReleased() {
        activePlayers.updateAndGet { current -> maxOf(0, current - 1) }
        if (activePlayers.get() == 0) {
            synchronized(this) {
                if (pendingRebuild) releaseLocked()
            }
        }
    }

    private fun releaseLocked() {
        runCatching { cache?.release() }
        cache = null
        pendingRebuild = false
    }

    /**
     * 清理媒体缓存。正在播放（含画中画）时直接放弃，返回 false；
     * 无播放器时释放 SimpleCache、删除缓存目录，返回 true。
     */
    @Synchronized
    fun tryClear(context: Context): Boolean {
        if (activePlayers.get() > 0) return false
        releaseLocked()
        File(context.applicationContext.cacheDir, CACHE_DIR_NAME).deleteRecursively()
        return true
    }

    @Synchronized
    fun get(context: Context): SimpleCache {
        return cache ?: run {
            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
            SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(maxBytes),
                StandaloneDatabaseProvider(appContext)
            ).also { cache = it }
        }
    }

    /**
     * 构建"先读缓存、未命中回源并写回"的数据源工厂。
     * [upstreamFactory] 为真实 HTTP 工厂（携带防盗链请求头与带宽监听器）。
     */
    fun cacheDataSourceFactory(
        context: Context,
        upstreamFactory: DefaultHttpDataSource.Factory
    ): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(get(context))
            .setCacheWriteDataSinkFactory(
                CountingCacheDataSinkFactory(get(context)) { bytes ->
                    cacheWriteBytes.addAndGet(bytes)
                }
            )
            .setUpstreamDataSourceFactory(upstreamFactory)
            // 缓存命中文件损坏/读取异常时不报错，直接走网络
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}

/**
 * 在 [CacheDataSink] 外层统计真正写入缓存的字节数。
 * SimpleCache.cacheSpace 会随 LRU 淘汰回落，不能反映写入速率；
 * 这里按写入调用累加，缓存满后仍能统计到持续写入。
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class CountingCacheDataSinkFactory(
    private val cache: Cache,
    private val onBytesWritten: (Long) -> Unit
) : DataSink.Factory {

    override fun createDataSink(): DataSink = CountingDataSink(
        delegate = CacheDataSink.Factory().setCache(cache).createDataSink(),
        onBytesWritten = onBytesWritten
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
private class CountingDataSink(
    private val delegate: DataSink,
    private val onBytesWritten: (Long) -> Unit
) : DataSink {

    override fun open(dataSpec: DataSpec) {
        delegate.open(dataSpec)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        delegate.write(buffer, offset, length)
        onBytesWritten(length.toLong())
    }

    override fun close() {
        delegate.close()
    }
}
