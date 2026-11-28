package com.code7.techupdates.data

import com.code7.techupdates.data.remote.ReaderApi
import com.code7.techupdates.data.remote.ReaderException
import com.code7.techupdates.data.remote.ReaderFailure
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleRepositoryTest {
    private var status = HttpStatusCode.OK
    private val repo =
        ArticleRepository(ReaderApi(MockEngine { respond("Markdown Content:\nHello", status) }))

    @Test
    fun article_success() =
        runTest {
            assertEquals("Hello", repo.article("https://example.com/").getOrThrow())
        }

    @Test
    fun article_failureCarriesReaderException() =
        runTest {
            status = HttpStatusCode.TooManyRequests
            val e = repo.article("https://example.com/").exceptionOrNull() as ReaderException
            assertEquals(ReaderFailure.RATE_LIMITED, e.failure)
        }
}
