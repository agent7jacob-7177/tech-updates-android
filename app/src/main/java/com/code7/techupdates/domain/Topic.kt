package com.code7.techupdates.domain

/**
 * JavaScript `\b` semantics: a boundary between an ASCII word char `[A-Za-z0-9_]` and anything else.
 * (Java/ICU `\b` is Unicode-aware, so it is expanded explicitly to match the web app exactly.)
 */
private object JsRegex {
    const val WORD_BOUNDARY =
        "(?:(?<=[A-Za-z0-9_])(?![A-Za-z0-9_])|(?<![A-Za-z0-9_])(?=[A-Za-z0-9_]))"
}

private fun jsRegex(pattern: String): Regex =
    Regex(pattern.replace("\\b", JsRegex.WORD_BOUNDARY), RegexOption.IGNORE_CASE)

/** Topic filters, ported exactly from the web app's `TOPICS` (same order; first match wins). */
enum class Topic(
    val id: String,
    val label: String,
    /** Search terms appended to the user query; null for [ALL]. */
    val q: String?,
    /** Title matcher; null for [ALL]. */
    val match: Regex?,
    /** Short tag shown on cards; falls back to [label]. */
    private val shortTag: String? = null,
) {
    ALL(id = "", label = "All", q = null, match = null),
    AI(
        id = "AI",
        label = "AI",
        q = "AI",
        match = jsRegex("""\b(ai|llms?|gpt|chatgpt|openai|anthropic|claude|gemini|agi|chatbot|copilot)\b"""),
    ),
    ML(
        id = "ML",
        label = "Machine Learning",
        q = "machine learning",
        match =
            jsRegex(
                """\b(machine learning|ml|deep learning|neural (net|network)s?|pytorch|tensorflow|jax|""" +
                    """transformers?|diffusion|reinforcement learning|rlhf|fine-?tun\w*|embeddings?|inference|""" +
                    """training run|datasets?|kaggle|scikit-learn|xgboost|open weights|model weights)\b""",
            ),
        shortTag = "ML",
    ),
    SECURITY(
        id = "Security",
        label = "Security",
        q = "security",
        match = jsRegex("""\b(security|vulnerab|breach|hack|exploit|cve|malware|ransomware|privacy)\b"""),
    ),
    PROGRAMMING(
        id = "Programming",
        label = "Programming",
        q = "programming",
        match =
            jsRegex(
                """\b(rust|python|javascript|typescript|golang|go |compiler|programming|code|""" +
                    """developer|api|framework)\b""",
            ),
    ),
    HARDWARE(
        id = "Hardware",
        label = "Hardware",
        q = "chip",
        match = jsRegex("""\b(chip|cpu|gpu|nvidia|amd|intel|arm|apple silicon|semiconductor|hardware)\b"""),
    ),
    SPACE(
        id = "Space",
        label = "Space",
        q = "space",
        match = jsRegex("""\b(nasa|spacex|rocket|satellite|orbit|space|mars|moon)\b"""),
    ),
    STARTUPS(
        id = "Startups",
        label = "Startups",
        q = "startup",
        match = jsRegex("""\b(startup|funding|raises|vc|acquire|acquisition|ipo|series [a-d])\b"""),
    ),
    ;

    /** `t.tag || t.label` in the JS. */
    val tag: String get() = shortTag ?: label

    companion object {
        /** `TOPICS.find(t => t.id === id)`; null when unknown. */
        fun byId(id: String?): Topic? = entries.firstOrNull { it.id == id }

        /** First topic whose matcher matches [title], or null. */
        fun forTitle(title: String): Topic? = entries.firstOrNull { it.match?.containsMatchIn(title) == true }
    }
}

/** Port of JS `topicOf(title)`: tag (or label) of the first matching topic, "" when none match. */
fun topicOf(title: String): String = Topic.forTitle(title)?.tag.orEmpty()
