package com.av123.video.data.net

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * 诊断分段耗时单测：注入脚本化时钟（毫秒→纳秒），按真实事件顺序驱动
 * [ProbeEventListener]，验证 DNS/TCP/TLS 三段互不重叠且加和不超过 TTFB/总耗时。
 */
class ProbeTimingsTest {

    /** 以毫秒为单位的可控时钟，监听器内部再换算纳秒；1000ms 基准避开 0 哨兵 */
    private class ScriptedClock {
        var nowMs: Long = 0L
        val nano: () -> Long = { (BASE_MS + nowMs) * NS_PER_MS }

        companion object {
            private const val BASE_MS = 1_000L
        }
    }

    /** 监听器回调从不解引用 Call，用最小空实现即可 */
    private val dummyCall: Call = object : Call {
        override fun request(): Request = error("不应在事件回调中使用")
        override fun execute(): Response = error("不应在事件回调中使用")
        override fun enqueue(responseCallback: Callback) = error("不应在事件回调中使用")
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout()
        override fun clone(): Call = this
    }

    private val dummyAddress: InetSocketAddress =
        InetSocketAddress.createUnresolved("example.invalid", 443)

    @Test
    fun httpsSegments_areDisjointAndBoundedByTtfbAndTotal() {
        val clock = ScriptedClock()
        val listener = ProbeEventListener(clock.nano)

        clock.nowMs = 0L; listener.callStart(dummyCall)
        clock.nowMs = 0L; listener.dnsStart(dummyCall, "example.invalid")
        clock.nowMs = 30L; listener.dnsEnd(dummyCall, "example.invalid", emptyList())
        clock.nowMs = 30L; listener.connectStart(dummyCall, dummyAddress, Proxy.NO_PROXY)
        clock.nowMs = 40L; listener.secureConnectStart(dummyCall)
        clock.nowMs = 100L; listener.secureConnectEnd(dummyCall, null)
        clock.nowMs = 100L; listener.connectEnd(dummyCall, dummyAddress, Proxy.NO_PROXY, null)
        clock.nowMs = 145L; listener.responseHeadersStart(dummyCall)
        clock.nowMs = 160L; listener.callEnd(dummyCall)

        val t = listener.snapshot()
        requireNotNull(t)
        // 纯 TCP = secureConnectStart - connectStart = 10ms（不再包含 60ms 的 TLS）
        assertEquals(30L, t.dnsMs)
        assertEquals(10L, t.tcpMs)
        assertEquals(60L, t.tlsMs)
        assertEquals(145L, t.ttfbMs)
        assertEquals(160L, t.totalMs)

        val segmentSum = listOfNotNull(t.dnsMs, t.tcpMs, t.tlsMs).sum()
        assertTrue("DNS+TCP+TLS 之和不应超过 TTFB", segmentSum <= t.ttfbMs!!)
        assertTrue("TTFB 不应超过总耗时", t.ttfbMs <= t.totalMs!!)
    }

    @Test
    fun plaintextConnection_tcpSpansConnectAndTlsIsNull() {
        val clock = ScriptedClock()
        val listener = ProbeEventListener(clock.nano)

        clock.nowMs = 0L; listener.callStart(dummyCall)
        clock.nowMs = 0L; listener.dnsStart(dummyCall, "example.invalid")
        clock.nowMs = 30L; listener.dnsEnd(dummyCall, "example.invalid", emptyList())
        clock.nowMs = 30L; listener.connectStart(dummyCall, dummyAddress, Proxy.NO_PROXY)
        clock.nowMs = 90L; listener.connectEnd(dummyCall, dummyAddress, Proxy.NO_PROXY, null)
        clock.nowMs = 120L; listener.responseHeadersStart(dummyCall)
        clock.nowMs = 130L; listener.callEnd(dummyCall)

        val t = listener.snapshot()
        requireNotNull(t)
        assertEquals(30L, t.dnsMs)
        // 无 TLS：TCP 段取完整建连区间 60ms
        assertEquals(60L, t.tcpMs)
        assertNull(t.tlsMs)
        assertEquals(120L, t.ttfbMs)
        assertEquals(130L, t.totalMs)
        assertTrue((t.dnsMs!! + t.tcpMs!!) <= t.ttfbMs!!)
    }

    @Test
    fun snapshotBeforeCallStart_isNull() {
        assertNull(ProbeEventListener().snapshot())
    }

    private companion object {
        const val NS_PER_MS = 1_000_000L
    }
}
