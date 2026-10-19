package com.av123.video.data.source

import com.av123.video.data.net.HttpClient
import com.av123.video.data.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

enum class SiteConfigSource {
    /** assets 内置配置 */
    BUNDLED,

    /** filesDir 下已校验的缓存配置 */
    CACHE,

    /** 代码兜底（assets 与缓存都不可用） */
    FALLBACK
}

data class SiteConfigState(
    val config: SiteConfig,
    val source: SiteConfigSource,
    val cachedAt: Long? = null,
    val etag: String? = null,
    val lastCheckedAt: Long? = null,
    val lastError: String? = null
)

@Serializable
private data class CachedSiteConfig(
    val savedAt: Long,
    val config: SiteConfig,
    val etag: String? = null,
    val lastCheckedAt: Long = 0L
)

/** 远程配置 HTTP 响应（测试可注入假实现） */
data class SiteConfigHttpResult(
    val code: Int,
    val body: String,
    val etag: String? = null
)

sealed interface SiteConfigRefreshResult {
    data class Updated(val config: SiteConfig) : SiteConfigRefreshResult
    data object NotModified : SiteConfigRefreshResult
    data object Skipped : SiteConfigRefreshResult
    data class Failed(val message: String) : SiteConfigRefreshResult
}

/**
 * P2.3 站点配置存储：
 *
 * 启动顺序：有效缓存（version 高于内置）→ assets 内置 → 代码兜底。
 * 远程刷新：HTTPS + ETag/304 + 最短刷新间隔 + [SiteConfig.remoteEnabled] kill switch。
 *
 * 远程成功后只更新本地缓存/StateFlow；当前进程的数据源在 P2.3 仍使用启动时配置，
 * 新配置在下次冷启动生效，避免运行中替换仓库造成页面状态撕裂。
 */
