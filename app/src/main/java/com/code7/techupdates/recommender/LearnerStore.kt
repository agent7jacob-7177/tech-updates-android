package com.code7.techupdates.recommender

import com.code7.techupdates.domain.Story
import kotlinx.coroutines.flow.Flow

/** A positive learning signal (JS event `k`): opening, saving or finishing a story. */
enum class SignalKind(
    /** Stable id, as the JS `k` field. */
    val id: String,
) {
    OPEN("open"),
    SAVE("save"),
    READ("read"),
    ;

    companion object {
        fun fromId(id: String): SignalKind? = entries.firstOrNull { it.id == id }
    }
}

/** JS `Learner.events[]` entry `{ k, h, at }`; [at] is epoch millis. */
data class LearnerEvent(
    val kind: SignalKind,
    val story: Story,
    val at: Long,
)

/** JS `Learner.imp[id]` entry `{ n, h, at }`: times a story was seen without being opened. */
data class Impression(
    val story: Story,
    val count: Int,
    val at: Long,
)

/** Persistence for the "For you" learner (port of the JS `Learner` storage half). */
interface LearnerStore {
    /** All events, oldest first; at most one per (kind, story id), capped to the newest [MAX_EVENTS]. */
    fun events(): Flow<List<LearnerEvent>>

    /** Impressions, capped to the newest [MAX_IMPRESSIONS]. */
    fun impressions(): Flow<List<Impression>>

    /** Records a signal, replacing an earlier one of the same kind for the same story (JS `record`). */
    suspend fun record(
        kind: SignalKind,
        story: Story,
    )

    /** Removes the signal of [kind] for [storyId], e.g. when a save or read is undone (JS `forget`). */
    suspend fun forget(
        kind: SignalKind,
        storyId: String,
    )

    /**
     * Counts one more impression of [story], at most once per app session (JS `impression`).
     * Returns false if it was already counted this session.
     */
    suspend fun recordImpression(story: Story): Boolean

    /** Forgets everything learned (JS `reset`). */
    suspend fun reset()

    companion object {
        const val MAX_EVENTS = 600
        const val MAX_IMPRESSIONS = 400
    }
}
