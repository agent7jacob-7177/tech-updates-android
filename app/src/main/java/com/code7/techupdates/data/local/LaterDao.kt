package com.code7.techupdates.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** JS `tu.lib.later`. */
@Dao
interface LaterDao {
    @Query("SELECT * FROM read_later ORDER BY saved_at DESC")
    fun laterList(): Flow<List<LaterEntity>>

    @Query("SELECT COUNT(*) FROM read_later")
    fun laterCount(): Flow<Int>

    @Query("SELECT story_id FROM read_later")
    fun laterIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM read_later WHERE story_id = :id)")
    fun observeIsLater(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM read_later WHERE story_id = :id)")
    suspend fun isLater(id: String): Boolean

    @Upsert
    suspend fun upsertLater(entity: LaterEntity)

    @Query("DELETE FROM read_later WHERE story_id = :id")
    suspend fun deleteLater(id: String)
}
