package com.av123.video.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.av123.video.LocalAppContainer
import com.av123.video.data.model.Video
import com.av123.video.data.source.HostAllowlist

/** 详情内容渲染后，延迟多久再启动抓流，错开首帧/封面加载 */
internal const val CAPTURE_WEBVIEW_DELAY_MS = 500L

/**
 * Compose 生命周期壳：真正的 WebView、超时、回退与状态机都在 [PlaybackResolver]。
 * 进入详情/重试时 start，离开时 dispose；本文件不再持有任何 WebView 逻辑。
 */
@Composable
internal fun M3U8CaptureWebView(
    video: Video,
    epoch: Int,
    onCaptured: (url: String, headers: Map<String, String>) -> Unit,
    onFailed: () -> Unit,
    targetUrl: String? = null
) {
    val context = LocalContext.current
    val siteConfig = LocalAppContainer.current.siteConfig
    val allowlist = remember(siteConfig) {
        HostAllowlist(
            baseUrl = siteConfig.baseUrl,
            embedHosts = siteConfig.embedHosts,
            mediaHosts = siteConfig.mediaHosts,
            allowDynamicMediaHosts = siteConfig.allowDynamicMediaHosts
        )
    }
    val resolver = remember(video.id, epoch, targetUrl, allowlist) {
        PlaybackResolver(
            context = context.applicationContext,
            video = video,
            epoch = epoch,
            onCaptured = onCaptured,
            onFailed = onFailed,
            allowlist = allowlist,
            targetUrl = targetUrl
        )
    }

    DisposableEffect(resolver) {
        resolver.start()
        onDispose { resolver.dispose() }
    }
}
