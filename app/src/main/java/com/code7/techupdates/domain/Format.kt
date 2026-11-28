package com.code7.techupdates.domain

import java.net.URI

private object DefaultPort {
    const val HTTP = 80
    const val HTTPS = 443
}

private class ParsedUrl(
    val scheme: String,
    val host: String,
    val uri: URI,
)

/** Lenient absolute-URL parse approximating the WHATWG `new URL(url)` used by the web app. */
private fun parseUrl(url: String?): ParsedUrl? {
    val trimmed = url?.trim().orEmpty()
    val uri = if (trimmed.isEmpty()) null else runCatching { URI(trimmed.replace(" ", "%20")) }.getOrNull()
    val scheme = uri?.scheme?.lowercase() ?: return null
    val host = uri.host ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')
    return ParsedUrl(scheme, host.orEmpty().lowercase(), uri)
}

/** Port of JS `domainOf(url)`: hostname without a leading `www.`, or "" when unparseable. */
fun domainOf(url: String?): String = parseUrl(url)?.host?.removePrefix("www.").orEmpty()

/**
 * Port of JS `safeUrl(url)`: the normalised URL when its scheme is http/https, else null
 * (blocks `javascript:`, `data:` etc.).
 */
fun safeUrl(url: String?): String? {
    val p = parseUrl(url)?.takeIf { (it.scheme == "http" || it.scheme == "https") && it.host.isNotEmpty() }
    return p?.let {
        val defaultPort = if (it.scheme == "http") DefaultPort.HTTP else DefaultPort.HTTPS
        val port = it.uri.port
        buildString {
            append(it.scheme).append("://")
            it.uri.rawUserInfo?.let { info -> append(info).append('@') }
            append(it.host)
            if (port != -1 && port != defaultPort) append(':').append(port)
            append(it.uri.rawPath?.ifEmpty { "/" } ?: "/")
            it.uri.rawQuery?.let { q -> append('?').append(q) }
            it.uri.rawFragment?.let { f -> append('#').append(f) }
        }
    }
}

private val TimeUnits =
    listOf(
        "y" to 31_536_000L,
        "mo" to 2_592_000L,
        "d" to 86_400L,
        "h" to 3_600L,
        "m" to 60L,
    )

/** Port of JS `timeAgo(sec)`, e.g. "5m ago", "3d ago", "just now". */
fun timeAgo(
    epochSec: Long,
    nowEpochSec: Long,
): String {
    val s = maxOf(1L, nowEpochSec - epochSec)
    for ((unit, n) in TimeUnits) if (s >= n) return "${s / n}$unit ago"
    return "just now"
}

private val BlockTag = Regex("""<\s*(p|br|/p|div|/div|li|pre|/pre)\b[^>]*>""", RegexOption.IGNORE_CASE)
private val AnyTag = Regex("""<[^>]*>""")
private val Entity = Regex("""&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);""")
private val Whitespace = Regex("""\s+""")
private val NamedEntities =
    mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

private object Radix {
    const val HEX = 16
}

private fun decodeEntity(body: String): String? =
    when {
        body.startsWith("#x", ignoreCase = true) -> body.drop(2).toIntOrNull(Radix.HEX)
        body.startsWith("#") -> body.drop(1).toIntOrNull()
        else -> null
    }?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
        ?: NamedEntities[body]

/** Decodes `&amp;`, `&#x27;`, `&#39;` etc.; unknown entities are left as they are. */
fun decodeHtmlEntities(text: String): String =
    if ('&' !in text) text else Entity.replace(text) { m -> decodeEntity(m.groupValues[1]) ?: m.value }

/**
 * Port of JS `stripHtml(html)` (the DOM's `textContent`): tags removed and entities decoded. Unlike
 * `textContent`, block breaks (`<p>`, `<br>` …) become spaces and whitespace runs collapse, since the
 * result is shown as a one-paragraph excerpt.
 */
fun stripHtml(html: String?): String {
    if (html.isNullOrEmpty()) return ""
    val text = html.replace(BlockTag, " ").replace(AnyTag, "")
    return decodeHtmlEntities(text)
        .replace(Whitespace, " ")
        .trim()
}
