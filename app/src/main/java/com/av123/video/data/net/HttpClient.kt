package com.av123.video.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** 拿到响应但状态码非 2xx/3xx 时抛出，供 [AppError] 归类为站点侧错误 */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** 一次 HTTP 调用的结果；4xx/5xx 不抛异常，由调用方决定语义（如站点探测） */
data class HttpResponse(
    val code: Int,
    val body: String,
    /** 响应头（保留原始大小写）；远程配置用 ETag 做条件请求 */
    val headers: Map<String, String> = emptyMap()
)

/**
 * 应用统一网络层：
 * - 全 App 共享同一个 [OkHttpClient]（连接池/线程池复用），网页抓取、站点探测、
 *   Coil 图片加载均经此出口；
 * - 统一 UA / Accept-Language、合理的连接与读取超时、gzip（OkHttp 默认透明处理）；
 * - 网络错误（DNS、连接重置、读超时等可恢复 IO 异常）与 HTTP 429/5xx 自动重试
 *   至多 [MAX_ATTEMPTS] 次，指数退避 + 抖动；其余 4xx 不重试（重试无意义）。
 *
 * Jsoup 仅保留 HTML 解析职责（Jsoup.parse），不再由它发起连接。
 */
object HttpClient {

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** 网络错误最大尝试次数（首请求 + 2 次重试） */
    const val MAX_ATTEMPTS = 3

    /** 首次重试前等待基数，后续按次数倍增（400–600ms / 800–1200ms，含抖动） */
    const val RETRY_BACKOFF_BASE_MS = 400L

    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val READ_TIMEOUT_MS = 20_000L
    private const val CALL_TIMEOUT_MS = 30_000L

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        // 系统级连接失败恢复（切网、连接复用 socket 失效等）
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .addInterceptor { chain ->
            // 统一浏览器口径请求头；调用方已设置的头优先（如各站点自定义 UA）
            val original = chain.request()
            val builder = original.newBuilder()
            if (original.header("User-Agent") == null) {
                builder.header("User-Agent", DEFAULT_USER_AGENT)
            }
            if (original.header("Accept-Language") == null) {
                builder.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            }
            chain.proceed(builder.build())
        }
        .build()

    /**
     * 发起 GET 并返回原始响应（不校验状态码）。网络错误抛 [IOException]；
     * 已拿到响应（含 4xx/5xx）即返回。瞬时 IO 错误与 429/5xx 自动重试。
     * 阻塞式 OkHttp 调用放在 [Dispatchers.IO]，重试等待使用可取消的 [delay]。
     *
     * @param timeoutMs 单次读取/连接超时覆盖值（站点选择器可自定义），null 用默认
     */
    suspend fun execute(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int? = null,
        /**
         * 可选事件监听工厂（网络诊断采集分段耗时时传入）；默认 null，
         * 既有抓取/图片等调用路径零改动。非 null 时走 per-call newBuilder，
         * 仍共享主 client 的连接池与 Dispatcher。
         */
        eventListenerFactory: EventListener.Factory? = null
    ): HttpResponse = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        val request = requestBuilder.build()

        // per-call 超时覆盖 / EventListener：newBuilder 共享连接池与 Dispatcher，成本很低
        val needsCustomClient = timeoutMs != null || eventListenerFactory != null
        val httpClient = if (needsCustomClient) {
            client.newBuilder().apply {
                if (timeoutMs != null) {
                    connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    callTimeout((timeoutMs * 2L), TimeUnit.MILLISECONDS)
                }
                if (eventListenerFactory != null) {
                    eventListenerFactory(eventListenerFactory)
                }
            }.build()
        } else {
            client
        }

        var lastError: IOException? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            currentCoroutineContext().ensureActive()

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: IOException) {
                lastError = e
                if (attempt < MAX_ATTEMPTS - 1) delay(retryDelayMs(attempt))
                continue
            }

            // 429/5xx 有限重试：丢弃本次响应体，退避后再试
            if (isRetryableStatus(response.code) && attempt < MAX_ATTEMPTS - 1) {
                response.close()
                delay(retryDelayMs(attempt))
                continue
            }

            return@withContext response.use { r ->
                val responseHeaders = buildMap {
                    r.headers.names().forEach { name ->
                        put(name, r.header(name).orEmpty())
                    }
                }
                HttpResponse(
                    code = r.code,
                    body = r.body?.string().orEmpty(),
                    headers = responseHeaders
                )
            }
        }
        throw lastError ?: IOException("网络请求失败")
    }

    /**
     * 期望成功（2xx/3xx）响应的 GET：返回响应体文本；
     * 状态码为 4xx/5xx 时抛 [HttpStatusException]，供错误归一化分类。
     */
    suspend fun getString(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int? = null
    ): String {
        val response = execute(url, headers, timeoutMs)
        if (response.code !in 200..399) throw HttpStatusException(response.code)
        return response.body
    }

    /** 429 与 5xx 可重试；其余 4xx（鉴权/不存在等）重试无意义 */
    internal fun isRetryableStatus(code: Int): Boolean =
        code == 429 || code in 500..599

    /** 指数退避 + 抖动：400–600ms、800–1200ms */
    internal fun retryDelayMs(attempt: Int): Long {
        val base = RETRY_BACKOFF_BASE_MS shl attempt
        return base + Random.nextLong(0L, base / 2 + 1L)
    }
}