class SiteConfigStore(
    private val bundled: SiteConfig,
    private val cacheFile: File,
    private val fetcher: suspend (url: String, etag: String?) -> SiteConfigHttpResult =
        ::fetchRemoteConfig
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val _state = MutableStateFlow(
        SiteConfigState(config = bundled, source = SiteConfigSource.BUNDLED)
    )
    val state: StateFlow<SiteConfigState> = _state.asStateFlow()

    val current: SiteConfig get() = _state.value.config

    init {
        hydrateFromCache()
    }

    /**
     * 拉取远程配置。返回 [SiteConfigRefreshResult.Skipped] 表示 URL 非 HTTPS、
     * kill switch 关闭或仍在 TTL 内。
     */
    suspend fun refreshFromNetwork(
        url: String,
        force: Boolean = false
    ): SiteConfigRefreshResult {
        if (!url.startsWith("https://")) return SiteConfigRefreshResult.Skipped
        val snapshot = _state.value
        if (!snapshot.config.remoteEnabled) return SiteConfigRefreshResult.Skipped
        if (!force && !isRefreshDue(snapshot)) return SiteConfigRefreshResult.Skipped

        val response = runCatchingCancellable { fetcher(url, snapshot.etag) }.getOrElse { error ->
            return fail("远程配置请求失败: ${error.message ?: "未知错误"}")
        }
        return when {
            response.code == 304 -> {
                markChecked()
                SiteConfigRefreshResult.NotModified
            }

            response.code !in 200..399 -> fail("远程配置 HTTP ${response.code}")

            else -> accept(response.body, response.etag).fold(
                onSuccess = { SiteConfigRefreshResult.Updated(it) },
                onFailure = { error ->
                    markChecked()
                    SiteConfigRefreshResult.Failed(error.message ?: "配置校验失败")
                }
            )
        }
    }

    /**
     * 接受一份已下载/已校验的配置并落盘。
     * 版本低于当前配置时拒绝，避免远程旧配置把本地回滚。
     */
    @Synchronized
    fun accept(rawJson: String, etag: String? = null): Result<SiteConfig> = runCatching {
        val config = SiteConfigLoader.decode(rawJson)
        val errors = SiteConfigLoader.validate(config)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        if (config.version < _state.value.config.version) {
            error("配置版本低于当前版本: ${config.version} < ${_state.value.config.version}")
        }

        val savedAt = System.currentTimeMillis()
        writeCache(
            CachedSiteConfig(
                savedAt = savedAt,
                config = config,
                etag = etag,
                lastCheckedAt = savedAt
            )
        )
        _state.value = SiteConfigState(
            config = config,
            source = SiteConfigSource.CACHE,
            cachedAt = savedAt,
            etag = etag,
            lastCheckedAt = savedAt
        )
        config
    }.onFailure { error ->
        _state.value = _state.value.copy(lastError = error.message)
    }

    /** 清空缓存并恢复内置配置 */
    @Synchronized
    fun clearCache() {
        runCatching { cacheFile.delete() }
        _state.value = SiteConfigState(
            config = bundled,
            source = SiteConfigSource.BUNDLED
        )
    }

    private fun isRefreshDue(state: SiteConfigState): Boolean {
        val last = state.lastCheckedAt ?: 0L
        val hours = state.config.remoteRefreshHours.coerceAtLeast(1)
        return System.currentTimeMillis() - last >= hours * 3_600_000L
    }

    private fun markChecked() {
        val now = System.currentTimeMillis()
        readCache()?.let { cached ->
            writeCache(cached.copy(lastCheckedAt = now))
        }
        _state.value = _state.value.copy(lastCheckedAt = now)
    }

    private fun fail(message: String): SiteConfigRefreshResult {
        // 失败也是一次“检查过”：记录时间，避免界面同时出现“未检查”和错误行
        markChecked()
        _state.value = _state.value.copy(lastError = message)
        return SiteConfigRefreshResult.Failed(message)
    }

    private fun hydrateFromCache() {
        if (!cacheFile.isFile) return
        val cached = runCatching {
            json.decodeFromString(CachedSiteConfig.serializer(), cacheFile.readText())
        }.getOrElse { error ->
            invalidate("缓存解析失败: ${error.message}")
            return
        }

        val errors = SiteConfigLoader.validate(cached.config)
        if (errors.isNotEmpty()) {
            invalidate("缓存校验失败: ${errors.joinToString("; ")}")
            return
        }

        when {
            cached.config.version > bundled.version -> {
                _state.value = SiteConfigState(
                    config = cached.config,
                    source = SiteConfigSource.CACHE,
                    cachedAt = cached.savedAt,
                    etag = cached.etag,
                    lastCheckedAt = cached.lastCheckedAt
                )
            }

            cached.config.version == bundled.version -> {
                // 同版本以内置配置为准，但保留缓存里的 ETag/检查时间用于 TTL
                _state.value = SiteConfigState(
                    config = bundled,
                    source = SiteConfigSource.BUNDLED,
                    cachedAt = cached.savedAt,
                    etag = cached.etag,
                    lastCheckedAt = cached.lastCheckedAt
                )
            }

            else -> {
                // App 内置配置已更新到更高版本，丢弃旧缓存
                runCatching { cacheFile.delete() }
            }
        }
    }

    private fun invalidate(reason: String) {
        runCatching { cacheFile.delete() }
        _state.value = SiteConfigState(
            config = bundled,
            source = SiteConfigSource.BUNDLED,
            lastError = reason
        )
    }

    private fun readCache(): CachedSiteConfig? = runCatching {
        if (!cacheFile.isFile) return null
        json.decodeFromString(CachedSiteConfig.serializer(), cacheFile.readText())
    }.getOrNull()

    private fun writeCache(cached: CachedSiteConfig) {
        val parent = cacheFile.absoluteFile.parentFile ?: return
        parent.mkdirs()
        val tmp = File(parent, "${cacheFile.name}.tmp")
        tmp.writeText(json.encodeToString(CachedSiteConfig.serializer(), cached))
        if (!tmp.renameTo(cacheFile)) {
            cacheFile.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

private suspend fun fetchRemoteConfig(
    url: String,
    etag: String?
): SiteConfigHttpResult = withContext(Dispatchers.IO) {
    val headers = if (etag.isNullOrBlank()) {
        emptyMap()
    } else {
        mapOf("If-None-Match" to etag)
    }
    val response = HttpClient.execute(url, headers = headers, timeoutMs = 8_000)
    val responseEtag = response.headers.entries
        .firstOrNull { it.key.equals("ETag", ignoreCase = true) }
        ?.value
    SiteConfigHttpResult(
        code = response.code,
        body = response.body,
        etag = responseEtag
    )
}
