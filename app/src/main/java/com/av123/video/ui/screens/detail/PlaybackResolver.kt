package com.av123.video.ui.screens.detail

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.av123.video.BuildConfig
import com.av123.video.data.model.Video
import com.av123.video.ui.util.AppLog
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

private const val M3U8_TAG = "M3U8Interceptor"
private const val WEB_MESSAGE_OBJECT = "M3U8Bridge"
private const val PAUSE_AFTER_CAPTURE_MS = 3_000L
private const val FALLBACK_DELAY_MS = 12_000L
private const val FAIL_AFTER_FALLBACK_MS = 12_000L

private const val PAUSE_VIDEO_JS = """
(function() {
  document.querySelectorAll('video').forEach(function(v) {
    try { v.muted = true; v.pause && v.pause(); } catch (e) {}
  });
})();
"""

private const val M3U8_HOOK_JS = """
(function() {
  if (window.__m3u8HookInstalled) return;
  window.__m3u8HookInstalled = true;
  function abs(u) {
    try { return new URL(u, location.href).href; } catch (e) { return u; }
  }
  function report(src, text) {
    try {
      if (!text) return;
      var s = String(text).replace(/\\\//g, '/').replace(/\\u0026/gi, '&');
      var re = /https?:[^\s"'<>()\\]*?\.m3u8[^\s"'<>()\\]*/gi;
      var m;
      while ((m = re.exec(s)) !== null) {
        var u = abs(m[0]);
        try {
          if (window.M3U8Bridge && window.M3U8Bridge.postMessage) {
            window.M3U8Bridge.postMessage(u);
          }
        } catch (e) {}
        console.log('[M3U8-JS] ' + src + ': ' + u);
      }
    } catch (e) {}
  }
  var origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(method, url) {
    this.__m3u8Url = url;
    report('xhr-url', url);
    return origOpen.apply(this, arguments);
  };
  var origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function() {
    var xhr = this;
    this.addEventListener('readystatechange', function() {
      if (xhr.readyState === 4) {
        report('xhr-resp(' + xhr.__m3u8Url + ')', xhr.responseText);
      }
    });
    return origSend.apply(this, arguments);
  };
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function() {
      var reqUrl = arguments[0] && (arguments[0].url || arguments[0]);
      report('fetch-url', reqUrl);
      return origFetch.apply(this, arguments).then(function(resp) {
        try {
          resp.clone().text().then(function(t) {
            report('fetch-resp(' + reqUrl + ')', t);
          });
        } catch (e) {}
        return resp;
      });
    };
  }
  function scanHtml() {
    report('html', document.documentElement ? document.documentElement.innerHTML : '');
  }
  scanHtml();
  setTimeout(scanHtml, 1500);
  setTimeout(scanHtml, 4000);
  setTimeout(scanHtml, 8000);
})();
"""

private val STRIP_REQUEST_HEADERS = setOf(
    "range", "accept-encoding", "connection", "content-length", "host"
)

/**
 * P3.1 播放抓流服务：拥有无头 WebView 与完整状态机，Composable 只负责
 * 在进入/离开详情或重试时调用 [start] / [dispose]，不再持有任何 WebView 逻辑。
 */
