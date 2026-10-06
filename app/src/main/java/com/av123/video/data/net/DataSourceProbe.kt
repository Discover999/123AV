package com.av123.video.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 探测失败的归一化类别：数据层不产中文，文案由 UI 层经 strings.xml 映射 */
enum class ProbeFailureKind { DNS, TIMEOUT, SSL, NETWORK, OTHER }

/** 数据源站点检测结果 */
sealed interface SiteProbe {
    /** 可访问：拿到 HTTP 响应且状态码 < 400（含正常重定向） */
    data class Ok(
        val httpCode: Int,
        val latencyMs: Long,
        val timings: ProbeTimings? = null
    ) : SiteProbe

    /** 网络通但站点返回错误状态码（4xx/5xx） */
    data class HttpError(
        val httpCode: Int,
        val latencyMs: Long,
        val timings: ProbeTimings? = null
    ) : SiteProbe

    /** 无法连接：[kind] 为归一化失败类别（UI 映射中文原因） */
    data class Failed(val kind: ProbeFailureKind) : SiteProbe
}

/** 公网 IP 探测结果 */
sealed interface IpProbe {
    data class Ok(
        val ip: String,
        /** 归属地，如“中国 北京 北京”；缺失字段已过滤 */
        val location: String,
        /** 运营商 / 组织，如“中国电信”；查不到时为空串 */
        val isp: String
    ) : IpProbe

    data class Failed(val kind: ProbeFailureKind) : IpProbe
}

/** 一次完整检测的聚合结果 */
data class DataSourceProbeResult(
    val siteUrl: String,
    val site: SiteProbe,
    val ip: IpProbe,
    val checkedAt: Long
)

/**
 * 数据源可用性探测：
 * - 站点检测：直接 GET 数据源首页（与列表抓取同款 UA），记录 HTTP 状态码与响应耗时；
 * - 网络信息：依次尝试多个 HTTPS 公网 IP 查询服务（任一成功即用），
 *   返回当前公网 IP、归属地与运营商。
 *
 * 两项检测并发执行，互不影响：站点挂了仍能取到 IP，反之亦然。
 * 全部使用 HTTPS（应用网络安全配置默认禁止明文），不依赖额外 HTTP 库，
 * 复用项目已有的 Jsoup 发请求。
 */
class DataSourceProbe(
    private val siteUrl: String,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val timeoutMs: Int = 10_000
) {

    suspend fun probe(): DataSourceProbeResult = coroutineScope {
        val siteDeferred = async { probeSite() }
        val ipDeferred = async { fetchIpInfo() }
        DataSourceProbeResult(
            siteUrl = siteUrl,
            site = siteDeferred.await(),
            ip = ipDeferred.await(),
            checkedAt = System.currentTimeMillis()
        )
    }

    private suspend fun probeSite(): SiteProbe = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        // 诊断分段耗时：EventListener 工厂逐 call 收集 DNS/TCP/TLS/TTFB（重试时取末次）
        val listenerFactory = ProbeEventListenerFactory()
        runCatching {
            // execute 不在 4xx/5xx 时抛异常：要拿到状态码区分“站点异常”与“网络不通”
            val response = HttpClient.execute(
                url = siteUrl,
                headers = mapOf("User-Agent" to userAgent),
                timeoutMs = timeoutMs,
                eventListenerFactory = listenerFactory
            )
            val latencyMs = (System.nanoTime() - start) / 1_000_000L
            val timings = listenerFactory.latest?.snapshot()
            val code = response.code
            if (code in 200..399) SiteProbe.Ok(code, latencyMs, timings)
            else SiteProbe.HttpError(code, latencyMs, timings)
        }.getOrElse { e -> SiteProbe.Failed(failureKind(e)) }
    }

    private suspend fun fetchIpInfo(): IpProbe = withContext(Dispatchers.IO) {
        var lastKind: ProbeFailureKind? = null
        for (provider in IP_PROVIDERS) {
            val result = runCatching {
                val body = HttpClient.getString(
                    url = provider,
                    headers = mapOf("User-Agent" to userAgent),
                    timeoutMs = timeoutMs
                )
                parseIpJson(provider, body)
            }
            val info = result.getOrNull()
            if (info != null) return@withContext info
            if (lastKind == null) lastKind = failureKind(result.exceptionOrNull())
        }
        IpProbe.Failed(lastKind ?: ProbeFailureKind.OTHER)
    }

    /** 按不同服务商的字段结构解析；ip 字段缺失视为失败，尝试下一个服务商 */
    private fun parseIpJson(url: String, body: String): IpProbe? {
        val json = JSONObject(body)
        val ip = when {
            url.startsWith(IPWHOIS_BASE) -> json.optString("ip")
            url.startsWith(IPAPI_BASE) -> json.optString("ip")
            url.startsWith(IPIFY_BASE) -> json.optString("ip")
            else -> ""
        }
        if (ip.isNullOrBlank()) return null

        val locationParts: List<String>
        var isp = ""
        when {
            url.startsWith(IPWHOIS_BASE) -> {
                // ipwho.is：success=false 时只有错误信息，按失败处理并切换备用源
                if (json.optBoolean("success", true).not()) return null
                locationParts = listOf(
                    json.optString("country"),
                    json.optString("region"),
                    json.optString("city")
                )
                isp = json.optJSONObject("connection")?.optString("isp").orEmpty()
            }
            url.startsWith(IPAPI_BASE) -> {
                // ipapi.co 超额时返回 {"error": true}
                if (json.optBoolean("error", false)) return null
                locationParts = listOf(
                    json.optString("country_name"),
                    json.optString("region"),
                    json.optString("city")
                )
                isp = json.optString("org")
            }
            else -> locationParts = emptyList()
        }
        return IpProbe.Ok(
            ip = ip,
            location = locationParts.filter { it.isNotBlank() }.joinToString(" "),
            isp = isp.orEmpty()
        )
    }

    /** 把常见网络异常映射成归一化失败类别（复用全局 [AppError] 口径），中文文案留给 UI */
    private fun failureKind(e: Throwable?): ProbeFailureKind =
        if (e == null) ProbeFailureKind.OTHER
        else when (AppError.from(e).kind) {
            AppErrorKind.DNS -> ProbeFailureKind.DNS
            AppErrorKind.TIMEOUT -> ProbeFailureKind.TIMEOUT
            AppErrorKind.SSL -> ProbeFailureKind.SSL
            AppErrorKind.NETWORK -> ProbeFailureKind.NETWORK
            else -> ProbeFailureKind.OTHER
        }

    private companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // 全部 HTTPS：ipwho.is / ipapi.co 带归属地与运营商，ipify 仅 IP，作为兜底
        const val IPWHOIS_BASE = "https://ipwho.is/"
        const val IPAPI_BASE = "https://ipapi.co/json/"
        const val IPIFY_BASE = "https://api.ipify.org/?format=json"

        val IP_PROVIDERS = listOf(IPWHOIS_BASE, IPAPI_BASE, IPIFY_BASE)
    }
}
