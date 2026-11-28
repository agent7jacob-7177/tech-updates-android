package com.code7.techupdates.domain

private object PoolQuery {
    const val FRONT_PAGE_SIZE = 60
    const val INTEREST_PAGE_SIZE = 25
    const val LATEST_MIN_POINTS = 20
    const val WEEK_MIN_POINTS = 40
    const val INTEREST_MIN_POINTS = 5
}

/**
 * The searches that make up the "For you" candidate pool (JS `loadForYou` `sources`, in order):
 * the front page, recent and popular-this-week stories, and one search per learned interest.
 */
fun forYouRequests(
    interests: List<String>,
    nowEpochSec: Long,
): List<SearchRequest> {
    val week = nowEpochSec - FeedQuery.WEEK_SECONDS

    fun stories(
        numericFilters: String,
        size: Int,
        endpoint: String = FeedQuery.ENDPOINT_SEARCH,
        query: String? = null,
    ): SearchRequest {
        val params = linkedMapOf("tags" to "story")
        if (query != null) params["query"] = query
        params["numericFilters"] = numericFilters
        params["hitsPerPage"] = size.toString()
        return SearchRequest(endpoint, params)
    }

    return listOf(
        SearchRequest(
            FeedQuery.ENDPOINT_SEARCH,
            linkedMapOf("tags" to "front_page", "hitsPerPage" to PoolQuery.FRONT_PAGE_SIZE.toString()),
        ),
        stories("points>${PoolQuery.LATEST_MIN_POINTS}", PoolQuery.FRONT_PAGE_SIZE, FeedQuery.ENDPOINT_SEARCH_BY_DATE),
        stories("created_at_i>$week,points>${PoolQuery.WEEK_MIN_POINTS}", PoolQuery.FRONT_PAGE_SIZE),
    ) +
        interests.map {
            stories(
                "created_at_i>$week,points>${PoolQuery.INTEREST_MIN_POINTS}",
                PoolQuery.INTEREST_PAGE_SIZE,
                query = it,
            )
        }
}
