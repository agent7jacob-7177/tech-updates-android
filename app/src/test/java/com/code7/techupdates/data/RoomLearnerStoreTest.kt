package com.code7.techupdates.data

import com.code7.techupdates.data.local.FakeClock
import com.code7.techupdates.data.local.StorySnapshot
import com.code7.techupdates.data.local.inMemoryDb
import com.code7.techupdates.data.local.story
import com.code7.techupdates.recommender.LearnerStore
import com.code7.techupdates.recommender.SignalKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomLearnerStoreTest {
    private val db = inMemoryDb()
    private val clock = FakeClock()
    private val store = RoomLearnerStore(db, clock)

    @After fun tearDown() = db.close()

    @Test
    fun record_dedupesPerKindAndId_movingItToTheEnd() =
        runTest {
            store.record(SignalKind.OPEN, story("1"))
            store.record(SignalKind.OPEN, story("2"))
            store.record(SignalKind.SAVE, story("1"))
            store.record(SignalKind.OPEN, story("1", title = "Updated"))
            val events = store.events().first()
            assertEquals(
                listOf(SignalKind.OPEN to "2", SignalKind.SAVE to "1", SignalKind.OPEN to "1"),
                events.map { it.kind to it.story.id },
            )
            assertEquals("Updated", events.last().story.title)
            assertTrue(events.zipWithNext().all { (a, b) -> a.at < b.at })
            assertEquals(2, store.positives().first())
        }

    @Test
    fun record_capsAtNewest600() =
        runTest {
            repeat(LearnerStore.MAX_EVENTS + 5) { store.record(SignalKind.OPEN, story("$it")) }
            val ids = store.events().first().map { it.story.id }
            assertEquals(LearnerStore.MAX_EVENTS, ids.size)
            assertEquals("5", ids.first())
            assertEquals("${LearnerStore.MAX_EVENTS + 4}", ids.last())
        }

    @Test
    fun record_slimsStoryText() =
        runTest {
            store.record(SignalKind.READ, story("1", storyText = "x".repeat(5000)))
            assertEquals(
                StorySnapshot.MAX_STORY_TEXT,
                store
                    .events()
                    .first()
                    .single()
                    .story.storyText
                    ?.length,
            )
        }

    @Test
    fun forget_removesOnlyThatKind() =
        runTest {
            store.record(SignalKind.SAVE, story("1"))
            store.record(SignalKind.READ, story("1"))
            store.forget(SignalKind.SAVE, "1")
            assertEquals(listOf(SignalKind.READ), store.events().first().map { it.kind })
        }

    @Test
    fun recordImpression_countsOncePerSession() =
        runTest {
            assertTrue(store.recordImpression(story("1")))
            assertFalse(store.recordImpression(story("1")))
            assertEquals(
                1,
                store
                    .impressions()
                    .first()
                    .single()
                    .count,
            )
            // A new session (new store instance) counts it again.
            assertTrue(RoomLearnerStore(db, clock).recordImpression(story("1")))
            assertEquals(
                2,
                store
                    .impressions()
                    .first()
                    .single()
                    .count,
            )
        }

    @Test
    fun recordImpression_capsAtNewest400() =
        runTest {
            repeat(LearnerStore.MAX_IMPRESSIONS + 3) { store.recordImpression(story("$it")) }
            val ids = store.impressions().first().map { it.story.id }
            assertEquals(LearnerStore.MAX_IMPRESSIONS, ids.size)
            assertEquals("3", ids.first())
        }

    @Test
    fun reset_clearsEverything() =
        runTest {
            store.record(SignalKind.OPEN, story("1"))
            store.recordImpression(story("2"))
            store.reset()
            assertTrue(store.events().first().isEmpty())
            assertTrue(store.impressions().first().isEmpty())
        }
}
