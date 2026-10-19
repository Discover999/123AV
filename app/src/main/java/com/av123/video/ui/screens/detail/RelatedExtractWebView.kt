package com.av123.video.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.av123.video.LocalAppContainer
import com.av123.video.data.source.HostAllowlist

/**
 * Compose 生命周期壳：WebView、轮询、超时与静默放弃都在 [RelatedResolver]。
 * 页面加载中 start，离开页面时 dispose；本文件不再持有 WebView 逻辑。
 */
@Composable
internal fun RelatedExtractWebView(
    pageUrl: String,
    onExtracted: (html: String) -> Unit,
    onFinished: () -> Unit
) {
    if (pageUrl.isBlank()) return
    val context = LocalContext.current
    val siteConfig = LocalAppContainer.current.siteConfig
    val allowlist = remember(siteConfig) {
        HostAllowlist(siteConfig.baseUrl, siteConfig.embedHosts, siteConfig.mediaHosts)
    }
    val resolver = remember(pageUrl, allowlist) {
        RelatedResolver(
            context = context.applicationContext,
            pageUrl = pageUrl,
            onExtracted = onExtracted,
            onFinished = onFinished,
            allowlist = allowlist
        )
    }

    DisposableEffect(resolver) {
        resolver.start()
        onDispose { resolver.dispose() }
    }
}
