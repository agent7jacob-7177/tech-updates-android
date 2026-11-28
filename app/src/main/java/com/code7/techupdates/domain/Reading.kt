package com.code7.techupdates.domain

import java.net.URI
import kotlin.math.roundToInt

/** Reader rules ported from the web app (`updateReadingTime`, `showReader`, the reader scroll handler). */
object Reading {
    const val WORDS_PER_MINUTE = 230

    /** JS shows a reading time only above this many words. */
    const val MIN_WORDS_FOR_READING_TIME = 60

    /** JS adds the "only a short preview" note below this many article words. */
    const val SHORT_PREVIEW_WORDS = 80

    private const val MARK_READ_MS_PER_WORD = 60L
    private const val MARK_READ_MIN_MS = 6_000L
    private const val MARK_READ_MAX_MS = 45_000L

    /** JS `textContent.trim().split(/\s+/).filter(Boolean).length`. */
    fun wordCount(text: String): Int = text.split(Whitespace).count { it.isNotEmpty() }

    /**
     * JS `updateReadingTime`: `max(1, round(words / 230))` minutes, or null for 60 words or fewer.
     * `Math.round` rounds halves up, as here.
     */
    fun readingMinutes(words: Int): Int? =
        if (words > MIN_WORDS_FOR_READING_TIME) maxOf(1, (words.toDouble() / WORDS_PER_MINUTE).roundToInt()) else null

    /**
     * How long the reader must have been open before reaching the end marks the story read (JS
     * `minMs = min(45000, max(6000, words * 60))`, ~20% of normal reading speed).
     */
    fun markReadDelayMillis(words: Int): Long =
        (words * MARK_READ_MS_PER_WORD).coerceIn(MARK_READ_MIN_MS, MARK_READ_MAX_MS)
}

private val Whitespace = Regex("""\s+""")
private val NonAlnum = Regex("""[^a-z0-9]+""")

private fun normalize(s: String) = s.lowercase().replace(NonAlnum, " ").trim()

private val AtxHeading = Regex("""^ {0,3}#{1,2}\s+(.*?)\s*#*\s*$""")
private val SetextUnderline = Regex("""^ {0,3}(=+|-+)\s*$""")
private val EmptyListItem = Regex("""^\s*(?:[-*+]|\d+[.)])\s*$""")

/**
 * Port of JS `tidyArticle`: drops a leading h1/h2 that repeats the headline ([title]) and empty
 * list items / leftovers.
 */
fun tidyMarkdown(
    md: String,
    title: String,
): String {
    val lines = md.lines().toMutableList()
    val first = lines.indexOfFirst { it.isNotBlank() }
    if (first >= 0) {
        val atx = AtxHeading.matchEntire(lines[first])?.groupValues?.get(1)
        val setext =
            lines
                .getOrNull(
                    first + 1,
                )?.takeIf { atx == null && SetextUnderline.matches(it) }
                ?.let { lines[first] }
        val heading = normalize(atx ?: setext.orEmpty())
        if (heading.isNotEmpty() && normalize(title).contains(heading)) {
            repeat(if (atx != null) 1 else 2) { lines.removeAt(first) }
        }
    }
    return lines
        .filterNot { EmptyListItem.matches(it) }
        .joinToString("\n")
        .trim()
}

private val Image = Regex("""!\[([^\]]*)]\(\s*<?([^)\s>]*)>?(?:\s+"[^"]*")?\s*\)""")
private val Link = Regex("""(?<!!)\[((?:[^\[\]]|\[[^\]]*])*)]\(\s*<?([^)\s>]*)>?(?:\s+"[^"]*")?\s*\)""")

/** Resolves [raw] against [baseUrl] and keeps it only if it is http(s) (JS `safeUrl(new URL(raw, base))`). */
fun resolveUrl(
    raw: String,
    baseUrl: String?,
): String? {
    val resolved =
        when {
            raw.isBlank() -> {
                null
            }

            baseUrl == null -> {
                raw
            }

            else -> {
                runCatching {
                    URI(
                        baseUrl.replace(" ", "%20"),
                    ).resolve(raw.trim().replace(" ", "%20")).toString()
                }.getOrNull()
            }
        }
    return safeUrl(resolved)
}

/**
 * Port of the link / image half of JS `fillHtml`: images and links get absolute http(s) URLs
 * resolved against [baseUrl]; unsafe or unresolvable images are removed, and unsafe links and
 * in-page anchors (`#…`) are unwrapped to their text.
 */
fun resolveMarkdownUrls(
    md: String,
    baseUrl: String?,
): String {
    val withImages =
        Image.replace(md) { m ->
            val src = resolveUrl(m.groupValues[2], baseUrl) ?: return@replace ""
            "![${m.groupValues[1]}]($src)"
        }
    return Link.replace(withImages) { m ->
        val text = m.groupValues[1]
        val raw = m.groupValues[2]
        val href = if (raw.startsWith("#")) null else resolveUrl(raw, baseUrl)
        if (href == null) text else "[$text]($href)"
    }
}

private val MdImage = Regex("""!\[[^\]]*]\([^)]*\)""")
private val MdLink = Regex("""\[([^\]]*)]\([^)]*\)""")
private val MdSyntax = Regex("""[#*_`>|~]+""")
private val MdRule = Regex("""^\s*[-=]{3,}\s*$""", RegexOption.MULTILINE)

/** Approximate rendered text of [md] (what JS counts with `textContent`): images dropped, links kept as text. */
fun markdownPlainText(md: String): String =
    stripHtml(
        md
            .replace(MdImage, " ")
            .replace(MdLink, "$1")
            .replace(MdRule, " ")
            .replace(MdSyntax, " "),
    )
