package com.code7.techupdates.data.local

import app.cash.turbine.test
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
class LibraryDaosTest {
    private val db = inMemoryDb()
    private val laterDao = db.laterDao()
    private val readDao = db.readDao()

    @After fun tearDown() = db.close()

    private fun later(
        id: String,
        at: Long,
    ) = LaterEntity(id, story(id).toSnapshot(), at)

    private fun read(
        id: String,
        at: Long,
    ) = ReadEntity(id, story(id).toSnapshot(), at, "manual")

    @Test
    fun laterList_newestFirst_andRoundTripsSnapshot() =
        runTest {
            laterDao.upsertLater(later("1", 10))
            laterDao.upsertLater(later("2", 30))
            laterDao.upsertLater(later("3", 20))
            val rows = laterDao.laterList().first()
            assertEquals(listOf("2", "3", "1"), rows.map { it.storyId })
            assertEquals(story("2"), rows[0].story.toStory("2"))
        }

    @Test
    fun laterCount_andExists_emitOnChange() =
        runTest {
            laterDao.laterCount().test {
                assertEquals(0, awaitItem())
                laterDao.upsertLater(later("1", 1))
                assertEquals(1, awaitItem())
                laterDao.upsertLater(later("1", 2)) // upsert: same id does not duplicate
                laterDao.upsertLater(later("2", 3))
                awaitValue(2)
                laterDao.deleteLater("1")
                awaitValue(1)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(laterDao.isLater("2"))
            assertFalse(laterDao.isLater("1"))
            assertTrue(laterDao.observeIsLater("2").first())
            assertEquals(listOf("2"), laterDao.laterIds().first())
        }

    @Test
    fun trimRead_keepsNewest() =
        runTest {
            (1..10).forEach { readDao.upsertRead(read("$it", it.toLong())) }
            readDao.trimRead(3)
            assertEquals(listOf("10", "9", "8"), readDao.readList().first().map { it.storyId })
            assertEquals(3, readDao.readCount().first())
        }

    @Test
    fun clearRead_andBulkRestore() =
        runTest {
            readDao.upsertRead(read("1", 1))
            readDao.upsertRead(read("2", 2))
            val backup = readDao.readSnapshot()
            readDao.clearRead()
            assertFalse(readDao.isRead("1"))
            backup.forEach { readDao.upsertRead(it) }
            assertEquals(backup, readDao.readSnapshot())
            assertTrue(readDao.observeIsRead("1").first())
        }
}
