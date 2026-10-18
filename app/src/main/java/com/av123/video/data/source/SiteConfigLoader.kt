package com.av123.video.data.source

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

/** P2 对外统一叫 SiteConfig；实现暂时复用现有 JsoupSiteConfig 字段，避免行为变化 */
typealias SiteConfig = JsoupSiteConfig

/**
 * 站点配置加载器。
 *
 * P2.1 只负责内置 assets 配置；P2.3 再在此基础上加缓存与远程刷新，
 * 当前任何异常都回退到代码默认值，保证首屏不因配置损坏而不可用。
 */
object SiteConfigLoader {

    const val BUNDLED_ASSET = "site-config.json"

    /** 当前 App 能理解的配置结构版本；远程/缓存更高版本必须回退 */
    const val CURRENT_SCHEMA_VERSION = 1

    private const val TAG = "SiteConfig"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** assets 缺失/损坏时的最终兜底：仍指向真实站点，而不是 example.com */
    private val fallback: SiteConfig = SiteConfig(
        baseUrl = "https://123av.com/",
        localePath = "/cn",
        categoryPaths = mapOf(
            "新发布" to "/new",
            "最近添加" to "/recent"
        ),
        searchQueryParam = "keyword"
    )

    fun loadBundled(context: Context): SiteConfig {
        val raw = runCatching {
            context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (raw.isNullOrBlank()) {
            Log.w(TAG, "内置站点配置缺失，使用代码兜底配置")
            return fallback
        }
        val decoded = runCatching { decode(raw) }.getOrElse {
            Log.e(TAG, "内置站点配置解析失败，使用代码兜底配置", it)
            return fallback
        }
        val errors = validate(decoded)
        if (errors.isNotEmpty()) {
            Log.e(TAG, "内置站点配置校验失败，使用代码兜底配置: $errors")
            return fallback
        }
        return decoded
    }

    internal fun decode(raw: String): SiteConfig =
        json.decodeFromString(JsoupSiteConfig.serializer(), raw)

    /** 配置契约校验：错误信息返回给诊断/测试，不在这里抛异常 */
    internal fun validate(config: SiteConfig): List<String> = buildList {
        if (config.schemaVersion <= 0) add("schemaVersion 必须大于 0")
        if (config.schemaVersion > CURRENT_SCHEMA_VERSION) {
            add("schemaVersion 高于当前支持版本: ${config.schemaVersion} > $CURRENT_SCHEMA_VERSION")
        }
        if (config.id.isBlank()) add("id 不能为空")
        if (config.version <= 0) add("version 必须大于 0")
        if (config.remoteRefreshHours <= 0) add("remoteRefreshHours 必须大于 0")
        if (config.cardSelector.isBlank()) add("cardSelector 不能为空")
        if (config.detailTitleSelector.isBlank()) add("detailTitleSelector 不能为空")
        if (config.localePath.isNotEmpty() && !config.localePath.startsWith("/")) {
            add("localePath 必须以 / 开头")
        }

        val base = runCatching { java.net.URI(config.baseUrl) }.getOrNull()
        if (base == null || base.scheme != "https" || base.host.isNullOrBlank()) {
            add("baseUrl 必须是合法 HTTPS 地址")
        }
        config.categoryPaths.forEach { (name, path) ->
            if (name.isBlank() || !path.startsWith("/")) {
                add("categoryPaths 非法: $name=$path")
            }
        }
    }
}
