package com.code7.techupdates.recommender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecommenderTest {
    private fun item(
        id: String,
        title: String,
    ) = Item(id, title, url = null)

    @Test
    fun featuresOf_treatsNonBreakingSpaceAsSpace_likeJs() {
        assertEquals(listOf("w:rust", "w:wins", "b:rust wins", "t:Programming"), featuresOf("Rust wins", null))
    }

    @Test
    fun featureLabel_expandsTopicTags_andStripsPrefixes() {
        assertEquals("Machine Learning", featureLabel("t:ML"))
        assertEquals("openai.com", featureLabel("d:openai.com"))
        assertEquals("Show HN", featureLabel("k:Show HN"))
    }

    @Test
    fun train_ordersNumericImpressionIdsAscending_likeJsObjectKeys() {
        val liked = listOf(Event(Signal.READ, item("1", "Rust compiler"), at = 1))
        val seen =
            listOf("300", "20", "100", "5").mapIndexed { i, id ->
                Seen(item(id, "Quarterly earnings $id report${"x".repeat(i + 2)}"), n = i + 1, at = i.toLong())
            }
        val shuffled = Recommender.train(liked, seen)
        val ascending = Recommender.train(liked, seen.sortedBy { it.item.id.toInt() })
        assertEquals(ascending.weights.toList(), shuffled.weights.toList())

        // Other keys keep their insertion order, which changes the training order.
        val named = seen.map { it.copy(item = it.item.copy(id = "s${it.item.id}")) }
        assertNotEquals(
            Recommender.train(liked, named).weights.toList(),
            Recommender.train(liked, named.reversed()).weights.toList(),
        )
    }

    @Test
    fun rank_blendsAffinityWithPopularity_andLimitsPicks() {
        assertEquals(0.0, Ranking.popularity(0), 0.0)
        assertEquals(1.0, Ranking.popularity(5_000), 0.0)
        val candidates = (1..50).toList()
        val ranked = Ranking.rank(candidates, model = null, item = { item("$it", "Story") }, points = { it })
        assertEquals((50 downTo 11).toList(), ranked.map { it.value })
    }
}
