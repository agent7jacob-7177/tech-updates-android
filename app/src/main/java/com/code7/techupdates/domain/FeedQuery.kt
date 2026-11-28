package com.code7.techupdates.domain

/** Live Hacker News feeds (JS `FEEDS`: "top", "new", "week"). */
enum class FeedType { TOP, LATEST, WEEK }

/** An Algolia request: endpoint relative to the API base plus query parameters (in JS order). */
data class SearchRequest(
    val endpoint: String,
    val params: Map<String, String>,
)

/** What the feed screen asks for. [query] is the (already trimmed) user search text. */
data class FeedQuery(
    val feed: FeedType = FeedType.TOP,
    val topicId: String = "",
    val query: String = "",
    val page: Int = 0,
) {
    val topic: Topic? get() = Topic.byId(topicId)

    /** User query + topic search terms, as JS `[state.query, topic && topic.q].filter(Boolean).join(" ")`. */
    val combinedQuery: String get() = listOfNotNull(query, topic?.q).filter { it.isNotEmpty() }.joinToString(" ")

    /** Whether results must be re-sorted by points (JS `load`: week, or top with a search/topic). */
    val sortsByPoints: Boolean
        get() = feed == FeedType.WEEK || (feed == FeedType.TOP && (query.isNotEmpty() || topicId.isNotEmpty()))

    /** Exact port of JS `buildUrl(page)`. */
    fun buildRequest(nowEpochSec: Long): SearchRequest {
        val q = combinedQuery
        val params = linkedMapOf("hitsPerPage" to PAGE_SIZE.toString(), "page" to page.toString())
        val week = nowEpochSec - WEEK_SECONDS
        var endpoint = ENDPOINT_SEARCH
        when {
            feed == FeedType.TOP && q.isEmpty() -> {
                params["tags"] = "front_page"
            }

            feed == FeedType.LATEST -> {
                endpoint = ENDPOINT_SEARCH_BY_DATE
                params["tags"] = "story"
                params["numericFilters"] = "points>$LATEST_MIN_POINTS"
            }

            else -> {
                // "This week", or Top with a search/topic: popular stories from the last 7 days
                val minPoints = if (q.isNotEmpty()) QUERY_MIN_POINTS else WEEK_MIN_POINTS
                params["tags"] = "story"
                params["numericFilters"] = "created_at_i>$week,points>$minPoints"
            }
        }
        if (q.isNotEmpty()) params["query"] = q
        return SearchRequest(endpoint, params)
    }

    companion object {
        const val PAGE_SIZE = 30
        const val ENDPOINT_SEARCH = "search"
        const val ENDPOINT_SEARCH_BY_DATE = "search_by_date"
        const val WEEK_SECONDS = 7 * 86_400L
        const val LATEST_MIN_POINTS = 5
        const val QUERY_MIN_POINTS = 10
        const val WEEK_MIN_POINTS = 50
    }
}
