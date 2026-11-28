package com.code7.techupdates.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ReaderApiTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond(fixture("article.txt"))
    }

    private fun api(cacheSize: Int = ReaderApi.CACHE_SIZE) =
        ReaderApi(
            MockEngine { request ->
                requests += request
                handler(request)
            },
            cacheSize = cacheSize,
        )

    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader?.getResource("reader/$name")) { "missing fixture $name" }.readText()

    private suspend fun ReaderApi.failure(url: String = ARTICLE): ReaderException =
        try {
            article(url)
            fail("expected a ReaderException")
            error("unreachable")
        } catch (e: ReaderException) {
            e
        }

    private fun respondWith(status: HttpStatusCode) {
        handler = { respond("busy", status) }
    }

    @Test
    fun article_requestsJinaWithTheRawArticleUrl() =
        runTest {
            api().article("https://example.com/a/b?x=1")
            assertEquals("https://r.jina.ai/https://example.com/a/b?x=1", requests.single().url.toString())
        }

    @Test
    fun article_returnsTextAfterTheMarker() =
        runTest {
            val md = api().article(ARTICLE)
            assertTrue(md.startsWith("# Credit card skimming scripts using WebRTC"))
            assertTrue(md.endsWith("They exfiltrate data over WebRTC data channels."))
            assertTrue("Title:" !in md && "URL Source:" !in md)
        }

    @Test
    fun article_withoutMarker_returnsWholeBodyTrimmed() =
        runTest {
            handler = { respond("\n  Just some text.\n\n") }
            assertEquals("Just some text.", api().article(ARTICLE))
        }

    @Test
    fun article_emptyAfterMarker_isEmptyFailure() =
        runTest {
            handler = { respond(fixture("empty.txt")) }
            assertEquals(ReaderFailure.EMPTY, api().failure().failure)
        }

    @Test
    fun article_emptyBody_isEmptyFailure() =
        runTest {
            handler = { respond("") }
            assertEquals(ReaderFailure.EMPTY, api().failure().failure)
        }

    @Test
    fun article_429_isRateLimited() =
        runTest {
            respondWith(HttpStatusCode.TooManyRequests)
            val e = api().failure()
            assertEquals(ReaderFailure.RATE_LIMITED, e.failure)
            assertEquals(429, e.status)
        }

    @Test
    fun article_451_isBlocked() =
        runTest {
            respondWith(HttpStatusCode.fromValue(451))
            assertEquals(ReaderFailure.BLOCKED, api().failure().failure)
        }

    @Test
    fun article_5xxAndOtherErrors_areServerFailures() =
        runTest {
            for (status in listOf(500, 502, 503, 404, 403)) {
                respondWith(HttpStatusCode.fromValue(status))
                val e = api().failure()
                assertEquals(ReaderFailure.SERVER, e.failure)
                assertEquals(status, e.status)
            }
        }

    @Test
    fun article_timeout_isTimeoutFailure() =
        runTest {
            handler = { throw HttpRequestTimeoutException(it.url.toString(), ReaderApi.TIMEOUT_MILLIS) }
            assertEquals(ReaderFailure.TIMEOUT, api().failure().failure)
        }

    @Test
    fun article_connectionError_isNetworkFailure() =
        runTest {
            handler = { throw IOException("offline") }
            assertEquals(ReaderFailure.NETWORK, api().failure().failure)
        }

    @Test
    fun article_isCachedPerUrl() =
        runTest {
            val api = api()
            val first = api.article(ARTICLE)
            assertEquals(first, api.article(ARTICLE))
            assertEquals(1, requests.size)
        }

    @Test
    fun article_failuresAreNotCached() =
        runTest {
            val api = api()
            respondWith(HttpStatusCode.TooManyRequests)
            api.failure()
            handler = { respond(fixture("article.txt")) }
            api.article(ARTICLE)
            assertEquals(2, requests.size)
        }

    @Test
    fun article_cacheEvictsLeastRecentlyUsed() =
        runTest {
            val api = api(cacheSize = 2)
            api.article("https://a.example/")
            api.article("https://b.example/")
            api.article("https://a.example/") // a is now most recent
            api.article("https://c.example/") // evicts b
            assertEquals(3, requests.size)
            api.article("https://a.example/")
            assertEquals(3, requests.size)
            api.article("https://b.example/")
            assertEquals(4, requests.size)
        }

    @Test
    fun extractMarkdown_handlesMarkerAndBlankText() {
        assertEquals("Body", ReaderApi.extractMarkdown("Title: x\nMarkdown Content:\nBody\n"))
        assertEquals("plain", ReaderApi.extractMarkdown(" plain "))
        assertNull(ReaderApi.extractMarkdown("Markdown Content:   "))
        assertNull(ReaderApi.extractMarkdown(""))
    }

    private companion object {
        const val ARTICLE = "https://www.infohawk.com/news/2026-11-27-black-friday-webrtc-skimmer"
    }
}
