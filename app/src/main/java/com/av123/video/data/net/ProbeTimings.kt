package com.av123.video.data.net

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * 一次站点探测请求的分段耗时（毫秒），任一段在连接复用/明文/失败时可能为 null
 * （UI 显示"—"）：
 * - [dnsMs] DNS 解析；[tcpMs] **纯 TCP** 建连（不含 TLS）；[tlsMs] TLS 握手（HTTPS 才有）；
 * - [ttfbMs] 从请求开始到收到响应头（首字节）；[totalMs] 整个 call 的总耗时。
 * 全部以同一次 call 的 callStart 纳秒为基准，三段互不重叠：
 * dns + tcp + tls ≤ ttfb ≤ total（TTFB 还含请求发送/排队）。
 */
data class ProbeTimings(
    val dnsMs: Long?,
    val tcpMs: Long?,
    val tlsMs: Long?,
    val ttfbMs: Long?,
    val totalMs: Long?
) {
    companion object {
        /** 无可展示的分段（事件未产生）时统一显示的占位 */
        const val UNKNOWN_SEGMENT = -1L
    }
}

/**
 * 站点诊断专用 EventListener：逐段记录 DNS/TCP/TLS/TTFB/总耗时。
 * 每次 newCall 由工厂新建一个实例；[snapshot] 在请求结束后读取。
 */
class ProbeEventListener(
    private val now: () -> Long = { System.nanoTime() }
) : EventListener() {

    private var callStartNs = 0L
    private var dnsStartNs = 0L
    private var connectStartNs = 0L
    private var secureConnectStartNs = 0L

    private var dnsMs: Long? = null
    private var tcpMs: Long? = null
    private var tlsMs: Long? = null
    private var ttfbMs: Long? = null
    private var totalMs: Long? = null

    override fun callStart(call: Call) {
        callStartNs = now()
    }

    override fun dnsStart(call: Call, domainName: String) {
        dnsStartNs = now()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<@JvmSuppressWildcards InetAddress>) {
        if (dnsStartNs > 0L) dnsMs = (now() - dnsStartNs) / NS_PER_MS
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStartNs = now()
    }

    override fun secureConnectStart(call: Call) {
        secureConnectStartNs = now()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        if (secureConnectStartNs > 0L) tlsMs = (now() - secureConnectStartNs) / NS_PER_MS
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        if (connectStartNs <= 0L) return
        // TCP 段只统计纯 TCP 建连：发生 TLS 时 secureConnectStart 即 TCP 完成时刻，
        // 保证 DNS/TCP/TLS 三段互不重叠、加和不超过 TTFB；明文连接才取到 connectEnd。
        val tcpEndNs = if (secureConnectStartNs > 0L) secureConnectStartNs else now()
        tcpMs = (tcpEndNs - connectStartNs) / NS_PER_MS
    }

    override fun responseHeadersStart(call: Call) {
        if (callStartNs > 0L) ttfbMs = (now() - callStartNs) / NS_PER_MS
    }

    override fun callEnd(call: Call) {
        if (callStartNs > 0L) totalMs = (now() - callStartNs) / NS_PER_MS
    }

    override fun callFailed(call: Call, ioe: IOException) {
        if (callStartNs > 0L) totalMs = (now() - callStartNs) / NS_PER_MS
    }

    /** callStart 未发生（极早期失败）时返回 null */
    fun snapshot(): ProbeTimings? =
        if (callStartNs == 0L) null
        else ProbeTimings(
            dnsMs = dnsMs,
            tcpMs = tcpMs,
            tlsMs = tlsMs,
            ttfbMs = ttfbMs,
            totalMs = totalMs
        )

    private companion object {
        const val NS_PER_MS = 1_000_000L
    }
}

/** 工厂：保存最近一次 call 的 listener（探测仅单次调用），结束后供调用方取 [ProbeTimings] */
class ProbeEventListenerFactory : EventListener.Factory {
    @Volatile
    var latest: ProbeEventListener? = null
        private set

    override fun create(call: Call): EventListener =
        ProbeEventListener().also { latest = it }
}
