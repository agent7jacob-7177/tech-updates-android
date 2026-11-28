package com.code7.techupdates.recommender

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/** What the model needs to know about a story. */
data class Item(
    val id: String,
    val title: String,
    val url: String?,
)

/** A positive signal and its training weight (JS `SIGNALS`). */
enum class Signal(
    val weight: Double,
) {
    OPEN(weight = 0.4),
    SAVE(weight = 0.8),
    READ(weight = 1.0),
}

/** JS `Learner.events[]` entry; [at] is epoch millis. */
data class Event(
    val kind: Signal,
    val item: Item,
    val at: Long,
)

/** JS `Learner.imp[id]` entry: [item] was seen [n] times without being opened. */
data class Seen(
    val item: Item,
    val n: Int,
    val at: Long,
)

/** The learned affinity of a story (independent of the bias) and the top reasons for it. */
data class Score(
    val affinity: Double,
    val why: List<String>,
)

/**
 * A trained model (JS `Learner.model`). [weights] and [support] iterate in JS `Map` order. [support]
 * counts the engaged stories sharing a feature; [examples] is the training set size (JS `n`).
 */
class Model(
    val weights: Map<String, Double>,
    val bias: Double,
    val examples: Int,
    val support: Map<String, Int>,
) {
    /** Port of JS `Learner.score(h)`. */
    fun score(item: Item): Score {
        val features = featuresOf(item.title, item.url)
        if (features.isEmpty()) return Score(0.0, emptyList())
        val scale = 1 / sqrt(features.size.toDouble())
        val parts = features.map { it to (weights[it] ?: 0.0) * scale }
        val affinity = parts.fold(0.0) { sum, (_, c) -> sum + c }
        val why =
            parts
                .filter { (f, c) -> c > MIN_REASON_CONTRIBUTION && isRecurring(f) }
                .sortedByDescending { it.second }
                .map { featureLabel(it.first) }
                .distinct()
                .take(MAX_REASONS)
        return Score(affinity, why)
    }

    /** Port of JS `Learner.interests(n)`: labels of the strongest recurring features. */
    fun interests(n: Int = DEFAULT_INTERESTS): List<String> {
        val out = mutableListOf<String>()
        for ((feature, weight) in weights.entries.sortedByDescending { it.value }) {
            if (weight < MIN_INTEREST_WEIGHT || out.size >= n) break
            if (isRecurring(feature)) {
                val label = featureLabel(feature)
                if (out.none { label in it }) out.add(label)
            }
        }
        return out
    }

    /** Only features shared by several engaged stories are shown as interests or reasons. */
    private fun isRecurring(feature: String) = (support[feature] ?: 0) >= MIN_SUPPORT

    companion object {
        const val DEFAULT_INTERESTS = 10
        private const val MIN_REASON_CONTRIBUTION = 0.04
        private const val MIN_INTEREST_WEIGHT = 0.05
        private const val MIN_SUPPORT = 2
        private const val MAX_REASONS = 2
    }
}

/**
 * The "For you" model: logistic regression over [featuresOf], trained on what the user opens, saves
 * and reads (positives) against what they scroll past (negatives). A port of the web app's `Learner`
 * that reproduces its arithmetic and iteration order exactly.
 */
object Recommender {
    private const val LEARNING_RATE = 0.5
    private const val L2 = 0.002
    private const val EPOCHS = 14
    private const val BIAS_RATE = 0.1
    private const val MAX_NEGATIVE_WEIGHT = 0.45
    private const val NEGATIVE_WEIGHT_PER_VIEW = 0.15
    private const val MAX_ARRAY_INDEX = 4_294_967_295L

    private class Example(
        val features: List<String>,
        val label: Double,
        val strength: Double,
    )

    /** Port of JS `Learner.train()`. [events] are oldest first, [impressions] in insertion order. */
    fun train(
        events: List<Event>,
        impressions: List<Seen>,
    ): Model {
        val data = examples(events, impressions)
        val weights = LinkedHashMap<String, Double>()
        var bias = 0.0
        val random = ParkMiller()
        repeat(EPOCHS) {
            shuffle(data, random)
            for (example in data) {
                if (example.features.isNotEmpty()) bias += step(weights, bias, example)
            }
        }
        val support = LinkedHashMap<String, Int>()
        data
            .filter { it.label == 1.0 }
            .forEach { example -> example.features.forEach { support[it] = (support[it] ?: 0) + 1 } }
        return Model(weights, bias, data.size, support)
    }

    /** One gradient step on [example]: updates [weights] and returns the change to the bias. */
    private fun step(
        weights: MutableMap<String, Double>,
        bias: Double,
        example: Example,
    ): Double {
        val scale = 1 / sqrt(example.features.size.toDouble())
        var z = bias
        for (f in example.features) z += (weights[f] ?: 0.0) * scale
        val gradient = (example.label - 1 / (1 + exp(-z))) * example.strength
        for (f in example.features) {
            val weight = weights[f] ?: 0.0
            weights[f] = weight + LEARNING_RATE * (gradient * scale - L2 * weight)
        }
        return LEARNING_RATE * BIAS_RATE * gradient
    }

    /** One example per story: the strongest positive signal, else a negative from its impressions. */
    private fun examples(
        events: List<Event>,
        impressions: List<Seen>,
    ): MutableList<Example> {
        val positives = LinkedHashMap<String, Event>()
        for (event in events) {
            val current = positives[event.item.id]
            if (current == null || current.kind.weight < event.kind.weight) positives[event.item.id] = event
        }
        val data = positives.values.map { example(it.item, 1.0, it.kind.weight) }.toMutableList()
        inJsKeyOrder(impressions)
            .filter { it.item.id !in positives && it.n >= 1 }
            .forEach {
                data.add(example(it.item, 0.0, min(MAX_NEGATIVE_WEIGHT, NEGATIVE_WEIGHT_PER_VIEW * it.n)))
            }
        return data
    }

    /**
     * The order of JS `Object.entries(imp)`: keys that are array indices (as Hacker News ids are)
     * come first in ascending numeric order, then the other keys in insertion order.
     */
    private fun inJsKeyOrder(impressions: List<Seen>): List<Seen> {
        val (numeric, other) = impressions.partition { arrayIndex(it.item.id) != null }
        return numeric.sortedBy { arrayIndex(it.item.id) } + other
    }

    private fun arrayIndex(key: String): Long? =
        key.toLongOrNull()?.takeIf { it in 0 until MAX_ARRAY_INDEX && it.toString() == key }

    private fun example(
        item: Item,
        label: Double,
        strength: Double,
    ) = Example(featuresOf(item.title, item.url), label, strength)

    /** Fisher–Yates, as in the JS. */
    private fun <T> shuffle(
        list: MutableList<T>,
        random: ParkMiller,
    ) {
        for (i in list.size - 1 downTo 1) {
            val j = floor(random.next() * (i + 1)).toInt()
            list[i] = list[j].also { list[j] = list[i] }
        }
    }

    /** The JS's seeded Park–Miller generator (products stay exact in both doubles and longs). */
    private class ParkMiller {
        private var seed = SEED

        fun next(): Double {
            seed = seed * MULTIPLIER % MODULUS
            return seed.toDouble() / MODULUS
        }

        private companion object {
            const val SEED = 7L
            const val MULTIPLIER = 16_807L
            const val MODULUS = 2_147_483_647L
        }
    }
}
