package com.av123.video.ui.screens.detail

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.av123.video.data.source.HostAllowlist
import com.av123.video.ui.util.AppLog
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

private const val RELATED_TAG = "RelatedExtract"
private const val POLL_INTERVAL_MS = 600L
private const val POLL_DEADLINE_MS = 9_000L
private const val START_DELAY_MS = 2_500L

private const val EXTRACT_CARDS_JS = """
(function() {
  var cards = Array.prototype.slice.call(document.querySelectorAll('.card'))
    .filter(function(el) {
      return !el.closest('template') && el.querySelector('a.card__cover[href]');
    })
    .map(function(el) { return el.outerHTML; });
  var sides = Array.prototype.slice.call(document.querySelectorAll('a.vside'))
    .map(function(el) { return el.outerHTML; });
  return encodeURIComponent(cards.join('') + sides.join(''));
})();
"""

private const val KICK_RECOMMENDATION_JS = """
(function() {
  try {
    var el = document.querySelector('.rec');
    if (el && window.Alpine && window.Alpine.${'$'}data) {
      var data = window.Alpine.${'$'}data(el);
      if (data && data.state === 'loading' && typeof data.load === 'function') data.load();
    }
  } catch (e) {}
})();
"""

private const val MUTE_PAUSE_JS = """
(function() {
  document.querySelectorAll('video').forEach(function(v) {
    try { v.muted = true; v.pause && v.pause(); } catch (e) {}
  });
})();
"""

/**
 * P3.1 相关推荐服务：拥有无头 WebView、轮询与超时；Composable 只负责
 * 进入页面时 start、离开时 dispose。
 */
@SuppressLint("SetJavaScriptEnabled")
internal class RelatedResolver(
    private val context: Context,
    private val pageUrl: String,
    private val onExtracted: (html: String) -> Unit,
    private val onFinished: () -> Unit,
    private val allowlist: HostAllowlist
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    private var webView: WebView? = null
    private val allowedOrigins = setOfNotNull(originOf(pageUrl))

    fun start() {
        if (!started.compareAndSet(false, true) || pageUrl.isBlank()) return
        if (!allowlist.isAllowedEmbed(pageUrl)) {
            AppLog.w(RELATED_TAG, "相关推荐页不在白名单: $pageUrl")
            onFinished()
            return
        }

        val delivered = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        var pollCount = 0
        var lastCardFingerprint: String? = null

        fun postCallback(block: () -> Unit) {
            mainHandler.post { if (!disposed.get()) block() }
        }

        fun reportFinished() {
            if (finished.compareAndSet(false, true)) postCallback(onFinished)
        }

        val poll = object : Runnable {
            override fun run() {
                val view = webView ?: return
                if (delivered.get() || disposed.get()) return
                pollCount++
                view.evaluateJavascript(KICK_RECOMMENDATION_JS, null)
                view.evaluateJavascript(MUTE_PAUSE_JS, null)
                view.evaluateJavascript(EXTRACT_CARDS_JS) { raw ->
                    if (delivered.get() || disposed.get() || raw.isNullOrEmpty()) {
                        return@evaluateJavascript
                    }
                    val encoded =
                        if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
                            raw.substring(1, raw.length - 1)
                        } else {
                            raw
                        }
                    if (encoded.isEmpty()) return@evaluateJavascript
                    val html = runCatching {
                        URLDecoder.decode(encoded, "UTF-8")
                    }.getOrNull().orEmpty()
                    if (html.isBlank()) return@evaluateJavascript
                    if (html == lastCardFingerprint) {
                        if (delivered.compareAndSet(false, true)) {
                            AppLog.d(
                                RELATED_TAG,
                                "相关推荐卡片渲染稳定，回调 HTML（长度=${html.length}）"
                            )
                            postCallback { onExtracted(html) }
                            reportFinished()
                        }
                    } else {
                        lastCardFingerprint = html
                    }
                }
                if (!delivered.get() && pollCount * POLL_INTERVAL_MS < POLL_DEADLINE_MS) {
                    mainHandler.postDelayed(this, POLL_INTERVAL_MS)
                } else if (!delivered.get()) {
                    AppLog.d(
                        RELATED_TAG,
                        "相关推荐 ${POLL_DEADLINE_MS}ms 内未渲染出卡片，静默放弃"
                    )
                    reportFinished()
                }
            }
        }

        val view = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                hardenForHeadlessWebView()
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    if (request?.isForMainFrame != true) return false
                    val url = request.url.toString()
                    if (!allowlist.isAllowedNavigation(url)) {
                        AppLog.w(RELATED_TAG, "阻止跳到非白名单页面: $url")
                        return true
                    }
                    return false
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    AppLog.w(RELATED_TAG, "SSL 校验失败，拒绝加载: ${error?.url}")
                    handler?.cancel()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (pollCount == 0 && !delivered.get() && !disposed.get()) {
                        AppLog.d(RELATED_TAG, "详情页加载完成，开始轮询推荐卡片: $url")
                        mainHandler.post(poll)
                    }
                }
            }

            mainHandler.postDelayed({
                if (!disposed.get()) loadUrl(pageUrl)
            }, START_DELAY_MS)
        }
        webView = view
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        mainHandler.removeCallbacksAndMessages(null)
        webView?.stopLoading()
        webView?.destroy()
        webView = null
    }
}
