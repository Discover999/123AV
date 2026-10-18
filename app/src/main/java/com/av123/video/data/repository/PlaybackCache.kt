package com.av123.video.data.repository

import java.util.concurrent.ConcurrentHashMap

/**
 * 已验证可用的播放源：无头 WebView 拦截到的 m3u8 地址 + CDN 防盗链校验
 * 所需请求头（Referer/Origin/User-Agent 等）。
 */
data class PlaybackSource(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * 进程内播放源缓存（"videoId#选集下标" -> m3u8 地址+请求头）。
 * 首集 key 不带后缀，兼容旧调用；仅内存、不持久化，m3u8 签名过期后由
 * 详情页 invalidate 后重新抓流。
 */
class PlaybackCache {
    private val cache = ConcurrentHashMap<String, PlaybackSource>()

    fun get(videoId: String, episodeIndex: Int = 0): PlaybackSource? =
        cache[key(videoId, episodeIndex)]

    fun put(videoId: String, episodeIndex: Int, source: PlaybackSource) {
        cache[key(videoId, episodeIndex)] = source
    }

    fun invalidate(videoId: String, episodeIndex: Int = 0) {
        cache.remove(key(videoId, episodeIndex))
    }

    fun clear() {
        cache.clear()
    }

    private fun key(videoId: String, episodeIndex: Int): String =
        if (episodeIndex <= 0) videoId else "$videoId#$episodeIndex"
}
