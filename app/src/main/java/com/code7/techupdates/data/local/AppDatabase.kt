package com.code7.techupdates.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LaterEntity::class, ReadEntity::class, LearnerEventEntity::class, ImpressionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun laterDao(): LaterDao

    abstract fun readDao(): ReadDao

    abstract fun learnerDao(): LearnerDao

    abstract fun impressionDao(): ImpressionDao

    companion object {
        const val NAME = "tech-updates.db"

        fun create(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
