package com.code7.techupdates.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** JS `tu.imp`. */
@Dao
interface ImpressionDao {
    @Query("SELECT * FROM impressions ORDER BY at ASC")
    fun impressions(): Flow<List<ImpressionEntity>>

    @Query("SELECT * FROM impressions WHERE story_id = :storyId")
    suspend fun impression(storyId: String): ImpressionEntity?

    @Upsert
    suspend fun upsertImpression(entity: ImpressionEntity)

    /** Keeps the newest [max] impressions (JS drops the oldest beyond 400). */
    @Query(
        "DELETE FROM impressions WHERE story_id NOT IN " +
            "(SELECT story_id FROM impressions ORDER BY at DESC LIMIT :max)",
    )
    suspend fun trimImpressions(max: Int)

    @Query("DELETE FROM impressions")
    suspend fun clearImpressions()
}
