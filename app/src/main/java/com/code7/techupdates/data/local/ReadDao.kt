package com.code7.techupdates.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** JS `tu.lib.read`. */
@Dao
interface ReadDao {
    @Query("SELECT * FROM read_history ORDER BY read_at DESC")
    fun readList(): Flow<List<ReadEntity>>

    @Query("SELECT * FROM read_history ORDER BY read_at DESC")
    suspend fun readSnapshot(): List<ReadEntity>

    @Query("SELECT COUNT(*) FROM read_history")
    fun readCount(): Flow<Int>

    @Query("SELECT story_id FROM read_history")
    fun readIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM read_history WHERE story_id = :id)")
    fun observeIsRead(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM read_history WHERE story_id = :id)")
    suspend fun isRead(id: String): Boolean

    @Upsert
    suspend fun upsertRead(entity: ReadEntity)

    @Query("DELETE FROM read_history WHERE story_id = :id")
    suspend fun deleteRead(id: String)

    @Query("DELETE FROM read_history")
    suspend fun clearRead()

    /** Keeps only the newest [max] read entries (JS `all.slice(500)`). */
    @Query(
        "DELETE FROM read_history WHERE story_id NOT IN " +
            "(SELECT story_id FROM read_history ORDER BY read_at DESC LIMIT :max)",
    )
    suspend fun trimRead(max: Int)
}
