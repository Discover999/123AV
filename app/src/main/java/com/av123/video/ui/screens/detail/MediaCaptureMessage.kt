package com.av123.video.ui.screens.detail

import com.av123.video.data.source.HostAllowlist
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.Locale

/**
 * JS hook -> Kotlin 的抓流消息。旧版本直接发 URL 字符串，仍按 legacy 兼容；
 * 新版本带 source/requestUrl，用于区分“可信播放请求”和“HTML 里随便出现的地址”。
 */
@Serializable
internal data class CapturedMediaMessage(
    val url: String = "",
    val source: String = "",
    val requestUrl: String = "",
    val page: String = ""
)

private val captureMessageJson = Json { ignoreUnknownKeys = true }

internal fun parseCapturedMediaMessage(raw: String?): CapturedMediaMessage? {
    val text = raw?.trim().orEmpty()
    if (text.isBlank()) return null
    if (!text.startsWith("{")) {
        return text.takeIf { it.contains(".m3u8", ignoreCase = true) }
            ?.let { CapturedMediaMessage(url = it, source = "legacy") }
    }
    return runCatching {
        captureMessageJson.decodeFromString(CapturedMediaMessage.serializer(), text)
    }.getOrNull()
}

/**
 * 媒体地址是否可交给 ExoPlayer：
 * - 静态 `mediaHosts` 直接放行；
 * - 未配置的 host 必须来自可信 embed 页的真实 XHR/fetch 播放请求；
 * - `*-resp` 还要求发起响应的请求本身位于可信 embed 页；
 * - `html`/legacy/空 source 只能命中静态白名单。
 */
internal fun isAllowedCapturedMedia(
    allowlist: HostAllowlist,
    message: CapturedMediaMessage,
    trustedPageOrigin: String?
): Boolean {
    if (message.url.isBlank()) return false
    val trustedSource = isTrustedMediaSource(message.source)
    val trustedResponse = !isTrustedResponseSource(message.source) ||
        (
            message.requestUrl.isNotBlank() &&
                capturedOriginOf(message.requestUrl)
                    ?.let { allowlist.isAllowedEmbed(it) } == true
            )
    return if (trustedSource && trustedResponse) {
        allowlist.isAllowedMedia(message.url, trustedPageOrigin)
    } else {
        allowlist.isAllowedConfiguredMedia(message.url)
    }
}

internal fun isTrustedMediaSource(source: String): Boolean {
    val kind = source.substringBefore('(').trim().lowercase(Locale.ROOT)
    return kind == "xhr-url" ||
        kind == "fetch-url" ||
        kind == "xhr-resp" ||
        kind == "fetch-resp"
}

internal fun isTrustedResponseSource(source: String): Boolean {
    val kind = source.substringBefore('(').trim().lowercase(Locale.ROOT)
    return kind == "xhr-resp" || kind == "fetch-resp"
}

private fun capturedOriginOf(url: String): String? =
    runCatching { URI(url) }.getOrNull()?.let { uri ->
        val scheme = uri.scheme ?: return@let null
        val authority = uri.authority ?: return@let null
        "$scheme://$authority"
    }
