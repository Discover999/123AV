package com.av123.video.data.source

import java.net.URI
import java.util.Locale

/**
 * 抓流主机白名单：
 * - Embed/详情页：站点 base host + `embedHosts`
 * - 媒体地址：`mediaHosts` + 已信任的 base/embed host
 *
 * 仅接受 HTTPS；支持精确 host 与 `*` 通配（如 `*.true*.site`）。
 * 空配置视为拒绝，避免“配置缺失 = 全放行”。
 */
internal class HostAllowlist(
    baseUrl: String,
    private val embedHosts: List<String>,
    private val mediaHosts: List<String>
) {
    private val baseHost: String? = hostOf(baseUrl)

    fun isAllowedEmbed(url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (!isHttps(url)) return false
        return host == baseHost || matches(embedHosts, host)
    }

    fun isAllowedMedia(url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (!isHttps(url)) return false
        return matches(mediaHosts, host) || host == baseHost || matches(embedHosts, host)
    }

    /** WebView 主帧导航：embed/base/media 站点均允许。 */
    fun isAllowedNavigation(url: String): Boolean =
        isAllowedEmbed(url) || isAllowedMedia(url)

    /** 选集直连可能是 embed 页，也可能是媒体地址。 */
    fun isAllowedEmbedOrMedia(url: String): Boolean = isAllowedNavigation(url)

    private fun isHttps(url: String): Boolean =
        runCatching { URI(url).scheme }
            .getOrNull()
            ?.equals("https", ignoreCase = true) == true

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }
            .getOrNull()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }

    private fun matches(patterns: List<String>, host: String): Boolean =
        patterns.any { raw ->
            val pattern = raw.trim().lowercase(Locale.ROOT)
            when {
                pattern.isBlank() -> false
                '*' !in pattern -> host == pattern
                else -> wildcardRegex(pattern).matches(host)
            }
        }

    private fun wildcardRegex(pattern: String): Regex {
        val body = pattern.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex("^$body$", RegexOption.IGNORE_CASE)
    }
}
