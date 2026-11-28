package com.code7.techupdates.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LearnerDaoTest {
    private val db = inMemoryDb()
    private val dao = db.learnerDao()
    private val impressionDao = db.impressionDao()

    @After fun tearDown() = db.close()

    private fun event(
        kind: String,
        id: String,
        at: Long,
    ) = LearnerEventEntity(kind = kind, storyId = id, story = story(id).toSnapshot(), at = at)

    @Test
    fun insertEvent_replacesSameKindAndStory() =
        runTest {
            dao.insertEvent(event("open", "1", 1))
            dao.insertEvent(event("save", "1", 2))
            dao.insertEvent(event("open", "2", 3))
            dao.insertEvent(event("open", "1", 4))
            val rows = dao.events().first()
            assertEquals(listOf("save" to "1", "open" to "2", "open" to "1"), rows.map { it.kind to it.storyId })
            assertEquals(4L, rows.last().at)
            assertEquals(2, dao.positives().first())
        }

    @Test
    fun trimEvents_keepsNewest() =
        runTest {
            (1..10).forEach { dao.insertEvent(event("open", "$it", it.toLong())) }
            dao.trimEvents(4)
            assertEquals(listOf("7", "8", "9", "10"), dao.events().first().map { it.storyId })
        }

    @Test
    fun deleteEvent_onlyThatKind() =
        runTest {
            dao.insertEvent(event("open", "1", 1))
            dao.insertEvent(event("read", "1", 2))
            dao.deleteEvent("read", "1")
            assertEquals(listOf("open"), dao.events().first().map { it.kind })
        }

    @Test
    fun impressions_upsertTrimAndClear() =
        runTest {
            (1..5).forEach {
                impressionDao.upsertImpression(ImpressionEntity("$it", 1, story("$it").toSnapshot(), it.toLong()))
            }
            impressionDao.upsertImpression(ImpressionEntity("1", 2, story("1").toSnapshot(), 10))
            impressionDao.trimImpressions(3)
            assertEquals(listOf("4", "5", "1"), impressionDao.impressions().first().map { it.storyId })
            assertEquals(2, impressionDao.impression("1")?.count)
            impressionDao.clearImpressions()
            assertNull(impressionDao.impression("1"))
        }
}
