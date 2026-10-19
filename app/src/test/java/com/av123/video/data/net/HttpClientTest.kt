package com.av123.video.data.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 统一网络层的真实 HTTP 行为：默认头、响应头、429/5xx 重试与 4xx 不重试。 */
class HttpClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun execute_addsDefaultHeadersAndReturnsBodyAndHeaders() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok")
                .addHeader("ETag", "v1")
        )

        val response = HttpClient.execute(server.url("/page").toString())

        assertEquals(200, response.code)
        assertEquals("ok", response.body)
        assertEquals(
            "v1",
            response.headers.entries.first { it.key.equals("ETag", ignoreCase = true) }.value
        )

        val request = server.takeRequest()
        assertTrue(request.getHeader("User-Agent")!!.contains("Mozilla"))
        assertEquals("zh-CN,zh;q=0.9,en;q=0.8", request.getHeader("Accept-Language"))
    }

    @Test
    fun execute_retriesOn429ThenSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = HttpClient.execute(server.url("/page").toString())

        assertEquals(200, response.code)
        assertEquals("ok", response.body)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun execute_retriesOn503ThenSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = HttpClient.execute(server.url("/page").toString())

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun execute_doesNotRetry403() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))

        val response = HttpClient.execute(server.url("/page").toString())

        assertEquals(403, response.code)
        assertEquals("forbidden", response.body)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun retryAfter_secondsParsedAndCapped() {
        assertEquals(0L, HttpClient.retryAfterDelayMs("0"))
        assertEquals(5_000L, HttpClient.retryAfterDelayMs("60"))
        assertNull(HttpClient.retryAfterDelayMs("not-a-number"))
    }

    @Test
    fun getString_throwsHttpStatusFor4xx() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))

        val error = runCatching {
            HttpClient.getString(server.url("/page").toString())
        }.exceptionOrNull()

        assertTrue(error is HttpStatusException)
        assertEquals(404, (error as HttpStatusException).code)
        assertEquals(1, server.requestCount)
    }
}
