package com.av123.video.data.net

import android.os.SystemClock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 检查更新：优先读公开仓库的 GitHub Releases API，失败时退到 releases.atom。
 *
 * 两条都要求带 User-Agent——GitHub 对无 UA 的请求直接 403（OkHttp 不会自动补），
 * 这是之前「检查更新太容易失败」的主因；Atom 那条不受 API 的 60 次/小时限流。
 */
object UpdateChecker {

    private const val REPO = "Discover999/123AV"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val ATOM = "https://github.com/$REPO/releases.atom"
    private const val USER_AGENT = "123AV (Android)"

    /** 同一份结果在 30 秒内复用，避免连点把限流打满 */
    private const val CACHE_WINDOW_MS = 30_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: Pair<Long, Release>? = null

    /** 只要 tag 与 Release 页地址；更新说明不在应用内展示 */
    data class Release(val tag: String, val pageUrl: String)

    fun check(): Result<Release> {
        cached?.let { (at, release) ->
            if (SystemClock.elapsedRealtime() - at < CACHE_WINDOW_MS) {
                return Result.success(release)
            }
        }
        val result = runCatching { fetchApiWithRetry() }
            .recoverCatching { fetchAtom() }
        result.onSuccess { cached = SystemClock.elapsedRealtime() to it }
        return result
    }

    private fun fetchApiWithRetry(): Release {
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            try {
                return fetchApi()
            } catch (t: Throwable) {
                lastError = t
                if (attempt == 0) Thread.sleep(400)
            }
        }
        throw lastError ?: IllegalStateException("update check failed")
    }

    private fun fetchApi(): Release {
        val request = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val json = JSONObject(response.body.string())
            return Release(
                tag = json.optString("tag_name"),
                pageUrl = json.optString("html_url")
            )
        }
    }

    /** 兜底：releases.atom 的第一条就是最新版本，不受 API 限流约束 */
    private fun fetchAtom(): Release {
        val request = Request.Builder()
            .url(ATOM)
            .header("Accept", "application/atom+xml")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val xml = response.body.string()
            val entry = Regex(
                """<entry>.*?<title>([^<]+)</title>.*?href="([^"]+)"""",
                RegexOption.DOT_MATCHES_ALL
            ).find(xml) ?: error("no release entry")
            val tag = entry.groupValues[1].trim()
            if (tag.isBlank()) error("empty tag")
            return Release(tag = tag, pageUrl = entry.groupValues[2].trim())
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
