package com.code7.techupdates.domain

import java.net.URI

/** Inline formatting found in Hacker News comment HTML. */
enum class RichStyle { ITALIC, BOLD, CODE, LINK }

/** [style] applies to `text[start, end)`; [url] is set (absolute, http/https) for [RichStyle.LINK]. */
data class RichSpan(
    val style: RichStyle,
    val start: Int,
    val end: Int,
    val url: String? = null,
)

/** Plain text plus formatting spans: a framework-free stand-in for Compose's `AnnotatedString`. */
data class RichText(
    val text: String,
    val spans: List<RichSpan> = emptyList(),
)

private val Tag = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>""")
private val Href = Regex("""href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

/** Base for relative links in HN text (the web app's `fillHtml(…, "https://news.ycombinator.com/")`). */
private object Hn {
    const val BASE_URL = "https://news.ycombinator.com/"
}

/**
 * Converts the HTML of an HN comment or self post to [RichText]. HN uses a small subset: paragraphs
 * start with `<p>` (the first one has none), plus `<a href>`, `<i>`, `<b>`, `<pre><code>` and
 * entities such as `&#x27;`. Paragraphs are separated by a blank line; whitespace collapses outside
 * `<pre>`. Links are resolved against [baseUrl] and kept only when http(s) (like the web app's
 * sanitizer); unknown tags are dropped but their text kept.
 */
fun parseHnHtml(
    html: String?,
    baseUrl: String = Hn.BASE_URL,
): RichText {
    if (html.isNullOrBlank()) return RichText("")
    val builder = RichTextBuilder()
    var pos = 0
    for (m in Tag.findAll(html)) {
        builder.text(decodeHtmlEntities(html.substring(pos, m.range.first)))
        pos = m.range.last + 1
        val (slash, tag, attrs) = m.destructured
        val name = tag.lowercase()
        if (slash == "/") builder.close(name) else builder.open(name, hrefOf(attrs, baseUrl))
    }
    builder.text(decodeHtmlEntities(html.substring(pos)))
    return builder.build()
}

private class OpenSpan(
    val tag: String,
    val style: RichStyle,
    val start: Int,
    val url: String?,
)

private class RichTextBuilder {
    private val sb = StringBuilder()
    private val spans = mutableListOf<RichSpan>()
    private val open = ArrayDeque<OpenSpan>()
    private var pendingSpace = false
    private var pendingBreak = false
    private var preDepth = 0
    private val atLineStart: Boolean get() = sb.isEmpty() || sb.last() == '\n'
    private val inlineGapAllowed: Boolean get() = preDepth == 0 && !atLineStart

    fun text(raw: String) {
        if (raw.isEmpty()) return
        if (preDepth > 0) {
            flushBreak()
            sb.append(raw)
            return
        }
        for (c in raw) {
            if (c.isWhitespace()) {
                pendingSpace = true
            } else {
                flushBreak()
                if (pendingSpace && !atLineStart) sb.append(' ')
                pendingSpace = false
                sb.append(c)
            }
        }
    }

    /** [href] is the tag's resolved, safe `href` (only used for `<a>`). */
    fun open(
        tag: String,
        href: String?,
    ) {
        when (tag) {
            "p", "div" -> {
                paragraph()
            }

            "br" -> {
                if (sb.isNotEmpty()) sb.append('\n')
                pendingSpace = false
            }

            "pre" -> {
                paragraph()
                preDepth++
                push(tag, RichStyle.CODE)
            }

            "code", "tt" -> {
                push(tag, RichStyle.CODE)
            }

            "i", "em" -> {
                push(tag, RichStyle.ITALIC)
            }

            "b", "strong" -> {
                push(tag, RichStyle.BOLD)
            }

            "a" -> {
                push(tag, RichStyle.LINK, href)
            }
        }
    }

    fun close(tag: String) {
        when (tag) {
            "p", "div" -> {
                paragraph()
            }

            "pre" -> {
                trimTrailingWhitespace()
                pop(tag)
                preDepth = maxOf(0, preDepth - 1)
                paragraph()
            }

            else -> {
                pop(tag)
            }
        }
    }

    fun build(): RichText {
        trimTrailingWhitespace()
        while (open.isNotEmpty()) closeSpan(open.removeLast())
        return RichText(sb.toString(), spans.sortedBy { it.start })
    }

    private fun paragraph() {
        pendingBreak = true
        pendingSpace = false
    }

    private fun flushBreak() {
        if (!pendingBreak) return
        pendingBreak = false
        pendingSpace = false
        if (sb.isEmpty()) return
        trimTrailingWhitespace()
        sb.append("\n\n")
    }

    private fun push(
        tag: String,
        style: RichStyle,
        url: String? = null,
    ) {
        // A paragraph break or space before the tag belongs before the formatted text (`a <i>b</i>`).
        flushBreak()
        if (pendingSpace && inlineGapAllowed) {
            sb.append(' ')
            pendingSpace = false
        }
        open.addLast(OpenSpan(tag, style, sb.length, url))
    }

    private fun pop(tag: String) {
        val index = open.indexOfLast { it.tag == tag }
        if (index < 0) return
        // Close the tag and anything left open inside it (lenient, like a browser).
        while (open.size > index) closeSpan(open.removeLast())
    }

    private fun closeSpan(span: OpenSpan) {
        val start = minOf(span.start, sb.length)
        if (span.style == RichStyle.LINK && span.url == null) return
        if (sb.length > start) spans += RichSpan(span.style, start, sb.length, span.url)
    }

    private fun trimTrailingWhitespace() {
        while (sb.isNotEmpty() && sb.last().isWhitespace()) sb.setLength(sb.length - 1)
        val len = sb.length
        spans.replaceAll { if (it.end > len) it.copy(end = len) else it }
        spans.removeAll { it.start >= it.end }
    }
}

/** The `href` of a tag's [attrs], resolved against [baseUrl]; null unless http(s). */
private fun hrefOf(
    attrs: String,
    baseUrl: String,
): String? {
    val value =
        Href
            .find(attrs)
            ?.groupValues
            ?.drop(1)
            ?.firstOrNull { it.isNotEmpty() }
    val raw = decodeHtmlEntities(value.orEmpty()).trim()
    val resolved = runCatching { URI(baseUrl).resolve(raw.replace(" ", "%20")).toString() }.getOrNull()
    return if (raw.isEmpty()) null else safeUrl(resolved ?: raw)
}
