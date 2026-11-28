package com.code7.techupdates.data

import com.code7.techupdates.data.remote.Fixtures
import com.code7.techupdates.data.remote.Fixtures.json
import com.code7.techupdates.data.remote.HnApi
import com.code7.techupdates.domain.FeedQuery
import com.code7.techupdates.domain.FeedType
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRepositoryTest {
    private var calls = 0
    private var failing = false
    private val engine =
        MockEngine { request ->
            calls++
            when {
                failing -> respond("", HttpStatusCode.InternalServerError)
                request.url.encodedPath.contains("/items/") -> json("item_story.json")
                request.url.encodedPath.endsWith("/search_by_date") -> json("search_by_date.json")
                else -> json("search_front_page.json")
            }
        }
    private val repo = StoryRepository(HnApi(engine), nowEpochSec = { 1_795_822_000L })

    @Test
    fun feed_cachesPerQuery() =
        runTest {
            val first = repo.feed(FeedQuery(FeedType.TOP)).getOrThrow()
            assertFalse(first.fromCache)
            assertEquals(1_795_822_000L, first.fetchedAtEpochSec)
            val second = repo.feed(FeedQuery(FeedType.TOP)).getOrThrow()
            assertTrue(second.fromCache)
            assertEquals(first.stories, second.stories)
            assertEquals(1, calls)

            repo.feed(FeedQuery(FeedType.TOP, page = 1))
            repo.feed(FeedQuery(FeedType.LATEST))
            assertEquals(3, calls)
        }

    @Test
    fun feed_forceRefresh_bypassesCache() =
        runTest {
            repo.feed(FeedQuery(FeedType.TOP))
            val refreshed = repo.feed(FeedQuery(FeedType.TOP), forceRefresh = true).getOrThrow()
            assertFalse(refreshed.fromCache)
            assertEquals(2, calls)
        }

    @Test
    fun feed_networkFailure_fallsBackToCache() =
        runTest {
            repo.feed(FeedQuery(FeedType.TOP))
            failing = true
            val page = repo.feed(FeedQuery(FeedType.TOP), forceRefresh = true).getOrThrow()
            assertTrue(page.fromCache)
            assertTrue(page.offline)
            assertEquals(3, page.stories.size)
        }

    @Test
    fun feed_plainCacheHit_isNotOffline() =
        runTest {
            repo.feed(FeedQuery(FeedType.TOP))
            val page = repo.feed(FeedQuery(FeedType.TOP)).getOrThrow()
            assertTrue(page.fromCache)
            assertFalse(page.offline)
        }

    @Test
    fun story_isRememberedFromFeedsAndExplicitly() =
        runTest {
            assertNull(repo.story("x"))
            val first =
                repo
                    .feed(FeedQuery(FeedType.TOP))
                    .getOrThrow()
                    .stories
                    .first()
            assertEquals(first, repo.story(first.id))
            val extra = first.copy(id = "x", title = "Extra")
            repo.remember(extra)
            assertEquals(extra, repo.story("x"))
        }

    @Test
    fun feed_networkFailure_withoutCache_isFailure() =
        runTest {
            failing = true
            assertTrue(repo.feed(FeedQuery(FeedType.TOP)).isFailure)
        }

    @Test
    fun clearCache_forcesRefetch() =
        runTest {
            repo.feed(FeedQuery(FeedType.TOP))
            repo.clearCache()
            repo.feed(FeedQuery(FeedType.TOP))
            assertEquals(2, calls)
        }

    @Test
    fun feed_sortsByPointsOnlyWhereJsDoes() =
        runTest {
            val unsorted =
                """{"hits":[{"objectID":"a","title":"A","points":3},""" +
                    """{"objectID":"b","title":"B","points":40},{"objectID":"c","title":"C"}],"nbPages":1}"""
            val r =
                StoryRepository(
                    HnApi(
                        MockEngine {
                            respond(unsorted, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                        },
                    ),
                )

            suspend fun ids(q: FeedQuery) =
                r
                    .feed(q)
                    .getOrThrow()
                    .stories
                    .map { it.id }
            assertEquals(listOf("b", "a", "c"), ids(FeedQuery(FeedType.WEEK)))
            assertEquals(listOf("b", "a", "c"), ids(FeedQuery(FeedType.TOP, query = "x")))
            assertEquals(listOf("a", "b", "c"), ids(FeedQuery(FeedType.TOP)))
            assertEquals(listOf("a", "b", "c"), ids(FeedQuery(FeedType.LATEST, topicId = "AI")))
        }

    @Test
    fun comments_returnsTopLevelTree() =
        runTest {
            val comments = repo.comments("50668145").getOrThrow()
            assertEquals(2, comments.size)
            assertEquals("barbaudue710", comments[0].author)
            assertEquals(1, comments[0].children.size)
        }

    @Test
    fun comments_failure_isFailure() =
        runTest {
            failing = true
            assertTrue(repo.comments("1").isFailure)
        }

    @Test
    fun fixtureEngine_servesAllEndpoints() =
        runTest {
            val r = StoryRepository(HnApi(Fixtures.engine()))
            assertTrue(r.feed(FeedQuery(FeedType.LATEST)).isSuccess)
            assertTrue(r.comments("1").isSuccess)
        }

    @Test
    fun fetchStory_mapsItemAndRemembersIt() =
        runTest {
            val story = repo.fetchStory("50668145").getOrThrow()
            assertEquals("50668145", story.id)
            assertEquals("Credit card skimming scripts using WebRTC found on popular shopping sites", story.title)
            assertEquals("https://www.infohawk.com/news/2026-11-27-black-friday-webrtc-skimmer", story.url)
            assertEquals("scanr", story.author)
            assertEquals(388, story.points)
            assertEquals(4, story.numComments)
            assertEquals(1_795_794_031L, story.createdAtEpochSec)
            assertNull(story.storyText)
            assertEquals(story, repo.story("50668145"))
        }

    @Test
    fun fetchStory_networkFailure_isFailure() =
        runTest {
            failing = true
            assertTrue(repo.fetchStory("1").isFailure)
            assertNull(repo.story("1"))
        }
}
