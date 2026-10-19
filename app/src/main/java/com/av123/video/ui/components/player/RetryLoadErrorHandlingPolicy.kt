package com.av123.video.ui.components.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

/**
 * 播放器加载重试策略：
 * - 网络 IO 错误与 HTTP 429/5xx 最多重试 [maxRetries] 次；
 * - 403/404 等其余 4xx 立即失败（签名过期/资源不存在，重试无意义）；
 * - 退避 1s、2s、4s，封顶 [maxDelayMs]。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class RetryLoadErrorHandlingPolicy(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 8_000L
) : LoadErrorHandlingPolicy {

    override fun getRetryDelayMsFor(
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): Long {
        val errorCount = loadErrorInfo.errorCount
        if (errorCount <= 0 || errorCount > maxRetries) return C.TIME_UNSET
        if (!isRetryable(loadErrorInfo.exception)) return C.TIME_UNSET
        val delay = baseDelayMs shl (errorCount - 1)
        return delay.coerceAtMost(maxDelayMs)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = maxRetries

    /** 不主动切换清晰度/轨道，只做重试；保持 ExoPlayer 默认选择逻辑 */
    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): LoadErrorHandlingPolicy.FallbackSelection? = null

    private fun isRetryable(exception: IOException): Boolean = when (exception) {
        is HttpDataSource.InvalidResponseCodeException ->
            exception.responseCode == 429 || exception.responseCode in 500..599

        else -> true
    }
}
