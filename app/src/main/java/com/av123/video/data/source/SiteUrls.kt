package com.av123.video.data.source

import java.net.URI
import java.net.URLDecoder

/**
 * 纯 JVM 的 URL 小工具：不依赖 android.net.Uri，
 * 使解析器逻辑可在本地单元测试（JVM）中直接运行。
 */
internal object SiteUrls {

    /** 读取查询参数值（自动百分号解码）；URL 非法或无此参数返回 null */
    fun queryParameter(url: String?, name: String): String? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val rawQuery = URI(url).rawQuery ?: return null
            rawQuery.split('&').firstNotNullOfOrNull { pair ->
                val idx = pair.indexOf('=')
                val key = if (idx >= 0) pair.substring(0, idx) else pair
                when {
                    key != name -> null
                    idx >= 0 -> URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    else -> ""
                }
            }
        }.getOrNull()
    }

    /** URL 的 host（小写不做转换，由调用方自行忽略大小写比较）；非法返回 null */
    fun host(url: String): String? =
        runCatching { URI(url).host }.getOrNull()

    /** 取 path + query（如 /cn/v/abc?page=2），用于同站链接规范化 */
    fun pathAndQuery(url: String): String {
        val uri = URI(url)
        return uri.rawPath.orEmpty() + (uri.rawQuery?.let { "?$it" } ?: "")
    }
}
