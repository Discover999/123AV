package com.av123.video.ui.screens.detail

import android.webkit.WebSettings

/** 取 URL 的 scheme://authority，解析失败返回 null */
internal fun originOf(url: String): String? =
    runCatching { java.net.URI(url) }.getOrNull()?.let { uri ->
        val scheme = uri.scheme ?: return@let null
        val authority = uri.authority ?: return@let null
        "$scheme://$authority"
    }

/**
 * 隐藏 WebView 的最小权限：不允许读本地文件/ContentProvider、不允许脚本弹窗、
 * 禁止 HTTPS 页面加载 HTTP 子资源。
 * 页面仍可正常发 XHR/fetch/媒体请求。
 */
internal fun WebSettings.hardenForHeadlessWebView() {
    allowFileAccess = false
    allowContentAccess = false
    javaScriptCanOpenWindowsAutomatically = false
    setSupportMultipleWindows(false)
    setGeolocationEnabled(false)
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    @Suppress("DEPRECATION")
    allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    allowUniversalAccessFromFileURLs = false
}
