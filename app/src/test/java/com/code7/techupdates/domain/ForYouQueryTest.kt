package com.code7.techupdates.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ForYouQueryTest {
    @Test
    fun forYouRequests_matchTheWebAppSources() {
        val now = 1_000_000L
        val week = now - 7 * 86_400
        val requests = forYouRequests(listOf("AI"), now)
        assertEquals(listOf("search", "search_by_date", "search", "search"), requests.map { it.endpoint })
        assertEquals(
            listOf(
                mapOf("tags" to "front_page", "hitsPerPage" to "60"),
                mapOf("tags" to "story", "numericFilters" to "points>20", "hitsPerPage" to "60"),
                mapOf("tags" to "story", "numericFilters" to "created_at_i>$week,points>40", "hitsPerPage" to "60"),
                mapOf(
                    "tags" to "story",
                    "query" to "AI",
                    "numericFilters" to "created_at_i>$week,points>5",
                    "hitsPerPage" to "25",
                ),
            ),
            requests.map { it.params },
        )
    }
}
