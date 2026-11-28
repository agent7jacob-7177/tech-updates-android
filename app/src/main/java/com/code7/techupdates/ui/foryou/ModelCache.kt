package com.code7.techupdates.ui.foryou

import com.code7.techupdates.data.Impression
import com.code7.techupdates.data.LearnerEvent
import com.code7.techupdates.data.toEvent
import com.code7.techupdates.data.toSeen
import com.code7.techupdates.recommender.Model
import com.code7.techupdates.recommender.Recommender
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the learner holds right now. */
data class LearnerData(
    val events: List<LearnerEvent>,
    val impressions: List<Impression>,
) {
    /** Distinct stories with a positive signal (JS `positives()`). */
    val positives: Int get() = events.distinctBy { it.story.id }.size
}

/** Trains the recommender on [data]. */
fun trainModel(data: LearnerData): Model =
    Recommender.train(data.events.map { it.toEvent() }, data.impressions.map { it.toSeen() })

/** Keeps the last trained model, retraining on [dispatcher] only when the data changed. */
class ModelCache(
    private val dispatcher: CoroutineDispatcher,
    private val train: (LearnerData) -> Model = ::trainModel,
) {
    private val mutex = Mutex()
    private var cached: Pair<LearnerData, Model>? = null

    suspend fun modelFor(data: LearnerData): Model =
        mutex.withLock {
            cached?.takeIf { it.first == data }?.second
                ?: withContext(dispatcher) { train(data) }.also { cached = data to it }
        }
}
