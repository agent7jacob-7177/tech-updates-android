package com.code7.techupdates.data

import androidx.room.withTransaction
import com.code7.techupdates.data.local.AppDatabase
import com.code7.techupdates.data.local.LaterEntity
import com.code7.techupdates.data.local.ReadEntity
import com.code7.techupdates.data.local.toSnapshot
import com.code7.techupdates.domain.Story
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How a story ended up in the read history (JS `markRead(h, how)`). */
enum class ReadHow(
    val id: String,
) {
    /** Reader scrolled to the end. */
    AUTO("auto"),

    /** The user tapped "Mark read". */
    MANUAL("manual"),
    ;

    companion object {
        fun fromId(id: String): ReadHow = entries.firstOrNull { it.id == id } ?: MANUAL
    }
}

/** A "Read later" entry; [savedAt] is epoch millis. */
data class SavedStory(
    val story: Story,
    val savedAt: Long,
)

/** A read-history entry; [readAt] is epoch millis. */
data class ReadStory(
    val story: Story,
    val readAt: Long,
    val how: ReadHow,
)

/** What [LibraryRepository.markRead] changed, so it can be undone with [LibraryRepository.undoMarkRead]. */
data class ReadReceipt(
    val story: Story,
    val wasLater: Boolean,
)

/** What the feed screen needs from [LibraryRepository] (a seam for fakes in tests). */
interface LaterToggle {
    fun laterIds(): Flow<Set<String>>

    fun readIds(): Flow<Set<String>>

    /** Saves ([on] = true), un-saves (false) or toggles (null) [story]; returns the new state. */
    suspend fun toggleLater(
        story: Story,
        on: Boolean? = null,
    ): Boolean
}

/** What the library screen needs from [LibraryRepository] (a seam for fakes in tests). */
interface LibrarySource : LaterToggle {
    /** "Read later", newest first. */
    fun later(): Flow<List<SavedStory>>

    /** Read history, newest first. */
    fun read(): Flow<List<ReadStory>>

    /** Removes [storyId] from "Read later" and forgets its `save` signal; null if it wasn't there. */
    suspend fun removeLater(storyId: String): SavedStory?

    /** Undoes [removeLater]: back in place (original time) and the `save` signal re-recorded. */
    suspend fun undoRemoveLater(entry: SavedStory)

    suspend fun markRead(
        story: Story,
        how: ReadHow,
    ): ReadReceipt?

    suspend fun undoMarkRead(receipt: ReadReceipt)

    /** Removes [storyId] from the read history and forgets its `read` signal; null if it wasn't there. */
    suspend fun removeRead(storyId: String): ReadStory?

    /** Undoes [removeRead]: back in place (original time) and the `read` signal re-recorded. */
    suspend fun undoRemoveRead(entry: ReadStory)

    suspend fun clearRead(): List<ReadStory>

    suspend fun restoreRead(entries: List<ReadStory>)
}

/**
 * The on-device library (JS `lib`, `tu.lib`): "Read later" and "Read" lists. Saves and reads are
 * also reported to the [learner] as positive signals, as the web app's `toggleLater` / `markRead` do.
 * [now] returns epoch millis.
 */
