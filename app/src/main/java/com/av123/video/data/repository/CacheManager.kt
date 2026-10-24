package com.av123.video.data.repository

import android.content.Context
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import com.av123.video.data.playback.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** 可单独清理的缓存分区 */
enum class CacheKind { MEDIA, IMAGE, PAGE, OTHER }

data class CacheClearResult(val mediaCacheCleared: Boolean)

/** 总预算应用结果：[mediaCapBytes] 下发后的视频分片预算，[deferred] 表示播放中推迟到下次起播 */
data class CacheBudgetApply(
    val mediaCapBytes: Long,
    val deferred: Boolean,
    val trimmedBytes: Long
)

/** 诊断页缓存占用明细（图片 / ExoPlayer 分片 / 页面 JSON） */
data class CacheUsage(
    val imageBytes: Long,
    val mediaBytes: Long,
    val pageBytes: Long
) {
    val totalBytes: Long get() = imageBytes + mediaBytes + pageBytes
}

/**
 * 存储空间页的四类占用快照。
 * [otherBytes] 是 cacheDir 里除图片、媒体之外的网页缓存与临时文件（保留锁文件）。
 */
data class CacheBreakdown(
    val mediaBytes: Long,
    val mediaCount: Int,
    val imageBytes: Long,
    val imageCount: Int,
    val pageStats: PageCacheStats,
    val webViewBytes: Long,
    val tempBytes: Long,
    val mediaClearable: Boolean
) {
    val pageBytes: Long get() = pageStats.totalBytes
    val otherBytes: Long get() = webViewBytes + tempBytes
    val totalBytes: Long get() = mediaBytes + imageBytes + pageBytes + otherBytes

    fun bytesOf(kind: CacheKind): Long = when (kind) {
        CacheKind.MEDIA -> mediaBytes
        CacheKind.IMAGE -> imageBytes
        CacheKind.PAGE -> pageBytes
        CacheKind.OTHER -> otherBytes
    }

    fun countOf(kind: CacheKind): Int = when (kind) {
        CacheKind.MEDIA -> mediaCount
        CacheKind.IMAGE -> imageCount
        CacheKind.PAGE -> pageStats.feedCount + pageStats.listCount + pageStats.detailCount
        CacheKind.OTHER -> 0
    }

    fun clearableOf(kind: CacheKind): Boolean =
        kind != CacheKind.MEDIA || mediaClearable
}

/**
 * 设置页缓存管理：统计图片、媒体分片、页面缓存与网页临时文件，并提供按类清理。
 * 媒体缓存由 SimpleCache 持有，播放中（含画中画）不能释放，清理时跳过。
 */
