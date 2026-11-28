package com.code7.techupdates.recommender

import kotlin.math.log10
import kotlin.math.min

/** A candidate with its "For you" score; [why] is empty unless the story matches the learned taste. */
data class Ranked<T>(
    val value: T,
    val score: Double,
    val why: List<String>,
)

/** The ranking half of the web app's `loadForYou()`. */
object Ranking {
    /** Engaged stories needed before the model ranks the feed (JS `MIN_SIGNALS`). */
    const val MIN_SIGNALS = 3

    /** Picks shown. */
    const val LIMIT = 40

    private const val AFFINITY_WEIGHT = 0.8
    private const val POPULARITY_WEIGHT = 0.6
    private const val MIN_AFFINITY_FOR_REASON = 0.05
    private const val POINTS_DECADES = 3

    /** JS `pop(h)`: 0 to 1, logarithmic in points, saturating around 1000. */
    fun popularity(points: Int): Double = min(1.0, log10(points + 1.0) / POINTS_DECADES)

    /**
     * Best first, at most [LIMIT]. With a [model] the score blends learned affinity and popularity;
     * without one (cold start) it is popularity alone. Ties keep the order of [candidates].
     */
    fun <T> rank(
        candidates: List<T>,
        model: Model?,
        item: (T) -> Item,
        points: (T) -> Int,
    ): List<Ranked<T>> =
        candidates
            .map { candidate ->
                val popularity = popularity(points(candidate))
                if (model == null) {
                    Ranked(candidate, popularity, emptyList())
                } else {
                    val score = model.score(item(candidate))
                    Ranked(
                        value = candidate,
                        score = AFFINITY_WEIGHT * score.affinity + POPULARITY_WEIGHT * popularity,
                        why = if (score.affinity > MIN_AFFINITY_FOR_REASON) score.why else emptyList(),
                    )
                }
            }.sortedByDescending { it.score }
            .take(LIMIT)
}
