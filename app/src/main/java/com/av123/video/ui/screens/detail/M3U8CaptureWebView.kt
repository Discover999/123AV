package com.av123.video.ui.screens.detail

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.av123.video.BuildConfig
import com.av123.video.data.model.Video
import com.av123.video.ui.util.AppLog
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

private const val M3U8_TAG = "M3U8Interceptor"

/** 详情内容渲染后，延迟多久再创建拦截 WebView（错开首帧/封面加载，避免卡顿） */
internal const val CAPTURE_WEBVIEW_DELAY_MS = 500L

/**
 * 抓到 m3u8 后多久暂停隐藏播放页：等主播放列表与码率/分片清单都请求完
 * （实测二者间隔 <1s），再静音暂停所有 <video>，避免隐藏页自动播放占用解码器/出声
 */
private const val PAUSE_AFTER_CAPTURE_MS = 3_000L

/** 暂停隐藏页内所有视频元素（m3u8 已抓到，后续播放没有意义） */
private const val PAUSE_VIDEO_JS = """
(function() {
  document.querySelectorAll('video').forEach(function(v) {
    try { v.muted = true; v.pause && v.pause(); } catch (e) {}
  });
})();
"""

/**
 * 注入播放页的兜底脚本：
 * - hook XMLHttpRequest.open / send 与 window.fetch：既报请求 URL，也扫描响应体
 *   （很多播放器先请求 /api/xxx JSON，m3u8 藏在响应里，请求 URL 本身不含 .m3u8）
 * - 加载后定时扫描 DOM 里的 m3u8 字面量
 * 命中后 console.log，由 WebChromeClient 转发到 Logcat。
 * 主拦截手段是 shouldInterceptRequest，此脚本用于漏网情况双保险。
 */
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
        console.log('[M3U8-JS] ' + src + ': ' + abs(m[0]));
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

