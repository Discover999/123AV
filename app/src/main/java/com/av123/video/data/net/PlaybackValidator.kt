package com.av123.video.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 播放地址的轻量校验：抓到 m3u8 后、用户还没点播放时，用它判断这条地址现在能不能用。
 *
 * 只取清单前 2KB，确认 HTTP 成功且以 `#EXTM3U` 开头——比「等播放器进 READY」便宜得多，
 * 也避免把广告 / 预览 / 已失效的地址写进缓存。请求头沿用抓流时拿到的那一套
 * （Referer / Origin 等），UA 取 App 默认值，和播放器实际请求尽量一致。
 */
object PlaybackValidator {

    private val client: OkHttpClient by lazy {
        HttpClient.client.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun isPlayable(url: String, headers: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(url).get()
                headers.forEach { (key, value) -> builder.header(key, value) }
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val head = response.peekBody(PEEK_BYTES).string().trimStart()
                    head.startsWith("#EXTM3U")
                }
            }.getOrDefault(false)
        }

    private const val PEEK_BYTES = 2048L
}
