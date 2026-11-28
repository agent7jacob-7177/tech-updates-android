package com.code7.techupdates.data

import com.code7.techupdates.data.remote.ReaderApi
import com.code7.techupdates.data.remote.ReaderException
import com.code7.techupdates.data.remote.ReaderFailure
import kotlinx.coroutines.CancellationException

/** What the reader screen needs to fetch article text (a seam for fakes in tests). */
fun interface ArticleSource {
    /** Markdown of the article at [url]; failures carry a [ReaderException]. */
    suspend fun article(url: String): Result<String>
}

/** Article text via the Jina reader ([ReaderApi], which also caches). */
class ArticleRepository(
    private val api: ReaderApi,
) : ArticleSource {
    override suspend fun article(url: String): Result<String> =
        try {
            Result.success(api.article(url))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReaderException) {
            Result.failure(e)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(ReaderException(ReaderFailure.NETWORK, cause = e))
        }
}
