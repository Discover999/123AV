package com.av123.video.data.source

import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * 抓流主机白名单：
 * - Embed/详情页：站点 base host + `embedHosts`
 * - 媒体地址：`mediaHosts` + 已信任的 base/embed host
 *
 * 仅接受 HTTPS；支持精确 host 与 `*` 通配（如 `*.true*.site`）。
 * 空配置视为拒绝，避免“配置缺失 = 全放行”。
 *
 * [allowDynamicMediaHosts] 打开时，可信 embed 页发起的真实播放请求可以放行
 * 未列入 [mediaHosts] 的 HTTPS 公网媒体 host；导航仍只认静态白名单。
 */
internal class HostAllowlist(
    baseUrl: String,
    private val embedHosts: List<String>,
    private val mediaHosts: List<String>,
    private val allowDynamicMediaHosts: Boolean = false
) {
    private val baseHost: String? = hostOf(baseUrl)

    fun isAllowedEmbed(url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (!isHttps(url)) return false
        return host == baseHost || matches(embedHosts, host)
    }

    /** 只按静态配置判断媒体 host，供 HTML/console 等无法证明来源的路径使用。 */
    fun isAllowedConfiguredMedia(url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (!isHttps(url)) return false
        return matches(mediaHosts, host) || host == baseHost || matches(embedHosts, host)
    }

    /**
     * 媒体地址信任：
     * - 静态 [mediaHosts] 永远放行；
     * - 未配置的 host 只有来自可信 embed 页、HTTPS 且非本地/私网地址时才放行，
     *   并受 [allowDynamicMediaHosts] 开关控制。
     */
    fun isAllowedMedia(url: String): Boolean = isAllowedMedia(url, null)

    fun isAllowedMedia(url: String, trustedPageOrigin: String?): Boolean {
        val host = hostOf(url) ?: return false
        if (!isHttps(url)) return false
        if (matches(mediaHosts, host) || host == baseHost || matches(embedHosts, host)) {
            return true
        }
        if (!allowDynamicMediaHosts || trustedPageOrigin == null) return false
        if (!isAllowedEmbed(trustedPageOrigin)) return false
        return !isPrivateOrLocalHost(host)
    }

    /** WebView 主帧导航：只认静态 embed/media，动态媒体 host 不允许导航。 */
    fun isAllowedNavigation(url: String): Boolean =
        isAllowedEmbed(url) || isAllowedConfiguredMedia(url)

    /** 选集直连可能是 embed 页，也可能是静态媒体地址。 */
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

    /**
     * 动态媒体 host 的最低网络边界：拒绝 localhost、字面量私网/链路本地/组播地址。
     * 域名解析到私网的情况不在主线程做 DNS 查询，避免阻塞抓流状态机。
     */
    private fun isPrivateOrLocalHost(rawHost: String): Boolean {
        val host = rawHost.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .trimEnd('.')
            .lowercase(Locale.ROOT)
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            return true
        }
        val isIpLiteral = host.contains(':') || IPV4_REGEX.matches(host)
        if (!isIpLiteral) return false

        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return true
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        // Java 的 isSiteLocalAddress 只覆盖 fec0::/10，不覆盖 ULA fc00::/7
        val bytes = address.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    }

    private companion object {
        val IPV4_REGEX = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")
    }
}
