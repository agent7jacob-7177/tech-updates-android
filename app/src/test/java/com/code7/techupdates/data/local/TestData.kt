package com.code7.techupdates.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import com.code7.techupdates.domain.Story

fun inMemoryDb(): AppDatabase =
    Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun story(
    id: String,
    title: String = "Story $id",
    storyText: String? = null,
): Story =
    Story(
        id = id,
        title = title,
        url = "https://example.com/$id",
        author = "pg",
        points = 42,
        numComments = 7,
        createdAtEpochSec = 1_795_000_000L,
        storyText = storyText,
    )

/** Monotonic fake clock (epoch millis). */
class FakeClock(
    var t: Long = 1_000L,
) : () -> Long {
    override fun invoke(): Long = t++
}

/** Skips emissions until [expected] arrives (Room re-emits on every table invalidation). */
suspend fun <T> ReceiveTurbine<T>.awaitValue(expected: T) {
    do {
        val item = awaitItem()
    } while (item != expected)
}
