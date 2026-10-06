package com.av123.video.data.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

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
    private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L

    @Volatile
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache {
        return cache ?: run {
            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
            SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
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
            .setUpstreamDataSourceFactory(upstreamFactory)
            // 缓存命中文件损坏/读取异常时不报错，直接走网络
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
