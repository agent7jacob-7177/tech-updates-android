package com.code7.techupdates.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LearnerDao {
    /** Oldest first, like the JS array. */
    @Query("SELECT * FROM learner_events ORDER BY at ASC, id ASC")
    fun events(): Flow<List<LearnerEventEntity>>

    /** Distinct stories with at least one positive signal (JS `positives()`). */
    @Query("SELECT COUNT(DISTINCT story_id) FROM learner_events")
    fun positives(): Flow<Int>

    /** Replaces any event with the same (kind, story id): the old row goes, the new one gets a fresh id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(entity: LearnerEventEntity)

    @Query("DELETE FROM learner_events WHERE kind = :kind AND story_id = :storyId")
    suspend fun deleteEvent(
        kind: String,
        storyId: String,
    )

    /** Keeps the newest [max] events (JS `events.slice(-600)`). */
    @Query(
        "DELETE FROM learner_events WHERE id NOT IN " +
            "(SELECT id FROM learner_events ORDER BY at DESC, id DESC LIMIT :max)",
    )
    suspend fun trimEvents(max: Int)

    @Query("DELETE FROM learner_events")
    suspend fun clearEvents()
}
