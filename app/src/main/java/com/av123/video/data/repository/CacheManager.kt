package com.av123.video.data.repository

import android.content.Context
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import com.av123.video.data.playback.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class CacheClearResult(val mediaCacheCleared: Boolean)

/** 诊断页缓存占用明细（图片 / ExoPlayer 分片 / 页面 JSON） */
data class CacheUsage(
    val imageBytes: Long,
    val mediaBytes: Long,
    val pageBytes: Long
) {
    val totalBytes: Long get() = imageBytes + mediaBytes + pageBytes
}

/**
 * 设置页缓存管理：统计图片、媒体分片、页面缓存占用，并提供安全清理。
 * 媒体缓存由 SimpleCache 持有，播放中（含画中画）不能释放，清理时跳过。
 */
class CacheManager(
    private val context: Context,
    private val pageCacheStore: PageCacheStore
) {
    suspend fun usage(): CacheUsage = withContext(Dispatchers.IO) {
        CacheUsage(
            imageBytes = directorySize(File(context.cacheDir, IMAGE_CACHE_DIR)),
            mediaBytes = directorySize(File(context.cacheDir, MEDIA_CACHE_DIR)),
            pageBytes = pageCacheStore.totalBytes()
        )
    }

    suspend fun sizeBytes(): Long = usage().totalBytes

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clear(): CacheClearResult = withContext(Dispatchers.IO) {
        runCatching { context.imageLoader.diskCache?.clear() }
        pageCacheStore.clearAll()
        val mediaCleared = MediaCache.tryClear(context)
        CacheClearResult(mediaCacheCleared = mediaCleared)
    }

    private fun directorySize(dir: File): Long =
        if (!dir.exists()) {
            0L
        } else {
            runCatching {
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
        }

    private companion object {
        const val IMAGE_CACHE_DIR = "image-cache"
        const val MEDIA_CACHE_DIR = "exo-media"
    }
}

/** 缓存大小的可读格式（B / KB / MB / GB） */
internal fun formatCacheSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f MB", mb)
    return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0)
}
