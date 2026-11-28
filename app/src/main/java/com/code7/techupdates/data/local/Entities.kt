package com.code7.techupdates.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.code7.techupdates.domain.Story

/** A persisted copy of a story (JS `slim(h)`); the story id is the owning entity's key. */
data class StorySnapshot(
    val title: String,
    val url: String?,
    val author: String,
    val points: Int,
    @ColumnInfo(name = "num_comments") val numComments: Int,
    @ColumnInfo(name = "created_at") val createdAtEpochSec: Long,
    @ColumnInfo(name = "story_text") val storyText: String?,
) {
    fun toStory(id: String): Story = Story(id, title, url, author, points, numComments, createdAtEpochSec, storyText)

    companion object {
        /** JS `slim` keeps at most this many characters of `story_text`. */
        const val MAX_STORY_TEXT = 4000
    }
}

/** JS `slim(h)`. */
fun Story.toSnapshot(): StorySnapshot =
    StorySnapshot(
        title = title,
        url = url,
        author = author,
        points = points,
        numComments = numComments,
        createdAtEpochSec = createdAtEpochSec,
        storyText = storyText?.take(StorySnapshot.MAX_STORY_TEXT),
    )

/** JS `tu.lib.later[id] = { h, at }`. */
@Entity(tableName = "read_later")
data class LaterEntity(
    @PrimaryKey @ColumnInfo(name = "story_id") val storyId: String,
    @Embedded val story: StorySnapshot,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
)

/** JS `tu.lib.read[id] = { h, at }`, plus how it was marked ([how] is a `ReadHow` id). */
@Entity(tableName = "read_history", indices = [Index("read_at")])
data class ReadEntity(
    @PrimaryKey @ColumnInfo(name = "story_id") val storyId: String,
    @Embedded val story: StorySnapshot,
    @ColumnInfo(name = "read_at") val readAt: Long,
    val how: String,
)

/** JS `tu.events[]` entry `{ k, h, at }`; unique per (kind, story id). [id] preserves insertion order. */
@Entity(
    tableName = "learner_events",
    indices = [Index(value = ["kind", "story_id"], unique = true), Index("at")],
)
data class LearnerEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    @ColumnInfo(name = "story_id") val storyId: String,
    @Embedded val story: StorySnapshot,
    val at: Long,
)

/** JS `tu.imp[id] = { n, h, at }`. */
@Entity(tableName = "impressions", indices = [Index("at")])
data class ImpressionEntity(
    @PrimaryKey @ColumnInfo(name = "story_id") val storyId: String,
    val count: Int,
    @Embedded val story: StorySnapshot,
    val at: Long,
)
