package com.code7.techupdates.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HnHtmlTest {
    private fun RichText.spanText(span: RichSpan) = text.substring(span.start, span.end)

    @Test
    fun emptyOrNull_isEmpty() {
        assertEquals(RichText(""), parseHnHtml(null))
        assertEquals(RichText(""), parseHnHtml("  "))
    }

    @Test
    fun entities_areDecoded() {
        val r = parseHnHtml("It&#x27;s a JSON&#x2F;AJAX call &amp; &quot;more&quot; &lt;3 &#39;x&#39; &bogus;")
        assertEquals("It's a JSON/AJAX call & \"more\" <3 'x' &bogus;", r.text)
        assertEquals(emptyList<RichSpan>(), r.spans)
    }

    @Test
    fun paragraphs_becomeBlankLines() {
        assertEquals("First.\n\nSecond.\n\nThird.", parseHnHtml("First.<p>Second.</p><p>Third.").text)
        assertEquals("Leading p", parseHnHtml("<p>Leading p</p>").text)
    }

    @Test
    fun whitespace_collapsesOutsidePre() {
        assertEquals("a b c", parseHnHtml("  a \n  b\tc  ").text)
        assertEquals("a\nb", parseHnHtml("a<br>b").text)
    }

    @Test
    fun italicAndBold_spans() {
        val r = parseHnHtml("This is <i>really</i> <b>bold</b>.")
        assertEquals("This is really bold.", r.text)
        assertEquals(listOf(RichStyle.ITALIC, RichStyle.BOLD), r.spans.map { it.style })
        assertEquals(listOf("really", "bold"), r.spans.map { r.spanText(it) })
    }

    @Test
    fun links_areResolvedAndSafe() {
        val r =
            parseHnHtml(
                "See <a href=\"https:&#x2F;&#x2F;example.com&#x2F;a?b=1&amp;c=2\" rel=\"nofollow\">example.com/a</a>" +
                    " and <a href=\"item?id=1\">this</a> but <a href=\"javascript:alert(1)\">not this</a>",
            )
        assertEquals("See example.com/a and this but not this", r.text)
        assertEquals(2, r.spans.size)
        assertEquals("https://example.com/a?b=1&c=2", r.spans[0].url)
        assertEquals("example.com/a", r.spanText(r.spans[0]))
        assertEquals("https://news.ycombinator.com/item?id=1", r.spans[1].url)
        assertEquals("this", r.spanText(r.spans[1]))
    }

    @Test
    fun spanAfterParagraph_startsAtItsText() {
        val r = parseHnHtml("a<p><i>b</i>")
        assertEquals("a\n\nb", r.text)
        assertEquals(RichSpan(RichStyle.ITALIC, 3, 4), r.spans.single())
    }

    @Test
    fun inlineCode_isCodeSpan() {
        val r = parseHnHtml("Run <code>ls -la</code> now")
        assertEquals("Run ls -la now", r.text)
        assertEquals(RichSpan(RichStyle.CODE, 4, 10), r.spans.single())
    }

    @Test
    fun preCode_keepsWhitespaceAsItsOwnParagraph() {
        val r = parseHnHtml("Try:<p><pre><code>  fn main() {\n      x();\n  }\n</code></pre>Done.")
        assertEquals("Try:\n\n  fn main() {\n      x();\n  }\n\nDone.", r.text)
        val code = r.spans.filter { it.style == RichStyle.CODE }
        assertEquals(2, code.size) // <pre> and the nested <code>
        code.forEach { assertEquals("  fn main() {\n      x();\n  }", r.spanText(it)) }
    }

    @Test
    fun unclosedAndUnknownTags_areLenient() {
        val r = parseHnHtml("<span>plain</span> <i>open to the end")
        assertEquals("plain open to the end", r.text)
        assertEquals("open to the end", r.spanText(r.spans.single()))
    }

    @Test
    fun nestedFormatting_insideLink() {
        val r = parseHnHtml("<a href=\"https://x.org\"><i>both</i></a>")
        assertEquals("both", r.text)
        assertEquals(setOf(RichStyle.LINK, RichStyle.ITALIC), r.spans.map { it.style }.toSet())
        r.spans.forEach { assertEquals("both", r.spanText(it)) }
    }
}
