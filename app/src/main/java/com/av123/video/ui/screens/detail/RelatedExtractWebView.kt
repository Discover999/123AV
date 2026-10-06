package com.av123.video.ui.screens.detail

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.av123.video.ui.util.AppLog
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

private const val RELATED_TAG = "RelatedExtract"

/** 轮询间隔：详情页 Alpine 模板渲染完成需要一点时间，600ms 一查 */
private const val POLL_INTERVAL_MS = 600L

/** 最长轮询窗口：9s 内渲染不出推荐卡片就静默放弃 */
private const val POLL_DEADLINE_MS = 9_000L

/** 进详情后延迟加载：错开 m3u8 抓流与首帧关键期，避免两个隐藏页同时抢网络/CPU */
private const val START_DELAY_MS = 2_500L

/**
 * 提取相关推荐卡片的 JS：
 * - .card：只取不在 <template> 内且含封面链接 a.card__cover[href] 的卡片
 * - a.vside：详情页服务端直出的侧栏“相关视频”，渲染后即在 DOM 中（与 Jsoup 静态路径同口径，
 *   兜底场景——如详情请求失败——也能靠它拿到推荐）
 * - 两类 outerHTML 直接首尾拼接（Jsoup 可解析并列片段）后整体 encodeURIComponent：
 *   回调值只含 ASCII 与 % 转义，不含引号/反斜杠，Kotlin 端剥掉外层 JS 字符串引号即可安全解码
 */
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

/**
 * 无头环境下推荐区 Alpine 组件不会自动加载：
 * 站点 recommendation() 的 init 用 IntersectionObserver 监听 .rec 元素进入视口才调用 load()，
 * 而本 WebView 不挂视图树，元素永远不可见 -> load() 永远不触发、.card 永远不渲染。
 * 轮询时手动取组件数据并补一次 load()；只在组件仍处于初始 'loading' 态时触发，
 * 组件成功（ready）/失败（empty）后不再重复调用，避免每轮轮询都打一次推荐 API。
 */
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

/** 详情页可能自动播放预览视频，每次轮询顺带静音暂停，避免隐藏页出声/占用解码器 */
private const val MUTE_PAUSE_JS = """
(function() {
  document.querySelectorAll('video').forEach(function(v) {
    try { v.muted = true; v.pause && v.pause(); } catch (e) {}
  });
})();
"""

/**
 * 无头 WebView 加载详情页整页提取“相关推荐”卡片 HTML：
 * applicationContext 创建、永不挂视图树（不产生黑帧、不可能遮挡页面）。
 *
 * 静态 Jsoup 抓详情时推荐区可能尚未由前端 JS 渲染，因此用隐藏 WebView 真渲染一遍，
 * onPageFinished 后每 [POLL_INTERVAL_MS] 执行一次提取脚本：
 * 卡片数量连续两次非零且相同（视为渲染稳定）即回调一次并停止；
 * 超过 [POLL_DEADLINE_MS] 静默放弃。任何失败都不打扰详情主流程。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun RelatedExtractWebView(
    pageUrl: String,
    onExtracted: (html: String) -> Unit,
    /**
     * 提取流程结束（成功交付或超时放弃）恰好回调一次：
     * UI 据此收起骨架；空结果时整区保持隐藏。页面提前离开（onDispose）不回调。
     */
    onFinished: () -> Unit
) {
    if (pageUrl.isBlank()) return
    val appContext = LocalContext.current.applicationContext

    DisposableEffect(pageUrl) {
        val mainHandler = Handler(Looper.getMainLooper())
        val delivered = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        var pollCount = 0
        var lastCardFingerprint: String? = null
        var webView: WebView? = null

        // 成功/超时只结束一次；evaluateJavascript 回调与 Handler 轮询都在主线程
        fun reportFinished() {
            if (finished.compareAndSet(false, true)) onFinished()
        }

        val poll = object : Runnable {
            override fun run() {
                val view = webView ?: return
                if (delivered.get()) return
                pollCount++
                // 无头环境下手动触发推荐区组件加载（IntersectionObserver 不会触发）
                view.evaluateJavascript(KICK_RECOMMENDATION_JS, null)
                // 顺带静音暂停页面内自动播放的预览
                view.evaluateJavascript(MUTE_PAUSE_JS, null)
                view.evaluateJavascript(EXTRACT_CARDS_JS) { raw ->
                    if (delivered.get() || raw.isNullOrEmpty()) return@evaluateJavascript
                    // raw 是 JS 字符串的 JSON 字面量（首尾各一个双引号）；
                    // encodeURIComponent 结果不含引号/反斜杠，直接剥壳后百分号解码
                    val encoded = if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
                        raw.substring(1, raw.length - 1)
                    } else {
                        raw
                    }
                    if (encoded.isEmpty()) return@evaluateJavascript
                    val html = runCatching {
                        URLDecoder.decode(encoded, "UTF-8")
                    }.getOrNull().orEmpty()
                    if (html.isBlank()) return@evaluateJavascript
                    // 以内容指纹做“连续两次非零且相同”稳定性判断
                    if (html == lastCardFingerprint) {
                        if (delivered.compareAndSet(false, true)) {
                            AppLog.d(RELATED_TAG, "相关推荐卡片渲染稳定，回调 HTML（长度=${html.length}）")
                            onExtracted(html)
                            reportFinished()
                        }
                    } else {
                        lastCardFingerprint = html
                    }
                }
                // 未交付且未到截止时间则继续轮询
                if (!delivered.get() && pollCount * POLL_INTERVAL_MS < POLL_DEADLINE_MS) {
                    mainHandler.postDelayed(this, POLL_INTERVAL_MS)
                } else if (!delivered.get()) {
                    AppLog.d(RELATED_TAG, "相关推荐 ${POLL_DEADLINE_MS}ms 内未渲染出卡片，静默放弃")
                    reportFinished()
                }
            }
        }

        val view = WebView(appContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // iframe/异步跳转可能多次回调；只启动一次轮询链
                    if (pollCount == 0 && !delivered.get()) {
                        AppLog.d(RELATED_TAG, "详情页加载完成，开始轮询推荐卡片: $url")
                        mainHandler.post(poll)
                    }
                }
            }
            // 延迟加载详情页，错开抓流 WebView 的关键启动期
            mainHandler.postDelayed({ loadUrl(pageUrl) }, START_DELAY_MS)
        }
        webView = view

        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            view.stopLoading()
            view.destroy()
        }
    }
}
