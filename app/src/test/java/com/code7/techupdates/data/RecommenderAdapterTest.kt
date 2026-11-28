package com.code7.techupdates.data

import com.code7.techupdates.domain.Story
import com.code7.techupdates.recommender.Item
import com.code7.techupdates.recommender.Signal
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommenderAdapterTest {
    private val story = Story("7", "Title", "https://a.test", "pg", 1, 0, 0, null)

    @Test
    fun mapsEventsAndImpressions() {
        val signals = SignalKind.entries.map { LearnerEvent(it, story, 5).toEvent().kind }
        assertEquals(listOf(Signal.OPEN, Signal.SAVE, Signal.READ), signals)
        val seen = Impression(story, 3, 9).toSeen()
        assertEquals(Item("7", "Title", "https://a.test"), seen.item)
        assertEquals(3, seen.n)
        assertEquals(9L, seen.at)
    }
}
