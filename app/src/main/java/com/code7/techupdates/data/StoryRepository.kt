package com.code7.techupdates.data

import com.code7.techupdates.data.remote.HnApi
import com.code7.techupdates.data.remote.toComment
import com.code7.techupdates.domain.Comment
import com.code7.techupdates.domain.FeedQuery
import com.code7.techupdates.domain.Story
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A page of feed results. [fromCache] is true when served from the in-memory cache. */
data class Page(
    val stories: List<Story>,
    val page: Int,
    val nbPages: Int,
    val fetchedAtEpochSec: Long,
    val fromCache: Boolean = false,
)

class StoryRepository(
    private val api: HnApi,
    private val nowEpochSec: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<FeedQuery, Page>()

    /**
     * Loads one feed page. Results are cached per [FeedQuery]; a cached page is returned unless
     * [forceRefresh]. If the network fails, a previously cached page is returned instead (the web
     * app's offline fallback), otherwise the failure.
     */
    suspend fun feed(
        query: FeedQuery,
        forceRefresh: Boolean = false,
    ): Result<Page> {
        val cached = mutex.withLock { cache[query] }
        if (cached != null && !forceRefresh) return Result.success(cached.copy(fromCache = true))
        return try {
            val now = nowEpochSec()
            val result = api.search(query.buildRequest(now))
            val stories = if (query.sortsByPoints) result.stories.sortedByDescending { it.points } else result.stories
            val page = Page(stories, result.page, result.nbPages, now)
            mutex.withLock { cache[query] = page }
            Result.success(page)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            if (cached != null) Result.success(cached.copy(fromCache = true)) else Result.failure(e)
        }
    }

    /** Top-level comments (with nested replies) of a story. */
    suspend fun comments(storyId: String): Result<List<Comment>> =
        try {
            Result.success(api.item(storyId).children.map { it.toComment() })
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    suspend fun clearCache() = mutex.withLock { cache.clear() }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
