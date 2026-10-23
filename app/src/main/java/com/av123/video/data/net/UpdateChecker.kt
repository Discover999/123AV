package com.av123.video.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 检查更新：读公开仓库的 GitHub Releases 最新一条。
 * 只做「有没有新版」的判断，下载交给浏览器打开 Release 页。
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/Discover999/123AV/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** 只要 tag 与 Release 页地址；更新说明不在应用内展示 */
    data class Release(val tag: String, val pageUrl: String)

    fun check(): Result<Release> = runCatching {
        val request = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val json = JSONObject(response.body.string())
            Release(
                tag = json.optString("tag_name"),
                pageUrl = json.optString("html_url")
            )
        }
    }

    /** 比较 "0.1.12" 与 "0.1.9" 这类点分版本串；无法解析时按「不是新版」处理 */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').mapNotNull { it.toIntOrNull() }
        val b = current.split('.').mapNotNull { it.toIntOrNull() }
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