class CacheManager(
    private val context: Context,
    private val pageCacheStore: PageCacheStore
) {
    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun breakdown(): CacheBreakdown = withContext(Dispatchers.IO) {
        val cacheRoot = context.cacheDir
        var imageBytes = 0L
        var imageCount = 0
        var mediaBytes = 0L
        var mediaCount = 0
        var webViewBytes = 0L
        var tempBytes = 0L

        // 单次遍历 cacheRoot、按顶层目录归类到四桶，替代旧实现「image/media/webView 各扫一遍、
        // 再为 temp 把整个根目录扫一遍」的重复扫盘；applyDefaultBudget 冷启动最多调用 4 次，
        // 1GB 媒体缓存下重复遍历会被放大成十几轮文件树扫描。
        runCatching {
            cacheRoot.listFiles()?.forEach { top ->
                val files = top.walkTopDown().filter { it.isFile }
                when {
                    top.name == IMAGE_CACHE_DIR -> files.forEach {
                        imageBytes += it.length()
                        // Coil 的 DiskCache 目录里还有一个 journal 文件，不算缓存图片
                        if (!it.name.startsWith("journal")) imageCount++
                    }

                    top.name == MEDIA_CACHE_DIR -> files.forEach {
                        mediaBytes += it.length()
                        if (it.name.endsWith(".exo")) mediaCount++
                    }

                    top.name == WEBVIEW_CACHE_DIR -> files.forEach {
                        webViewBytes += it.length()
                    }

                    else -> files.forEach {
                        tempBytes += it.length()
                    }
                }
            }
        }

        CacheBreakdown(
            mediaBytes = mediaBytes,
            mediaCount = mediaCount,
            imageBytes = imageBytes,
            imageCount = imageCount,
            pageStats = pageCacheStore.stats(),
            webViewBytes = webViewBytes,
            tempBytes = tempBytes,
            mediaClearable = !MediaCache.isPlaybackActive
        )
    }

    suspend fun usage(): CacheUsage = withContext(Dispatchers.IO) {
        val breakdown = breakdown()
        CacheUsage(
            imageBytes = breakdown.imageBytes,
            mediaBytes = breakdown.mediaBytes,
            pageBytes = breakdown.pageBytes
        )
    }

    suspend fun sizeBytes(): Long = usage().totalBytes

    /** 只清理 [kinds] 指定的分区；媒体分区在播放中会被跳过并返回 false */
    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clear(kinds: Set<CacheKind>): CacheClearResult = withContext(Dispatchers.IO) {
        if (CacheKind.IMAGE in kinds) {
            runCatching { context.imageLoader.diskCache?.clear() }
        }
        if (CacheKind.PAGE in kinds) {
            pageCacheStore.clearAll()
        }
        val mediaCleared = if (CacheKind.MEDIA in kinds) {
            MediaCache.tryClear(context)
        } else {
            true
        }
        if (CacheKind.OTHER in kinds) {
            clearOtherFiles()
        }
        CacheClearResult(mediaCacheCleared = mediaCleared)
    }

    suspend fun clear(): CacheClearResult = clear(CacheKind.entries.toSet())

    /**
     * 把固定的 1 GB 总额预算落到四类缓存上：
     * - 总额减去图片/页面/网页临时后的余量全部交给视频分片 LRU，
     *   这样总量始终受一个预算约束，而最大的一类仍按最久未看淘汰；
     * - 非媒体部分自己就超过 1 GB 时，按「页面 → 图片 → 网页临时」的顺序让位。
     * 缓存上限不在 UI 暴露，启动时调用一次即可。
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun applyDefaultBudget(): CacheBudgetApply = withContext(Dispatchers.IO) {
        val before = breakdown()
        var current = before
        fun nonMedia(b: CacheBreakdown): Long = b.imageBytes + b.pageBytes + b.otherBytes
        if (nonMedia(current) > DEFAULT_TOTAL_BUDGET_BYTES) {
            pageCacheStore.clearAll()
            current = breakdown()
        }
        if (nonMedia(current) > DEFAULT_TOTAL_BUDGET_BYTES) {
            runCatching { context.imageLoader.diskCache?.clear() }
            current = breakdown()
        }
        if (nonMedia(current) > DEFAULT_TOTAL_BUDGET_BYTES) {
            clearOtherFiles()
            current = breakdown()
        }
        val mediaCap = (DEFAULT_TOTAL_BUDGET_BYTES - nonMedia(current)).coerceAtLeast(0L)
        MediaCache.setMaxBytes(mediaCap)
        CacheBudgetApply(
            mediaCapBytes = mediaCap,
            deferred = MediaCache.isPlaybackActive,
            trimmedBytes = (before.totalBytes - current.totalBytes).coerceAtLeast(0L)
        )
    }

    /** 网页缓存与临时文件：删掉 cacheDir 下除图片、媒体与锁文件外的内容 */
    private fun clearOtherFiles() {
        context.cacheDir.listFiles()?.forEach { child ->
            if (child.name == IMAGE_CACHE_DIR || child.name == MEDIA_CACHE_DIR) return@forEach
            if (child.name.endsWith(".lck")) return@forEach
            runCatching { child.deleteRecursively() }
        }
    }

    companion object {
        /** 四类缓存合计的固定预算（不再暴露设置项） */
        const val DEFAULT_TOTAL_BUDGET_BYTES = 1L * 1024 * 1024 * 1024

        private const val IMAGE_CACHE_DIR = "image-cache"
        private const val MEDIA_CACHE_DIR = "exo-media"
        private const val WEBVIEW_CACHE_DIR = "WebView"
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
