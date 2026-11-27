package com.code7.techupdates.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test
    fun domainOf_stripsWwwAndLowercases() {
        assertEquals("example.com", domainOf("https://www.example.com/a?b=c"))
        assertEquals("blog.example.com", domainOf("http://Blog.Example.com:8080/x"))
        assertEquals("www2.example.com", domainOf("https://www2.example.com"))
        assertEquals("example.com", domainOf("https://user:pw@example.com/"))
        assertEquals("my_host.dev", domainOf("https://my_host.dev/"))
        assertEquals("example.com", domainOf("https://example.com/path with space"))
    }

    @Test
    fun domainOf_invalidOrMissing_returnsEmpty() {
        assertEquals("", domainOf(null))
        assertEquals("", domainOf(""))
        assertEquals("", domainOf("not a url"))
        assertEquals("", domainOf("/relative/path"))
        assertEquals("", domainOf("mailto:someone@example.com"))
    }

    @Test
    fun safeUrl_allowsHttpAndHttps() {
        assertEquals("https://example.com/", safeUrl("https://example.com"))
        assertEquals("http://example.com/a?b=1#c", safeUrl("http://example.com/a?b=1#c"))
        assertEquals("https://example.com/x", safeUrl("HTTPS://EXAMPLE.com:443/x"))
        assertEquals("http://example.com:8080/", safeUrl("http://example.com:8080"))
        assertEquals("https://example.com/a%20b", safeUrl("https://example.com/a b"))
    }

    @Test
    fun safeUrl_rejectsOtherSchemesAndGarbage() {
        assertNull(safeUrl("javascript:alert(1)"))
        assertNull(safeUrl("data:text/html,hi"))
        assertNull(safeUrl("ftp://example.com/file"))
        assertNull(safeUrl("file:///etc/passwd"))
        assertNull(safeUrl("example.com"))
        assertNull(safeUrl("https://"))
        assertNull(safeUrl(""))
        assertNull(safeUrl(null))
    }

    @Test
    fun timeAgo_matchesJsUnits() {
        val now = 1_800_000_000L
        assertEquals("just now", timeAgo(now, now))
        assertEquals("just now", timeAgo(now - 59, now))
        assertEquals("1m ago", timeAgo(now - 60, now))
        assertEquals("59m ago", timeAgo(now - 3_599, now))
        assertEquals("1h ago", timeAgo(now - 3_600, now))
        assertEquals("23h ago", timeAgo(now - 86_399, now))
        assertEquals("1d ago", timeAgo(now - 86_400, now))
        assertEquals("29d ago", timeAgo(now - 2_591_999, now))
        assertEquals("1mo ago", timeAgo(now - 2_592_000, now))
        assertEquals("12mo ago", timeAgo(now - 31_535_999, now))
        assertEquals("1y ago", timeAgo(now - 31_536_000, now))
        assertEquals("3y ago", timeAgo(now - 3 * 31_536_000L, now))
    }

    @Test
    fun timeAgo_futureTimestamps_areJustNow() {
        assertEquals("just now", timeAgo(2_000, 1_000))
    }
}