@SuppressLint("SetJavaScriptEnabled")
class PlaybackResolver(
    private val context: Context,
    private val video: Video,
    private val epoch: Int,
    private val onCaptured: (url: String, headers: Map<String, String>) -> Unit,
    private val onFailed: () -> Unit,
    targetUrl: String? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    private var webView: WebView? = null
    private var webMessageSupported = false

    private val embedUrl = video.embedUrl
    private val detailUrl = video.sourcePageUrl
    private val primaryUrl = targetUrl?.takeIf { it.isNotBlank() }
        ?: embedUrl.ifBlank { detailUrl }
    private val allowDetailFallback = targetUrl == null
    private val allowedOrigins = listOf(primaryUrl, detailUrl)
        .mapNotNull { originOf(it) }
        .toSet()

    fun start() {
        if (!started.compareAndSet(false, true) || primaryUrl.isBlank()) return

        val seenM3U8 = Collections.synchronizedSet(HashSet<String>())
        val captured = AtomicBoolean(false)
        val pauseScheduled = AtomicBoolean(false)
        val reported = AtomicBoolean(false)
        val fallbackDone = AtomicBoolean(false)
        val failureReported = AtomicBoolean(false)

        fun postCallback(block: () -> Unit) {
            mainHandler.post { if (!disposed.get()) block() }
        }

        fun reportFailed(reason: String) {
            if (!captured.get() && failureReported.compareAndSet(false, true)) {
                AppLog.w(M3U8_TAG, "播放地址解析失败: $reason")
                postCallback(onFailed)
            }
        }

        val userAgent = WebSettings.getDefaultUserAgent(context)
        webMessageSupported =
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

        val view = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                hardenForHeadlessWebView()
            }
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

            fun markCaptured() {
                if (captured.compareAndSet(false, true) &&
                    pauseScheduled.compareAndSet(false, true)
                ) {
                    mainHandler.postDelayed({
                        if (disposed.get()) return@postDelayed
                        AppLog.d(M3U8_TAG, "已抓到 m3u8，暂停隐藏播放页的自动播放")
                        evaluateJavascript(PAUSE_VIDEO_JS, null)
                    }, PAUSE_AFTER_CAPTURE_MS)
                }
            }

            fun reportCapturedFromJs(url: String) {
                if (!reported.compareAndSet(false, true)) return
                markCaptured()
                val capturedHeaders = buildMap {
                    if (primaryUrl.isNotBlank()) {
                        put("Referer", primaryUrl)
                        runCatching { java.net.URI(primaryUrl) }.getOrNull()?.let { uri ->
                            if (uri.scheme != null && uri.authority != null) {
                                put("Origin", "${uri.scheme}://${uri.authority}")
                            }
                        }
                    }
                    put("User-Agent", userAgent)
                }
                AppLog.i(
                    M3U8_TAG,
                    "JS hook 抓到的播放地址回调 Media3/ExoPlayer: $url\n请求头: $capturedHeaders"
                )
                postCallback { onCaptured(url, capturedHeaders) }
            }

            fun fallbackToDetailPage(reason: String) {
                if (captured.get()) return
                val canFallback = allowDetailFallback &&
                    embedUrl.isNotBlank() &&
                    detailUrl.isNotBlank() &&
                    detailUrl != embedUrl
                if (canFallback && fallbackDone.compareAndSet(false, true)) {
                    AppLog.w(M3U8_TAG, "$reason，回退加载详情页整页: $detailUrl")
                    loadUrl(detailUrl)
                } else if (!canFallback || fallbackDone.get()) {
                    reportFailed(reason)
                }
            }

            if (webMessageSupported && allowedOrigins.isNotEmpty()) {
                WebViewCompat.addWebMessageListener(
                    this,
                    WEB_MESSAGE_OBJECT,
                    allowedOrigins,
                    object : WebViewCompat.WebMessageListener {
                        override fun onPostMessage(
                            webView: WebView,
                            message: WebMessageCompat,
                            sourceOrigin: Uri,
                            isMainFrame: Boolean,
                            replyProxy: JavaScriptReplyProxy
                        ) {
                            if (!isMainFrame) return
                            val url = message.data?.trim().orEmpty()
                            if (!url.contains(".m3u8", ignoreCase = true)) return
                            AppLog.d(M3U8_TAG, "WebMessage 收到播放地址: $url")
                            postCallback { reportCapturedFromJs(url) }
                        }
                    }
                )
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val msg = consoleMessage.message()
                    if (msg.contains("[M3U8-JS]")) {
                        markCaptured()
                        AppLog.i(M3U8_TAG, "===== JS hook 发现 m3u8 =====\n$msg")
                        if (!webMessageSupported) {
                            val m3u8Url = Regex("""https?://[^\s"'<>()\\]+""")
                                .findAll(msg)
                                .map { it.value }
                                .firstOrNull { it.contains(".m3u8", ignoreCase = true) }
                            if (m3u8Url != null) reportCapturedFromJs(m3u8Url)
                        }
                    } else {
                        AppLog.d(
                            M3U8_TAG,
                            "[console] $msg @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                        )
                    }
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    if (request?.isForMainFrame != true) return false
                    val url = request.url.toString()
                    if (!isAllowedPage(url, allowedOrigins)) {
                        AppLog.w(M3U8_TAG, "阻止跳转到非白名单页面: $url")
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    AppLog.d(M3U8_TAG, "页面开始加载: $url")
                    view?.evaluateJavascript(M3U8_HOOK_JS, null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    AppLog.d(M3U8_TAG, "页面加载完成: $url")
                    view?.evaluateJavascript(M3U8_HOOK_JS, null)
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    AppLog.w(
                        M3U8_TAG,
                        "HTTP ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase} " +
                            "-> ${request?.url}"
                    )
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    AppLog.w(M3U8_TAG, "SSL 校验失败，拒绝加载: ${error?.url}")
                    handler?.cancel()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        AppLog.w(
                            M3U8_TAG,
                            "主帧加载失败: ${error?.errorCode} ${error?.description} " +
                                "-> ${request.url}"
                        )
                        fallbackToDetailPage("播放页主帧加载失败(${error?.description})")
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString().orEmpty()
                    if (url.contains(".m3u8", ignoreCase = true) && seenM3U8.add(url)) {
                        markCaptured()
                        AppLog.i(M3U8_TAG, "===== 拦截到 m3u8 播放链接 =====\n$url")
                        if (reported.compareAndSet(false, true)) {
                            val capturedHeaders = buildMap {
                                request?.requestHeaders?.forEach { (k, v) ->
                                    if (k !in STRIP_REQUEST_HEADERS) put(k, v)
                                }
                                put("User-Agent", userAgent)
                            }
                            AppLog.i(M3U8_TAG, "播放地址回调 Media3/ExoPlayer，请求头: $capturedHeaders")
                            postCallback { onCaptured(url, capturedHeaders) }
                        }
                    }
                    return null
                }
            }

            val headers = mutableMapOf<String, String>()
            if (detailUrl.isNotBlank() && primaryUrl != detailUrl) {
                headers["Referer"] = detailUrl
            }
            val targetDesc = if (allowDetailFallback) "首集" else "选集直连(禁回退)"
            AppLog.i(
                M3U8_TAG,
                "启动 m3u8 拦截：video=${video.id} epoch=$epoch 模式=$targetDesc\n目标页: $primaryUrl"
            )
            loadUrl(primaryUrl, headers)

            val canFallback = allowDetailFallback &&
                embedUrl.isNotBlank() &&
                detailUrl.isNotBlank() &&
                detailUrl != embedUrl
            if (canFallback) {
                mainHandler.postDelayed({
                    if (!captured.get()) {
                        fallbackToDetailPage("嵌入页 ${FALLBACK_DELAY_MS}ms 内未抓到 m3u8")
                    }
                }, FALLBACK_DELAY_MS)
            }

            val failAt = if (canFallback) FALLBACK_DELAY_MS + FAIL_AFTER_FALLBACK_MS
            else FAIL_AFTER_FALLBACK_MS
            mainHandler.postDelayed({
                if (!captured.get()) {
                    reportFailed("超时未拦截到 m3u8（嵌入页与详情页均已尝试）")
                }
            }, failAt)
        }
        webView = view
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        mainHandler.removeCallbacksAndMessages(null)
        if (webMessageSupported) {
            webView?.let { view ->
                runCatching { WebViewCompat.removeWebMessageListener(view, WEB_MESSAGE_OBJECT) }
            }
        }
        webView?.stopLoading()
        webView?.destroy()
        webView = null
    }
}
