package com.code7.techupdates.data.remote

import com.code7.techupdates.domain.FeedQuery
import com.code7.techupdates.domain.FeedType
import com.code7.techupdates.domain.SearchRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HnApiTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val api = HnApi(Fixtures.engine(requests))

    @Test
    fun search_sendsEndpointAndParams() =
        runTest {
            api.search(FeedQuery(FeedType.WEEK, topicId = "AI", query = "agents").buildRequest(1_000_000L))
            val url = requests.single().url
            assertEquals("hn.algolia.com", url.host)
            assertEquals("/api/v1/search", url.encodedPath)
            assertEquals("30", url.parameters["hitsPerPage"])
            assertEquals("0", url.parameters["page"])
            assertEquals("story", url.parameters["tags"])
            assertEquals("created_at_i>395200,points>10", url.parameters["numericFilters"])
            assertEquals("agents AI", url.parameters["query"])
        }

    @Test
    fun search_mapsFrontPageHits() =
        runTest {
            val result = api.search(FeedQuery(FeedType.TOP).buildRequest(0))
            assertEquals(0, result.page)
            assertEquals(10, result.nbPages)
            assertEquals(3, result.stories.size)
            val first = result.stories.first()
            assertEquals("50660180", first.id)
            assertEquals("AI made self-hosting fun again", first.title)
            assertEquals("https://janlukas.blog/posts/ai-self-hosting", first.url)
            assertEquals("jlelse", first.author)
            assertEquals(1093, first.points)
            assertEquals(502, first.numComments)
            assertEquals("front_page", requests.single().url.parameters["tags"])
        }

    @Test
    fun searchByDate_dropsTitlelessHitsAndMapsStoryText() =
        runTest {
            val result = api.search(FeedQuery(FeedType.LATEST).buildRequest(0))
            assertEquals("/api/v1/search_by_date", requests.single().url.encodedPath)
            assertEquals(334, result.nbPages)
            assertEquals(listOf("50672700", "50672580", "50672574"), result.stories.map { it.id })
            val ask = result.stories.first()
            assertNull(ask.url)
            assertEquals("What are you using for <i>local</i> LLM inference?", ask.storyText)
            assertEquals(1_795_822_200L, ask.createdAtEpochSec)
        }

    @Test
    fun item_parsesNestedCommentTree() =
        runTest {
            val item = api.item("50668145")
            assertEquals("/api/v1/items/50668145", requests.single().url.encodedPath)
            assertEquals(50668145L, item.id)
            assertEquals(388, item.points)
            val comments = item.children.map { it.toComment() }
            assertEquals(listOf(50668191L, 50668579L), comments.map { it.id })
            val reply = comments[0].children.single()
            assertEquals(50669200L, reply.id)
            assertEquals("deathanatos", reply.author)
            assertEquals(50670078L, reply.children.single().id)
            assertEquals(emptyList<Any>(), reply.children.single().children)
        }

    @Test
    fun item_encodesId() =
        runTest {
            api.item("a/b")
            assertEquals("/api/v1/items/a%2Fb", requests.single().url.encodedPath)
        }

    @Test(expected = ServerResponseException::class)
    fun search_httpError_throws() =
        runTest {
            HnApi(MockEngine { respond("oops", HttpStatusCode.ServiceUnavailable) })
                .search(SearchRequest("search", emptyMap()))
        }

    @Test(expected = ClientRequestException::class)
    fun item_notFound_throws() =
        runTest {
            HnApi(MockEngine { respond("", HttpStatusCode.NotFound) }).item("1")
        }

    @Test
    fun toStory_defaultsMissingFields() {
        val story = HitDto(objectID = "1", title = "T", url = "").toStory()!!
        assertNull(story.url)
        assertEquals("", story.author)
        assertEquals(0, story.points)
        assertEquals(0, story.numComments)
        assertNull(HitDto(objectID = "2", title = "").toStory())
    }
}
