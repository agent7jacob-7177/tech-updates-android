package com.code7.techupdates.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTest {
    private fun words(n: Int) = List(n) { "w$it" }.joinToString(" ")

    @Test
    fun wordCount_splitsOnAnyWhitespace() {
        assertEquals(0, Reading.wordCount(""))
        assertEquals(0, Reading.wordCount("  \n\t "))
        assertEquals(3, Reading.wordCount("  one\ttwo\n\nthree "))
    }

    @Test
    fun readingMinutes_hiddenAtOrBelow60Words() {
        assertNull(Reading.readingMinutes(0))
        assertNull(Reading.readingMinutes(60))
        assertEquals(1, Reading.readingMinutes(61))
    }

    @Test
    fun readingMinutes_roundsLikeJsMathRound() {
        assertEquals(1, Reading.readingMinutes(230))
        assertEquals(1, Reading.readingMinutes(344)) // 1.496
        assertEquals(2, Reading.readingMinutes(345)) // 1.5 rounds up
        assertEquals(4, Reading.readingMinutes(1_000)) // 4.35
        assertEquals(5, Reading.readingMinutes(1_100)) // 4.78
    }

    @Test
    fun readingMinutes_matchesWordCountOfText() {
        assertEquals(2, Reading.readingMinutes(Reading.wordCount(words(400))))
    }

    @Test
    fun markReadDelay_isSixtyMsPerWordClampedTo6To45Seconds() {
        assertEquals(6_000L, Reading.markReadDelayMillis(0))
        assertEquals(6_000L, Reading.markReadDelayMillis(100))
        assertEquals(30_000L, Reading.markReadDelayMillis(500))
        assertEquals(45_000L, Reading.markReadDelayMillis(750))
        assertEquals(45_000L, Reading.markReadDelayMillis(5_000))
    }

    @Test
    fun tidyMarkdown_dropsLeadingHeadingThatRepeatsTheTitle() {
        val md = "\n# Credit Card Skimming!\n\nBody text."
        assertEquals("Body text.", tidyMarkdown(md, "Credit card skimming scripts using WebRTC"))
        assertEquals("Body", tidyMarkdown("## The Title\nBody", "The title"))
    }

    @Test
    fun tidyMarkdown_dropsSetextHeadingThatRepeatsTheTitle() {
        assertEquals("Body", tidyMarkdown("The Title\n=========\n\nBody", "The title"))
    }

    @Test
    fun tidyMarkdown_keepsUnrelatedOrLaterHeadings() {
        assertEquals("# Intro\n\nBody", tidyMarkdown("# Intro\n\nBody", "Something else"))
        assertEquals("Lead\n\n# Title", tidyMarkdown("Lead\n\n# Title", "Title"))
        assertEquals("### Title\nBody", tidyMarkdown("### Title\nBody", "Title"))
    }

    @Test
    fun tidyMarkdown_dropsEmptyListItems() {
        assertEquals("- one\n- two", tidyMarkdown("- one\n-\n*  \n1.\n- two", "x"))
    }

    @Test
    fun resolveMarkdownUrls_makesLinksAndImagesAbsolute() {
        val md = "See [docs](/docs/a) and ![chart](img/c.png \"Chart\")."
        assertEquals(
            "See [docs](https://example.com/docs/a) and ![chart](https://example.com/post/img/c.png).",
            resolveMarkdownUrls(md, "https://example.com/post/index.html"),
        )
    }

    @Test
    fun resolveMarkdownUrls_unwrapsUnsafeLinksAndAnchors() {
        val md = "[js](javascript:void) [top](#top) [mail](mailto:a@b.c)"
        assertEquals("js top mail", resolveMarkdownUrls(md, "https://example.com/"))
    }

    @Test
    fun resolveMarkdownUrls_removesUnsafeImagesAndKeepsLinkedImages() {
        assertEquals("a  b", resolveMarkdownUrls("a ![x](data:image/png;base64,AAA) b", "https://example.com/"))
        assertEquals(
            "[![logo](https://example.com/l.png)](https://example.com/home)",
            resolveMarkdownUrls("[![logo](/l.png)](/home)", "https://example.com/x"),
        )
    }

    @Test
    fun resolveUrl_withoutBase_keepsOnlyAbsoluteHttp() {
        assertEquals("https://a.example/x", resolveUrl("https://a.example/x", null))
        assertNull(resolveUrl("/relative", null))
        assertNull(resolveUrl("", "https://a.example/"))
    }

    @Test
    fun markdownPlainText_dropsSyntaxAndImagesKeepsLinkText() {
        val text =
            markdownPlainText(
                "# Head\n\n**Bold** [link text](https://x.y) ![img alt](https://x.y/i.png)\n\n---\n> quote",
            )
        assertEquals(listOf("Head", "Bold", "link", "text", "quote"), text.split(" ").filter { it.isNotEmpty() })
        assertFalse(text.contains("https"))
        assertTrue(Reading.wordCount(text) == 5)
    }
}
