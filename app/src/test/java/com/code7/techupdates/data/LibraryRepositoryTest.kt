package com.code7.techupdates.data

import app.cash.turbine.test
import com.code7.techupdates.data.local.FakeClock
import com.code7.techupdates.data.local.awaitValue
import com.code7.techupdates.data.local.inMemoryDb
import com.code7.techupdates.data.local.story
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {
    private val db = inMemoryDb()
    private val clock = FakeClock()
    private val learner = RoomLearnerStore(db, clock)
    private val repo = LibraryRepository(db, learner, clock)

    @After fun tearDown() = db.close()

    private suspend fun signals() = learner.events().first().map { it.kind to it.story.id }

    @Test
    fun toggleLater_flipsStateAndSaveSignal() =
        runTest {
            assertTrue(repo.toggleLater(story("1")))
            assertTrue(repo.isLater("1").first())
            assertEquals(listOf(SignalKind.SAVE to "1"), signals())

            assertFalse(repo.toggleLater(story("1")))
            assertFalse(repo.isLater("1").first())
            assertTrue(signals().isEmpty())
        }

    @Test
    fun toggleLater_explicitStateIsIdempotent() =
        runTest {
            assertTrue(repo.toggleLater(story("1"), on = true))
            assertTrue(repo.toggleLater(story("1"), on = true))
            assertEquals(1, repo.laterCount.first())
            assertEquals(1, signals().size)
        }

    @Test
    fun later_listsNewestFirst() =
        runTest {
            repo.toggleLater(story("1"))
            repo.toggleLater(story("2"))
            val list = repo.later().first()
            assertEquals(listOf("2", "1"), list.map { it.story.id })
            assertTrue(list[0].savedAt > list[1].savedAt)
            assertEquals(setOf("1", "2"), repo.laterIds().first())
        }

    @Test
    fun markRead_movesOutOfLaterAndRecordsRead() =
        runTest {
            repo.toggleLater(story("1"))
            val receipt = repo.markRead(story("1"), ReadHow.AUTO)
            assertEquals(ReadReceipt(story("1"), wasLater = true), receipt)
            assertFalse(repo.isLater("1").first())
            assertTrue(repo.isRead("1").first())
            val entry = repo.read().first().single()
            assertEquals(ReadHow.AUTO, entry.how)
            assertEquals(listOf(SignalKind.SAVE to "1", SignalKind.READ to "1"), signals())
            assertEquals(setOf("1"), repo.readIds().first())
        }

    @Test
    fun markRead_alreadyReadIsNoOp() =
        runTest {
            assertNotNull(repo.markRead(story("1"), ReadHow.MANUAL))
            val readAt =
                repo
                    .read()
                    .first()
                    .single()
                    .readAt
            assertNull(repo.markRead(story("1"), ReadHow.AUTO))
            val entry = repo.read().first().single()
            assertEquals(readAt, entry.readAt)
            assertEquals(ReadHow.MANUAL, entry.how)
        }

    @Test
    fun undoMarkRead_restoresLaterAndForgetsRead() =
        runTest {
            repo.toggleLater(story("1"))
            val receipt = repo.markRead(story("1"), ReadHow.MANUAL)!!
            repo.undoMarkRead(receipt)
            assertFalse(repo.isRead("1").first())
            assertTrue(repo.isLater("1").first())
            assertEquals(listOf(SignalKind.SAVE to "1"), signals())
        }

    @Test
    fun undoMarkRead_notPreviouslySaved_staysOutOfLater() =
        runTest {
            val receipt = repo.markRead(story("1"), ReadHow.MANUAL)!!
            repo.undoMarkRead(receipt)
            assertFalse(repo.isRead("1").first())
            assertFalse(repo.isLater("1").first())
            assertTrue(signals().isEmpty())
        }

    @Test
    fun unmarkRead_forgetsReadWithoutRestoringLater() =
        runTest {
            repo.toggleLater(story("1"))
            repo.markRead(story("1"), ReadHow.MANUAL)
            repo.unmarkRead("1")
            assertFalse(repo.isRead("1").first())
            assertFalse(repo.isLater("1").first())
            assertEquals(listOf(SignalKind.SAVE to "1"), signals())
        }

    @Test
    fun markRead_capsHistoryAt500() =
        runTest {
            repeat(LibraryRepository.MAX_READ + 2) { repo.markRead(story("$it"), ReadHow.MANUAL) }
            assertEquals(LibraryRepository.MAX_READ, repo.readCount.first())
            assertFalse(repo.isRead("0").first())
            assertFalse(repo.isRead("1").first())
            assertTrue(repo.isRead("2").first())
        }

    @Test
    fun clearRead_thenRestore() =
        runTest {
            repo.markRead(story("1"), ReadHow.MANUAL)
            repo.markRead(story("2"), ReadHow.AUTO)
            val before = repo.read().first()
            val backup = repo.clearRead()
            assertEquals(0, repo.readCount.first())
            repo.restoreRead(backup)
            assertEquals(before, repo.read().first())
        }

    @Test
    fun counts_emitAsLibraryChanges() =
        runTest {
            repo.laterCount.test {
                assertEquals(0, awaitItem())
                repo.toggleLater(story("1"))
                awaitValue(1)
                repo.markRead(story("1"), ReadHow.MANUAL)
                awaitValue(0)
                cancelAndIgnoreRemainingEvents()
            }
            repo.readCount.test {
                assertEquals(1, awaitItem())
                repo.unmarkRead("1")
                awaitValue(0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun removeLater_forgetsSave_undoRestoresPositionAndSignal() =
        runTest {
            repo.toggleLater(story("1"))
            repo.toggleLater(story("2"))
            val before = repo.later().first()
            val removed = repo.removeLater("1")!!
            assertEquals(before[1], removed)
            assertEquals(listOf("2"), repo.later().first().map { it.story.id })
            assertEquals(listOf(SignalKind.SAVE to "2"), signals())

            repo.undoRemoveLater(removed)
            assertEquals(before, repo.later().first())
            assertEquals(setOf(SignalKind.SAVE to "1", SignalKind.SAVE to "2"), signals().toSet())
        }

    @Test
    fun removeLater_missingReturnsNullAndKeepsSignals() =
        runTest {
            repo.markRead(story("1"), ReadHow.MANUAL)
            assertNull(repo.removeLater("1"))
            assertEquals(listOf(SignalKind.READ to "1"), signals())
        }

    @Test
    fun removeRead_forgetsRead_undoRestoresEntryAndSignal() =
        runTest {
            repo.markRead(story("1"), ReadHow.AUTO)
            repo.markRead(story("2"), ReadHow.MANUAL)
            val before = repo.read().first()
            val removed = repo.removeRead("1")!!
            assertEquals(before[1], removed)
            assertEquals(listOf("2"), repo.read().first().map { it.story.id })
            assertEquals(listOf(SignalKind.READ to "2"), signals())

            repo.undoRemoveRead(removed)
            assertEquals(before, repo.read().first())
            assertEquals(setOf(SignalKind.READ to "1", SignalKind.READ to "2"), signals().toSet())
        }

    @Test
    fun removeRead_missingReturnsNull() = runTest { assertNull(repo.removeRead("nope")) }

    @Test
    fun clearRead_keepsLearnerSignals() =
        runTest {
            repo.markRead(story("1"), ReadHow.MANUAL)
            repo.clearRead()
            assertEquals(listOf(SignalKind.READ to "1"), signals())
        }
}