@Suppress("TooManyFunctions") // the library's public API, mirroring JS `lib`
class LibraryRepository(
    private val db: AppDatabase,
    private val learner: LearnerStore,
    private val now: () -> Long = System::currentTimeMillis,
) : LaterToggle,
    LibrarySource {
    private val laterDao = db.laterDao()
    private val readDao = db.readDao()

    val laterCount: Flow<Int> = laterDao.laterCount()
    val readCount: Flow<Int> = readDao.readCount()

    /** Newest first. */
    override fun later(): Flow<List<SavedStory>> = laterDao.laterList().map { rows -> rows.map { it.toSavedStory() } }

    /** Newest first. */
    override fun read(): Flow<List<ReadStory>> = readDao.readList().map { rows -> rows.map { it.toReadStory() } }

    override fun laterIds(): Flow<Set<String>> = laterDao.laterIds().map { it.toSet() }

    override fun readIds(): Flow<Set<String>> = readDao.readIds().map { it.toSet() }

    fun isLater(storyId: String): Flow<Boolean> = laterDao.observeIsLater(storyId)

    fun isRead(storyId: String): Flow<Boolean> = readDao.observeIsRead(storyId)

    /**
     * Saves or un-saves [story] (JS `toggleLater(h, on = !isLater)`); returns the new state. Saving
     * records a `save` signal, un-saving forgets it.
     */
    override suspend fun toggleLater(
        story: Story,
        on: Boolean?,
    ): Boolean {
        val target =
            db.withTransaction {
                val target = on ?: !laterDao.isLater(story.id)
                if (target) {
                    laterDao.upsertLater(LaterEntity(story.id, story.toSnapshot(), now()))
                } else {
                    laterDao.deleteLater(story.id)
                }
                target
            }
        if (target) learner.record(SignalKind.SAVE, story) else learner.forget(SignalKind.SAVE, story.id)
        return target
    }

    /**
     * Moves [story] to the read history (leaving "Read later"), records a `read` signal and keeps the
     * newest [MAX_READ] entries. Returns null if it was already read (nothing changed).
     */
    override suspend fun markRead(
        story: Story,
        how: ReadHow,
    ): ReadReceipt? {
        val receipt =
            db.withTransaction {
                if (readDao.isRead(story.id)) return@withTransaction null
                val wasLater = laterDao.isLater(story.id)
                readDao.upsertRead(ReadEntity(story.id, story.toSnapshot(), now(), how.id))
                laterDao.deleteLater(story.id)
                readDao.trimRead(MAX_READ)
                ReadReceipt(story, wasLater)
            } ?: return null
        learner.record(SignalKind.READ, story)
        return receipt
    }

    /** Removes [storyId] from the read history and forgets its `read` signal ("Mark as unread"). */
    suspend fun unmarkRead(storyId: String) {
        readDao.deleteRead(storyId)
        learner.forget(SignalKind.READ, storyId)
    }

    /** Undoes [markRead]: unread, and back into "Read later" if it was there. */
    override suspend fun undoMarkRead(receipt: ReadReceipt) {
        db.withTransaction {
            readDao.deleteRead(receipt.story.id)
            if (receipt.wasLater) laterDao.upsertLater(LaterEntity(receipt.story.id, receipt.story.toSnapshot(), now()))
        }
        learner.forget(SignalKind.READ, receipt.story.id)
    }

    /** Clears the read history; returns the removed entries for [restoreRead] (undo). */
    override suspend fun clearRead(): List<ReadStory> =
        db.withTransaction {
            val all = readDao.readSnapshot()
            readDao.clearRead()
            all.map { it.toReadStory() }
        }

    /** Undoes [clearRead]. Clearing keeps the learner's `read` signals (as JS), so nothing to restore there. */
    override suspend fun restoreRead(entries: List<ReadStory>) =
        db.withTransaction {
            entries.forEach { readDao.upsertRead(ReadEntity(it.story.id, it.story.toSnapshot(), it.readAt, it.how.id)) }
        }

    override suspend fun removeLater(storyId: String): SavedStory? {
        val removed =
            db.withTransaction {
                laterDao.later(storyId)?.also { laterDao.deleteLater(storyId) }
            } ?: return null
        learner.forget(SignalKind.SAVE, storyId)
        return removed.toSavedStory()
    }

    override suspend fun undoRemoveLater(entry: SavedStory) {
        laterDao.upsertLater(LaterEntity(entry.story.id, entry.story.toSnapshot(), entry.savedAt))
        learner.record(SignalKind.SAVE, entry.story)
    }

    override suspend fun removeRead(storyId: String): ReadStory? {
        val removed =
            db.withTransaction {
                readDao.read(storyId)?.also { readDao.deleteRead(storyId) }
            } ?: return null
        learner.forget(SignalKind.READ, storyId)
        return removed.toReadStory()
    }

    override suspend fun undoRemoveRead(entry: ReadStory) {
        restoreRead(listOf(entry))
        learner.record(SignalKind.READ, entry.story)
    }

    private fun LaterEntity.toSavedStory() = SavedStory(story.toStory(storyId), savedAt)

    private fun ReadEntity.toReadStory() = ReadStory(story.toStory(storyId), readAt, ReadHow.fromId(how))

    companion object {
        /** JS keeps the newest 500 read entries. */
        const val MAX_READ = 500
    }
}