/**
 * 无头（headless）m3u8 拦截器：详情页数据加载后，用 applicationContext 创建
 * WebView 并加载播放器 iframe 嵌入页（[Video.embedUrl]，携带 Referer=详情页，
 * 模拟站内外链环境），但**永不挂到视图树**——JS 执行、网络请求、
 * shouldInterceptRequest 全部照常工作，却不参与任何渲染/合成，
 * 因此不会产生黑帧、也不可能遮挡 Compose 页面（此前 1dp 可见 WebView
 * 首次创建时初始化 Chromium GPU 上下文，会把内容区顶成 1-2s 黑屏）。
 * 12 秒内未抓到任何 m3u8 时自动回退加载详情页整页。
 *
 * 拦截手段，任一命中即可在 Logcat 看到 m3u8 地址：
 * 1. shouldInterceptRequest —— WebView 全部资源请求（XHR/fetch/iframe/媒体都经过）
 * 2. 注入 JS hook XMLHttpRequest / fetch 的请求 URL 与响应体（m3u8 常藏在 API JSON 里）
 * 3. onPageFinished 后定时正则扫描页面 HTML 中的 m3u8 字面量
 *
 * 只观察不干预：shouldInterceptRequest 返回 null，请求正常放行。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun M3U8CaptureWebView(
    video: Video,
    epoch: Int,
    onCaptured: (url: String, headers: Map<String, String>) -> Unit,
    onFailed: () -> Unit,
    /**
     * 指定选集的 iframe 播放页地址（非首集切换时传入）。
     * 为 null 时走默认路径：首集 [Video.embedUrl]，抓不到再回退详情页整页。
     * 显式指定时**不回退详情页**——详情页只会播首集，回退反而抓错集。
     */
    targetUrl: String? = null
) {
    val embedUrl = video.embedUrl
    val detailUrl = video.sourcePageUrl
    // 优先指定集播放页，其次首集 iframe 播放页（m3u8 由该页 JS 直接发起请求），
    // 都解析不到则直接加载详情页
    val primaryUrl = targetUrl?.takeIf { it.isNotBlank() } ?: embedUrl.ifBlank { detailUrl }
    if (primaryUrl.isBlank()) return
    // 仅默认首集路径允许回退详情页整页；指定选集直连时回退会抓到首集，禁止
    val allowDetailFallback = targetUrl == null

    val appContext = LocalContext.current.applicationContext

    // epoch 变化（用户重试）时销毁旧 WebView 并重新加载播放页
    DisposableEffect(video.id, epoch) {
        val mainHandler = Handler(Looper.getMainLooper())
        // 同一地址只打一次日志，避免媒体播放列表轮询刷屏
        val seenM3U8 = Collections.synchronizedSet(HashSet<String>())
        // 是否已抓到 m3u8（请求拦截或 JS hook 任一命中），用于决定是否回退加载详情页
        val captured = AtomicBoolean(false)
        // 抓到后暂停隐藏页播放的任务是否已安排（只安排一次）
        val pauseScheduled = AtomicBoolean(false)
        // 首个 m3u8（主播放列表）是否已回调给 ViewModel
        val reported = AtomicBoolean(false)
        // 是否已回退加载过详情页整页（超时回退或主帧错误回退，只做一次）
        val fallbackDone = AtomicBoolean(false)
        // 失败回调是否已上报（超时/主帧错误只通知一次）
        val failureReported = AtomicBoolean(false)

        // 嵌入页与详情页都没能抓到 m3u8：通知 UI 展示失败态与重试入口
        fun reportFailed(reason: String) {
            if (!captured.get() && failureReported.compareAndSet(false, true)) {
                AppLog.w(M3U8_TAG, "播放地址解析失败: $reason")
                mainHandler.post { onFailed() }
            }
        }
        // UA 在主线程提前取好：shouldInterceptRequest 运行在 Chromium 后台线程，
        // 其中严禁调用任何 WebView 实例方法（settings 等），否则线程检查崩溃
        val userAgent = WebSettings.getDefaultUserAgent(appContext)

        // headless WebView：applicationContext 创建、不加入任何视图层级
        val webView = WebView(appContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            // 仅 debug 包允许 chrome://inspect 远程调试播放页，release 关闭避免泄露抓流过程
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

            // 首次抓到 m3u8：3 秒后静音暂停页内所有 <video>，
            // 等主播放列表与码率清单都请求完再停，不影响拦截
            fun onCaptured() {
                if (captured.compareAndSet(false, true) && pauseScheduled.compareAndSet(false, true)) {
                    mainHandler.postDelayed({
                        AppLog.d(M3U8_TAG, "已抓到 m3u8，暂停隐藏播放页的自动播放")
                        evaluateJavascript(PAUSE_VIDEO_JS, null)
                    }, PAUSE_AFTER_CAPTURE_MS)
                }
            }

            // 嵌入页抓不到时回退加载详情页整页（超时或主帧错误共用，只回退一次）；
            // 没有可回退地址或已回退过，则直接判定解析失败
            fun fallbackToDetailPage(reason: String) {
                if (captured.get()) return
                val canFallback = allowDetailFallback &&
                    embedUrl.isNotBlank() &&
                    detailUrl.isNotBlank() && detailUrl != embedUrl
                if (canFallback && fallbackDone.compareAndSet(false, true)) {
                    AppLog.w(M3U8_TAG, "$reason，回退加载详情页整页: $detailUrl")
                    val headers = mutableMapOf<String, String>()
                    if (detailUrl.isNotBlank()) headers["Referer"] = detailUrl
                    loadUrl(detailUrl, headers)
                } else if (!canFallback || fallbackDone.get()) {
                    reportFailed(reason)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val msg = consoleMessage.message()
                    if (msg.contains("[M3U8-JS]")) {
                        onCaptured()
                        AppLog.i(M3U8_TAG, "===== JS hook 发现 m3u8 =====\n$msg")
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
                        // 主帧错误（断网/404/站点不可达）：嵌入页挂了立即回退详情页，
                        // 详情页也挂了则直接判定解析失败，不用傻等超时
                        fallbackToDetailPage("播放页主帧加载失败(${error?.description})")
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString().orEmpty()
                    if (url.contains(".m3u8", ignoreCase = true) && seenM3U8.add(url)) {
                        onCaptured()
                        AppLog.i(M3U8_TAG, "===== 拦截到 m3u8 播放链接 =====\n$url")
                        // 首个 m3u8 即主播放列表：连同请求头（Referer/Origin 等）回调给
                        // ViewModel 供 Media3/ExoPlayer 使用——CDN 多按 Referer 防盗链，
                        // 分片请求也必须带这些头。Range/Host 等由播放器自行管理，剔除
                        if (reported.compareAndSet(false, true)) {
                            val capturedHeaders = buildMap {
                                request?.requestHeaders?.forEach { (k, v) ->
                                    if (k !in STRIP_REQUEST_HEADERS) put(k, v)
                                }
                                put("User-Agent", userAgent)
                            }
                            AppLog.i(M3U8_TAG, "播放地址回调 Media3/ExoPlayer，请求头: $capturedHeaders")
                            onCaptured(url, capturedHeaders)
                        }
                    }
                    return null
                }
            }

            // iframe 嵌入页正常运行时 Referer 是父页面（站点详情页），
            // 部分播放源校验 Referer，缺失会直接返回“无法抓取播放地址”
            val headers = mutableMapOf<String, String>()
            if (detailUrl.isNotBlank()) headers["Referer"] = detailUrl
            val targetDesc = if (allowDetailFallback) "首集" else "选集直连(禁回退)"
            AppLog.i(
                M3U8_TAG,
                "启动 m3u8 拦截：video=${video.id} epoch=$epoch 模式=$targetDesc\n目标页: $primaryUrl"
            )
            loadUrl(primaryUrl, headers)

            val canFallback = allowDetailFallback &&
                embedUrl.isNotBlank() &&
                detailUrl.isNotBlank() && detailUrl != embedUrl

            // 嵌入页 12 秒仍未抓到：回退加载详情页整页，让 iframe 在原生环境自行初始化
            if (canFallback) {
                mainHandler.postDelayed({
                    if (!captured.get()) {
                        fallbackToDetailPage("嵌入页 ${FALLBACK_DELAY_MS}ms 内未抓到 m3u8")
                    }
                }, FALLBACK_DELAY_MS)
            }

            // 回退（或直连详情页）后再等 FAIL_AFTER_FALLBACK_MS 仍未抓到：
            // 判定解析失败，UI 展示“解析失败 + 重试”
            val failAt = if (canFallback) FALLBACK_DELAY_MS + FAIL_AFTER_FALLBACK_MS
            else FAIL_AFTER_FALLBACK_MS
            mainHandler.postDelayed({
                if (!captured.get()) {
                    reportFailed("超时未拦截到 m3u8（嵌入页与详情页均已尝试）")
                }
            }, failAt)
        }

        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            webView.stopLoading()
            webView.destroy()
        }
    }
}

/** 嵌入页加载后等待 m3u8 出现的时间，超时则回退加载详情页整页 */
private const val FALLBACK_DELAY_MS = 12_000L

/** 回退详情页（或直连详情页）后再等多久仍抓不到则判定解析失败、展示重试入口 */
private const val FAIL_AFTER_FALLBACK_MS = 12_000L

/**
 * 从 WebView 请求头转给播放器时需要剔除的头：
 * Range/Accept-Encoding/Connection/Content-Length/Host 由 HTTP 栈与播放器自行管理，
 * 原样透传反而会破坏分片请求
 */
private val STRIP_REQUEST_HEADERS = setOf(
    "range", "accept-encoding", "connection", "content-length", "host"
)
