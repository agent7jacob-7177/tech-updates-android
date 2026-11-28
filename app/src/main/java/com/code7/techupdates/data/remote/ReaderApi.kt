package com.code7.techupdates.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Why the reader service couldn't give us article text. */
enum class ReaderFailure {
    /** HTTP 429: the free reader service is throttling us. */
    RATE_LIMITED,

    /** HTTP 451: the site doesn't allow reader copies. */
    BLOCKED,

    /** HTTP 5xx or another non-2xx status. */
    SERVER,

    /** No answer within [ReaderApi.TIMEOUT_MILLIS]. */
    TIMEOUT,

    /** Connection problem (offline, DNS …). */
    NETWORK,

    /** A 2xx answer without any article text. */
    EMPTY,
}

class ReaderException(
    val failure: ReaderFailure,
    val status: Int? = null,
    cause: Throwable? = null,
) : IOException("Reader failed: $failure${status?.let { " (HTTP $it)" }.orEmpty()}", cause)

/**
 * Client for the public Jina reader (`GET https://r.jina.ai/<url>`), port of JS `getArticle`.
 * Successful results are kept in a small in-memory LRU cache ([cacheSize] articles).
 */
class ReaderApi(
    engine: HttpClientEngine = OkHttp.create(),
    private val baseUrl: String = BASE_URL,
    private val cacheSize: Int = CACHE_SIZE,
) {
    private val client =
        HttpClient(engine) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
                socketTimeoutMillis = TIMEOUT_MILLIS
            }
        }

    private val mutex = Mutex()
    private val cache =
        object : LinkedHashMap<String, String>(cacheSize, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > cacheSize
        }

    /** The article at [url] as Markdown. Throws [ReaderException] on any failure. */
    suspend fun article(url: String): String {
        mutex.withLock { cache[url] }?.let { return it }
        val md = extractMarkdown(fetch(url)) ?: throw ReaderException(ReaderFailure.EMPTY)
        mutex.withLock { cache[url] = md }
        return md
    }

    private suspend fun fetch(url: String): String {
        val result =
            try {
                Result.success(get(url))
            } catch (e: HttpRequestTimeoutException) {
                Result.failure(ReaderException(ReaderFailure.TIMEOUT, cause = e))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Result.failure(e.toReaderException())
            }
        return result.getOrThrow()
    }

    private suspend fun get(url: String): String {
        val response = client.get(baseUrl + url)
        val status = response.status.value
        if (status !in HTTP_OK_RANGE) throw ReaderException(failureFor(status), status)
        return response.bodyAsText()
    }

    private fun IOException.toReaderException() =
        when (this) {
            is ReaderException -> {
                this
            }

            is ConnectTimeoutException, is SocketTimeoutException -> {
                ReaderException(
                    ReaderFailure.TIMEOUT,
                    cause = this,
                )
            }

            else -> {
                ReaderException(ReaderFailure.NETWORK, cause = this)
            }
        }

    fun close() = client.close()

    companion object {
        const val BASE_URL = "https://r.jina.ai/"

        /** JS aborts the reader fetch after 30 s. */
        const val TIMEOUT_MILLIS = 30_000L
        const val CACHE_SIZE = 20
        const val MARKER = "Markdown Content:"
        private const val LOAD_FACTOR = 0.75f
        private val HTTP_OK_RANGE = 200..299
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNAVAILABLE_FOR_LEGAL_REASONS = 451

        /** The text after [MARKER] (or the whole body without one), trimmed; null when empty. */
        fun extractMarkdown(body: String): String? {
            val i = body.indexOf(MARKER)
            val md = (if (i >= 0) body.substring(i + MARKER.length) else body).trim()
            return md.ifEmpty { null }
        }

        fun failureFor(status: Int): ReaderFailure =
            when (status) {
                HTTP_TOO_MANY_REQUESTS -> ReaderFailure.RATE_LIMITED
                HTTP_UNAVAILABLE_FOR_LEGAL_REASONS -> ReaderFailure.BLOCKED
                else -> ReaderFailure.SERVER
            }
    }
}
