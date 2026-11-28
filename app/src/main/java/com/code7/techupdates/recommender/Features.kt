package com.code7.techupdates.recommender

import com.code7.techupdates.domain.Topic
import com.code7.techupdates.domain.domainOf
import com.code7.techupdates.domain.topicOf

/** JS `STOP`: words that carry no signal. */
val Stop: Set<String> =
    (
        "a an the and or of to in on for with by from at as is are was were be been being it its this " +
            "that these those " +
            "how why what when where who which your you we our i my me vs via into about over after before new show " +
            "ask hn not no can could will would should just using use used make made get got than then there their " +
            "they them his her do does did done one two first more most all any some out up down off only also like " +
            "way ways years year day days"
    ).split(" ").toSet()

private object JsText {
    /** JavaScript's `\s` (Unicode white space and line terminators), which is wider than Java's `\s`. */
    const val SPACE =
        "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
}

private val NonWordChars = Regex("[^a-z0-9+#.\\-${JsText.SPACE}]")
private val Spaces = Regex("[${JsText.SPACE}]+")
private val EdgeDotsDashes = Regex("^[.\\-]+|[.\\-]+$")
private val DigitsOnly = Regex("[0-9]+")
private val ShowHn = Regex("^show hn", RegexOption.IGNORE_CASE)
private val AskHn = Regex("^ask hn", RegexOption.IGNORE_CASE)

private object FeatureFormat {
    const val KIND_PREFIX_LENGTH = 2
}

/**
 * Port of JS `featuresOf(h)`: title words (`w:`), adjacent word pairs (`b:`), site (`d:`), topic
 * (`t:`) and Show/Ask HN (`k:`), without duplicates, in JS `Set` insertion order.
 */
fun featuresOf(
    title: String,
    url: String?,
): List<String> {
    val words =
        title
            .lowercase()
            .replace(NonWordChars, " ")
            .split(Spaces)
            .map { it.replace(EdgeDotsDashes, "") }
            .filter { it.length > 1 && it !in Stop && !DigitsOnly.matches(it) }
    val features = LinkedHashSet<String>()
    words.forEach { features.add("w:$it") }
    words.zipWithNext { a, b -> features.add("b:$a $b") }
    val domain = domainOf(url)
    if (domain.isNotEmpty()) features.add("d:$domain")
    val topic = topicOf(title)
    if (topic.isNotEmpty()) features.add("t:$topic")
    if (ShowHn.containsMatchIn(title)) features.add("k:Show HN")
    if (AskHn.containsMatchIn(title)) features.add("k:Ask HN")
    return features.toList()
}

/** Port of JS `featureLabel(f)`: the text shown to the user (topic tags expand to their label). */
fun featureLabel(feature: String): String {
    val value = feature.drop(FeatureFormat.KIND_PREFIX_LENGTH)
    if (!feature.startsWith("t")) return value
    return Topic.entries.firstOrNull { it.tag == value }?.label ?: value
}
