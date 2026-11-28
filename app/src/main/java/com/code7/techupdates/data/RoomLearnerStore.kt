package com.code7.techupdates.data

import androidx.room.withTransaction
import com.code7.techupdates.data.local.AppDatabase
import com.code7.techupdates.data.local.ImpressionEntity
import com.code7.techupdates.data.local.LearnerEventEntity
import com.code7.techupdates.data.local.toSnapshot
import com.code7.techupdates.domain.Story
import com.code7.techupdates.recommender.Impression
import com.code7.techupdates.recommender.LearnerEvent
import com.code7.techupdates.recommender.LearnerStore
import com.code7.techupdates.recommender.SignalKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room-backed [LearnerStore] (JS `tu.events` / `tu.imp`). [now] returns epoch millis. */
class RoomLearnerStore(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : LearnerStore {
    private val dao = db.learnerDao()
    private val impressionDao = db.impressionDao()

    /** Story ids whose impression was already counted in this session (JS `Learner.seen`). */
    private val seen = mutableSetOf<String>()
    private val seenLock = Mutex()

    override fun events(): Flow<List<LearnerEvent>> =
        dao.events().map { rows ->
            rows.mapNotNull { row ->
                SignalKind.fromId(row.kind)?.let { LearnerEvent(it, row.story.toStory(row.storyId), row.at) }
            }
        }

    override fun impressions(): Flow<List<Impression>> =
        impressionDao.impressions().map { rows ->
            rows.map { Impression(it.story.toStory(it.storyId), it.count, it.at) }
        }

    /** Number of distinct stories with a positive signal (JS `positives()`). */
    fun positives(): Flow<Int> = dao.positives()

    override suspend fun record(
        kind: SignalKind,
        story: Story,
    ) {
        db.withTransaction {
            val event = LearnerEventEntity(kind = kind.id, storyId = story.id, story = story.toSnapshot(), at = now())
            dao.insertEvent(event)
            dao.trimEvents(LearnerStore.MAX_EVENTS)
        }
    }

    override suspend fun forget(
        kind: SignalKind,
        storyId: String,
    ) = dao.deleteEvent(kind.id, storyId)

    override suspend fun recordImpression(story: Story): Boolean {
        if (!seenLock.withLock { seen.add(story.id) }) return false
        db.withTransaction {
            val count = impressionDao.impression(story.id)?.count ?: 0
            impressionDao.upsertImpression(ImpressionEntity(story.id, count + 1, story.toSnapshot(), now()))
            impressionDao.trimImpressions(LearnerStore.MAX_IMPRESSIONS)
        }
        return true
    }

    override suspend fun reset() {
        db.withTransaction {
            dao.clearEvents()
            impressionDao.clearImpressions()
        }
    }
}
