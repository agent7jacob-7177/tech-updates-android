package com.code7.techupdates.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedQueryTest {
    private val now = 1_795_822_000L
    private val week = now - 7 * 86_400

    private fun req(
        feed: FeedType,
        topicId: String = "",
        query: String = "",
        page: Int = 0,
    ) = FeedQuery(feed, topicId, query, page).buildRequest(now)

    @Test
    fun top_noQuery_isFrontPage() {
        assertEquals(
            SearchRequest("search", mapOf("hitsPerPage" to "30", "page" to "0", "tags" to "front_page")),
            req(FeedType.TOP),
        )
    }

    @Test
    fun top_withQuery_isPopularThisWeekWithLowThreshold() {
        assertEquals(
            SearchRequest(
                "search",
                mapOf(
                    "hitsPerPage" to "30",
                    "page" to "2",
                    "tags" to "story",
                    "numericFilters" to "created_at_i>$week,points>10",
                    "query" to "rust",
                ),
            ),
            req(FeedType.TOP, query = "rust", page = 2),
        )
    }

    @Test
    fun top_withTopic_usesTopicQuery() {
        val r = req(FeedType.TOP, topicId = "ML")
        assertEquals("search", r.endpoint)
        assertEquals("story", r.params["tags"])
        assertEquals("created_at_i>$week,points>10", r.params["numericFilters"])
        assertEquals("machine learning", r.params["query"])
    }

    @Test
    fun latest_isSearchByDate() {
        assertEquals(
            SearchRequest(
                "search_by_date",
                mapOf("hitsPerPage" to "30", "page" to "0", "tags" to "story", "numericFilters" to "points>5"),
            ),
            req(FeedType.LATEST),
        )
        assertEquals("points>5", req(FeedType.LATEST, topicId = "AI", query = "agents").params["numericFilters"])
        assertEquals("agents AI", req(FeedType.LATEST, topicId = "AI", query = "agents").params["query"])
    }

    @Test
    fun week_noQuery_usesHighThreshold() {
        assertEquals(
            SearchRequest(
                "search",
                mapOf(
                    "hitsPerPage" to "30",
                    "page" to "1",
                    "tags" to "story",
                    "numericFilters" to "created_at_i>$week,points>50",
                ),
            ),
            req(FeedType.WEEK, page = 1),
        )
    }

    @Test
    fun week_withQueryAndTopic_combinesQueryThenTopic() {
        val r = req(FeedType.WEEK, topicId = "Hardware", query = "apple m5")
        assertEquals("created_at_i>$week,points>10", r.params["numericFilters"])
        assertEquals("apple m5 chip", r.params["query"])
    }

    @Test
    fun paramOrder_matchesJs() {
        assertEquals(
            listOf("hitsPerPage", "page", "tags", "numericFilters", "query"),
            req(FeedType.WEEK, query = "x").params.keys.toList(),
        )
    }

    @Test
    fun unknownOrAllTopic_addsNoQuery() {
        assertEquals(req(FeedType.TOP), req(FeedType.TOP, topicId = "Nope"))
        assertFalse("query" in req(FeedType.WEEK, topicId = "").params)
    }

    @Test
    fun everyFeedTopicQueryCombination() {
        val combos =
            FeedType.entries.flatMap { feed ->
                Topic.entries.flatMap { topic -> listOf("", "rust").map { Triple(feed, topic, it) } }
            }
        assertEquals(48, combos.size)
        combos.forEach { (feed, topic, query) -> assertCombination(feed, topic, query) }
    }

    private fun assertCombination(
        feed: FeedType,
        topic: Topic,
        query: String,
    ) {
        val r = req(feed, topic.id, query, page = 3)
        val combined = listOfNotNull(query.ifEmpty { null }, topic.q).joinToString(" ")
        val label = "$feed/${topic.id}/$query"
        assertEquals(label, "30", r.params["hitsPerPage"])
        assertEquals(label, "3", r.params["page"])
        assertEquals(label, combined.ifEmpty { null }, r.params["query"])
        val expectedFilter =
            when {
                feed == FeedType.TOP && combined.isEmpty() -> null
                feed == FeedType.LATEST -> "points>5"
                combined.isEmpty() -> "created_at_i>$week,points>50"
                else -> "created_at_i>$week,points>10"
            }
        assertEquals(label, expectedFilter, r.params["numericFilters"])
        assertEquals(label, if (expectedFilter == null) "front_page" else "story", r.params["tags"])
        assertEquals(label, if (feed == FeedType.LATEST) "search_by_date" else "search", r.endpoint)
    }

    @Test
    fun sortsByPoints_matchesJsLoad() {
        assertFalse(FeedQuery(FeedType.TOP).sortsByPoints)
        assertTrue(FeedQuery(FeedType.TOP, query = "x").sortsByPoints)
        assertTrue(FeedQuery(FeedType.TOP, topicId = "AI").sortsByPoints)
        assertTrue(FeedQuery(FeedType.WEEK).sortsByPoints)
        assertFalse(FeedQuery(FeedType.LATEST, topicId = "AI", query = "x").sortsByPoints)
    }
}
